package gestor.estrategia;

import gestor.excepcion.GestorException.MargenNoCalculable;
import java.math.BigDecimal;
import java.math.MathContext;

/** Utilidad / ingresos, en porcentaje. Sin ingresos no se calcula (RN-09). */
public class EstrategiaMargen implements EstrategiaFinanciera {
    @Override
    public BigDecimal calcular(ContextoFinanciero ctx) {
        Totales t = Totales.de(ctx.movimientos());
        if (t.ingresos().signum() == 0) {
            throw new MargenNoCalculable("No es posible calcular el margen: no hay ingresos en el periodo.");
        }
        return t.balance().multiply(BigDecimal.valueOf(100)).divide(t.ingresos(), MathContext.DECIMAL64);
    }
}
