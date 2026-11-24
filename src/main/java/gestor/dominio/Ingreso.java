package gestor.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Ingreso extends Movimiento {
    public Ingreso(int id, BigDecimal monto, LocalDate fecha, String categoria, Ambito ambito,
              MedioPago medioPago, String descripcion) {
        this(id, monto, fecha, categoria, ambito, medioPago, descripcion, false);
    }

    Ingreso(int id, BigDecimal monto, LocalDate fecha, String categoria, Ambito ambito,
       MedioPago medioPago, String descripcion, boolean anulado) {
        super(id, monto, fecha, categoria, ambito, medioPago, descripcion, anulado);
    }

    @Override public TipoMovimiento tipo() { return TipoMovimiento.INGRESO; }
}
