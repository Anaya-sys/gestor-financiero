package gestor.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * JSON mínimo (Map, List, String, Number, Boolean, null) para no depender de librerías externas.
 * Produce el mismo formato que la versión en Python, por lo que los archivos son intercambiables.
 */
public final class Json {
    private Json() {}

    // ---------- escritura ----------
    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb, 0);
        return sb.toString();
    }

    private static void escribir(Object v, StringBuilder sb, int nivel) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            cadena(s, sb);
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sangria(sb, nivel + 1);
                cadena(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                escribir(e.getValue(), sb, nivel + 1);
                sb.append(++i < m.size() ? ",\n" : "\n");
            }
            sangria(sb, nivel);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                sangria(sb, nivel + 1);
                escribir(l.get(i), sb, nivel + 1);
                sb.append(i + 1 < l.size() ? ",\n" : "\n");
            }
            sangria(sb, nivel);
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Tipo no serializable: " + v.getClass());
        }
    }

    private static void sangria(StringBuilder sb, int nivel) {
        sb.append("  ".repeat(nivel));
    }

    private static void cadena(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------- lectura ----------
    /** Lanza IllegalArgumentException si el texto no es JSON válido. */
    public static Object leer(String texto) {
        Lector l = new Lector(texto);
        Object v = l.valor();
        l.saltarEspacios();
        if (l.i != texto.length()) throw l.error("Texto sobrante");
        return v;
    }

    private static final class Lector {
        final String s;
        int i;

        Lector(String s) { this.s = s; }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON inválido: " + msg + " (posición " + i + ")");
        }

        void saltarEspacios() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object valor() {
            saltarEspacios();
            if (i >= s.length()) throw error("fin inesperado");
            char c = s.charAt(i);
            if (c == '{') return objeto();
            if (c == '[') return arreglo();
            if (c == '"') return cadena();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return numero();
        }

        Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            saltarEspacios();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                saltarEspacios();
                if (i >= s.length() || s.charAt(i) != '"') throw error("se esperaba un nombre de campo");
                String clave = cadena();
                saltarEspacios();
                esperar(':');
                m.put(clave, valor());
                saltarEspacios();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                esperar('}');
                return m;
            }
        }

        List<Object> arreglo() {
            List<Object> l = new ArrayList<>();
            i++; // [
            saltarEspacios();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(valor());
                saltarEspacios();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                esperar(']');
                return l;
            }
        }

        void esperar(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw error("se esperaba '" + c + "'");
            i++;
        }

        String cadena() {
            StringBuilder sb = new StringBuilder();
            i++; // comilla de apertura
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("escape \\u incompleto");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("escape \\u inválido");
                        }
                        i += 4;
                    }
                    default -> throw error("escape desconocido");
                }
            }
            throw error("cadena sin cerrar");
        }

        Number numero() {
            int inicio = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(inicio, i);
            if (t.isEmpty()) throw error("valor no reconocido");
            try {
                return t.matches("-?\\d+") ? (Number) Long.valueOf(t) : new BigDecimal(t);
            } catch (NumberFormatException e) {
                throw error("número inválido");
            }
        }
    }

    // ---------- lectura tipada de campos (lanzan excepción si falta o no coincide el tipo) ----------
    private static Object requerido(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        if (v == null) throw new NoSuchElementException("Falta el campo '" + clave + "'");
        return v;
    }

    public static String str(Map<String, Object> m, String clave) { return (String) requerido(m, clave); }

    public static int entero(Map<String, Object> m, String clave) { return ((Number) requerido(m, clave)).intValue(); }

    public static boolean bool(Map<String, Object> m, String clave) { return (Boolean) requerido(m, clave); }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> lista(Map<String, Object> m, String clave) {
        return (List<Map<String, Object>>) requerido(m, clave);
    }
}
