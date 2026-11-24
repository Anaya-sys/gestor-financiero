package gestor.dominio;

import gestor.util.Json;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetaAhorro {
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final int id;
    private final String nombre;
    private final BigDecimal objetivo;
    private final LocalDate fechaObjetivo;
    private final List<Abono> aportes = new ArrayList<>();

    public MetaAhorro(int id, String nombre, BigDecimal objetivo, LocalDate fechaObjetivo) {
        this.id = id;
        this.nombre = Montos.exigirTexto(nombre, "nombre");
        this.objetivo = Montos.aMonto(objetivo);
        this.fechaObjetivo = Montos.exigirFecha(fechaObjetivo, "fecha objetivo");
    }

    public int id() { return id; }
    public String nombre() { return nombre; }
    public BigDecimal objetivo() { return objetivo; }
    public LocalDate fechaObjetivo() { return fechaObjetivo; }
    public List<Abono> aportes() { return Collections.unmodifiableList(aportes); }

    public BigDecimal acumulado() {
        return aportes.stream().map(Abono::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal faltante() { return objetivo.subtract(acumulado()).max(BigDecimal.ZERO); }

    /** Porcentaje de avance limitado a 100 % (RN-11). */
    public BigDecimal avancePct() {
        return acumulado().multiply(CIEN).divide(objetivo, MathContext.DECIMAL64).min(CIEN);
    }

    public Abono aportar(BigDecimal monto, LocalDate fecha) {
        Abono aporte = new Abono(Montos.aMonto(monto), Montos.exigirFecha(fecha, "fecha"));
        aportes.add(aporte);
        return aporte;
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", nombre);
        m.put("objetivo", objetivo.toPlainString());
        m.put("fecha_objetivo", fechaObjetivo.toString());
        m.put("aportes", aportes.stream().map(Abono::aMapa).toList());
        return m;
    }

    public static MetaAhorro desdeMapa(Map<String, Object> m) {
        MetaAhorro meta = new MetaAhorro(Json.entero(m, "id"), Json.str(m, "nombre"),
                new BigDecimal(Json.str(m, "objetivo")), LocalDate.parse(Json.str(m, "fecha_objetivo")));
        Json.lista(m, "aportes").forEach(a -> meta.aportes.add(Abono.desdeMapa(a)));
        return meta;
    }
}
