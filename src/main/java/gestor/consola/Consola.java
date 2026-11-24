package gestor.consola;

import static gestor.util.Formato.cop;
import static gestor.util.Formato.pct;

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
import gestor.excepcion.GestorException;
import gestor.excepcion.GestorException.DatoInvalido;
import gestor.excepcion.GestorException.ErrorPersistencia;
import gestor.excepcion.GestorException.MargenNoCalculable;
import gestor.observador.ObservadorPresupuesto;
import gestor.servicio.GestorFinanciero;
import gestor.servicio.Resumen;
import gestor.util.Etiquetado;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Capa de presentación: menús numerados, validación de entradas y reportes en texto.
 * No contiene cálculos financieros; todo se delega en GestorFinanciero.
 */
public class Consola {
    /** Se lanza cuando se acaban las entradas (fin de archivo o Ctrl+D / Ctrl+Z). */
    public static class FinDeEntrada extends RuntimeException {
        public FinDeEntrada() { super("Fin de la entrada"); }
    }

    private record Opcion(String texto, Runnable accion) {}

    private record Periodo(LocalDate desde, LocalDate hasta, Ambito ambito) {}

    private final GestorFinanciero gestor;
    private final ObservadorPresupuesto observadorPresupuesto;
    private final Function<String, String> entrada; // recibe el prompt; devuelve null al terminar la entrada
    private final Consumer<String> salida;

    public Consola(GestorFinanciero gestor, ObservadorPresupuesto observadorPresupuesto,
                   Function<String, String> entrada, Consumer<String> salida) {
        this.gestor = gestor;
        this.observadorPresupuesto = observadorPresupuesto;
        this.entrada = entrada;
        this.salida = salida;
    }

    // ---------- conversores de entradas (lanzan DatoInvalido) ----------
    private static LocalDate aFecha(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new DatoInvalido("Fecha inválida. Use el formato AAAA-MM-DD (ej. 2026-11-22).");
        }
    }

    private static int aEntero(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new DatoInvalido("Ingrese un número entero.");
        }
    }

    private static BigDecimal aDecimal(String texto) {
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new DatoInvalido("Ingrese un número válido.");
        }
    }

    private static String aTexto(String texto) {
        if (texto.isEmpty()) throw new DatoInvalido("Este campo es obligatorio.");
        return texto;
    }

    private static String vacioANull(String texto) { return texto.isEmpty() ? null : texto; }

    // ---------- infraestructura de menús y entradas ----------
    public void ejecutar() {
        salida.accept("=== GESTOR FINANCIERO PERSONAL PARA EMPRENDEDORES LOCALES ===");
        salida.accept("Valores en pesos colombianos (COP). Fechas: AAAA-MM-DD.");
        gestor.revisarVencimientos();
        try {
            menu("MENU PRINCIPAL", List.of(
                    new Opcion("Registrar ingreso", () -> registrar(TipoMovimiento.INGRESO)),
                    new Opcion("Registrar gasto", () -> registrar(TipoMovimiento.GASTO)),
                    new Opcion("Consultar movimientos", this::menuMovimientos),
                    new Opcion("Administrar categorías", this::menuCategorias),
                    new Opcion("Cuentas por cobrar y por pagar", this::menuCuentas),
                    new Opcion("Presupuestos y alertas", this::menuPresupuestos),
                    new Opcion("Metas de ahorro", this::menuMetas),
                    new Opcion("Indicadores y proyección financiera", this::menuIndicadores),
                    new Opcion("Resumen del periodo", this::resumen),
                    new Opcion("Guardar / respaldar información", this::respaldar)), "Salir");
        } catch (FinDeEntrada e) {
            salida.accept("\nSesión interrumpida.");
        }
        guardar();
        salida.accept("Información guardada. Hasta pronto.");
    }

    private void menu(String titulo, List<Opcion> opciones, String salir) {
        while (true) {
            salida.accept("\n--- " + titulo + " ---");
            for (int i = 0; i < opciones.size(); i++) salida.accept((i + 1) + ". " + opciones.get(i).texto());
            salida.accept("0. " + salir);
            String eleccion = leer("Opción: ");
            if (eleccion.equals("0")) return;
            if (eleccion.matches("\\d{1,3}") && Integer.parseInt(eleccion) <= opciones.size()
                    && Integer.parseInt(eleccion) >= 1) {
                ejecutar(opciones.get(Integer.parseInt(eleccion) - 1).accion());
            } else {
                salida.accept("  [x] Opción no válida.");
            }
        }
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (GestorException e) {
            salida.accept("  [x] " + e.getMessage());
        }
        guardar();
    }

    private void guardar() {
        try {
            gestor.guardar();
        } catch (ErrorPersistencia e) {
            salida.accept("  [x] " + e.getMessage());
        }
    }

    private String leer(String prompt) {
        String texto = entrada.apply(prompt);
        if (texto == null) throw new FinDeEntrada();
        return texto.strip();
    }

    /** Repite la pregunta hasta que `convertir` acepte el valor. */
    private <T> T pedir(String texto, Function<String, T> convertir) {
        while (true) {
            try {
                return convertir.apply(leer(texto + ": "));
            } catch (GestorException e) {
                salida.accept("  [x] " + e.getMessage());
            }
        }
    }

    /** Como pedir, pero Enter (vacío) devuelve `porDefecto` (que puede ser null). */
    private <T> T pedirOpcional(String texto, Function<String, T> convertir, T porDefecto) {
        while (true) {
            String crudo = leer(texto + ": ");
            if (crudo.isEmpty()) return porDefecto;
            try {
                return convertir.apply(crudo);
            } catch (GestorException e) {
                salida.accept("  [x] " + e.getMessage());
            }
        }
    }

    /** Opciones numeradas. Con todos=true, 0 devuelve null (sin filtro). */
    private String elegir(String titulo, List<String> opciones, boolean todos) {
        salida.accept(titulo + ":");
        if (todos) salida.accept("  0. Todos");
        for (int i = 0; i < opciones.size(); i++) salida.accept("  " + (i + 1) + ". " + opciones.get(i));
        return pedir("Opción", t -> {
            if (todos && t.equals("0")) return null;
            if (t.matches("\\d{1,3}") && Integer.parseInt(t) >= 1 && Integer.parseInt(t) <= opciones.size()) {
                return opciones.get(Integer.parseInt(t) - 1);
            }
            throw new DatoInvalido("Elija un número de la lista.");
        });
    }

    private <E extends Enum<E> & Etiquetado> E elegirEnum(String titulo, Class<E> clase, boolean todos) {
        List<String> etiquetas = Arrays.stream(clase.getEnumConstants()).map(Etiquetado::etiqueta).toList();
        String elegida = elegir(titulo, etiquetas, todos);
        return elegida == null ? null : Etiquetado.desde(clase, elegida);
    }

    private Periodo periodo(boolean conAmbito) {
        LocalDate desde = pedirOpcional("Desde AAAA-MM-DD [Enter = sin límite]", Consola::aFecha, null);
        LocalDate hasta = pedirOpcional("Hasta AAAA-MM-DD [Enter = sin límite]", Consola::aFecha, null);
        Ambito ambito = conAmbito ? elegirEnum("Ámbito", Ambito.class, true) : null;
        return new Periodo(desde, hasta, ambito);
    }

    // ---------- movimientos (1, 2, 3) ----------
    private void registrar(TipoMovimiento tipo) {
        salida.accept("\nRegistrar " + tipo);
        BigDecimal monto = pedir("Monto (COP)", Montos::aMonto);
        LocalDate fecha = pedirOpcional("Fecha [Enter = hoy]", Consola::aFecha, gestor.hoy());
        String categoria = elegir("Categoría", gestor.categorias(tipo).stream().map(c -> c.nombre()).toList(), false);
        Ambito ambito = elegirEnum("Ámbito", Ambito.class, false);
        MedioPago medio = elegirEnum("Medio de pago", MedioPago.class, false);
        String descripcion = leer("Descripción (opcional): ");
        Movimiento mov = gestor.registrarMovimiento(tipo, monto, fecha, categoria, ambito, medio, descripcion);
        salida.accept("  Listo: " + tipo + " #" + mov.id() + " registrado por " + cop(mov.monto()) + ".");
    }

    private void menuMovimientos() {
        menu("MOVIMIENTOS", List.of(
                new Opcion("Consultar / filtrar historial", this::consultar),
                new Opcion("Editar un movimiento", this::editar),
                new Opcion("Anular un movimiento", this::anular)), "Volver");
    }

    private void tablaMovimientos(List<Movimiento> movs) {
        if (movs.isEmpty()) {
            salida.accept("  (sin movimientos)");
            return;
        }
        salida.accept(String.format("%4s %-10s %-8s %-20s %-9s %-17s %14s  %s",
                "ID", "Fecha", "Tipo", "Categoría", "Ámbito", "Medio", "Monto", "Descripción"));
        for (Movimiento m : movs) {
            salida.accept(String.format("%4d %-10s %-8s %-20s %-9s %-17s %14s  %s", m.id(), m.fecha(), m.tipo(),
                    m.categoria(), m.ambito(), m.medioPago(), cop(m.monto()), m.descripcion()));
        }
    }

    private void consultar() {
        TipoMovimiento tipo = elegirEnum("Tipo", TipoMovimiento.class, true);
        Periodo p = periodo(true);
        String categoria = vacioANull(leer("Categoría [Enter = todas]: "));
        tablaMovimientos(gestor.consultarMovimientos(p.desde(), p.hasta(), tipo, categoria, p.ambito()));
    }

    private void editar() {
        tablaMovimientos(gestor.consultarMovimientos());
        int id = pedir("ID del movimiento a editar", Consola::aEntero);
        Movimiento mov = gestor.obtenerMovimiento(id);
        salida.accept("Enter conserva el valor actual.");
        BigDecimal monto = pedirOpcional("Monto [" + mov.monto().toPlainString() + "]", Montos::aMonto, null);
        LocalDate fecha = pedirOpcional("Fecha [" + mov.fecha() + "]", Consola::aFecha, null);
        String categoria = vacioANull(leer("Categoría [" + mov.categoria() + "]: "));
        String descripcion = vacioANull(leer("Descripción [" + mov.descripcion() + "]: "));
        if (monto == null && fecha == null && categoria == null && descripcion == null) {
            salida.accept("  Sin cambios.");
            return;
        }
        gestor.editarMovimiento(id, monto, fecha, categoria, descripcion);
        salida.accept("  Movimiento actualizado.");
    }

    private void anular() {
        tablaMovimientos(gestor.consultarMovimientos());
        int id = pedir("ID del movimiento a anular", Consola::aEntero);
        if (leer("¿Confirma la anulación? (s/n): ").equalsIgnoreCase("s")) {
            gestor.anularMovimiento(id);
            salida.accept("  Movimiento anulado; ya no cuenta en los reportes.");
        }
    }

    // ---------- categorías (4) ----------
    private void menuCategorias() {
        menu("CATEGORÍAS", List.of(
                new Opcion("Listar categorías", this::listarCategorias),
                new Opcion("Agregar categoría", this::agregarCategoria)), "Volver");
    }

    private void listarCategorias() {
        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            String nombres = String.join(", ", gestor.categorias(tipo).stream().map(c -> c.nombre()).toList());
            salida.accept("  " + tipo.etiqueta().substring(0, 1).toUpperCase() + tipo.etiqueta().substring(1) + "s: " + nombres);
        }
    }

    private void agregarCategoria() {
        TipoMovimiento tipo = elegirEnum("Tipo", TipoMovimiento.class, false);
        String nombre = pedir("Nombre de la categoría", Consola::aTexto);
        gestor.agregarCategoria(nombre, tipo);
        salida.accept("  Categoría '" + nombre + "' agregada.");
    }

    // ---------- cuentas (5) ----------
    private void menuCuentas() {
        menu("CUENTAS POR COBRAR Y POR PAGAR", List.of(
                new Opcion("Registrar cuenta", this::registrarCuenta),
                new Opcion("Registrar abono", this::registrarAbono),
                new Opcion("Ver cuentas pendientes", this::verCuentas)), "Volver");
    }

    private void registrarCuenta() {
        TipoCuenta tipo = elegirEnum("Tipo de cuenta", TipoCuenta.class, false);
        String tercero = pedir("Cliente o proveedor (nombre o alias)", Consola::aTexto);
        BigDecimal monto = pedir("Valor (COP)", Montos::aMonto);
        LocalDate vence = pedir("Fecha de vencimiento AAAA-MM-DD", Consola::aFecha);
        CuentaFinanciera c = gestor.registrarCuenta(tipo, tercero, monto, vence);
        salida.accept("  Cuenta #" + c.id() + " registrada.");
    }

    private void verCuentas() {
        LocalDate hoy = gestor.hoy();
        for (TipoCuenta tipo : TipoCuenta.values()) {
            salida.accept("\nCuentas " + (tipo == TipoCuenta.COBRAR ? "por cobrar" : "por pagar") + " pendientes:");
            List<CuentaFinanciera> cuentas = gestor.cuentas(tipo, true);
            if (cuentas.isEmpty()) {
                salida.accept("  (ninguna)");
                continue;
            }
            salida.accept(String.format("%4s %-20s %14s %14s %14s %-10s %s",
                    "ID", "Tercero", "Valor", "Abonado", "Saldo", "Vence", "Estado"));
            for (CuentaFinanciera c : cuentas) {
                salida.accept(String.format("%4d %-20s %14s %14s %14s %-10s %s", c.id(), c.tercero(), cop(c.monto()),
                        cop(c.monto().subtract(c.saldo())), cop(c.saldo()), c.vencimiento(),
                        c.vencida(hoy) ? "VENCIDA" : "Al día"));
            }
        }
    }

    private void registrarAbono() {
        verCuentas();
        int id = pedir("ID de la cuenta", Consola::aEntero);
        BigDecimal monto = pedir("Monto del abono (COP)", Consola::aDecimal);
        LocalDate fecha = pedirOpcional("Fecha [Enter = hoy]", Consola::aFecha, gestor.hoy());
        gestor.registrarAbono(id, monto, fecha);
        CuentaFinanciera cuenta = gestor.obtenerCuenta(id);
        salida.accept(cuenta.saldada() ? "  Cuenta saldada." : "  Saldo pendiente: " + cop(cuenta.saldo()) + ".");
    }

    // ---------- presupuestos (6) ----------
    private void menuPresupuestos() {
        menu("PRESUPUESTOS Y ALERTAS", List.of(
                new Opcion("Crear presupuesto", this::crearPresupuesto),
                new Opcion("Ver estado de presupuestos", this::verPresupuestos),
                new Opcion("Activar / desactivar alertas de presupuesto", this::alternarAlertas)), "Volver");
    }

    private void crearPresupuesto() {
        String categoria = elegir("Categoría de gasto",
                gestor.categorias(TipoMovimiento.GASTO).stream().map(c -> c.nombre()).toList(), false);
        BigDecimal limite = pedir("Monto límite (COP)", Montos::aMonto);
        LocalDate inicio = pedir("Inicio del periodo AAAA-MM-DD", Consola::aFecha);
        LocalDate fin = pedir("Fin del periodo AAAA-MM-DD", Consola::aFecha);
        BigDecimal umbral = pedirOpcional("Porcentaje de alerta [Enter = 80]", Consola::aDecimal, BigDecimal.valueOf(80));
        Presupuesto p = gestor.crearPresupuesto(categoria, limite, inicio, fin, umbral);
        salida.accept("  Presupuesto #" + p.id() + " creado.");
    }

    private void verPresupuestos() {
        List<Presupuesto> presupuestos = gestor.presupuestos();
        if (presupuestos.isEmpty()) {
            salida.accept("  (no hay presupuestos)");
            return;
        }
        salida.accept(String.format("%4s %-20s %-23s %14s %14s %7s  %s",
                "ID", "Categoría", "Periodo", "Límite", "Gastado", "Uso", "Estado"));
        for (Presupuesto p : presupuestos) {
            BigDecimal gastado = gestor.consumo(p);
            BigDecimal uso = gastado.multiply(BigDecimal.valueOf(100)).divide(p.limite(), MathContext.DECIMAL64);
            String estado = gastado.compareTo(p.limite()) > 0 ? "EXCEDIDO"
                    : gastado.compareTo(p.montoAlerta()) >= 0 ? "En alerta" : "Normal";
            salida.accept(String.format("%4d %-20s %-23s %14s %14s %7s  %s", p.id(), p.categoria(),
                    p.inicio() + " a " + p.fin(), cop(p.limite()), cop(gastado), pct(uso), estado));
        }
    }

    private void alternarAlertas() {
        if (gestor.estaSuscrito(observadorPresupuesto)) {
            gestor.retirar(observadorPresupuesto);
            salida.accept("  Alertas de presupuesto DESACTIVADAS.");
        } else {
            gestor.suscribir(observadorPresupuesto);
            salida.accept("  Alertas de presupuesto ACTIVADAS.");
        }
    }

    // ---------- metas (7) ----------
    private void menuMetas() {
        menu("METAS DE AHORRO", List.of(
                new Opcion("Crear meta", this::crearMeta),
                new Opcion("Registrar aporte", this::aportarMeta),
                new Opcion("Ver avance de metas", this::verMetas)), "Volver");
    }

    private void crearMeta() {
        String nombre = pedir("Nombre de la meta", Consola::aTexto);
        BigDecimal objetivo = pedir("Monto objetivo (COP)", Montos::aMonto);
        LocalDate fecha = pedir("Fecha objetivo AAAA-MM-DD", Consola::aFecha);
        MetaAhorro m = gestor.crearMeta(nombre, objetivo, fecha);
        salida.accept("  Meta #" + m.id() + " creada.");
    }

    private void aportarMeta() {
        verMetas();
        int id = pedir("ID de la meta", Consola::aEntero);
        BigDecimal monto = pedir("Monto del aporte (COP)", Montos::aMonto);
        LocalDate fecha = pedirOpcional("Fecha [Enter = hoy]", Consola::aFecha, gestor.hoy());
        gestor.aportarMeta(id, monto, fecha);
        salida.accept("  Aporte registrado.");
    }

    private void verMetas() {
        List<MetaAhorro> metas = gestor.metas();
        if (metas.isEmpty()) {
            salida.accept("  (no hay metas)");
            return;
        }
        salida.accept(String.format("%4s %-22s %14s %14s %14s %7s %s",
                "ID", "Meta", "Objetivo", "Acumulado", "Falta", "Avance", "Fecha objetivo"));
        for (MetaAhorro m : metas) {
            salida.accept(String.format("%4d %-22s %14s %14s %14s %7s %s", m.id(), m.nombre(), cop(m.objetivo()),
                    cop(m.acumulado()), cop(m.faltante()), pct(m.avancePct()), m.fechaObjetivo()));
        }
    }

    // ---------- indicadores (8) y resumen (9) ----------
    private void menuIndicadores() {
        menu("INDICADORES Y PROYECCIÓN", List.of(
                new Opcion("Balance del periodo", this::balance),
                new Opcion("Utilidad y margen del periodo", this::margen),
                new Opcion("Proyección de flujo de caja", this::proyeccion),
                new Opcion("Definir saldo inicial", this::saldoInicial)), "Volver");
    }

    private void balance() {
        Periodo p = periodo(true);
        salida.accept("  Balance (ingresos - gastos): "
                + cop(gestor.analizar(new EstrategiaBalance(), p.desde(), p.hasta(), p.ambito())));
    }

    private void margen() {
        Periodo p = periodo(true);
        salida.accept("  Utilidad: " + cop(gestor.analizar(new EstrategiaBalance(), p.desde(), p.hasta(), p.ambito())));
        try {
            salida.accept("  Margen: " + pct(gestor.analizar(new EstrategiaMargen(), p.desde(), p.hasta(), p.ambito())));
        } catch (MargenNoCalculable e) {
            salida.accept("  " + e.getMessage());
        }
    }

    private void proyeccion() {
        int dias = pedirOpcional("Días a proyectar [Enter = 30]", Consola::aEntero, 30);
        BigDecimal saldo = gestor.analizar(new EstrategiaProyeccion(dias), null, null, null);
        salida.accept("  Saldo inicial registrado: " + cop(gestor.saldoInicial()));
        salida.accept("  Saldo proyectado a " + dias + " días: " + cop(saldo));
        salida.accept("  (saldo inicial + todos los movimientos + cuentas por cobrar/pagar que vencen en ese plazo)");
    }

    private void saldoInicial() {
        BigDecimal monto = pedir("Saldo inicial disponible (COP)", t -> Montos.aMonto(t, true));
        gestor.definirSaldoInicial(monto);
        salida.accept("  Saldo inicial: " + cop(monto) + ".");
    }

    private void resumen() {
        Periodo p = periodo(false);
        Resumen r = gestor.resumen(p.desde(), p.hasta());
        salida.accept("\nRESUMEN FINANCIERO");
        salida.accept("  Ingresos: " + cop(r.ingresos()) + "   Gastos: " + cop(r.gastos()) + "   Balance: " + cop(r.balance()));
        r.porAmbito().forEach((ambito, v) -> salida.accept(String.format("  %-9s ingresos %s, gastos %s, balance %s",
                ambito.etiqueta().substring(0, 1).toUpperCase() + ambito.etiqueta().substring(1),
                cop(v.ingresos()), cop(v.gastos()), cop(v.balance()))));
        r.porCategoria().forEach((tipo, porCat) -> {
            salida.accept("  " + tipo.etiqueta().substring(0, 1).toUpperCase() + tipo.etiqueta().substring(1) + "s por categoría:");
            if (porCat.isEmpty()) salida.accept("    (ninguno)");
            porCat.forEach((cat, total) -> salida.accept(String.format("    %-22s %14s", cat, cop(total))));
        });
    }

    // ---------- respaldo (10) ----------
    private void respaldar() {
        salida.accept("  Copia de seguridad creada en: " + gestor.respaldar());
    }
}
