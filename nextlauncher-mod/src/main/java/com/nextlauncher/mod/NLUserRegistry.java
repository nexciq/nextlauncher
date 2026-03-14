package com.nextlauncher.mod;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Fetches and caches the list of NextLauncher user UUIDs from a public JSON file.
 *
 * Registry file format (GitHub raw URL):
 *   ["uuid1-...", "uuid2-...", ...]
 *
 * Set REGISTRY_URL to the raw GitHub URL of nl-users.json in your repo.
 * Set REGISTER_URL to your registration endpoint (or leave blank to skip auto-registration).
 */
public class NLUserRegistry {

    // ← Change this to your raw GitHub URL:
    // e.g. "https://raw.githubusercontent.com/your-name/nextlauncher/main/nl-users.json"
    private static final String REGISTRY_URL =
            "https://nextlauncher-register.nexciq.workers.dev/users";

    // ← Optional: REST endpoint that accepts POST ?uuid=<uuid> to register a user.
    // Leave empty to skip auto-registration.
    private static final String REGISTER_URL = "https://nextlauncher-register.nexciq.workers.dev";

    private static final Set<String> nlUsers = Collections.synchronizedSet(new HashSet<>());

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nl-registry");
                t.setDaemon(true);
                return t;
            });

    /** Call once at mod init. Fetches immediately, then every 60 s. */
    public static void startPolling() {
        fetchUsers();
        scheduler.scheduleAtFixedRate(NLUserRegistry::fetchUsers, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Registers the local player with the backend on world join.
     * Fire-and-forget; fails silently if no endpoint is configured.
     */
    public static void registerSelf(UUID uuid) {
        // Always treat yourself as an NL user in your own client
        nlUsers.add(uuid.toString());

        if (REGISTER_URL == null || REGISTER_URL.isBlank()) return;

        scheduler.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        new URL(REGISTER_URL + "?uuid=" + uuid).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "NextLauncher-Mod/1.0");
                conn.getResponseCode(); // trigger request
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    /** Returns true if the given player UUID is a registered NextLauncher user. */
    public static boolean isNLUser(UUID uuid) {
        return uuid != null && nlUsers.contains(uuid.toString());
    }

    private static void fetchUsers() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(REGISTRY_URL).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "NextLauncher-Mod/1.0");
            if (conn.getResponseCode() != 200) return;

            String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .trim();
            if (!json.startsWith("[")) return;

            // Simple JSON array parse without a dependency on Gson
            json = json.substring(1, json.length() - 1);
            Set<String> fresh = new HashSet<>();
            for (String part : json.split(",")) {
                String uuid = part.trim().replace("\"", "").replace("'", "");
                if (!uuid.isBlank()) fresh.add(uuid);
            }
            nlUsers.clear();
            nlUsers.addAll(fresh);
        } catch (Exception ignored) {
            // No internet or wrong URL – fail silently
        }
    }
}
