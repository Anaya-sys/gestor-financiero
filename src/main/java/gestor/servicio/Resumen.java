package gestor.servicio;

import gestor.dominio.Ambito;
import gestor.dominio.TipoMovimiento;
import java.math.BigDecimal;
import java.util.Map;

/** Resumen financiero de un periodo (RF-18). */
public record Resumen(BigDecimal ingresos, BigDecimal gastos, BigDecimal balance,
                      Map<Ambito, Parcial> porAmbito,
                      Map<TipoMovimiento, Map<String, BigDecimal>> porCategoria) {

    public record Parcial(BigDecimal ingresos, BigDecimal gastos, BigDecimal balance) {}
}
