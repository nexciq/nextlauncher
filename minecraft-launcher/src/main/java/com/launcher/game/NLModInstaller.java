package com.launcher.game;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.function.Consumer;

/**
 * Downloads and installs the NextLauncher Fabric mod into the game's mods folder.
 *
 * The mod is downloaded from GitHub Releases.
 * Change MOD_URL below to your actual release asset URL after you build and upload the mod.
 *
 * The mod adds a green "[NL]" badge to nametags of other NextLauncher users on servers.
 */
public class NLModInstaller {

    /**
     * Direct download URL of nextlauncher-mod-X.Y.Z.jar from GitHub Releases.
     * ← Change this to your actual release URL after uploading the mod JAR.
     */
    public static final String MOD_URL =
            "https://github.com/twoj-nick/nextlauncher/releases/download/v1.0.0/nextlauncher-mod-1.0.0.jar";

    public static final String MOD_FILENAME = "nextlauncher-mod.jar";

    /**
     * Ensures the NextLauncher mod JAR is present in the given mods directory.
     * Skips if already downloaded. Downloads silently if not.
     *
     * @param gameDir  root game directory (e.g. .nextlauncher)
     * @param status   optional status callback for UI updates
     */
    public static void ensureInstalled(Path gameDir, Consumer<String> status) {
        Path modsDir = gameDir.resolve("mods");
        Path modJar = modsDir.resolve(MOD_FILENAME);

        try {
            Files.createDirectories(modsDir);

            if (Files.exists(modJar)) {
                return; // already installed
            }

            if (status != null) status.accept("Pobieranie NextLauncher Mod…");

            HttpURLConnection conn = (HttpURLConnection) new URL(MOD_URL).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("User-Agent", "NextLauncher/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                System.err.println("[NLModInstaller] HTTP " + code + " – skipping mod install.");
                return;
            }

            try (InputStream is = conn.getInputStream();
                 OutputStream os = new FileOutputStream(modJar.toFile())) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            }

            if (status != null) status.accept("NextLauncher Mod zainstalowany.");
            System.out.println("[NLModInstaller] Mod installed to " + modJar);

        } catch (Exception e) {
            // Fail silently – the game will still launch, just without the mod
            System.err.println("[NLModInstaller] Could not install mod: " + e.getMessage());
        }
    }
}
