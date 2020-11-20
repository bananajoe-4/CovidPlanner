import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.IOException;
import java.net.URI;
import java.util.Scanner;

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
        } catch (IOException e) {
            e.printStackTrace();
        }

        runConsoleClient();
        System.out.println("Shutdown server");
        server.shutdownNow();
    }

    private static void runConsoleClient(){
        Scanner scanner = new Scanner(System.in);
        System.out.println("type \"new route\" to start a new route \ntype \"shutdown\" to shut down the server.");
        while (true) {
            String input = scanner.nextLine();
            if ("shutdown".equals(input))
                break;
            if ("new route".equals(input)) {
                System.out.println("Please enter your origin address");
                String origin = scanner.nextLine();
                System.out.println("Now enter the destination address");
                String destination = scanner.nextLine();

                Client client = ClientBuilder.newClient();
                WebTarget target = client.target("http://localhost:4434/covid-planner/start")
                        .queryParam("origin", origin)
                        .queryParam("destination", destination);

                target.request().post(null);
                System.out.println("The server is now ready for location updates.");
            }
        }
    }
}
