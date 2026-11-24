package gestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gestor.dominio.Ambito;
import gestor.dominio.CuentaFinanciera;
import gestor.dominio.MedioPago;
import gestor.dominio.MetaAhorro;
import gestor.dominio.Montos;
import gestor.dominio.Movimiento;
import gestor.dominio.Presupuesto;
import gestor.dominio.TipoCuenta;
import gestor.dominio.TipoMovimiento;
import gestor.estrategia.EstrategiaBalance;
import gestor.estrategia.EstrategiaMargen;
import gestor.estrategia.EstrategiaProyeccion;
import gestor.excepcion.GestorException.AbonoInvalido;
import gestor.excepcion.GestorException.CategoriaInexistente;
import gestor.excepcion.GestorException.CuentaSaldada;
import gestor.excepcion.GestorException.ErrorPersistencia;
import gestor.excepcion.GestorException.MargenNoCalculable;
import gestor.excepcion.GestorException.MontoInvalido;
import gestor.excepcion.GestorException.NoEncontrado;
import gestor.excepcion.GestorException.OperacionInvalida;
import gestor.excepcion.GestorException.PeriodoInvalido;
import gestor.observador.ObservadorBitacora;
import gestor.observador.ObservadorPresupuesto;
import gestor.observador.ObservadorVencimientos;
import gestor.persistencia.RepositorioFinanciero;
import gestor.servicio.GestorFinanciero;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GestorFinancieroTest {
    static final LocalDate HOY = LocalDate.of(2026, 11, 22);

    static BigDecimal d(String valor) { return new BigDecimal(valor); }

    static void assertMonto(String esperado, BigDecimal real) {
        assertEquals(0, d(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    abstract static class Base {
        @TempDir Path tmp;
        Path ruta;
        GestorFinanciero gestor;

        @BeforeEach
        void preparar() {
            ruta = tmp.resolve("datos.json");
            gestor = nuevoGestor();
        }

        GestorFinanciero nuevoGestor() { return new GestorFinanciero(new RepositorioFinanciero(ruta), () -> HOY); }

        Movimiento ingreso(String monto) { return ingreso(monto, HOY, "Ventas", Ambito.NEGOCIO); }

        Movimiento ingreso(String monto, LocalDate fecha, String cat, Ambito ambito) {
            return gestor.registrarMovimiento(TipoMovimiento.INGRESO, d(monto), fecha, cat, ambito, MedioPago.EFECTIVO, "");
        }

        Movimiento gasto(String monto) { return gasto(monto, HOY, "Inventario", Ambito.NEGOCIO); }

        Movimiento gasto(String monto, LocalDate fecha, String cat, Ambito ambito) {
            return gestor.registrarMovimiento(TipoMovimiento.GASTO, d(monto), fecha, cat, ambito, MedioPago.EFECTIVO, "");
        }
    }

    @Nested
    class Movimientos extends Base {
        @Test void pt01_registrarIngresoApareceEnHistorial() {
            Movimiento m = ingreso("100000");
            assertEquals(List.of(m), gestor.consultarMovimientos());
            assertMonto("100000", m.monto());
        }

        @Test void pt02_montoInvalidoRechazado() {
            // Texto no numérico: se valida al convertir lo que escribe el usuario (la consola usa Montos.aMonto)
            for (String malo : new String[] {"abc", "", "nan", "inf", "-5", "0"}) {
                assertThrows(MontoInvalido.class, () -> Montos.aMonto(malo), "monto: '" + malo + "'");
            }
            // Valores numéricos inválidos: también los rechaza el servicio, sin registrar nada
            for (String malo : new String[] {"-5", "0"}) {
                assertThrows(MontoInvalido.class, () -> gasto(malo), "monto: '" + malo + "'");
            }
            assertTrue(gestor.consultarMovimientos().isEmpty());
        }

        @Test void categoriaInexistenteYTipoCruzado() {
            assertThrows(CategoriaInexistente.class, () -> gasto("10", HOY, "Ventas", Ambito.NEGOCIO)); // Ventas es de ingreso
            gestor.agregarCategoria("Papelería", TipoMovimiento.GASTO);
            gasto("10", HOY, "papelería", Ambito.NEGOCIO); // sin distinguir mayúsculas
        }

        @Test void filtrosYPeriodoInvalido() {
            ingreso("100", LocalDate.of(2026, 11, 1), "Ventas", Ambito.NEGOCIO);
            gasto("40", LocalDate.of(2026, 11, 10), "Inventario", Ambito.PERSONAL);
            assertEquals(1, gestor.consultarMovimientos(null, null, TipoMovimiento.GASTO, null, null).size());
            assertEquals(1, gestor.consultarMovimientos(null, null, null, null, Ambito.NEGOCIO).size());
            assertEquals(1, gestor.consultarMovimientos(LocalDate.of(2026, 11, 5), null, null, null, null).size());
            assertThrows(PeriodoInvalido.class, () ->
                    gestor.consultarMovimientos(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1), null, null, null));
        }

        @Test void editarYAnularMantienenBalanceConsistente() {
            ingreso("1000");
            Movimiento g = gasto("300");
            assertMonto("700", gestor.analizar(new EstrategiaBalance(), null, null, null));
            gestor.editarMovimiento(g.id(), d("400"), null, null, null);
            assertMonto("600", gestor.analizar(new EstrategiaBalance(), null, null, null));
            assertThrows(MontoInvalido.class, () -> gestor.editarMovimiento(g.id(), d("-1"), null, null, null));
            assertMonto("400", g.monto()); // la edición fallida no cambió nada
            gestor.anularMovimiento(g.id());
            assertMonto("1000", gestor.analizar(new EstrategiaBalance(), null, null, null));
            assertThrows(OperacionInvalida.class, () -> gestor.editarMovimiento(g.id(), d("1"), null, null, null));
            assertThrows(OperacionInvalida.class, () -> gestor.anularMovimiento(g.id()));
            assertThrows(NoEncontrado.class, () -> gestor.anularMovimiento(99));
        }
    }

    @Nested
    class Indicadores extends Base {
        @Test void pt03_balance() {
            ingreso("1000");
            gasto("250");
            assertMonto("750", gestor.analizar(new EstrategiaBalance(), null, null, null));
        }

        @Test void balanceRespetaPeriodoYAmbito() {
            ingreso("1000", LocalDate.of(2026, 10, 1), "Ventas", Ambito.NEGOCIO);
            ingreso("500", LocalDate.of(2026, 11, 1), "Ventas", Ambito.NEGOCIO);
            gasto("100", LocalDate.of(2026, 11, 2), "Inventario", Ambito.PERSONAL);
            assertMonto("400", gestor.analizar(new EstrategiaBalance(), LocalDate.of(2026, 11, 1), HOY, null));
            assertMonto("1500", gestor.analizar(new EstrategiaBalance(), null, null, Ambito.NEGOCIO));
        }

        @Test void margen() {
            ingreso("1000");
            gasto("250");
            assertMonto("75", gestor.analizar(new EstrategiaMargen(), null, null, null));
        }

        @Test void pt04_margenSinIngresos() {
            gasto("100");
            assertThrows(MargenNoCalculable.class, () -> gestor.analizar(new EstrategiaMargen(), null, null, null));
        }

        @Test void proyeccionUsaSaldoMovimientosYCuentas() {
            gestor.definirSaldoInicial(d("1000"));
            ingreso("500");
            gasto("200");
            gestor.registrarCuenta(TipoCuenta.COBRAR, "Ana", d("300"), LocalDate.of(2026, 11, 30));
            gestor.registrarCuenta(TipoCuenta.PAGAR, "Proveedor", d("100"), LocalDate.of(2026, 12, 10));
            gestor.registrarCuenta(TipoCuenta.PAGAR, "Lejano", d("999"), LocalDate.of(2027, 6, 1)); // fuera del horizonte
            assertMonto("1500", gestor.analizar(new EstrategiaProyeccion(30), null, null, null));
            assertThrows(MontoInvalido.class, () -> gestor.definirSaldoInicial(d("-1")));
        }

        @Test void resumenPorAmbitoYCategoria() {
            ingreso("1000");
            gasto("200", HOY, "Alimentación", Ambito.PERSONAL);
            var r = gestor.resumen(null, null);
            assertMonto("1000", r.ingresos());
            assertMonto("200", r.gastos());
            assertMonto("800", r.balance());
            assertMonto("-200", r.porAmbito().get(Ambito.PERSONAL).balance());
            assertEquals(1, r.porCategoria().get(TipoMovimiento.GASTO).size());
            assertMonto("200", r.porCategoria().get(TipoMovimiento.GASTO).get("Alimentación"));
        }
    }

    @Nested
    class Cuentas extends Base {
        CuentaFinanciera cuenta;

        @BeforeEach
        void crearCuenta() {
            cuenta = gestor.registrarCuenta(TipoCuenta.COBRAR, "Doña Rosa", d("1000"), LocalDate.of(2026, 11, 30));
        }

        @Test void pt07_abonoParcialReduceSaldo() {
            gestor.registrarAbono(cuenta.id(), d("400"), null);
            assertMonto("600", cuenta.saldo());
            assertFalse(cuenta.saldada());
        }

        @Test void pt08_pagoCompletoSalda() {
            gestor.registrarAbono(cuenta.id(), d("400"), null);
            gestor.registrarAbono(cuenta.id(), d("600"), null);
            assertTrue(cuenta.saldada());
            assertThrows(CuentaSaldada.class, () -> gestor.registrarAbono(cuenta.id(), d("1"), null));
        }

        @Test void abonosInvalidos() {
            for (String malo : new String[] {"0", "-10", "1001"}) {
                assertThrows(AbonoInvalido.class, () -> gestor.registrarAbono(cuenta.id(), d(malo), null), malo);
            }
            assertMonto("1000", cuenta.saldo());
        }

        @Test void vencidaSoloConSaldo() {
            CuentaFinanciera vieja = gestor.registrarCuenta(TipoCuenta.PAGAR, "Prov", d("100"), LocalDate.of(2026, 11, 1));
            assertTrue(vieja.vencida(HOY));
            assertFalse(cuenta.vencida(HOY));
            gestor.registrarAbono(vieja.id(), d("100"), null);
            assertFalse(vieja.vencida(HOY));
            assertEquals(List.of(cuenta), gestor.cuentas(null, true));
        }

        @Test void observadorVencimientos() {
            var obs = new ObservadorVencimientos(3, m -> { });
            gestor.suscribir(obs);
            gestor.registrarCuenta(TipoCuenta.PAGAR, "Prov", d("100"), LocalDate.of(2026, 11, 1));    // vencida
            gestor.registrarCuenta(TipoCuenta.COBRAR, "Cli", d("100"), LocalDate.of(2026, 11, 24));   // vence en 2 días
            gestor.registrarCuenta(TipoCuenta.COBRAR, "Lejos", d("100"), LocalDate.of(2026, 12, 30)); // sin alerta
            assertEquals(2, obs.alertas().size());
            assertTrue(obs.alertas().get(0).contains("VENCIDA"));
            assertTrue(obs.alertas().get(1).contains("vence en 2 día"));
            gestor.revisarVencimientos(); // revisa todas las pendientes, incluida `cuenta` (vence en 8 días: sin alerta)
            assertEquals(4, obs.alertas().size());
        }
    }

    @Nested
    class PresupuestosYMetas extends Base {
        ObservadorPresupuesto obs;

        @BeforeEach
        void crearPresupuesto() {
            obs = new ObservadorPresupuesto(gestor, m -> { });
            gestor.suscribir(obs);
            gestor.crearPresupuesto("Inventario", d("1000"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), d("80"));
        }

        @Test void pt05_alertaAlAlcanzarUmbral() {
            gasto("700");
            assertTrue(obs.alertas().isEmpty());
            gasto("100"); // 800 = 80 %
            assertEquals(1, obs.alertas().size());
            assertTrue(obs.alertas().get(0).contains("80%"));
            gasto("50"); // sigue entre 80 % y 100 %: no repite
            assertEquals(1, obs.alertas().size());
        }

        @Test void pt06_alertaAlSuperarLimite() {
            gasto("900");
            assertEquals(1, obs.alertas().size()); // umbral
            gasto("200");
            assertEquals(2, obs.alertas().size());
            assertTrue(obs.alertas().get(1).contains("EXCEDIDO"));
        }

        @Test void saltoDirectoSobreElLimiteDaUnaSolaAlerta() {
            gasto("1500");
            assertEquals(1, obs.alertas().size());
            assertTrue(obs.alertas().get(0).contains("EXCEDIDO"));
        }

        @Test void gastosDeOtraCategoriaOFueraDePeriodoNoCuentan() {
            gasto("900", HOY, "Transporte", Ambito.NEGOCIO);
            gasto("900", LocalDate.of(2026, 10, 15), "Inventario", Ambito.NEGOCIO);
            ingreso("5000");
            assertTrue(obs.alertas().isEmpty());
        }

        @Test void pt09_observadorRetiradoNoRecibeEventos() {
            gestor.retirar(obs);
            gasto("2000");
            assertTrue(obs.alertas().isEmpty());
            gestor.suscribir(obs);
            gasto("10");
            assertTrue(obs.alertas().isEmpty()); // ya estaba excedido antes de este gasto: sin alerta nueva
            gestor.crearPresupuesto("Transporte", d("100"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), d("80"));
            gasto("150", HOY, "Transporte", Ambito.NEGOCIO);
            assertEquals(1, obs.alertas().size());
        }

        @Test void bitacoraRegistraEventos() {
            var bit = new ObservadorBitacora();
            gestor.suscribir(bit);
            Movimiento m = gasto("10");
            gestor.anularMovimiento(m.id());
            assertEquals(2, bit.registros().size());
            assertTrue(bit.registros().get(1).contains("anulado"));
        }

        @Test void presupuestoInvalidoSeRechaza() {
            var ini = LocalDate.of(2026, 11, 1);
            assertThrows(PeriodoInvalido.class, () -> new Presupuesto(1, "X", d("10"), ini.plusDays(5), ini, d("80")));
            assertThrows(MontoInvalido.class, () -> new Presupuesto(1, "X", d("0"), ini, ini, d("80")));
            assertThrows(gestor.excepcion.GestorException.DatoInvalido.class,
                    () -> new Presupuesto(1, "X", d("10"), ini, ini, d("101")));
        }

        @Test void metaAvanceYTope100() {
            MetaAhorro meta = gestor.crearMeta("Moto", d("1000"), LocalDate.of(2027, 6, 1));
            gestor.aportarMeta(meta.id(), d("250"), null);
            assertMonto("250", meta.acumulado());
            assertMonto("750", meta.faltante());
            assertMonto("25", meta.avancePct());
            gestor.aportarMeta(meta.id(), d("2000"), null);
            assertMonto("0", meta.faltante());
            assertMonto("100", meta.avancePct());
            assertThrows(MontoInvalido.class, () -> gestor.aportarMeta(meta.id(), d("0"), null));
            assertThrows(NoEncontrado.class, () -> gestor.aportarMeta(99, d("10"), null));
        }
    }

    @Nested
    class Persistencia extends Base {
        @Test void pt10_cerrarYReabrirRecuperaTodo() throws IOException {
            gestor.agregarCategoria("Papelería", TipoMovimiento.GASTO);
            ingreso("1000");
            Movimiento g = gasto("250.50", HOY, "Inventario", Ambito.PERSONAL);
            gestor.anularMovimiento(g.id());
            CuentaFinanciera c = gestor.registrarCuenta(TipoCuenta.COBRAR, "Ana", d("500"), LocalDate.of(2026, 12, 1));
            gestor.registrarAbono(c.id(), d("200"), null);
            gestor.crearPresupuesto("Inventario", d("900"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), d("75"));
            MetaAhorro meta = gestor.crearMeta("Moto", d("5000"), LocalDate.of(2027, 1, 1));
            gestor.aportarMeta(meta.id(), d("100"), null);
            gestor.definirSaldoInicial(d("777"));
            gestor.guardar();

            GestorFinanciero otro = nuevoGestor();
            assertMonto("300", otro.obtenerCuenta(c.id()).saldo());
            assertTrue(otro.obtenerMovimiento(g.id()).anulado());
            assertMonto("250.50", otro.obtenerMovimiento(g.id()).monto());
            assertMonto("777", otro.saldoInicial());
            assertEquals(1, otro.presupuestos().size());
            assertMonto("100", otro.metas().get(0).acumulado());
            Movimiento nuevo = otro.registrarMovimiento(TipoMovimiento.INGRESO, d("1"), HOY, "Ventas", Ambito.NEGOCIO,
                    MedioPago.EFECTIVO, "");
            assertEquals(3, nuevo.id()); // los ids no se reutilizan
        }

        @Test void guardarYRecargarEsEstable() throws IOException {
            ingreso("1000");
            gasto("250.50");
            gestor.guardar();
            String antes = Files.readString(ruta);
            nuevoGestor().guardar();
            assertEquals(antes, Files.readString(ruta));
        }

        @Test void archivoDanadoNoSeSobrescribe() throws IOException {
            Files.writeString(ruta, "{ esto no es json");
            assertThrows(ErrorPersistencia.class, this::nuevoGestor);
            assertEquals("{ esto no es json", Files.readString(ruta));
        }

        @Test void estadoConDatosInvalidosSeRechaza() throws IOException {
            gestor.guardar();
            String texto = Files.readString(ruta).replace("\"movimientos\": []", "\"movimientos\": [{\"tipo\": \"x\"}]");
            Files.writeString(ruta, texto);
            assertThrows(ErrorPersistencia.class, this::nuevoGestor);
        }

        @Test void respaldo() throws IOException {
            gestor.guardar();
            Path destino = gestor.respaldar();
            assertTrue(Files.exists(destino));
            assertEquals(Files.readString(ruta), Files.readString(destino));
        }
    }
}
