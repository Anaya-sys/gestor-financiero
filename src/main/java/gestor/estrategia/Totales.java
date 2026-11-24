package gestor.estrategia;

import gestor.dominio.Movimiento;
import gestor.dominio.TipoMovimiento;
import java.math.BigDecimal;
import java.util.Collection;

public record Totales(BigDecimal ingresos, BigDecimal gastos) {
    public static Totales de(Collection<? extends Movimiento> movimientos) {
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        for (Movimiento m : movimientos) {
            if (m.tipo() == TipoMovimiento.INGRESO) ingresos = ingresos.add(m.monto());
            else gastos = gastos.add(m.monto());
        }
        return new Totales(ingresos, gastos);
    }

    public BigDecimal balance() { return ingresos.subtract(gastos); }
}
