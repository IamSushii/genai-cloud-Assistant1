import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;

public class Server {
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String DB_URL = System.getenv("DB_URL"); 
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/chat", new ApiHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("--- Enterprise Java Backend Running on Port " + port + " ---");
    }

    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            boolean hasPrompt = query != null && query.contains("prompt=");

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && !hasPrompt) {
                String jsonHistory = getChatHistoryAsJson("default_session");
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] responseBytes = jsonHistory.getBytes("UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                String prompt = "Hello";
                if (query != null && query.contains("prompt=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("prompt=")) {
                            prompt = java.net.URLDecoder.decode(param.substring(7), "UTF-8");
                        }
                    }
                }

                String botResponse = callGeminiAI("default_session", prompt);
                saveToDatabase("default_session", prompt, botResponse);

                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                byte[] responseBytes = botResponse.getBytes("UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            }
        }
    }

    private static String getChatHistoryAsJson(String sessionId) {
        if (DB_URL == null) return "[]";
        JsonArray jsonArray = new JsonArray();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT user_message, bot_response FROM chat_history WHERE session_id = ? ORDER BY id ASC";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("userMessage", rs.getString("user_message"));
                obj.addProperty("botResponse", rs.getString("bot_response"));
                jsonArray.add(obj);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return gson.toJson(jsonArray);
    }

    private static String callGeminiAI(String sessionId, String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + API_KEY;
            
            JsonObject bodyObj = new JsonObject();
            JsonArray contentsArray = new JsonArray();

            if (DB_URL != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT user_message, bot_response FROM chat_history WHERE session_id = ? ORDER BY id ASC";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, sessionId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {
                                String uMsg = rs.getString("user_message");
                                String bResp = rs.getString("bot_response");

                                JsonObject userPart = new JsonObject();
                                userPart.addProperty("text", uMsg);
                                JsonArray userParts = new JsonArray();
                                userParts.add(userPart);
                                JsonObject userContent = new JsonObject();
                                userContent.addProperty("role", "user");
                                userContent.add("parts", userParts);
                                contentsArray.add(userContent);

                                JsonObject modelPart = new JsonObject();
                                modelPart.addProperty("text", bResp);
                                JsonArray modelParts = new JsonArray();
                                modelParts.add(modelPart);
                                JsonObject modelContent = new JsonObject();
                                modelContent.addProperty("role", "model");
                                modelContent.add("parts", modelParts);
                                contentsArray.add(modelContent);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            JsonObject currentPart = new JsonObject();
            currentPart.addProperty("text", prompt);
            JsonArray currentParts = new JsonArray();
            currentParts.add(currentPart);
            JsonObject currentContent = new JsonObject();
            currentContent.addProperty("role", "user");
            currentContent.add("parts", currentParts);
            contentsArray.add(currentContent);

            bodyObj.add("contents", contentsArray);
            String jsonPayload = gson.toJson(bodyObj);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "API Error: " + response.body();
            }

            JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            if (jsonResponse.has("candidates")) {
                return jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
            } else {
                return "Error: Blocked or invalid response structure from Gemini.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private static void saveToDatabase(String sessionId, String userMsg, String botMsg) {
        if (DB_URL == null) return; 
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "INSERT INTO chat_history (session_id, user_message, bot_response) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, sessionId);
            pstmt.setString(2, userMsg);
            pstmt.setString(3, botMsg);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}