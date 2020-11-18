import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.StringReader;

@Path("/covid-planner")
public class CovidPlanner {

    private static final int GREEN_THRESHOLD = 19;
    private static final int ORANGE_THRESHOLD = 50;
    private static final int RED_THRESHOLD = 100;

    private static final int TRAVEL_DURATION_THRESHOLD = 28800; // 8 hours in seconds.

    private static final int COLOR_GREEN = 24432;
    private static final int COLOR_ORANGE = 8568;
    private static final int COLOR_RED = 65403;

    private static final String MAPS_API_KEY = "AIzaSyCrSXZCLikclqLeOkQSktOQ3pTL-rXaZmc";

    private static final String BRIDGE_IP = "192.168.178.92";
    private static final String BRIDGE_USERNAME = "r0vUXAhhNiHy6CT3oLBlTBMECbykzVobj11KmGyG";

    private Client client;
    private HueController hueController;
    private boolean SingleUserLock;

    @Path("Test")
    @GET
    public String test(){
        return "test succeeded";
    }

    @Path("start")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String PlanRoute(String message) {
        int duration = 0;
        // RoutePlanner (singleUserLock) sperren (keine neuen Anfragen entgegen nehmen)
        SingleUserLock = true;
        // Route planen (Gesammtdauer)
        String origin = "Munich";
        String destination = "Berlin";
        WebTarget mapsApi = client.target("https://maps.googleapis.com/maps/api/distancematrix/json")
                .queryParam("units", "imperial")
                .queryParam("origins", origin)
                .queryParam("destinations", destination)
                .queryParam("key", MAPS_API_KEY);
        String mapsResponse = mapsApi.request(MediaType.APPLICATION_JSON).get(String.class);
        try (JsonReader reader = Json.createReader(new StringReader(mapsResponse));) {
            JsonObject jsonObject = reader.readObject();
            duration = jsonObject.getJsonArray("rows").getJsonObject(0).getJsonArray("elements")
                    .getJsonObject(0).getJsonObject("duration").getInt("value");
        } catch (Exception e) {
            System.out.println("could not parse google api response");
            //throw new IllegalStateException("Could not parse the google api response properly.", e);
        }

        // Startort und Zielort nach Faellen abfragen.
        WebTarget rkiApi = client.target("https://rki-covid-api.now.sh/api/districts");
        String rkiResponse = rkiApi.request(MediaType.APPLICATION_JSON).get(String.class);
        System.out.println(rkiResponse);
        origin = "München"; // TODO: Nur debug hilfsmittel...
        System.out.println(origin);
        int casesOrigin = -1;
        int casesDestination = -1;
        try (JsonReader reader = Json.createReader(new StringReader(rkiResponse));) {
            JsonArray districts = reader.readObject().getJsonArray("districts");
            for (int index = 0; index < districts.size(); ++index) {
                JsonObject district = districts.getJsonObject(index);
                String districtName = district.getString("name");
                System.out.println("District name: " + districtName);
                if (origin.equals(districtName))
                    casesOrigin = district.getInt("weekIncidence");
                if (destination.equals(districtName))
                    casesDestination = district.getInt("weekIncidence");
            }
        }
        System.out.println("origin: " + casesOrigin + ", dest: " + casesDestination);


        // Lampen entsprechend schalten.
        /*setTrafficLight(1, casesOrigin);
        setTrafficLight(2, casesDestination);
        if (duration < TRAVEL_DURATION_THRESHOLD)
            hueController.setLightState(3, true, COLOR_GREEN);
        else
            hueController.setLightState(3, true, COLOR_RED);*/

        return "Duration: " + duration + ", casesOrigin: " + casesOrigin + ", casesDestination: " + casesDestination;
    }


    @Path("update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void UpdateLocation() {
        // TODO: Muesste man sicherstellen dass nur der Nutzer der die Route erstellt hat diese auch updated?
        // Wenn RoutePlanner gesperrt
        // Wenn NeuerOrt == ZielOrt
        // Fallzahlen von aktuellem Ort abfragen.
        // Lampe 1 entsprechend schalten.
    }

    @Path("end")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String ReachedTarget() {
        // Alle Lampen ausschalten.
        hueController.setLightState(1, false, 0);
        hueController.setLightState(2, false, 0);
        hueController.setLightState(3, false, 0);

        // RoutePlanner wieder freigeben.
        SingleUserLock = false;

        return "received call";
    }


    /**
     * Sets the light of the lamp with the specified lampId to a case dependent color and blink state.
     *
     * @param lampId The id of the lamp you want to control.
     * @param cases  The number of cases. Enter -1 if no information is given.
     */
    private void setTrafficLight(int lampId, int cases) {
        if (cases < 0) // No information.
            hueController.setLightState(lampId, false, 0);
        else if (cases <= GREEN_THRESHOLD)
            hueController.setLightState(lampId, true, COLOR_GREEN);
        else if (cases <= ORANGE_THRESHOLD)
            hueController.setLightState(lampId, true, COLOR_ORANGE);
        else if (cases <= RED_THRESHOLD)
            hueController.setLightState(lampId, true, COLOR_RED);
        else // More than 100 cases.
            hueController.setLampBlinkMode(lampId, COLOR_RED);
    }

    @PostConstruct
    public void init() {
        this.client = ClientBuilder.newClient();
        hueController = new HueController("localhost", "42a2a5107557157408564077931eb15", client);
    }

    @PreDestroy
    public void destroy() {
        this.client.close();
    }
}
