package com.launcher.profile;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.launcher.model.Profile;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class ProfileManager {

    private final Path configFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<Profile> profiles = new ArrayList<>();

    public ProfileManager(Path configDir) {
        this.configFile = configDir.resolve("launcher_profiles.json");
    }

    public void load() {
        if (!Files.exists(configFile)) return;
        try {
            Type listType = new TypeToken<List<Profile>>() {}.getType();
            List<Profile> loaded = gson.fromJson(Files.readString(configFile), listType);
            if (loaded != null) profiles = loaded;
        } catch (Exception e) {
            profiles = new ArrayList<>();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, gson.toJson(profiles));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Profile> getProfiles() { return profiles; }

    public void addProfile(Profile profile) { profiles.add(profile); save(); }

    public void removeProfile(Profile profile) { profiles.remove(profile); save(); }

    public void updateProfile() { save(); }
}
