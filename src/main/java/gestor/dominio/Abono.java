package gestor.dominio;

import gestor.util.Json;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pago parcial o total de una cuenta, o aporte a una meta de ahorro. */
public record Abono(BigDecimal monto, LocalDate fecha) {
    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("monto", monto.toPlainString());
        m.put("fecha", fecha.toString());
        return m;
    }

    public static Abono desdeMapa(Map<String, Object> m) {
        return new Abono(new BigDecimal(Json.str(m, "monto")), LocalDate.parse(Json.str(m, "fecha")));
    }
}
