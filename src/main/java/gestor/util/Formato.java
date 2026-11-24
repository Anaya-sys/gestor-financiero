package gestor.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formato de valores para consola: $1.234.567 (punto de miles, coma decimal). */
public final class Formato {
    private Formato() {}

    private static DecimalFormat formato(String patron) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.ROOT);
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return new DecimalFormat(patron, s);
    }

    public static String cop(BigDecimal monto) {
        String signo = monto.signum() < 0 ? "-" : "";
        BigDecimal m = monto.abs();
        boolean entero = m.stripTrailingZeros().scale() <= 0;
        String texto = entero ? formato("#,##0").format(m) : formato("#,##0.00").format(m.setScale(2, RoundingMode.HALF_UP));
        return signo + "$" + texto;
    }

    public static String pct(BigDecimal valor) {
        return valor.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
