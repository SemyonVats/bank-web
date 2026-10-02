import java.awt.Desktop;
import java.net.URI;

public final class BankApplication {
    private BankApplication() {
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        final BankHttpServer server = new BankHttpServer(port, new BankService());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "bank-shutdown"));
        server.start();
        System.out.printf("Bank Web started: http://localhost:%d%n", port);
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
        }
        Thread.currentThread().join();
    }
}

