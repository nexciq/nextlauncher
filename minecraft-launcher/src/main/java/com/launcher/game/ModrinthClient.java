package com.launcher.game;

import com.google.gson.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Downloads mandatory Fabric mods from Modrinth.
 * fabric-api and modmenu are always present and cannot be removed by the user.
 */
public class ModrinthClient {

    private static final String API = "https://api.modrinth.com/v2";

    // Mandatory mods: always present, never removable from the UI
    private static final String[] MANDATORY_IDS = {
            "P7dR8mSH",   // fabric-api   https://modrinth.com/mod/fabric-api
            "mOgUt4GM",   // modmenu      https://modrinth.com/mod/modmenu
    };

    private final Path gameDir;
    private final Gson gson = new Gson();
    private final VersionManager versionManager = new VersionManager();

    public ModrinthClient(Path gameDir) {
        this.gameDir = gameDir;
    }

    /**
     * Ensures fabric-api and modmenu are in the mods folder.
     * Downloads the latest version compatible with the given MC version.
     * Falls back to any latest Fabric version if no exact match is found.
     */
    public void ensureMandatoryMods(String gameVersion, Path modsDir,
                                     Consumer<String> status) throws IOException {
        Files.createDirectories(modsDir);
        for (String id : MANDATORY_IDS) {
            try {
                ensureMod(id, gameVersion, modsDir, status);
            } catch (Exception e) {
                status.accept("Ostrzeżenie: nie pobrano moda " + id + " — " + e.getMessage());
            }
        }
    }

    private void ensureMod(String projectId, String gameVersion, Path modsDir,
                             Consumer<String> status) throws IOException {
        // Try exact game version first, then fall back to no version filter
        JsonArray versions = fetchVersions(projectId, gameVersion);
        if (versions == null || versions.isEmpty())
            versions = fetchVersions(projectId, null);
        if (versions == null || versions.isEmpty()) {
            status.accept("Nie znaleziono moda " + projectId + " dla Fabric");
            return;
        }

        JsonObject version    = versions.get(0).getAsJsonObject();
        String     verNumber  = version.get("version_number").getAsString();
        JsonArray  files      = version.getAsJsonArray("files");

        // Find primary file
        JsonObject file = null;
        for (JsonElement fe : files) {
            JsonObject f = fe.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) { file = f; break; }
        }
        if (file == null && files.size() > 0) file = files.get(0).getAsJsonObject();
        if (file == null) return;

        String filename    = file.get("filename").getAsString();
        String downloadUrl = file.get("url").getAsString();
        String sha1        = file.has("hashes")
                ? file.getAsJsonObject("hashes").get("sha1").getAsString() : null;

        Path target = modsDir.resolve(filename);
        if (Files.exists(target)) return;   // already up to date

        // Remove previous version of this mod (tracked via marker file)
        Path marker = modsDir.resolve("." + projectId + ".tracker");
        if (Files.exists(marker)) {
            Path old = modsDir.resolve(Files.readString(marker).trim());
            if (Files.exists(old)) {
                try { Files.delete(old); } catch (IOException ignored) {}
            }
        }

        status.accept("Pobieranie: " + filename);
        new GameDownloader(gameDir).downloadFile(downloadUrl, target, sha1);
        Files.writeString(marker, filename);
    }

    private JsonArray fetchVersions(String projectId, String gameVersion) {
        try {
            String url = API + "/project/" + projectId + "/version"
                    + "?loaders=" + URLEncoder.encode("[\"fabric\"]", "UTF-8");
            if (gameVersion != null)
                url += "&game_versions=" + URLEncoder.encode("[\"" + gameVersion + "\"]", "UTF-8");
            String json = versionManager.fetchUrl(url);
            return gson.fromJson(json, JsonArray.class);
        } catch (Exception e) {
            return null;
        }
    }
}
