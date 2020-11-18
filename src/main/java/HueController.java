import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HueController {

    private static final int WAIT_TIME = 1000;
    private final String bridgeIpAddr;
    private String bridgeUsername = null;
    private Map<Integer, ScheduledExecutorService> blinkingLamps = new HashMap<>();
    private static final int BLINK_PERIOD_DURATION = 500;
    private final Client client;

    public HueController(String bridgeIpAddr, Client client) {
        if (bridgeIpAddr == null || bridgeIpAddr.length() == 0)
            throw new IllegalArgumentException(bridgeIpAddr + " ist keine gueltige Ip Adresse.");
        if (client == null)
            throw new IllegalArgumentException("Der Client darf nicht null sein.");
        this.bridgeIpAddr = bridgeIpAddr;
        this.client = client;
    }

    public HueController(String bridgeIpAddr, String bridgeUsername, Client client) {
        this(bridgeIpAddr, client);
        if (bridgeUsername == null || bridgeUsername.length() == 0)
            throw new IllegalArgumentException(bridgeUsername + " ist kein gueltiger username.");
        this.bridgeUsername = bridgeUsername;
    }

    public void setLampBlinkMode(int lampId, int color, int periodDuration) {
        Runnable lampBlinker = new Runnable() {
            boolean isOn = true;
            public void run() {
                setLightState(lampId, isOn, color);
                isOn = !isOn;
            }
        };

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        blinkingLamps.put(lampId, executor);
        executor.scheduleAtFixedRate(lampBlinker, 0, periodDuration/2, TimeUnit.MILLISECONDS);
    }

    public void setLampBlinkMode(int lampId, int color) {
        setLampBlinkMode(lampId, color, BLINK_PERIOD_DURATION);
    }

    public void stopBlinkingLamp(int lampId) {
        ScheduledExecutorService blinker = blinkingLamps.get(lampId);
        if (blinker != null)
            blinker.shutdownNow();
    }

    public void setLightState(int lampNr, boolean isOn, int color) {
        if (color < 0 || color >= 1 << 16)
            throw new IllegalArgumentException("Color darf nur Werte im Bereich [0-65535] annehmen.");
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                .add("on", isOn);
        // Die Farbe kann nur angepasst werden wenn die Lampe an ist.
        if (isOn)
            jsonBuilder.add("sat", 254)
                    .add("bri", 254)
                    .add("hue", color);

        applyLightState(lampNr, jsonBuilder.build().toString());
    }

    private void applyLightState(int lampNr, String stateJson) {
        if (bridgeUsername == null)
            bridgeUsername = getBridgeUsername();

        WebTarget lampStateApi = client.target("http://" + bridgeIpAddr + "/api/" + bridgeUsername + "/lights/" + lampNr + "/state");
        lampStateApi.request().put(Entity.json(stateJson));
    }

    private String getBridgeUsername() {
        String username = null;
        System.out.println("Um fortzufahren druecken sie bitte den Link Knopf ihrer Bridge.");
        while (username == null) {
            // Warten damit die Bridge nicht mit Http-Anfragen bombardiert wird.
            try {
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException e) {
                throw new AssertionError("Prozess wurde unerwartet unterbrochen.");
            }
            username = requestUsername();
        }

        return username;
    }

    private String requestUsername() {
        String username = null;
        WebTarget bridgeApi = client.target("http://" + bridgeIpAddr + "/api");
        String usernameRequest = Json.createObjectBuilder().add("devicetype", "HueController#Anonymous").build().toString();
        String response = bridgeApi.request(MediaType.APPLICATION_JSON).post(Entity.json(usernameRequest), String.class);

        try (JsonReader reader = Json.createReader(new StringReader(response));) {
            JsonObject jsonObject = reader.readArray().getJsonObject(0);
            if (jsonObject.containsKey("success")) {
                username = jsonObject.getJsonObject("success").getJsonString("username").getString();
            }
        }
        System.out.println(username);
        return username;
    }
}