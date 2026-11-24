package gestor.observador;

import static gestor.util.Formato.cop;

import gestor.dominio.CuentaFinanciera;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/** Avisa de cuentas vencidas o próximas a vencer al registrarlas y al revisar vencimientos. */
public class ObservadorVencimientos extends ObservadorAlertas {
    private final int diasAviso;

    public ObservadorVencimientos(int diasAviso, Consumer<String> salida) {
        super(salida);
        this.diasAviso = diasAviso;
    }

    @Override
    public void actualizar(Evento evento) {
        if (evento.tipo() != TipoEvento.CUENTA_REGISTRADA && evento.tipo() != TipoEvento.REVISION_VENCIMIENTOS) return;
        LocalDate hoy = evento.hoy();
        for (CuentaFinanciera c : evento.cuentas()) {
            long dias = ChronoUnit.DAYS.between(hoy, c.vencimiento());
            String base = "Cuenta por " + c.tipo() + " #" + c.id() + " de " + c.tercero() + " (saldo " + cop(c.saldo()) + ")";
            if (c.vencida(hoy)) {
                alertar(base + " VENCIDA hace " + (-dias) + " día(s).");
            } else if (!c.saldada() && dias <= diasAviso) {
                alertar(base + (dias == 0 ? " vence hoy." : " vence en " + dias + " día(s)."));
            }
        }
    }
}
