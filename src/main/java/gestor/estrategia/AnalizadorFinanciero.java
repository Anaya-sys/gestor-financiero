package gestor.estrategia;

import java.math.BigDecimal;

/** Contexto del Strategy: delega el cálculo en la estrategia recibida, sin condicionales por indicador. */
public class AnalizadorFinanciero {
    private EstrategiaFinanciera estrategia;

    public AnalizadorFinanciero(EstrategiaFinanciera estrategia) { this.estrategia = estrategia; }

    public void cambiarEstrategia(EstrategiaFinanciera estrategia) { this.estrategia = estrategia; }

    public BigDecimal calcular(ContextoFinanciero ctx) { return estrategia.calcular(ctx); }
}
