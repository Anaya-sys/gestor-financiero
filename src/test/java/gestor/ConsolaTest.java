package gestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gestor.consola.Consola;
import gestor.observador.ObservadorPresupuesto;
import gestor.observador.ObservadorVencimientos;
import gestor.persistencia.RepositorioFinanciero;
import gestor.servicio.GestorFinanciero;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsolaTest {
    static final LocalDate HOY = LocalDate.of(2026, 11, 22);

    @TempDir Path tmp;

    /** Ejecuta la consola con entradas simuladas y devuelve (gestor, texto de salida). */
    record Sesion(GestorFinanciero gestor, String salida) {}

    Sesion sesion(String... entradas) {
        GestorFinanciero gestor = new GestorFinanciero(new RepositorioFinanciero(tmp.resolve("datos.json")), () -> HOY);
        List<String> salida = new ArrayList<>();
        var obs = new ObservadorPresupuesto(gestor, salida::add);
        gestor.suscribir(obs);
        gestor.suscribir(new ObservadorVencimientos(3, salida::add));
        Iterator<String> it = Arrays.asList(entradas).iterator();
        new Consola(gestor, obs, prompt -> {
            salida.add(prompt);
            return it.hasNext() ? it.next() : null; // null = fin de la entrada
        }, salida::add).ejecutar();
        return new Sesion(gestor, String.join("\n", salida));
    }

    @Test void sesionCompletaConErroresDeEntradaYPersistencia() {
        Sesion s = sesion(
                "1", "abc", "-5", "100000", "", "1", "1", "1", "venta del día",  // ingreso (2 montos inválidos)
                "2", "30000", "2026-13-45", "", "1", "1", "1", "",               // gasto (fecha inválida)
                "99", "",                                                         // opción inválida y Enter
                "6", "1", "1", "40000", "2026-11-01", "2026-11-30", "", "0",      // presupuesto Inventario 40.000
                "2", "15000", "", "1", "1", "1", "",                              // 45.000 > 40.000: alerta de exceso
                "8", "2", "", "", "0", "0",                                       // utilidad y margen, y volver
                "9", "", "", "0");                                                // resumen
        String out = s.salida();
        assertTrue(out.contains("Monto (COP)"));
        assertTrue(out.split("\\[x\\] El monto debe ser", -1).length - 1 >= 2);
        assertTrue(out.contains("[x] Fecha inválida"));
        assertTrue(out.contains("[x] Opción no válida"));
        assertTrue(out.contains("[ALERTA] Presupuesto de 'Inventario' EXCEDIDO"));
        assertTrue(out.contains("Margen: 55.0%"));
        assertTrue(out.contains("Ingresos: $100.000   Gastos: $45.000   Balance: $55.000"));
        assertEquals(3, s.gestor().consultarMovimientos().size());

        // Reabrir: los datos persisten (PT-10)
        Sesion s2 = sesion("3", "1", "0", "", "", "0", "", "0", "0");
        assertEquals(3, s2.gestor().consultarMovimientos().size());
        assertTrue(s2.salida().contains("venta del día"));
    }

    @Test void finDeEntradaGuardaYSaleSinError() throws Exception {
        Sesion s = sesion("1", "5000"); // se corta la entrada a mitad de un registro
        assertTrue(s.salida().contains("Sesión interrumpida"));
        assertTrue(s.gestor().consultarMovimientos().isEmpty());
        assertTrue(Files.exists(tmp.resolve("datos.json")));
    }

    @Test void abonosYCuentasDesdeElMenu() {
        Sesion s = sesion(
                "5", "1", "2", "Doña Rosa", "450000", "2026-11-10", "0",   // cuenta por pagar vencida (alerta)
                "5", "2", "1", "abc", "150000", "", "0", "0");             // abono (con un monto inválido antes)
        assertTrue(s.salida().contains("VENCIDA hace 12"));
        assertTrue(s.salida().contains("Saldo pendiente: $300.000."));
        assertTrue(s.salida().contains("[x] Ingrese un número válido."));
        assertEquals(0, new java.math.BigDecimal("300000").compareTo(s.gestor().obtenerCuenta(1).saldo()));

        // Sesión nueva (datos persistidos): un abono mayor al saldo se rechaza y el saldo no cambia
        Sesion s2 = sesion("5", "2", "1", "999999", "", "0", "0");
        assertTrue(s2.salida().contains("[x] El abono supera el saldo pendiente."));
        assertEquals(0, new java.math.BigDecimal("300000").compareTo(s2.gestor().obtenerCuenta(1).saldo()));
    }
}
