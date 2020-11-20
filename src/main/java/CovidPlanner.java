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
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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


    @Path("Test")
    @GET
    public String test(){
        return "test succeeded";
    }

    @Path("start")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public String planRoute(@QueryParam("origin") String origin, @QueryParam("destination") String destination) {
        System.out.println("Called PlanRoute with: origin = "+origin+" and destination = "+destination);
        // Gesamtdauer berechnen.
        int duration = getTravelDuration(origin, destination);

        // Landkreis für RKI API ermitteln
        String originDistrict = getDistrict(origin);
        String destinationDistrict = getDistrict(destination);

        // Startort und Zielort nach Faellen abfragen.
        Map<String, Integer> casesPerDistrict = new HashMap<>();
        casesPerDistrict.put(originDistrict, -1);
        casesPerDistrict.put(destinationDistrict, -1);
        getDistrictWeekIncidence(casesPerDistrict);
        int casesOrigin = casesPerDistrict.get(originDistrict);
        int casesDestination = casesPerDistrict.get(destinationDistrict);

        // Lampen entsprechend schalten.
        setTrafficLight(1, casesOrigin);
        setTrafficLight(2, casesDestination);
        if (duration < TRAVEL_DURATION_THRESHOLD)
            hueController.setLightState(3, true, COLOR_GREEN);
        else
            hueController.setLightState(3, true, COLOR_RED);

        return "Duration: " + duration + ", casesOrigin: " + casesOrigin + ", casesDestination: " + casesDestination;
    }


    @Path("update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void UpdateLocation(@QueryParam("location") String location) {
        // Fallzahlen von aktuellem Ort abfragen.
        String locationDistrict = getDistrict(location);
        Map<String, Integer> casesMap = new HashMap<>();
        casesMap.put(locationDistrict, -1);
        getDistrictWeekIncidence(casesMap);
        int locationCases = casesMap.get(locationDistrict);
        // Lampe 1 entsprechend schalten.
        setTrafficLight(1, locationCases);
    }

    @Path("end")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String ReachedTarget() {
        // Alle Lampen ausschalten.
        hueController.setLightState(1, false, 0);
        hueController.setLightState(2, false, 0);
        hueController.setLightState(3, false, 0);

        return "success";
    }

    /**
     * Fills each entry of the given map with the districts associated current corona week incidence number.
     * @param districtCasesMap A Map<String, int> containing the wanted districts as keys.
     */
    private void getDistrictWeekIncidence(Map<String, Integer> districtCasesMap){
        WebTarget rkiApi = client.target("https://rki-covid-api.now.sh/api/districts");
        String rkiResponse = rkiApi.request(MediaType.APPLICATION_JSON).get(String.class);

        try (JsonReader reader = Json.createReader(new StringReader(rkiResponse));) {
            JsonArray districts = reader.readObject().getJsonArray("districts");

            for (int index = 0; index < districts.size(); ++index) {
                JsonObject district = districts.getJsonObject(index);
                String districtName = district.getString("name");
                // Check whether any of the district names from the given map equal the current iterations district name.
                for(String mapDistrictName : districtCasesMap.keySet()){
                    if(districtName != null && districtName.equals(mapDistrictName))
                        districtCasesMap.put(mapDistrictName, district.getInt("weekIncidence"));
                }
            }
        }
    }

    /**
     * Returns the time in seconds needed to travel from the origin to the destination. Returns -1 if it fails to do so.
     * @param origin An address that specifies the origin location.
     * @param destination An address that specifies the destination location.
     * @return Travel time in seconds or -1 if it fails.
     */
    private int getTravelDuration(String origin, String destination){
        int duration = -1;
        WebTarget mapsApi = client.target("https://maps.googleapis.com/maps/api/distancematrix/json")
                .queryParam("units", "metric")
                .queryParam("origins", origin)
                .queryParam("destinations", destination)
                .queryParam("key", MAPS_API_KEY);
        String mapsResponse = mapsApi.request(MediaType.APPLICATION_JSON).get(String.class);
        try (JsonReader reader = Json.createReader(new StringReader(mapsResponse));) {
            JsonObject jsonObject = reader.readObject();
            duration = jsonObject.getJsonArray("rows").getJsonObject(0).getJsonArray("elements")
                    .getJsonObject(0).getJsonObject("duration").getInt("value");
        } catch (Exception e) {
            System.err.println("Could not parse google maps api response");
        }
        return duration;
    }

    /**
     * Uses the google geocode api to return the district of the given location.
     * @param location An address that specifies the location.
     * @return The district of the given location. Returns an empty string if it fails to gather the district.
     */
    private String getDistrict(String location){
        String geoDistrict = "";
        WebTarget geocodeApi = client.target("https://maps.googleapis.com/maps/api/geocode/json")
                .queryParam("address", "München")
                .queryParam("sensor", "false")
                .queryParam("key", "AIzaSyDMQoPhRGqPxTGNctZ5Mh3z1AIJ9uzQE2A");
        String geocodeResponse = geocodeApi.request(MediaType.APPLICATION_JSON).get(String.class);
        try (JsonReader reader = Json.createReader(new StringReader(geocodeResponse));) {
            JsonObject jsonObject = reader.readObject();
            geoDistrict = jsonObject.getJsonArray("results").getJsonObject(0).getJsonArray("address_components")
                    .getJsonObject(1).getString("long_name");
        } catch (Exception e) {
            System.err.println("could not parse google geocode api response.");
        }
        return geoDistrict;
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
        hueController = new HueController("localhost", "702322b92a3c03046bf5b83a889c704", client);
    }

    @PreDestroy
    public void destroy() {
        this.client.close();
    }
}
