package com.launcher.auth;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Microsoft OAuth 2.0 Authorization Code flow with localhost callback.
 * Opens the system browser for login; captures the auth code via a local HTTP server.
 * Flow: browser login → localhost callback → MS token → Xbox Live → XSTS → Minecraft
 */
public class MicrosoftAuth {

    public static final String CLIENT_ID = "f6ac6dd5-f676-406f-b4d2-f3520cdeeebf";

    private static final String AUTH_URL   = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL  = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL    = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL   = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN   = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    private final Gson gson = new Gson();
    private Consumer<String> statusCallback;
    private volatile boolean cancelled = false;

    public void setStatusCallback(Consumer<String> cb) { statusCallback = cb; }
    public void cancel() { cancelled = true; }
    private void status(String msg) { if (statusCallback != null) statusCallback.accept(msg); }

    /** Full auth flow: opens browser, waits for localhost callback, exchanges code. */
    public AuthResult authenticate() throws Exception {
        // Pick a free port for the local callback server
        int port;
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }
        String redirectUri = "http://localhost:" + port;

        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        // Start local HTTP server to receive the OAuth callback
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 1);
        server.createContext("/", exchange -> {
            try {
                String query = exchange.getRequestURI().getQuery();
                String code = null, error = null;
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length < 2) continue;
                        String key = kv[0];
                        String val = URLDecoder.decode(kv[1], "UTF-8");
                        if ("code".equals(key))              code = val;
                        if ("error_description".equals(key)) error = val;
                        else if ("error".equals(key) && error == null) error = val;
                    }
                }

                String html = code != null ? successHtml() : errorHtml(error != null ? error : "Błąd OAuth");
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }

                if (code != null) codeFuture.complete(code);
                else              codeFuture.completeExceptionally(new AuthException(error != null ? error : "Błąd OAuth"));
            } catch (Exception e) {
                codeFuture.completeExceptionally(e);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        try {
            String authUrl = AUTH_URL
                + "?client_id="    + URLEncoder.encode(CLIENT_ID, "UTF-8")
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&scope="        + URLEncoder.encode("XboxLive.SignIn offline_access", "UTF-8")
                + "&response_mode=query"
                + "&prompt=select_account";

            status("Otwieranie przeglądarki...");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(authUrl));
            } else {
                throw new AuthException("Nie można otworzyć przeglądarki.\nURL: " + authUrl);
            }

            status("Zaloguj się w otwartej przeglądarce...");

            String code;
            try {
                code = codeFuture.get(5, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw new AuthException("Czas logowania minął (5 minut).");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new AuthException(cause != null ? cause.getMessage() : "Błąd logowania");
            }

            if (cancelled) throw new AuthException("Logowanie anulowane.");

            // Exchange authorization code for access token
            status("Wymiana kodu na token...");
            String tokenBody = "client_id="   + URLEncoder.encode(CLIENT_ID, "UTF-8")
                + "&code="         + URLEncoder.encode(code, "UTF-8")
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8");

            JsonObject tokenResp = postForm(TOKEN_URL, tokenBody);
            if (!tokenResp.has("access_token")) {
                throw new AuthException("Brak tokenu MS w odpowiedzi.");
            }

            return completeAuth(tokenResp.get("access_token").getAsString());

        } finally {
            server.stop(1);
        }
    }

    private AuthResult completeAuth(String msToken) throws IOException, AuthException {
        // Xbox Live
        status("Logowanie do Xbox Live...");
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msToken);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");

        JsonObject xblResp = postJson(XBL_URL, xblBody.toString());
        String xblToken = xblResp.get("Token").getAsString();
        String userHash = xblResp.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        // XSTS
        status("Pobieranie tokenu XSTS...");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsProps.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");

        JsonObject xstsResp = postJson(XSTS_URL, xstsBody.toString());
        String xstsToken = xstsResp.get("Token").getAsString();

        // Minecraft
        status("Logowanie do Minecrafta...");
        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject mcResp = postJson(MC_LOGIN, mcBody.toString());
        String mcToken = mcResp.get("access_token").getAsString();

        // Profile
        status("Pobieranie profilu gracza...");
        JsonObject profile = getJson(MC_PROFILE, mcToken);
        if (!profile.has("id")) {
            throw new AuthException("To konto nie posiada Minecrafta Java Edition.");
        }

        return new AuthResult(
                profile.get("name").getAsString(),
                profile.get("id").getAsString(),
                mcToken, true);
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private JsonObject postForm(String url, String body) throws IOException {
        HttpURLConnection c = open(url, "POST");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setDoOutput(true);
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return readResponse(c);
    }

    private JsonObject postJson(String url, String body) throws IOException {
        HttpURLConnection c = open(url, "POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setDoOutput(true);
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String resp = read(is);
        if (code >= 400) {
            JsonObject j = gson.fromJson(resp, JsonObject.class);
            String err = j != null && j.has("XErr") ? "XErr=" + j.get("XErr") : "HTTP " + code;
            throw new HttpException(err, resp);
        }
        return gson.fromJson(resp, JsonObject.class);
    }

    private JsonObject getJson(String url, String bearer) throws IOException {
        HttpURLConnection c = open(url, "GET");
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        return readResponse(c);
    }

    private JsonObject readResponse(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String resp = read(is);
        JsonObject j = gson.fromJson(resp, JsonObject.class);
        if (code >= 400) {
            String err = j != null && j.has("error") ? j.get("error").getAsString() : "HTTP " + code;
            throw new HttpException(err, resp);
        }
        return j;
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10_000);
        c.setReadTimeout(30_000);
        return c;
    }

    private String read(InputStream is) throws IOException {
        if (is == null) return "{}";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    // -------------------------------------------------------------------------
    // HTML responses shown in browser after login
    // -------------------------------------------------------------------------

    private static String successHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>NextLauncher</title></head>"
             + "<body style='font-family:Arial,sans-serif;text-align:center;padding:60px;"
             + "background:#1a1a1a;color:#fff'>"
             + "<div style='font-size:72px;color:#4CAF50'>✓</div>"
             + "<h1 style='color:#4CAF50'>Zalogowano pomyślnie!</h1>"
             + "<p style='color:#aaa'>Możesz zamknąć tę kartę i wrócić do launchera.</p>"
             + "</body></html>";
    }

    private static String errorHtml(String error) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>NextLauncher</title></head>"
             + "<body style='font-family:Arial,sans-serif;text-align:center;padding:60px;"
             + "background:#1a1a1a;color:#fff'>"
             + "<div style='font-size:72px;color:#f44336'>✗</div>"
             + "<h1 style='color:#f44336'>Błąd logowania</h1>"
             + "<p style='color:#aaa'>" + escapeHtml(error) + "</p>"
             + "</body></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    public static class AuthException extends Exception {
        public AuthException(String msg) { super(msg); }
    }

    static class HttpException extends IOException {
        private final String error;
        HttpException(String error, String body) {
            super("HTTP error: " + error + "\n" + body);
            this.error = error;
        }
        String getError() { return error; }
    }
}
