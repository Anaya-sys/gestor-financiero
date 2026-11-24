package gestor.dominio;

import gestor.util.Etiquetado;

public enum TipoCuenta implements Etiquetado {
    COBRAR("cobrar"), PAGAR("pagar");

    private final String etiqueta;

    TipoCuenta(String etiqueta) { this.etiqueta = etiqueta; }

    @Override public String etiqueta() { return etiqueta; }

    @Override public String toString() { return etiqueta; }
}
