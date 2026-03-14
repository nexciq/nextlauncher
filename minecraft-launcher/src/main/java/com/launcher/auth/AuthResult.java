package com.launcher.auth;

public class AuthResult {
    private final String username;
    private final String uuid;
    private final String accessToken;
    private final boolean premium;

    public AuthResult(String username, String uuid, String accessToken, boolean premium) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.premium = premium;
    }

    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getAccessToken() { return accessToken; }
    public boolean isPremium() { return premium; }
}
