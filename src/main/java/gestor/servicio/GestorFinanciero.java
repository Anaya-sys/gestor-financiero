package gestor.servicio;

import static gestor.util.Formato.cop;

import gestor.dominio.Ambito;
import gestor.dominio.Categoria;
import gestor.dominio.CuentaFinanciera;
import gestor.dominio.Gasto;
import gestor.dominio.Ingreso;
import gestor.dominio.MedioPago;
import gestor.dominio.MetaAhorro;
import gestor.dominio.Montos;
import gestor.dominio.Movimiento;
import gestor.dominio.Presupuesto;
import gestor.dominio.TipoCuenta;
import gestor.dominio.TipoMovimiento;
import gestor.estrategia.AnalizadorFinanciero;
import gestor.estrategia.ContextoFinanciero;
import gestor.estrategia.EstrategiaBalance;
import gestor.estrategia.EstrategiaFinanciera;
import gestor.estrategia.Totales;
import gestor.excepcion.GestorException.CategoriaInexistente;
import gestor.excepcion.GestorException.DatoInvalido;
import gestor.excepcion.GestorException.ErrorPersistencia;
import gestor.excepcion.GestorException.NoEncontrado;
import gestor.excepcion.GestorException.OperacionInvalida;
import gestor.excepcion.GestorException.PeriodoInvalido;
import gestor.observador.ConsultaPresupuestos;
import gestor.observador.Evento;
import gestor.observador.SujetoFinanciero;
import gestor.observador.TipoEvento;
import gestor.persistencia.RepositorioFinanciero;
import gestor.util.Json;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Coordina los casos de uso y publica eventos (es el Subject del patrón Observer). */
public class GestorFinanciero extends SujetoFinanciero implements ConsultaPresupuestos {
    private static final String[][] CATEGORIAS_INICIALES = {
        {"Ventas", "ingreso"}, {"Servicios prestados", "ingreso"}, {"Otros ingresos", "ingreso"},
        {"Inventario", "gasto"}, {"Arriendo", "gasto"}, {"Servicios públicos", "gasto"},
        {"Transporte", "gasto"}, {"Nómina", "gasto"}, {"Alimentación", "gasto"}, {"Otros gastos", "gasto"},
    };

    private final RepositorioFinanciero repositorio;
    private final Supplier<LocalDate> reloj;
    private BigDecimal saldoInicial = BigDecimal.ZERO;
    private List<Categoria> categorias = new ArrayList<>();
    private Map<Integer, Movimiento> movimientos = new LinkedHashMap<>();
    private Map<Integer, CuentaFinanciera> cuentas = new LinkedHashMap<>();
    private Map<Integer, Presupuesto> presupuestos = new LinkedHashMap<>();
    private Map<Integer, MetaAhorro> metas = new LinkedHashMap<>();

    public GestorFinanciero(RepositorioFinanciero repositorio) { this(repositorio, LocalDate::now); }

    /** El reloj se inyecta para poder probar fechas y vencimientos. */
    public GestorFinanciero(RepositorioFinanciero repositorio, Supplier<LocalDate> reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
        for (String[] c : CATEGORIAS_INICIALES) {
            categorias.add(new Categoria(c[0], c[1].equals("ingreso") ? TipoMovimiento.INGRESO : TipoMovimiento.GASTO));
        }
        Optional<Map<String, Object>> estado = repositorio.cargar();
        if (estado.isPresent()) {
            try {
                restaurar(estado.get());
            } catch (RuntimeException e) { // formato dañado: GestorException, ClassCast, DateTimeParse, etc.
                throw new ErrorPersistencia("El archivo de datos está dañado (" + e + "). No se modificó.", e);
            }
        }
    }

    // ---------- utilidades internas ----------
    public LocalDate hoy() { return reloj.get(); }

    private static int siguienteId(Map<Integer, ?> coleccion) {
        return coleccion.keySet().stream().max(Integer::compare).orElse(0) + 1;
    }

    private static <T> T buscar(Map<Integer, T> coleccion, int id, String nombre) {
        T valor = coleccion.get(id);
        if (valor == null) throw new NoEncontrado(nombre + " #" + id + " no existe.");
        return valor;
    }

    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new PeriodoInvalido("La fecha inicial no puede ser posterior a la final.");
        }
    }

    /** Devuelve el nombre canónico de la categoría o lanza CategoriaInexistente. */
    private String categoriaValida(String nombre, TipoMovimiento tipo) {
        String buscado = nombre == null ? "" : nombre.strip();
        return categorias.stream()
                .filter(c -> c.tipo() == tipo && c.nombre().equalsIgnoreCase(buscado))
                .map(Categoria::nombre).findFirst()
                .orElseThrow(() -> new CategoriaInexistente("La categoría '" + buscado + "' no existe entre las de " + tipo + "."));
    }

    // ---------- categorías (RF-06) ----------
    /** @param tipo null para todas */
    public List<Categoria> categorias(TipoMovimiento tipo) {
        return categorias.stream().filter(c -> tipo == null || c.tipo() == tipo).toList();
    }

    public Categoria agregarCategoria(String nombre, TipoMovimiento tipo) {
        Categoria nueva = new Categoria(nombre, tipo);
        if (categorias.stream().anyMatch(c -> c.tipo() == tipo && c.nombre().equalsIgnoreCase(nueva.nombre()))) {
            throw new DatoInvalido("Ya existe la categoría '" + nueva.nombre() + "' de " + tipo + ".");
        }
        categorias.add(nueva);
        return nueva;
    }

    // ---------- movimientos (RF-01 a RF-05) ----------
    public Movimiento registrarMovimiento(TipoMovimiento tipo, BigDecimal monto, LocalDate fecha, String categoria,
                                          Ambito ambito, MedioPago medioPago, String descripcion) {
        if (tipo == null) throw new DatoInvalido("El tipo de movimiento debe ser ingreso o gasto.");
        String cat = categoriaValida(categoria, tipo);
        int id = siguienteId(movimientos);
        Movimiento mov = tipo == TipoMovimiento.INGRESO
                ? new Ingreso(id, monto, fecha, cat, ambito, medioPago, descripcion)
                : new Gasto(id, monto, fecha, cat, ambito, medioPago, descripcion);
        movimientos.put(id, mov);
        notificar(Evento.de(TipoEvento.MOVIMIENTO_REGISTRADO,
                capitalizar(tipo.etiqueta()) + " #" + id + " de " + cop(mov.monto()) + " en " + cat + " (" + mov.ambito() + ")", mov));
        return mov;
    }

    public Movimiento obtenerMovimiento(int id) { return buscar(movimientos, id, "Movimiento"); }

    /** Los parámetros null conservan el valor actual. */
    public Movimiento editarMovimiento(int id, BigDecimal monto, LocalDate fecha, String categoria, String descripcion) {
        Movimiento mov = obtenerMovimiento(id);
        if (mov.anulado()) throw new OperacionInvalida("No se puede editar un movimiento anulado.");
        String cat = categoria == null ? null : categoriaValida(categoria, mov.tipo());
        mov.editar(monto, fecha, cat, null, null, descripcion);
        notificar(Evento.de(TipoEvento.MOVIMIENTO_MODIFICADO, "Movimiento #" + id + " editado", mov));
        return mov;
    }

    public Movimiento anularMovimiento(int id) {
        Movimiento mov = obtenerMovimiento(id);
        if (mov.anulado()) throw new OperacionInvalida("El movimiento ya estaba anulado.");
        mov.anular(); // se conserva para trazabilidad, pero deja de contar en los cálculos
        notificar(Evento.de(TipoEvento.MOVIMIENTO_ANULADO, "Movimiento #" + id + " anulado", mov));
        return mov;
    }

    public List<Movimiento> consultarMovimientos() { return consultarMovimientos(null, null, null, null, null); }

    /** Movimientos activos; cada filtro null significa "sin filtro". */
    public List<Movimiento> consultarMovimientos(LocalDate desde, LocalDate hasta, TipoMovimiento tipo,
                                                 String categoria, Ambito ambito) {
        return consultar(desde, hasta, tipo, categoria, ambito, false);
    }

    public List<Movimiento> consultarMovimientosConAnulados() {
        return consultar(null, null, null, null, null, true);
    }

    private List<Movimiento> consultar(LocalDate desde, LocalDate hasta, TipoMovimiento tipo, String categoria,
                                       Ambito ambito, boolean incluirAnulados) {
        validarPeriodo(desde, hasta);
        return movimientos.values().stream()
                .filter(m -> incluirAnulados || !m.anulado())
                .filter(m -> desde == null || !m.fecha().isBefore(desde))
                .filter(m -> hasta == null || !m.fecha().isAfter(hasta))
                .filter(m -> tipo == null || m.tipo() == tipo)
                .filter(m -> categoria == null || m.categoria().equalsIgnoreCase(categoria))
                .filter(m -> ambito == null || m.ambito() == ambito)
                .sorted(Comparator.comparing(Movimiento::fecha).thenComparing(Movimiento::id))
                .toList();
    }

    // ---------- cuentas por cobrar y pagar (RF-07 a RF-09) ----------
    public CuentaFinanciera registrarCuenta(TipoCuenta tipo, String tercero, BigDecimal monto, LocalDate vencimiento) {
        CuentaFinanciera c = new CuentaFinanciera(siguienteId(cuentas), tipo, tercero, monto, vencimiento);
        cuentas.put(c.id(), c);
        notificar(Evento.de(TipoEvento.CUENTA_REGISTRADA,
                "Cuenta por " + tipo + " #" + c.id() + " de " + c.tercero() + " por " + cop(c.monto()), List.of(c), hoy()));
        return c;
    }

    public CuentaFinanciera obtenerCuenta(int id) { return buscar(cuentas, id, "Cuenta"); }

    /** @param tipo null para ambos tipos */
    public List<CuentaFinanciera> cuentas(TipoCuenta tipo, boolean soloPendientes) {
        return cuentas.values().stream()
                .filter(c -> tipo == null || c.tipo() == tipo)
                .filter(c -> !(soloPendientes && c.saldada()))
                .toList();
    }

    /** @param fecha null para usar la fecha de hoy */
    public void registrarAbono(int idCuenta, BigDecimal monto, LocalDate fecha) {
        CuentaFinanciera cuenta = obtenerCuenta(idCuenta);
        var abono = cuenta.abonar(monto, fecha != null ? fecha : hoy());
        String estado = cuenta.saldada() ? "cuenta saldada" : "saldo pendiente " + cop(cuenta.saldo());
        notificar(Evento.de(TipoEvento.ABONO_REGISTRADO,
                "Abono de " + cop(abono.monto()) + " a cuenta #" + idCuenta + ": " + estado));
    }

    public void revisarVencimientos() {
        List<CuentaFinanciera> pendientes = cuentas(null, true);
        notificar(Evento.de(TipoEvento.REVISION_VENCIMIENTOS,
                "Revisión de vencimientos (" + pendientes.size() + " cuentas pendientes)", pendientes, hoy()));
    }

    // ---------- presupuestos (RF-10, RF-11) ----------
    public Presupuesto crearPresupuesto(String categoria, BigDecimal limite, LocalDate inicio, LocalDate fin, BigDecimal umbral) {
        String cat = categoriaValida(categoria, TipoMovimiento.GASTO);
        Presupuesto p = new Presupuesto(siguienteId(presupuestos), cat, limite, inicio, fin, umbral);
        presupuestos.put(p.id(), p);
        notificar(Evento.de(TipoEvento.PRESUPUESTO_CREADO, "Presupuesto #" + p.id() + " de " + cat + " por " + cop(p.limite())));
        return p;
    }

    public List<Presupuesto> presupuestos() { return List.copyOf(presupuestos.values()); }

    @Override
    public List<Presupuesto> presupuestosDe(String categoria) {
        return presupuestos.values().stream().filter(p -> p.categoria().equalsIgnoreCase(categoria)).toList();
    }

    /** Gasto acumulado de la categoría del presupuesto dentro de su periodo. */
    @Override
    public BigDecimal consumo(Presupuesto p) {
        return Totales.de(consultarMovimientos(p.inicio(), p.fin(), TipoMovimiento.GASTO, p.categoria(), null)).gastos();
    }

    // ---------- metas de ahorro (RF-12, RF-13) ----------
    public MetaAhorro crearMeta(String nombre, BigDecimal objetivo, LocalDate fechaObjetivo) {
        MetaAhorro m = new MetaAhorro(siguienteId(metas), nombre, objetivo, fechaObjetivo);
        metas.put(m.id(), m);
        notificar(Evento.de(TipoEvento.META_CREADA, "Meta #" + m.id() + " '" + m.nombre() + "' por " + cop(m.objetivo())));
        return m;
    }

    public List<MetaAhorro> metas() { return List.copyOf(metas.values()); }

    public MetaAhorro obtenerMeta(int id) { return buscar(metas, id, "Meta"); }

    /** @param fecha null para usar la fecha de hoy */
    public void aportarMeta(int idMeta, BigDecimal monto, LocalDate fecha) {
        MetaAhorro meta = obtenerMeta(idMeta);
        var aporte = meta.aportar(monto, fecha != null ? fecha : hoy());
        notificar(Evento.de(TipoEvento.APORTE_REGISTRADO, "Aporte de " + cop(aporte.monto()) + " a la meta #" + idMeta));
    }

    // ---------- análisis (RF-14 a RF-16, RF-18) ----------
    public BigDecimal saldoInicial() { return saldoInicial; }

    public void definirSaldoInicial(BigDecimal monto) { this.saldoInicial = Montos.aMonto(monto, true); }

    public BigDecimal analizar(EstrategiaFinanciera estrategia, LocalDate desde, LocalDate hasta, Ambito ambito) {
        List<Movimiento> movs = consultarMovimientos(desde, hasta, null, null, ambito);
        var ctx = new ContextoFinanciero(movs, List.copyOf(cuentas.values()), saldoInicial, hoy());
        return new AnalizadorFinanciero(estrategia).calcular(ctx);
    }

    public Resumen resumen(LocalDate desde, LocalDate hasta) {
        List<Movimiento> movs = consultarMovimientos(desde, hasta, null, null, null);
        Totales total = Totales.de(movs);
        Map<Ambito, Resumen.Parcial> porAmbito = new EnumMap<>(Ambito.class);
        for (Ambito a : Ambito.values()) {
            Totales t = Totales.de(movs.stream().filter(m -> m.ambito() == a).toList());
            porAmbito.put(a, new Resumen.Parcial(t.ingresos(), t.gastos(),
                    analizar(new EstrategiaBalance(), desde, hasta, a)));
        }
        Map<TipoMovimiento, Map<String, BigDecimal>> porCategoria = new EnumMap<>(TipoMovimiento.class);
        for (TipoMovimiento t : TipoMovimiento.values()) {
            porCategoria.put(t, movs.stream().filter(m -> m.tipo() == t)
                    .collect(Collectors.toMap(Movimiento::categoria, Movimiento::monto, BigDecimal::add, LinkedHashMap::new)));
        }
        return new Resumen(total.ingresos(), total.gastos(), analizar(new EstrategiaBalance(), desde, hasta, null),
                porAmbito, porCategoria);
    }

    // ---------- persistencia (RF-19) ----------
    private static List<Map<String, Object>> mapas(Stream<Map<String, Object>> s) { return s.toList(); }

    Map<String, Object> estado() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("version", 1);
        e.put("saldo_inicial", saldoInicial.toPlainString());
        e.put("categorias", mapas(categorias.stream().map(Categoria::aMapa)));
        e.put("movimientos", mapas(movimientos.values().stream().map(Movimiento::aMapa)));
        e.put("cuentas", mapas(cuentas.values().stream().map(CuentaFinanciera::aMapa)));
        e.put("presupuestos", mapas(presupuestos.values().stream().map(Presupuesto::aMapa)));
        e.put("metas", mapas(metas.values().stream().map(MetaAhorro::aMapa)));
        return e;
    }

    private static <T> Map<Integer, T> indexar(List<Map<String, Object>> datos, Function<Map<String, Object>, T> crear,
                                               Function<T, Integer> id) {
        Map<Integer, T> m = new LinkedHashMap<>();
        for (Map<String, Object> d : datos) {
            T t = crear.apply(d);
            m.put(id.apply(t), t);
        }
        return m;
    }

    private void restaurar(Map<String, Object> e) {
        saldoInicial = new BigDecimal(Json.str(e, "saldo_inicial"));
        categorias = new ArrayList<>(Json.lista(e, "categorias").stream().map(Categoria::desdeMapa).toList());
        movimientos = indexar(Json.lista(e, "movimientos"), Movimiento::desdeMapa, Movimiento::id);
        cuentas = indexar(Json.lista(e, "cuentas"), CuentaFinanciera::desdeMapa, CuentaFinanciera::id);
        presupuestos = indexar(Json.lista(e, "presupuestos"), Presupuesto::desdeMapa, Presupuesto::id);
        metas = indexar(Json.lista(e, "metas"), MetaAhorro::desdeMapa, MetaAhorro::id);
    }

    public void guardar() { repositorio.guardar(estado()); }

    public Path respaldar() {
        guardar();
        return repositorio.respaldar();
    }

    private static String capitalizar(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
