package com.launcher.update;

import com.google.gson.*;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class AutoUpdater {

    /** Current launcher version — bump this with every release. */
    public static final String CURRENT_VERSION = "1.0.0";

    /**
     * Your GitHub repo in "owner/repo" format.
     * The updater reads releases from: https://github.com/{GITHUB_REPO}/releases
     */
    private static final String GITHUB_REPO = "nexciq/nextlauncher";

    private static final String API_URL =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    /** Checks for updates in background. Shows a dialog if a newer version is found. */
    public static void checkAsync(JFrame parent) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestProperty("User-Agent", "NextLauncher/" + CURRENT_VERSION);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() != 200) return;

                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject release = new Gson().fromJson(json, JsonObject.class);

                if (!release.has("tag_name")) return;
                String latestTag = release.get("tag_name").getAsString().replaceAll("[^0-9.]", "");
                String downloadUrl = release.has("html_url")
                        ? release.get("html_url").getAsString() : "";

                if (isNewer(latestTag, CURRENT_VERSION)) {
                    SwingUtilities.invokeLater(() -> showUpdateDialog(parent, latestTag, downloadUrl));
                }
            } catch (Exception ignored) {
                // Fail silently — no internet, wrong repo, etc.
            }
        }, "update-check").start();
    }

    private static boolean isNewer(String latest, String current) {
        String[] l = latest.split("\\.");
        String[] c = current.split("\\.");
        for (int i = 0; i < Math.min(l.length, c.length); i++) {
            try {
                int lv = Integer.parseInt(l[i]);
                int cv = Integer.parseInt(c[i]);
                if (lv > cv) return true;
                if (lv < cv) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return l.length > c.length;
    }

    private static void showUpdateDialog(JFrame parent, String version, String url) {
        String[] options = {"Pobierz aktualizację", "Później"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                "<html><b>Dostępna jest nowa wersja NextLauncher!</b><br><br>" +
                "Nowa wersja: <b>" + version + "</b><br>" +
                "Aktualna wersja: " + CURRENT_VERSION + "<br><br>" +
                "Pobierz nową wersję ze strony GitHub.</html>",
                "Aktualizacja dostępna",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0 && !url.isBlank()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {}
        }
    }
}
