package com.launcher.game;

import com.google.gson.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Handles Fabric integration via the official Fabric Meta API.
 * https://meta.fabricmc.net/
 */
public class FabricManager {

    private static final String META = "https://meta.fabricmc.net/v2";
    private final Gson gson = new Gson();
    private final VersionManager versionManager = new VersionManager();

    /** Returns the latest stable Fabric loader version for a given Minecraft version. */
    public String getLatestLoaderVersion(String gameVersion) throws IOException {
        String url = META + "/versions/loader/" + enc(gameVersion) + "?limit=1";
        JsonArray arr = gson.fromJson(versionManager.fetchUrl(url), JsonArray.class);
        if (arr == null || arr.isEmpty())
            throw new IOException("Brak Fabric dla wersji Minecraft " + gameVersion);
        return arr.get(0).getAsJsonObject()
                .getAsJsonObject("loader").get("version").getAsString();
    }

    /**
     * Downloads the Fabric launch profile JSON for the given MC + loader version.
     * This profile has mainClass, Fabric-specific libraries, and extra JVM/game args.
     * It does NOT contain the vanilla client — it must be merged with the vanilla profile.
     */
    public JsonObject getFabricProfile(String gameVersion, String loaderVersion) throws IOException {
        String url = META + "/versions/loader/" + enc(gameVersion) + "/" + enc(loaderVersion) + "/profile/json";
        return gson.fromJson(versionManager.fetchUrl(url), JsonObject.class);
    }

    /**
     * Merges a Fabric profile on top of a vanilla profile.
     * Result: Fabric mainClass, Fabric libs prepended, arguments merged.
     */
    public JsonObject mergeProfiles(JsonObject vanilla, JsonObject fabric) {
        JsonObject merged = vanilla.deepCopy();

        // Fabric overrides the main class
        if (fabric.has("mainClass"))
            merged.addProperty("mainClass", fabric.get("mainClass").getAsString());

        // Fabric libs go FIRST in the classpath (they must shadow vanilla classes)
        JsonArray libs = new JsonArray();
        if (fabric.has("libraries"))
            fabric.getAsJsonArray("libraries").forEach(libs::add);
        if (vanilla.has("libraries"))
            vanilla.getAsJsonArray("libraries").forEach(libs::add);
        merged.add("libraries", libs);

        // Merge arguments (Fabric adds extra JVM args like -DFabricMcEmu=...)
        if (fabric.has("arguments")) {
            JsonObject fa = fabric.getAsJsonObject("arguments");
            JsonObject ma = merged.has("arguments")
                    ? merged.getAsJsonObject("arguments").deepCopy()
                    : new JsonObject();

            if (fa.has("game")) {
                JsonArray ga = ma.has("game") ? ma.getAsJsonArray("game").deepCopy() : new JsonArray();
                fa.getAsJsonArray("game").forEach(ga::add);
                ma.add("game", ga);
            }
            if (fa.has("jvm")) {
                JsonArray ja = ma.has("jvm") ? ma.getAsJsonArray("jvm").deepCopy() : new JsonArray();
                fa.getAsJsonArray("jvm").forEach(ja::add);
                ma.add("jvm", ja);
            }
            merged.add("arguments", ma);
        }

        return merged;
    }

    /**
     * Downloads all Fabric-specific Maven libraries.
     * Fabric libs use "name" + "url" format instead of the vanilla "downloads.artifact" format.
     */
    public void downloadFabricLibraries(JsonObject fabricProfile, Path gameDir,
                                         Consumer<String> status) throws IOException {
        if (!fabricProfile.has("libraries")) return;
        GameDownloader downloader = new GameDownloader(gameDir);
        downloader.setStatusCallback(status);
        Path libDir = gameDir.resolve("libraries");

        for (JsonElement el : fabricProfile.getAsJsonArray("libraries")) {
            JsonObject lib = el.getAsJsonObject();
            if (!lib.has("name")) continue;

            String name    = lib.get("name").getAsString();
            String baseUrl = lib.has("url") ? lib.get("url").getAsString()
                    : "https://repo1.maven.org/maven2/";
            if (!baseUrl.endsWith("/")) baseUrl += "/";

            String path   = mavenPath(name);
            Path   target = libDir.resolve(path.replace("/", File.separator));
            Files.createDirectories(target.getParent());

            if (!Files.exists(target)) {
                status.accept("Fabric: " + name);
                downloader.downloadFile(baseUrl + path, target, null);
            }
        }
    }

    /**
     * "net.fabricmc:fabric-loader:0.16.5"
     * → "net/fabricmc/fabric-loader/0.16.5/fabric-loader-0.16.5.jar"
     */
    public static String mavenPath(String coords) {
        String[] p = coords.split(":");
        String group    = p[0].replace('.', '/');
        String artifact = p[1];
        String version  = p[2];
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }
}
