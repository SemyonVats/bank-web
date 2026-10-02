
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.*;

public final class BankHttpServer implements AutoCloseable {
    private final SimpleHttpServer server;
    private final BankService bank;

    public BankHttpServer(final int port, final BankService bank) throws IOException {
        this.bank = bank;
        server = new SimpleHttpServer(port);
        registerApi();
        server.route("/", request -> staticResource("/web/index.html", "text/html; charset=UTF-8"));
        server.route("/index.html", request -> staticResource("/web/index.html", "text/html; charset=UTF-8"));
        server.route("/app.css", request -> staticResource("/web/app.css", "text/css; charset=UTF-8"));
        server.route("/app.js", request -> staticResource("/web/app.js", "text/javascript; charset=UTF-8"));
    }

    public void start() {
        server.start();
    }

    private void registerApi() {
        api("/api/person/create", "POST", parameters -> personJson(bank.createPerson(
                HttpSupport.require(parameters, "firstName"),
                HttpSupport.require(parameters, "lastName"),
                HttpSupport.require(parameters, "passport")
        )));
        api("/api/person", "GET", parameters -> personJson(requirePerson(
                bank.getPerson(HttpSupport.require(parameters, "passport"))
        )));
        api("/api/accounts", "GET", parameters -> accountsJson(
                bank.getAccounts(HttpSupport.require(parameters, "passport"))
        ));
        api("/api/account/create", "POST", parameters -> accountJson(bank.createAccount(
                HttpSupport.require(parameters, "passport"),
                HttpSupport.require(parameters, "subId")
        )));
        api("/api/account", "GET", parameters -> accountJson(requireAccount(bank.getAccount(
                HttpSupport.require(parameters, "passport"),
                HttpSupport.require(parameters, "subId")
        ))));
        api("/api/account/set", "POST", parameters -> {
            final Account account = requireAccount(bank.getAccount(
                    HttpSupport.require(parameters, "passport"),
                    HttpSupport.require(parameters, "subId")
            ));
            account.setBalance(parseLong(parameters, "amount"));
            return accountJson(account);
        });
        api("/api/account/change", "POST", parameters -> {
            final Account account = requireAccount(bank.getAccount(
                    HttpSupport.require(parameters, "passport"),
                    HttpSupport.require(parameters, "subId")
            ));
            account.changeBalance(parseLong(parameters, "delta"));
            return accountJson(account);
        });
    }

    private void api(final String path, final String method,
                     final Function<Map<String, String>, String> action) {
        server.route(path, request -> {
            if (!method.equalsIgnoreCase(request.method())) {
                return jsonResponse(405, errorJson("Method not allowed"));
            }
            try {
                final String data = action.apply(HttpSupport.parameters(request));
                return jsonResponse(200, "{\"ok\":true,\"data\":" + data + "}");
            } catch (final IllegalArgumentException e) {
                return jsonResponse(400, errorJson(e.getMessage()));
            } catch (final Exception e) {
                e.printStackTrace(System.err);
                return jsonResponse(500, errorJson("Internal server error"));
            }
        });
    }

    private static SimpleHttpServer.Response staticResource(final String name, final String contentType)
            throws IOException {
        try (InputStream input = BankHttpServer.class.getResourceAsStream(name)) {
            return input == null
                    ? SimpleHttpServer.Response.text(404, "Not found")
                    : new SimpleHttpServer.Response(200, contentType, input.readAllBytes());
        }
    }

    private static SimpleHttpServer.Response jsonResponse(final int status, final String body) {
        return new SimpleHttpServer.Response(status, "application/json; charset=UTF-8",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static long parseLong(final Map<String, String> parameters, final String name) {
        try {
            return Long.parseLong(HttpSupport.require(parameters, name));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private static Person requirePerson(final Person person) {
        if (person == null) {
            throw new IllegalArgumentException("Person not found");
        }
        return person;
    }

    private static Account requireAccount(final Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        return account;
    }

    private static String personJson(final Person person) {
        return "{\"firstName\":%s,\"lastName\":%s,\"passport\":%s}".formatted(
                HttpSupport.jsonString(person.firstName()),
                HttpSupport.jsonString(person.lastName()),
                HttpSupport.jsonString(person.passport())
        );
    }

    private static String accountJson(final Account account) {
        final int separator = account.id().indexOf(':');
        final String subId = separator < 0 ? account.id() : account.id().substring(separator + 1);
        return "{\"id\":%s,\"subId\":%s,\"balance\":%d}".formatted(
                HttpSupport.jsonString(account.id()), HttpSupport.jsonString(subId), account.balance()
        );
    }

    private static String accountsJson(final List<Account> accounts) {
        return accounts.stream().map(BankHttpServer::accountJson)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String errorJson(final String message) {
        return "{\"ok\":false,\"error\":" + HttpSupport.jsonString(message) + "}";
    }

    @Override
    public void close() {
        server.close();
    }
}

