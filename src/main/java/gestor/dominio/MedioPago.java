package gestor.dominio;

import gestor.util.Etiquetado;

public enum MedioPago implements Etiquetado {
    EFECTIVO("efectivo"), TRANSFERENCIA("transferencia"), BILLETERA_DIGITAL("billetera digital");

    private final String etiqueta;

    MedioPago(String etiqueta) { this.etiqueta = etiqueta; }

    @Override public String etiqueta() { return etiqueta; }

    @Override public String toString() { return etiqueta; }
}
