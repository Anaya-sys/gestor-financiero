package gestor.dominio;

import gestor.excepcion.GestorException.DatoInvalido;
import gestor.util.Etiquetado;
import gestor.util.Json;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Base de Ingreso y Gasto. Los datos solo cambian mediante editar(), que valida antes de asignar. */
public abstract class Movimiento {
    private final int id;
    private boolean anulado;
    private BigDecimal monto;
    private LocalDate fecha;
    private String categoria;
    private Ambito ambito;
    private MedioPago medioPago;
    private String descripcion;

    protected Movimiento(int id, BigDecimal monto, LocalDate fecha, String categoria, Ambito ambito,
                         MedioPago medioPago, String descripcion, boolean anulado) {
        this.id = id;
        this.anulado = anulado;
        asignar(monto, fecha, categoria, ambito, medioPago, descripcion);
    }

    public abstract TipoMovimiento tipo();

    private void asignar(BigDecimal monto, LocalDate fecha, String categoria, Ambito ambito,
                         MedioPago medioPago, String descripcion) {
        BigDecimal m = Montos.aMonto(monto);
        LocalDate f = Montos.exigirFecha(fecha, "fecha");
        String c = Montos.exigirTexto(categoria, "categoría");
        if (ambito == null) throw new DatoInvalido("El ámbito debe ser negocio o personal.");
        if (medioPago == null) throw new DatoInvalido("Medio de pago no válido.");
        // Se asigna todo junto para que una edición inválida no deje el objeto a medias.
        this.monto = m;
        this.fecha = f;
        this.categoria = c;
        this.ambito = ambito;
        this.medioPago = medioPago;
        this.descripcion = descripcion == null ? "" : descripcion.strip();
    }

    /** Los parámetros null conservan el valor actual. */
    public void editar(BigDecimal monto, LocalDate fecha, String categoria, Ambito ambito,
                       MedioPago medioPago, String descripcion) {
        asignar(monto != null ? monto : this.monto,
                fecha != null ? fecha : this.fecha,
                categoria != null ? categoria : this.categoria,
                ambito != null ? ambito : this.ambito,
                medioPago != null ? medioPago : this.medioPago,
                descripcion != null ? descripcion : this.descripcion);
    }

    public int id() { return id; }
    public boolean anulado() { return anulado; }
    public void anular() { this.anulado = true; }
    public BigDecimal monto() { return monto; }
    public LocalDate fecha() { return fecha; }
    public String categoria() { return categoria; }
    public Ambito ambito() { return ambito; }
    public MedioPago medioPago() { return medioPago; }
    public String descripcion() { return descripcion; }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo().etiqueta());
        m.put("id", id);
        m.put("monto", monto.toPlainString());
        m.put("fecha", fecha.toString());
        m.put("categoria", categoria);
        m.put("ambito", ambito.etiqueta());
        m.put("medio_pago", medioPago.etiqueta());
        m.put("descripcion", descripcion);
        m.put("anulado", anulado);
        return m;
    }

    public static Movimiento desdeMapa(Map<String, Object> m) {
        TipoMovimiento tipo = Etiquetado.desde(TipoMovimiento.class, Json.str(m, "tipo"));
        int id = Json.entero(m, "id");
        BigDecimal monto = new BigDecimal(Json.str(m, "monto"));
        LocalDate fecha = LocalDate.parse(Json.str(m, "fecha"));
        String categoria = Json.str(m, "categoria");
        Ambito ambito = Etiquetado.desde(Ambito.class, Json.str(m, "ambito"));
        MedioPago medio = Etiquetado.desde(MedioPago.class, Json.str(m, "medio_pago"));
        String descripcion = Json.str(m, "descripcion");
        boolean anulado = Json.bool(m, "anulado");
        return tipo == TipoMovimiento.INGRESO
                ? new Ingreso(id, monto, fecha, categoria, ambito, medio, descripcion, anulado)
                : new Gasto(id, monto, fecha, categoria, ambito, medio, descripcion, anulado);
    }
}
