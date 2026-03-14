package com.launcher.game;

import com.google.gson.*;
import com.launcher.auth.AuthResult;
import com.launcher.model.Profile;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GameLauncher {

    private final Path gameDir;

    public GameLauncher(Path gameDir) {
        this.gameDir = gameDir;
    }

    /** Builds the command and starts the Minecraft process. */
    public Process launch(JsonObject versionData, AuthResult auth, Profile profile)
            throws IOException {
        String versionId = versionData.get("id").getAsString();
        Path nativesDir = gameDir.resolve("versions").resolve(versionId).resolve("natives");

        List<String> classpath = buildClasspath(versionData, versionId);
        String mainClass = versionData.get("mainClass").getAsString();
        String cpSep = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";

        // Prepend brand-patch JAR so our ClientBrandRetriever overrides the one
        // hardcoded as "vanilla" inside the Minecraft client JAR.
        String brandPrefix = "";
        try {
            brandPrefix = BrandPatcher.createBrandJar("NextLauncher").toAbsolutePath() + cpSep;
        } catch (Exception e) {
            System.err.println("[Launcher] Brand patch failed: " + e.getMessage());
        }
        String cpString = brandPrefix + String.join(cpSep, classpath);

        List<String> command = new ArrayList<>();

        // Java binary
        String javaHome = System.getProperty("java.home");
        command.add(javaHome + File.separator + "bin" + File.separator + "java");

        // Memory
        int ram = profile.getAllocatedRam();
        command.add("-Xmx" + ram + "M");
        command.add("-Xms" + Math.min(512, ram) + "M");

        // JVM args from profile (Aikar's flags are pre-filled there by default)
        String customArgs = profile.getCustomJvmArgs();
        if (customArgs != null && !customArgs.isBlank()) {
            for (String arg : customArgs.trim().split("\\s+")) {
                if (!arg.isEmpty()) command.add(arg);
            }
        }

        // JVM args: new format has -cp / ${classpath} / -Djava.library.path etc. as templates
        if (versionData.has("arguments") && versionData.getAsJsonObject("arguments").has("jvm")) {
            JsonArray jvmArgs = versionData.getAsJsonObject("arguments").getAsJsonArray("jvm");
            for (JsonElement el : jvmArgs) {
                if (!el.isJsonPrimitive()) continue;
                String arg = el.getAsString();
                if (arg.startsWith("-Xmx") || arg.startsWith("-Xms")) continue;
                // Replace ${classpath} in-place so -cp gets its value
                command.add(resolveJvm(arg, nativesDir).replace("${classpath}", cpString));
            }
        } else {
            // Old format (pre-1.13): add manually
            command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
            command.add("-cp");
            command.add(cpString);
        }

        // Main class
        command.add(mainClass);

        // Game args
        command.addAll(buildGameArgs(versionData, auth, versionId));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    // -------------------------------------------------------------------------
    // Classpath
    // -------------------------------------------------------------------------

    private List<String> buildClasspath(JsonObject versionData, String versionId) {
        String os = GameDownloader.getOs();
        List<String> cp = new ArrayList<>();
        Path libDir = gameDir.resolve("libraries");

        for (JsonElement el : versionData.getAsJsonArray("libraries")) {
            JsonObject lib = el.getAsJsonObject();
            if (!ruleAllows(lib, os)) continue;

            if (lib.has("downloads")) {
                // Vanilla format: downloads.artifact.path
                JsonObject downloads = lib.getAsJsonObject("downloads");
                if (!downloads.has("artifact")) continue;
                String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                cp.add(libDir.resolve(path.replace("/", File.separator)).toAbsolutePath().toString());
            } else if (lib.has("name")) {
                // Fabric/Maven format: "group:artifact:version"
                String path = FabricManager.mavenPath(lib.get("name").getAsString());
                cp.add(libDir.resolve(path.replace("/", File.separator)).toAbsolutePath().toString());
            }
        }

        // Client JAR last
        cp.add(gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".jar")
                .toAbsolutePath().toString());
        return cp;
    }

    private boolean ruleAllows(JsonObject lib, String os) {
        if (!lib.has("rules")) return true;
        boolean allowed = false;
        for (JsonElement ruleEl : lib.getAsJsonArray("rules")) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (rule.has("os")) {
                String ruleName = rule.getAsJsonObject("os").has("name")
                        ? rule.getAsJsonObject("os").get("name").getAsString() : null;
                if (os.equals(ruleName)) allowed = "allow".equals(action);
            } else {
                allowed = "allow".equals(action);
            }
        }
        return allowed;
    }

    // -------------------------------------------------------------------------
    // Argument resolution
    // -------------------------------------------------------------------------

    private String resolveJvm(String arg, Path nativesDir) {
        return arg
                .replace("${natives_directory}", nativesDir.toAbsolutePath().toString())
                .replace("${launcher_name}", "MinecraftLauncher")
                .replace("${launcher_version}", "1.0");
    }

    private List<String> buildGameArgs(JsonObject versionData, AuthResult auth, String versionId) {
        List<String> args = new ArrayList<>();
        String assetsDir = gameDir.resolve("assets").toAbsolutePath().toString();
        String assetIndex = versionData.has("assetIndex")
                ? versionData.getAsJsonObject("assetIndex").get("id").getAsString()
                : versionId;

        if (versionData.has("arguments")
                && versionData.getAsJsonObject("arguments").has("game")) {
            // New format (1.13+)
            for (JsonElement el : versionData.getAsJsonObject("arguments").getAsJsonArray("game")) {
                if (el.isJsonPrimitive()) {
                    args.add(resolve(el.getAsString(), auth, versionId, assetsDir, assetIndex));
                }
            }
        } else if (versionData.has("minecraftArguments")) {
            // Legacy format
            for (String arg : versionData.get("minecraftArguments").getAsString().split(" ")) {
                args.add(resolve(arg, auth, versionId, assetsDir, assetIndex));
            }
        }
        return args;
    }

    private String resolve(String arg, AuthResult auth, String versionId,
                            String assetsDir, String assetIndex) {
        return arg
                .replace("${auth_player_name}", auth.getUsername())
                .replace("${version_name}", versionId)
                .replace("${game_directory}", gameDir.toAbsolutePath().toString())
                .replace("${assets_root}", assetsDir)
                .replace("${assets_index_name}", assetIndex)
                .replace("${auth_uuid}", auth.getUuid())
                .replace("${auth_access_token}", auth.getAccessToken())
                .replace("${user_type}", auth.isPremium() ? "msa" : "legacy")
                .replace("${version_type}", "NextLauncher")
                .replace("${user_properties}", "{}")
                .replace("${clientid}", "")
                .replace("${auth_xuid}", "");
    }
}
