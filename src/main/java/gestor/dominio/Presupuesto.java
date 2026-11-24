package gestor.dominio;

import gestor.excepcion.GestorException.DatoInvalido;
import gestor.excepcion.GestorException.PeriodoInvalido;
import gestor.util.Json;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Límite de gasto de una categoría en un periodo, con porcentaje de alerta. Inmutable. */
public record Presupuesto(int id, String categoria, BigDecimal limite, LocalDate inicio, LocalDate fin,
                          BigDecimal umbral) {
    public Presupuesto {
        limite = Montos.aMonto(limite);
        Montos.exigirFecha(inicio, "fecha inicial");
        Montos.exigirFecha(fin, "fecha final");
        if (inicio.isAfter(fin)) throw new PeriodoInvalido("La fecha inicial no puede ser posterior a la final.");
        if (umbral == null || umbral.signum() <= 0 || umbral.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DatoInvalido("El porcentaje de alerta debe estar entre 0 y 100.");
        }
    }

    /** Gasto acumulado a partir del cual se alerta. */
    public BigDecimal montoAlerta() {
        return limite.multiply(umbral).divide(BigDecimal.valueOf(100));
    }

    public boolean incluye(LocalDate fecha) { return !fecha.isBefore(inicio) && !fecha.isAfter(fin); }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("categoria", categoria);
        m.put("limite", limite.toPlainString());
        m.put("inicio", inicio.toString());
        m.put("fin", fin.toString());
        m.put("umbral", umbral.toPlainString());
        return m;
    }

    public static Presupuesto desdeMapa(Map<String, Object> m) {
        return new Presupuesto(Json.entero(m, "id"), Json.str(m, "categoria"), new BigDecimal(Json.str(m, "limite")),
                LocalDate.parse(Json.str(m, "inicio")), LocalDate.parse(Json.str(m, "fin")),
                new BigDecimal(Json.str(m, "umbral")));
    }
}
