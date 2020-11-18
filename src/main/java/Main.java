import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;

public class Main {
    public static void main(String... args) {
        String baseUrl = "http://localhost:4434";

        final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(
                URI.create(baseUrl),
                new ResourceConfig(CovidPlanner.class),
                false
        );
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));

        try {
            server.start();
            System.out.println("Server started. Listen @ " + baseUrl);
            Thread.currentThread().join();
        } catch (IOException | InterruptedException ioException) {
            System.out.println("------ FEHLER ------");
            ioException.printStackTrace();
        }
    }
}
