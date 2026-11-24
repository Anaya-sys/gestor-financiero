package gestor.dominio;

import gestor.excepcion.GestorException.DatoInvalido;
import gestor.excepcion.GestorException.MontoInvalido;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Validaciones comunes de entradas (RF-03, RN-01). */
public final class Montos {
    private Montos() {}

    public static BigDecimal aMonto(Object valor) { return aMonto(valor, false); }

    /** Convierte a BigDecimal positivo (o no negativo con permitirCero). Lanza MontoInvalido si no cumple. */
    public static BigDecimal aMonto(Object valor, boolean permitirCero) {
        String texto = String.valueOf(valor).strip().replace("$", "").replace(" ", "");
        BigDecimal monto;
        try {
            monto = new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new MontoInvalido("El monto debe ser un número.");
        }
        if (monto.signum() < 0 || (monto.signum() == 0 && !permitirCero)) {
            throw new MontoInvalido("El monto debe ser mayor que cero.");
        }
        return monto;
    }

    public static LocalDate exigirFecha(LocalDate fecha, String nombre) {
        if (fecha == null) throw new DatoInvalido("La " + nombre + " no es válida.");
        return fecha;
    }

    public static String exigirTexto(String texto, String nombre) {
        String t = texto == null ? "" : texto.strip();
        if (t.isEmpty()) throw new DatoInvalido("El campo " + nombre + " es obligatorio.");
        return t;
    }
}
