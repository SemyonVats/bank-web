import java.net.*;
import java.nio.charset.*;
import java.util.*;

final class HttpSupport {
    private HttpSupport() {
    }

    static Map<String, String> parameters(final SimpleHttpServer.Request request) {
        final String encoded = "GET".equalsIgnoreCase(request.method()) ? request.rawQuery() : request.body();
        final Map<String, String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (final String pair : encoded.split("&")) {
            final String[] parts = pair.split("=", 2);
            final String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            final String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            result.put(key, value);
        }
        return result;
    }

    static String require(final Map<String, String> parameters, final String name) {
        final String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing parameter: " + name);
        }
        return value.trim();
    }

    static String jsonString(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder result = new StringBuilder("\"");
        for (final char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32) {
                        result.append("\\u%04x".formatted((int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }
}

