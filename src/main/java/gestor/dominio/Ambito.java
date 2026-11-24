package gestor.dominio;

import gestor.util.Etiquetado;

public enum Ambito implements Etiquetado {
    NEGOCIO("negocio"), PERSONAL("personal");

    private final String etiqueta;

    Ambito(String etiqueta) { this.etiqueta = etiqueta; }

    @Override public String etiqueta() { return etiqueta; }

    @Override public String toString() { return etiqueta; }
}
