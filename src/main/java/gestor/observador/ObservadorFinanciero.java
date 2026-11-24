package gestor.observador;

/** Patrón Observer (sección 7.2): reacciona a los eventos publicados por el sujeto. */
public interface ObservadorFinanciero {
    void actualizar(Evento evento);
}
