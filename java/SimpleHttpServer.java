import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

final class SimpleHttpServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Handler> routes = new HashMap<>();
    private volatile boolean running;
    private Thread acceptThread;

    SimpleHttpServer(final int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
    }

    void route(final String path, final Handler handler) {
        routes.put(path, handler);
    }

    void start() {
        running = true;
        acceptThread = Thread.ofPlatform().name("bank-http-accept").start(() -> {
            while (running) {
                try {
                    final Socket socket = serverSocket.accept();
                    workers.submit(() -> handle(socket));
                } catch (final IOException e) {
                    if (running) e.printStackTrace(System.err);
                }
            }
        });
    }

    private void handle(final Socket socket) {
        try (socket) {
            socket.setSoTimeout(15_000);
            final Request request = readRequest(socket.getInputStream());
            final Handler handler = routes.get(request.path());
            final Response response = handler == null ? Response.text(404, "Not found") : handler.handle(request);
            writeResponse(socket.getOutputStream(), response);
        } catch (final Exception e) {
            if (running) e.printStackTrace(System.err);
        }
    }

    private static Request readRequest(final InputStream input) throws IOException {
        final String requestLine = readLine(input);
        if (requestLine == null || requestLine.isBlank()) throw new IOException("Empty HTTP request");
        final String[] parts = requestLine.split(" ", 3);
        if (parts.length < 2) throw new IOException("Invalid HTTP request line");
        final URI target = URI.create(parts[1]);
        final Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            final int separator = line.indexOf(':');
            if (separator > 0) {
                headers.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                        line.substring(separator + 1).trim());
            }
        }
        final int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        if (contentLength < 0 || contentLength > 1_000_000) throw new IOException("Invalid request body size");
        final byte[] body = input.readNBytes(contentLength);
        if (body.length != contentLength) throw new IOException("Incomplete request body");
        return new Request(parts[0], target.getPath(), target.getRawQuery(),
                new String(body, StandardCharsets.UTF_8));
    }

    private static String readLine(final InputStream input) throws IOException {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            final int current = input.read();
            if (current == -1) return result.size() == 0 ? null : result.toString(StandardCharsets.US_ASCII);
            if (previous == '\r' && current == '\n') {
                final byte[] bytes = result.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.US_ASCII);
            }
            result.write(current);
            previous = current;
            if (result.size() > 16_384) throw new IOException("HTTP header line is too long");
        }
    }

    private static void writeResponse(final OutputStream output, final Response response) throws IOException {
        final String reason = switch (response.status()) {
            case 200 -> "OK"; case 400 -> "Bad Request"; case 404 -> "Not Found";
            case 405 -> "Method Not Allowed"; default -> "Internal Server Error";
        };
        final String headers = "HTTP/1.1 %d %s\r\nContent-Type: %s\r\nContent-Length: %d\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                .formatted(response.status(), reason, response.contentType(), response.body().length);
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(response.body());
        output.flush();
    }

    @Override
    public void close() {
        running = false;
        try { serverSocket.close(); } catch (final IOException ignored) { }
        if (acceptThread != null) acceptThread.interrupt();
        workers.close();
    }

    record Request(String method, String path, String rawQuery, String body) { }
    record Response(int status, String contentType, byte[] body) {
        static Response text(final int status, final String text) {
            return new Response(status, "text/plain; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8));
        }
    }
    @FunctionalInterface interface Handler { Response handle(Request request) throws Exception; }
}

