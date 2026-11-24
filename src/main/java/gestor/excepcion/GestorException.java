package gestor.excepcion;

/** Base de los errores controlados; la consola los muestra sin cerrarse (RNF-02). */
public class GestorException extends RuntimeException {
    public GestorException(String mensaje) { super(mensaje); }
    public GestorException(String mensaje, Throwable causa) { super(mensaje, causa); }

    public static class DatoInvalido extends GestorException {
        public DatoInvalido(String m) { super(m); }
    }
    public static class MontoInvalido extends DatoInvalido {
        public MontoInvalido(String m) { super(m); }
    }
    public static class CategoriaInexistente extends DatoInvalido {
        public CategoriaInexistente(String m) { super(m); }
    }
    public static class PeriodoInvalido extends DatoInvalido {
        public PeriodoInvalido(String m) { super(m); }
    }
    public static class NoEncontrado extends GestorException {
        public NoEncontrado(String m) { super(m); }
    }
    public static class OperacionInvalida extends GestorException {
        public OperacionInvalida(String m) { super(m); }
    }
    public static class AbonoInvalido extends OperacionInvalida {
        public AbonoInvalido(String m) { super(m); }
    }
    public static class CuentaSaldada extends OperacionInvalida {
        public CuentaSaldada(String m) { super(m); }
    }
    public static class MargenNoCalculable extends GestorException {
        public MargenNoCalculable(String m) { super(m); }
    }
    public static class ErrorPersistencia extends GestorException {
        public ErrorPersistencia(String m) { super(m); }
        public ErrorPersistencia(String m, Throwable causa) { super(m, causa); }
    }
}
