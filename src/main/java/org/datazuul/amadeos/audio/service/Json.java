package org.datazuul.amadeos.audio.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON for the web services and the station list: objects become {@link Map}s, arrays
 * {@link List}s, numbers {@link Double}s.
 */
public final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** Parses any JSON value. */
    public static Object parse(String text) {
        Json json = new Json(text == null ? "" : text);
        Object value = json.value();
        json.skipSpace();
        if (json.pos != json.text.length()) throw json.error("Trailing characters");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (!(parse(text) instanceof Map<?, ?> map)) throw new IllegalArgumentException("Not a JSON object");
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        if (!(parse(text) instanceof List<?> list)) throw new IllegalArgumentException("Not a JSON array");
        return (List<Object>) list;
    }

    // ---- reading helpers ---------------------------------------------------------------------

    /** String member, or "" if missing or not a string/number. */
    public static String str(Object object, String key) {
        if (!(object instanceof Map<?, ?> map)) return "";
        Object v = map.get(key);
        if (v instanceof String s) return s;
        if (v instanceof Double d) return d == Math.rint(d) ? Long.toString(d.longValue()) : d.toString();
        return v == null ? "" : v.toString();
    }

    public static int integer(Object object, String key) {
        if (object instanceof Map<?, ?> map && map.get(key) instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(str(object, key).strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Object member(Object object, String key) {
        return object instanceof Map<?, ?> map ? map.get(key) : null;
    }

    /** Array member, or an empty list. */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object object, String key) {
        return member(object, key) instanceof List<?> l ? (List<Object>) l : List.of();
    }

    // ---- writing -----------------------------------------------------------------------------

    /** Writes maps, lists, strings, numbers, booleans and null, indented for people. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            quote(sb, s);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d));
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append("  ".repeat(indent + 1));
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                sb.append(++i < map.size() ? ",\n" : "\n");
            }
            sb.append("  ".repeat(indent)).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append("  ".repeat(indent + 1));
                write(sb, list.get(i), indent + 1);
                sb.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            sb.append("  ".repeat(indent)).append(']');
        } else {
            quote(sb, value.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
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

    // ---- parser ------------------------------------------------------------------------------

    private Object value() {
        skipSpace();
        if (pos >= text.length()) throw error("Unexpected end");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipSpace();
        if (consume('}')) return map;
        do {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
        } while (consume(','));
        expect('}');
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpace();
        if (consume(']')) return list;
        do {
            list.add(value());
            skipSpace();
        } while (consume(','));
        expect(']');
        return list;
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) break;
            char e = text.charAt(pos++);
            switch (e) {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw error("Bad unicode escape");
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(e);
            }
        }
        throw error("Unterminated string");
    }

    private Double number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("Unexpected character");
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("Bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) throw error("Unexpected character");
        pos += word.length();
        return value;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private boolean consume(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!consume(c)) throw error("Expected '" + c + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos);
    }
}
