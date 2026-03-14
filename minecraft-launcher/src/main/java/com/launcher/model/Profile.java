package com.launcher.model;

public class Profile {

    private String name;
    private String selectedVersion;
    private int allocatedRam;
    private String type;         // "microsoft" or "offline"
    private String username;
    private String uuid;
    private String accessToken;
    private boolean fabricEnabled = false;
    private boolean closeLauncherOnStart = false;
    private String customJvmArgs = DEFAULT_JVM_ARGS;

    // Aikar's optimized GC flags — pre-filled so users can see and modify them
    public static final String DEFAULT_JVM_ARGS =
        "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
        "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
        "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
        "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
        "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
        "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
        "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";

    public Profile() {
        this.allocatedRam = 2048;
        this.type = "offline";
    }

    public Profile(String name) {
        this();
        this.name = name;
    }

    // Getters / setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSelectedVersion() { return selectedVersion; }
    public void setSelectedVersion(String version) { this.selectedVersion = version; }

    public int getAllocatedRam() { return allocatedRam; }
    public void setAllocatedRam(int ram) { this.allocatedRam = ram; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String token) { this.accessToken = token; }

    public boolean isMicrosoft() { return "microsoft".equals(type); }

    public boolean isFabricEnabled() { return fabricEnabled; }
    public void setFabricEnabled(boolean v) { this.fabricEnabled = v; }

    public boolean isCloseLauncherOnStart() { return closeLauncherOnStart; }
    public void setCloseLauncherOnStart(boolean v) { this.closeLauncherOnStart = v; }

    public String getCustomJvmArgs() { return customJvmArgs != null ? customJvmArgs : ""; }
    public void setCustomJvmArgs(String args) { this.customJvmArgs = args; }

    @Override
    public String toString() { return name != null ? name : "Nowy profil"; }
}
