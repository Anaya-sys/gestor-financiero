package gestor.dominio;

import gestor.excepcion.GestorException.DatoInvalido;
import gestor.util.Etiquetado;
import gestor.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;

public record Categoria(String nombre, TipoMovimiento tipo) {
    public Categoria {
        nombre = Montos.exigirTexto(nombre, "nombre de la categoría");
        if (tipo == null) throw new DatoInvalido("El tipo de categoría es obligatorio.");
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("tipo", tipo.etiqueta());
        return m;
    }

    public static Categoria desdeMapa(Map<String, Object> m) {
        return new Categoria(Json.str(m, "nombre"), Etiquetado.desde(TipoMovimiento.class, Json.str(m, "tipo")));
    }
}
