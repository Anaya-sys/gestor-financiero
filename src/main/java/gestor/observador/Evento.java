package gestor.observador;

import gestor.dominio.CuentaFinanciera;
import gestor.dominio.Movimiento;
import java.time.LocalDate;
import java.util.List;

/** Evento financiero. Según el tipo, trae el movimiento o las cuentas (y la fecha de hoy) afectadas. */
public record Evento(TipoEvento tipo, String mensaje, Movimiento movimiento, List<CuentaFinanciera> cuentas,
                     LocalDate hoy) {
    public static Evento de(TipoEvento tipo, String mensaje) {
        return new Evento(tipo, mensaje, null, List.of(), null);
    }

    public static Evento de(TipoEvento tipo, String mensaje, Movimiento movimiento) {
        return new Evento(tipo, mensaje, movimiento, List.of(), null);
    }

    public static Evento de(TipoEvento tipo, String mensaje, List<CuentaFinanciera> cuentas, LocalDate hoy) {
        return new Evento(tipo, mensaje, null, cuentas, hoy);
    }
}
