package com.launcher.game;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Downloads the correct NextLauncher Fabric mod JAR for the active MC version,
 * and registers the player's UUID with the NL user registry via Cloudflare Worker.
 */
public class NLModInstaller {

    private static final String RELEASE_BASE =
            "https://github.com/nexciq/nextlauncher/releases/download/v1.0.0/";

    /**
     * Maps MC major version prefix → mod JAR filename on GitHub Releases.
     * Key matched with startsWith against the running MC version.
     */
    private static final Map<String, String> VERSION_MAP = Map.of(
        "1.21.11", "nextlauncher-mod-1.0.0+mc1.21.11.jar",
        "1.21",    "nextlauncher-mod-1.0.0+mc1.21.1.jar",
        "1.20",    "nextlauncher-mod-1.0.0+mc1.20.4.jar"
    );

    public static final String MOD_FILENAME = "nextlauncher-mod.jar";

    /** Cloudflare Worker endpoint for UUID registration. */
    public static final String REGISTER_URL =
            "https://nextlauncher-register.nexciq.workers.dev";

    // -------------------------------------------------------------------------

    /**
     * Downloads the mod JAR matching the given MC version if not already present.
     * Silently skips unsupported versions.
     */
    public static void ensureInstalled(Path gameDir, String mcVersion, Consumer<String> status) {
        String jarName = resolveJarName(mcVersion);
        if (jarName == null) {
            System.out.println("[NLModInstaller] No mod for MC " + mcVersion + " – skipping.");
            return;
        }

        Path modsDir = gameDir.resolve("mods");
        Path modJar  = modsDir.resolve(MOD_FILENAME);
        Path marker  = modsDir.resolve(".nl-mod-version");

        try {
            Files.createDirectories(modsDir);

            // Re-download if the cached version doesn't match
            if (Files.exists(modJar) && Files.exists(marker)) {
                String current = Files.readString(marker, StandardCharsets.UTF_8).trim();
                if (current.equals(jarName)) return;
                Files.delete(modJar);
            } else if (Files.exists(modJar) && Files.exists(marker)) {
                return;
            }

            if (status != null) status.accept("Pobieranie NextLauncher Mod dla MC " + mcVersion + "…");

            downloadFile(RELEASE_BASE + jarName, modJar);
            Files.writeString(marker, jarName, StandardCharsets.UTF_8);

            if (status != null) status.accept("NextLauncher Mod zainstalowany.");
            System.out.println("[NLModInstaller] Installed " + jarName);

        } catch (Exception e) {
            System.err.println("[NLModInstaller] Could not install mod: " + e.getMessage());
        }
    }

    /**
     * Registers the player UUID with the Cloudflare Worker backend.
     * Fire-and-forget background thread.
     */
    public static void registerUuid(String uuid) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        new URL(REGISTER_URL + "?uuid=" + uuid).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8_000);
                conn.setReadTimeout(8_000);
                conn.setRequestProperty("User-Agent", "NextLauncher/1.0");
                int code = conn.getResponseCode();
                System.out.println("[NLModInstaller] UUID registration HTTP " + code);
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[NLModInstaller] UUID registration failed: " + e.getMessage());
            }
        }, "nl-register").start();
    }

    // -------------------------------------------------------------------------

    private static String resolveJarName(String mcVersion) {
        // Check exact match first (e.g. "1.21.11")
        if (VERSION_MAP.containsKey(mcVersion)) return VERSION_MAP.get(mcVersion);
        // Then prefix match (e.g. "1.21.x" → "1.21")
        for (Map.Entry<String, String> e : VERSION_MAP.entrySet()) {
            if (mcVersion.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static void downloadFile(String urlStr, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "NextLauncher/1.0");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + urlStr);
        try (InputStream is = conn.getInputStream();
             OutputStream os = new FileOutputStream(target.toFile())) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
        }
    }
}
