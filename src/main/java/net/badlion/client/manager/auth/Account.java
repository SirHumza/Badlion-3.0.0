package net.badlion.client.manager.auth;

/*
 * Ported from OpenMyau (https://github.com/60124808866/OpenMyau), which
 * is itself derived from https://github.com/ksyzov/AccountManager.
 * Original AccountManager: GNU LGPL. OpenMyau modifications: GNU GPL v3.
 */
public class Account {
    private String refreshToken;
    private String accessToken;
    private String username;
    private long unban;
    private String clientId;
    private String scope;

    public Account(String refreshToken, String accessToken, String username, String clientId, String scope) {
        this(refreshToken, accessToken, username, 0L, clientId, scope);
    }

    public Account(String refreshToken, String accessToken, String username, long unban, String clientId, String scope) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
        this.unban = unban;
        this.clientId = clientId;
        this.scope = scope;
    }

    public String getClientId() { return clientId; }
    public String getScope() { return scope; }
    public String getRefreshToken() { return refreshToken; }
    public String getAccessToken() { return accessToken; }
    public String getUsername() { return username; }
    public long getUnban() { return unban; }

    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setUsername(String username) { this.username = username; }
    public void setUnban(long unban) { this.unban = unban; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setScope(String scope) { this.scope = scope; }
}
