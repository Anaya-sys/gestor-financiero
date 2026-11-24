package gestor.util;

import gestor.excepcion.GestorException.DatoInvalido;

/** Enumeraciones con una etiqueta en español (la misma que se guarda en el JSON). */
public interface Etiquetado {
    String etiqueta();

    static <E extends Enum<E> & Etiquetado> E desde(Class<E> tipo, String texto) {
        String buscado = texto == null ? "" : texto.strip();
        for (E e : tipo.getEnumConstants()) {
            if (e.etiqueta().equalsIgnoreCase(buscado)) return e;
        }
        throw new DatoInvalido("Valor no válido: '" + buscado + "'.");
    }
}
