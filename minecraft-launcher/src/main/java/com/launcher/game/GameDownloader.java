package com.launcher.game;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.zip.*;

public class GameDownloader {

    private final Path gameDir;
    private final Gson gson = new Gson();
    private Consumer<String> statusCallback;
    private BiConsumer<Integer, Integer> progressCallback;

    public GameDownloader(Path gameDir) {
        this.gameDir = gameDir;
    }

    public void setStatusCallback(Consumer<String> cb) { this.statusCallback = cb; }
    public void setProgressCallback(BiConsumer<Integer, Integer> cb) { this.progressCallback = cb; }

    private void status(String msg) {
        if (statusCallback != null) statusCallback.accept(msg);
    }

    /** Downloads all required files for a Minecraft version. */
    public void downloadVersion(JsonObject versionData, String versionId) throws IOException {
        Path versionDir = gameDir.resolve("versions").resolve(versionId);
        Files.createDirectories(versionDir);

        // Save version JSON locally
        Files.writeString(versionDir.resolve(versionId + ".json"), gson.toJson(versionData));

        // 1. Client JAR
        status("Pobieranie klienta " + versionId + "...");
        JsonObject clientDl = versionData.getAsJsonObject("downloads").getAsJsonObject("client");
        downloadFile(
                clientDl.get("url").getAsString(),
                versionDir.resolve(versionId + ".jar"),
                clientDl.get("sha1").getAsString()
        );

        // 2. Libraries + natives
        status("Pobieranie bibliotek...");
        downloadLibraries(versionData.getAsJsonArray("libraries"), versionId);

        // 3. Assets
        status("Pobieranie zasobów...");
        downloadAssets(versionData.getAsJsonObject("assetIndex"));
    }

    // -------------------------------------------------------------------------
    // Libraries
    // -------------------------------------------------------------------------

    private void downloadLibraries(JsonArray libraries, String versionId) throws IOException {
        String os = getOs();
        Path librariesDir = gameDir.resolve("libraries");
        Path nativesDir = gameDir.resolve("versions").resolve(versionId).resolve("natives");
        Files.createDirectories(nativesDir);

        for (JsonElement el : libraries) {
            JsonObject lib = el.getAsJsonObject();
            if (!ruleAllows(lib, os)) continue;

            JsonObject downloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
            if (downloads == null) continue;

            // Main artifact
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                Path target = librariesDir.resolve(
                        artifact.get("path").getAsString().replace("/", File.separator));
                Files.createDirectories(target.getParent());
                String sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
                downloadFile(artifact.get("url").getAsString(), target, sha1);
            }

            // Natives
            if (downloads.has("classifiers") && lib.has("natives")) {
                String classifier = getNativeKey(lib, os);
                JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                if (classifier != null && classifiers.has(classifier)) {
                    JsonObject nat = classifiers.getAsJsonObject(classifier);
                    String sha1 = nat.has("sha1") ? nat.get("sha1").getAsString() : null;
                    Path natJar = nativesDir.resolve(classifier + ".jar");
                    downloadFile(nat.get("url").getAsString(), natJar, sha1);
                    extractNatives(natJar, nativesDir);
                }
            }
        }
    }

    /** Returns true if the library's rules allow it on the current OS. */
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

    private String getNativeKey(JsonObject lib, String os) {
        if (!lib.has("natives")) return null;
        JsonObject natives = lib.getAsJsonObject("natives");
        if (!natives.has(os)) return null;
        // Replace ${arch} with 64 for 64-bit systems
        return natives.get(os).getAsString().replace("${arch}", "64");
    }

    private void extractNatives(Path jarFile, Path outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                    Path outFile = outputDir.resolve(new File(name).getName());
                    try (OutputStream os = new FileOutputStream(outFile.toFile())) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) > 0) os.write(buf, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Assets
    // -------------------------------------------------------------------------

    private void downloadAssets(JsonObject assetIndex) throws IOException {
        String assetId = assetIndex.get("id").getAsString();
        String assetUrl = assetIndex.get("url").getAsString();

        Path assetsDir = gameDir.resolve("assets");
        Path indexDir = assetsDir.resolve("indexes");
        Path objectsDir = assetsDir.resolve("objects");
        Files.createDirectories(indexDir);

        Path indexFile = indexDir.resolve(assetId + ".json");
        downloadFile(assetUrl, indexFile, null);

        JsonObject objects = gson.fromJson(Files.readString(indexFile), JsonObject.class)
                .getAsJsonObject("objects");

        int total = objects.size();
        AtomicInteger current = new AtomicInteger(0);

        int threads = Math.min(16, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            futures.add(pool.submit(() -> {
                try {
                    String prefix = hash.substring(0, 2);
                    Path assetFile = objectsDir.resolve(prefix).resolve(hash);
                    Files.createDirectories(assetFile.getParent());
                    downloadFile("https://resources.download.minecraft.net/" + prefix + "/" + hash,
                            assetFile, hash);
                    int done = current.incrementAndGet();
                    if (progressCallback != null) progressCallback.accept(done, total);
                    if (done % 100 == 0) status("Zasoby: " + done + "/" + total);
                } catch (IOException e) {
                    status("Błąd pobierania zasobu " + hash + ": " + e.getMessage());
                }
                return null;
            }));
        }

        pool.shutdown();
        try {
            for (Future<?> f : futures) f.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Błąd pobierania zasobów: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // File download with SHA-1 check
    // -------------------------------------------------------------------------

    public void downloadFile(String urlStr, Path target, String expectedSha1) throws IOException {
        if (Files.exists(target)) {
            if (expectedSha1 == null) return;
            try {
                if (sha1(target).equals(expectedSha1)) return;
            } catch (Exception ignored) {}
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "MinecraftLauncher/1.0");

        try (InputStream is = conn.getInputStream();
             OutputStream os = new FileOutputStream(target.toFile())) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
        }
    }

    private String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) digest.update(buf, 0, len);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static String getOs() {
        String name = System.getProperty("os.name").toLowerCase();
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "osx";
        return "linux";
    }
}
