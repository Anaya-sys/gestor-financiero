package gestor.estrategia;

import java.math.BigDecimal;

/** Patrón Strategy (sección 7.1): contrato común de los cálculos financieros intercambiables. */
public interface EstrategiaFinanciera {
    BigDecimal calcular(ContextoFinanciero ctx);
}
