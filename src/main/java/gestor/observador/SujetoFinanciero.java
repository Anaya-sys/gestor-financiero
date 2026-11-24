package gestor.observador;

import java.util.ArrayList;
import java.util.List;

public class SujetoFinanciero {
    private final List<ObservadorFinanciero> observadores = new ArrayList<>();

    public void suscribir(ObservadorFinanciero o) {
        if (!observadores.contains(o)) observadores.add(o);
    }

    public void retirar(ObservadorFinanciero o) { observadores.remove(o); }

    public boolean estaSuscrito(ObservadorFinanciero o) { return observadores.contains(o); }

    public void notificar(Evento evento) {
        for (ObservadorFinanciero o : new ArrayList<>(observadores)) o.actualizar(evento);
    }
}
