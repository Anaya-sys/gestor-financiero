package gestor.estrategia;

import gestor.dominio.CuentaFinanciera;
import gestor.dominio.TipoCuenta;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Saldo inicial + movimientos registrados + cuentas por cobrar/pagar que vencen dentro de `dias`. */
public class EstrategiaProyeccion implements EstrategiaFinanciera {
    private final int dias;

    public EstrategiaProyeccion(int dias) { this.dias = dias; }

    public EstrategiaProyeccion() { this(30); }

    @Override
    public BigDecimal calcular(ContextoFinanciero ctx) {
        BigDecimal saldo = ctx.saldoInicial().add(Totales.de(ctx.movimientos()).balance());
        LocalDate limite = ctx.hoy().plusDays(dias);
        for (CuentaFinanciera c : ctx.cuentas()) {
            if (!c.saldada() && !c.vencimiento().isAfter(limite)) {
                saldo = c.tipo() == TipoCuenta.COBRAR ? saldo.add(c.saldo()) : saldo.subtract(c.saldo());
            }
        }
        return saldo;
    }
}
