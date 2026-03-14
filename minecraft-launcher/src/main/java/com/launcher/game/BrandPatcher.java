package com.launcher.game;

import javax.tools.ToolProvider;
import javax.tools.JavaCompiler;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Creates a tiny JAR with a custom ClientBrandRetriever that overrides
 * the "vanilla" brand shown on Minecraft's F3 debug screen.
 * The JAR must be prepended to the classpath so the JVM loads it first.
 */
public class BrandPatcher {

    private static final String SOURCE =
            "package net.minecraft.client;\n" +
            "public class ClientBrandRetriever {\n" +
            "    public static String getClientModName() { return \"%s\"; }\n" +
            "}\n";

    public static Path createBrandJar(String brandName) throws Exception {
        Path tempDir = Files.createTempDirectory("mc-brand-");
        Path pkgDir  = tempDir.resolve(Path.of("net", "minecraft", "client"));
        Files.createDirectories(pkgDir);

        // Write .java source
        Path srcFile = pkgDir.resolve("ClientBrandRetriever.java");
        Files.writeString(srcFile, String.format(SOURCE, brandName));

        // Try to compile via javax.tools first, then fall back to javac process
        boolean ok = false;
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler != null) {
            ok = compiler.run(null, null, null, srcFile.toAbsolutePath().toString()) == 0;
        }
        if (!ok) {
            String javac = System.getProperty("java.home")
                    + File.separator + "bin" + File.separator + "javac";
            Process p = new ProcessBuilder(javac, srcFile.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            ok = p.waitFor() == 0;
        }
        if (!ok) throw new Exception("Kompilacja ClientBrandRetriever nie powiodła się.");

        // Pack .class into a JAR
        Path jar = tempDir.resolve("brand-patch.jar");
        Path cls = pkgDir.resolve("ClientBrandRetriever.class");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jar.toFile()))) {
            zos.putNextEntry(new ZipEntry("net/minecraft/client/ClientBrandRetriever.class"));
            Files.copy(cls, zos);
            zos.closeEntry();
        }
        return jar;
    }
}
