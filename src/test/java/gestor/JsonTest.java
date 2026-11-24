package gestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gestor.util.Json;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test void idaYVueltaConTodosLosTipos() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texto", "Doña \"Rosa\" \\ \n\t ñ € 😀");
        m.put("entero", 42);
        m.put("verdadero", true);
        m.put("nulo", null);
        m.put("lista", List.of(1, "dos", List.of(), Map.of()));
        m.put("vacio", Map.of());
        Object leido = Json.leer(Json.escribir(m));
        assertEquals(Json.escribir(m), Json.escribir(leido));
        @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) leido;
        assertEquals("Doña \"Rosa\" \\ \n\t ñ € 😀", r.get("texto"));
        assertEquals(42L, r.get("entero"));
        assertNull(r.get("nulo"));
    }

    @Test void leeJsonGeneradoPorPython() {
        // Formato de la versión Python: ensure_ascii=False, indent=2, números enteros para ids y booleans
        String py = "{\n  \"version\": 1,\n  \"saldo_inicial\": \"1500000\",\n  \"movimientos\": [\n    {\"id\": 1, \"anulado\": false}\n  ]\n}";
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) Json.leer(py);
        assertEquals(1, Json.entero(m, "version"));
        assertEquals("1500000", Json.str(m, "saldo_inicial"));
        assertEquals(1, Json.entero(Json.lista(m, "movimientos").get(0), "id"));
    }

    @Test void escapesUnicodeYNumeros() {
        assertEquals("ñ", Json.leer("\"\\u00f1\""));
        assertEquals(new BigDecimal("1.5"), Json.leer("1.5"));
        assertEquals(-7L, Json.leer("-7"));
    }

    @Test void rechazaJsonInvalido() {
        for (String malo : new String[] {"", "{ roto", "{\"a\": }", "[1, 2", "{\"a\": 1} basura", "\"sin cerrar", "{'a': 1}", "{\"a\": 1,}"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.leer(malo), "debía rechazar: " + malo);
        }
    }

    @Test void camposFaltantesLanzanExcepcion() {
        Map<String, Object> m = new LinkedHashMap<>();
        assertThrows(RuntimeException.class, () -> Json.str(m, "falta"));
    }
}
