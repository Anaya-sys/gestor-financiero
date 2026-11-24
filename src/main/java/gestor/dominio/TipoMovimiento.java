package gestor.dominio;

import gestor.util.Etiquetado;

public enum TipoMovimiento implements Etiquetado {
    INGRESO("ingreso"), GASTO("gasto");

    private final String etiqueta;

    TipoMovimiento(String etiqueta) { this.etiqueta = etiqueta; }

    @Override public String etiqueta() { return etiqueta; }

    @Override public String toString() { return etiqueta; }
}
