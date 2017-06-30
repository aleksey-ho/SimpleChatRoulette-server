import com.opentok.OpenTok;
import com.opentok.exception.OpenTokException;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class ChatServer extends WebSocketServer {

    private static final int port = 8887;
    private static final String apiKey = "45899912";
    private static final String apiSecret = "4f33680959ea7c211faa7606be0559e565c312e9";
    private static OpenTok opentok;

    private List<WebSocket> users = new ArrayList<WebSocket>();

    private WebSocket waiter;
    private String waiterSessionId;
    private String waiterToken;

    public ChatServer(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
    }

    public ChatServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        System.out.println(connection.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room");

        users.add(connection);
        userJoined(connection);
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        System.out.println(connection.getRemoteSocketAddress().getAddress().getHostAddress() + " has left the room");

        users.remove(connection);
        userLeft(connection);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        System.out.println(connection + ": " + message);

        JSONObject response;
        try {
            response = new JSONObject(message);
            String sessionId = response.getString("sessionId");
            String token = response.getString("token");
            userJoinedWithSession(connection, sessionId, token);
        } catch (JSONException e) {
            userJoined(connection);
        }
    }

    @Override
    public void onFragment(WebSocket conn, Framedata fragment) {
        System.out.println("Fragment received: " + fragment);
    }

    public static void main(String[] args) throws InterruptedException , IOException {
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            System.out.println("You must define API_KEY and API_SECRET system properties in the build.gradle file.");
            System.exit(-1);
        }
        opentok = new OpenTok(Integer.parseInt(apiKey), apiSecret);

        ChatServer s = new ChatServer(port);
        s.start();
        System.out.println("ChatServer started on port: " + s.getPort());

        BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String in = sysin.readLine();
            if(in.equals("exit")) {
                s.stop();
                break;
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started");
    }

    private void userJoined(WebSocket connection) {
        // send existing session data if there is a waiter and clear stored data
        if (waiter != null) {
            JSONObject json = new JSONObject();
            json.put("apiKey", apiKey);
            json.put("sessionId", waiterSessionId);
            json.put("token", waiterToken);

            String message = json.toString();
            connection.send(message);

            waiter = null;
        }
        // create Session, send and store SessionId and Token if there is no waiter.
        else {
            waiter = connection;
            try {
                com.opentok.Session waiterSession = opentok.createSession();
                waiterSessionId = waiterSession.getSessionId();
                waiterToken = opentok.generateToken(waiterSessionId);

                JSONObject json = new JSONObject();
                json.put("apiKey", apiKey);
                json.put("sessionId", waiterSessionId);
                json.put("token", waiterToken);

                String message = json.toString();
                waiter.send(message);
            } catch (OpenTokException e) {
                e.printStackTrace();
            }
        }
    }

    private void userJoinedWithSession(WebSocket connection, String sessionId, String token) {
        // join to existing session and abandon its own one if there is a waiter
        if (waiter != null)
            userJoined(connection);
        // store received data if there is no waiter
        else {
            waiter = connection;
            waiterSessionId = sessionId;
            waiterToken = token;
        }
    }

    // clear stored data if waiter left chat
    private void userLeft(WebSocket user) {
        if (waiter == user)
            waiter = null;
    }

}
