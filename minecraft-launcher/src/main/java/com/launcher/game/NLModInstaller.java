package com.launcher.game;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Downloads the correct NextLauncher Fabric mod JAR for the active MC version,
 * and registers the player's UUID with the NL user registry.
 *
 * Mod JARs are released on GitHub as:
 *   nextlauncher-mod-1.0.0+mc1.21.jar   → for MC 1.21.x
 *   nextlauncher-mod-1.0.0+mc1.20.jar   → for MC 1.20.x
 *
 * UUID registration endpoint (Cloudflare Worker):
 *   Set REGISTER_URL to your deployed worker URL, e.g.
 *   https://nextlauncher-register.<your-subdomain>.workers.dev
 */
public class NLModInstaller {

    private static final String RELEASE_BASE =
            "https://github.com/nexciq/nextlauncher/releases/download/v1.0.0/";

    /**
     * Maps MC major version prefix → mod JAR filename suffix.
     * Key = prefix matched against mcVersion (e.g. "1.21" matches "1.21.1", "1.21.4" etc.)
     */
    private static final Map<String, String> VERSION_MAP = Map.of(
        "1.21", "nextlauncher-mod-1.0.0+mc1.21.jar",
        "1.20", "nextlauncher-mod-1.0.0+mc1.20.jar"
    );

    public static final String MOD_FILENAME = "nextlauncher-mod.jar";

    /**
     * URL of the Cloudflare Worker that registers player UUIDs.
     * Leave blank to skip auto-registration.
     * Set to your worker URL after: wrangler deploy (registration-worker/)
     */
    public static final String REGISTER_URL = "https://nextlauncher-register.nexciq.workers.dev";

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

        try {
            Files.createDirectories(modsDir);

            // Re-download if the file is from a different MC version (name stored in a marker)
            Path marker = modsDir.resolve(".nl-mod-version");
            if (Files.exists(modJar) && Files.exists(marker)) {
                String current = Files.readString(marker, StandardCharsets.UTF_8).trim();
                if (current.equals(jarName)) return; // correct version already installed
                Files.delete(modJar); // wrong version, re-download
            } else if (Files.exists(modJar)) {
                return; // assume correct (legacy, no marker)
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
     * Registers the player UUID with the backend in a fire-and-forget thread.
     * Does nothing if REGISTER_URL is blank.
     */
    public static void registerUuid(String uuid) {
        if (REGISTER_URL == null || REGISTER_URL.isBlank()) return;

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

    /** Picks the JAR name for the given MC version, or null if unsupported. */
    private static String resolveJarName(String mcVersion) {
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
