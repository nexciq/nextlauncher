package com.launcher.game;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VersionManager {

    private static final String VERSION_MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private final Gson gson = new Gson();

    /** Returns all release and snapshot versions from Mojang. */
    public List<VersionInfo> getVersionList() throws IOException {
        String json = fetchUrl(VERSION_MANIFEST_URL);
        JsonObject manifest = gson.fromJson(json, JsonObject.class);
        JsonArray versions = manifest.getAsJsonArray("versions");

        List<VersionInfo> list = new ArrayList<>();
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            String type = v.get("type").getAsString();
            if ("release".equals(type) || "snapshot".equals(type)) {
                VersionInfo info = new VersionInfo();
                info.id = v.get("id").getAsString();
                info.type = type;
                info.url = v.get("url").getAsString();
                list.add(info);
            }
        }
        return list;
    }

    /** Fetches and parses the JSON for a specific version. */
    public JsonObject getVersionData(String url) throws IOException {
        return gson.fromJson(fetchUrl(url), JsonObject.class);
    }

    public String fetchUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "MinecraftLauncher/1.0");

        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    public static class VersionInfo {
        public String id;
        public String type;
        public String url;

        @Override
        public String toString() {
            return "snapshot".equals(type) ? id + " [snapshot]" : id;
        }
    }
}
