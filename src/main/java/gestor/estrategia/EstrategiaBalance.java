package gestor.estrategia;

import java.math.BigDecimal;

/** Ingresos - gastos (RN-07). Coincide con la utilidad del periodo (RN-08). */
public class EstrategiaBalance implements EstrategiaFinanciera {
    @Override
    public BigDecimal calcular(ContextoFinanciero ctx) {
        return Totales.de(ctx.movimientos()).balance();
    }
}
