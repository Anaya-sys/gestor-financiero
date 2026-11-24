package gestor.observador;

import static gestor.util.Formato.cop;

import gestor.dominio.Movimiento;
import gestor.dominio.Presupuesto;
import gestor.dominio.TipoMovimiento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

/** Una alerta al alcanzar el umbral y otra al superar el límite (RN-10). */
public class ObservadorPresupuesto extends ObservadorAlertas {
    private final ConsultaPresupuestos consulta;

    public ObservadorPresupuesto(ConsultaPresupuestos consulta, Consumer<String> salida) {
        super(salida);
        this.consulta = consulta;
    }

    @Override
    public void actualizar(Evento evento) {
        if (evento.tipo() != TipoEvento.MOVIMIENTO_REGISTRADO) return;
        Movimiento mov = evento.movimiento();
        if (mov.tipo() != TipoMovimiento.GASTO) return;
        for (Presupuesto p : consulta.presupuestosDe(mov.categoria())) {
            if (!p.incluye(mov.fecha())) continue;
            BigDecimal despues = consulta.consumo(p);
            BigDecimal antes = despues.subtract(mov.monto());
            if (despues.compareTo(p.limite()) > 0) {
                if (antes.compareTo(p.limite()) <= 0) {
                    alertar("Presupuesto de '" + p.categoria() + "' EXCEDIDO: gastado " + cop(despues)
                            + " de " + cop(p.limite()) + ".");
                }
            } else if (antes.compareTo(p.montoAlerta()) < 0 && despues.compareTo(p.montoAlerta()) >= 0) {
                BigDecimal uso = despues.multiply(BigDecimal.valueOf(100)).divide(p.limite(), 0, RoundingMode.HALF_UP);
                alertar("Presupuesto de '" + p.categoria() + "' próximo a agotarse: " + uso.toPlainString()
                        + "% consumido (umbral " + p.umbral().setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + "%), gastado " + cop(despues) + " de " + cop(p.limite()) + ".");
            }
        }
    }
}
