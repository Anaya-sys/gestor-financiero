package gestor.observador;

public enum TipoEvento {
    MOVIMIENTO_REGISTRADO("movimiento registrado"),
    MOVIMIENTO_MODIFICADO("movimiento modificado"),
    MOVIMIENTO_ANULADO("movimiento anulado"),
    CUENTA_REGISTRADA("cuenta registrada"),
    ABONO_REGISTRADO("abono registrado"),
    PRESUPUESTO_CREADO("presupuesto creado"),
    META_CREADA("meta creada"),
    APORTE_REGISTRADO("aporte registrado"),
    REVISION_VENCIMIENTOS("revisión de vencimientos");

    private final String descripcion;

    TipoEvento(String descripcion) { this.descripcion = descripcion; }

    public String descripcion() { return descripcion; }
}
