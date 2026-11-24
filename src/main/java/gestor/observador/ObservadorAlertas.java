package gestor.observador;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Base de los observadores que muestran alertas en consola y las conservan en `alertas()`. */
public abstract class ObservadorAlertas implements ObservadorFinanciero {
    private final Consumer<String> salida;
    private final List<String> alertas = new ArrayList<>();

    protected ObservadorAlertas(Consumer<String> salida) { this.salida = salida; }

    protected void alertar(String mensaje) {
        alertas.add(mensaje);
        salida.accept("[ALERTA] " + mensaje);
    }

    public List<String> alertas() { return Collections.unmodifiableList(alertas); }
}
