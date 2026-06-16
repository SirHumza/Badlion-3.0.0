package net.badlion.client.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import net.badlion.client.manager.auth.Account;
import net.badlion.client.manager.auth.MicrosoftAuth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Badlion's account manager, rewired for Microsoft auth.
 *
 * The original implementation talked to badlion.net's auth endpoint using
 * Mojang Yggdrasil credentials (email + password) and is dead — Mojang
 * killed Yggdrasil for new accounts. This rewrite keeps the same public
 * surface (so the existing Badlion GUI screens compile unchanged) but
 * swaps the guts: accounts are stored as Microsoft refresh tokens in
 * {@code <mcDataDir>/badlion-accounts.json}, refreshed to MC sessions
 * on demand via {@link MicrosoftAuth}.
 *
 * Add Account flow: {@link #loginProfile(String, String)} ignores its
 * email/password arguments, opens the Microsoft consent page in the
 * user's default browser, listens on http://localhost:25575/callback
 * for the OAuth2 redirect, exchanges the code for tokens, and appends
 * the resulting Account to the persisted list.
 */
public class AccountManager
{
    public static final ResourceLocation locationStevePng = new ResourceLocation("textures/entity/steve.png");

    private final File accountsFile = new File(Minecraft.getMinecraft().mcDataDir, "badlion-accounts.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Account> accounts = new ArrayList<Account>();

    private final Map<String, Session> sessionMap = new HashMap<String, Session>();
    private final List<String> sortedUsernames = new ArrayList<String>();
    private final Map<UUID, GameProfile> gameProfileCache = new HashMap<UUID, GameProfile>();
    private final Map<UUID, ResourceLocation> cachedSkinResources = new HashMap<UUID, ResourceLocation>();

    private static final ExecutorService asyncTaskThreadPool = new ThreadPoolExecutor(
            0, 2, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
    private static final ExecutorService authExecutor = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Badlion-MSAuth");
            t.setDaemon(true);
            return t;
        }
    });

    public void addSession(Session session)
    {
        LogManager.getLogger().info("Adding session: " + session.getUsername());
        this.sessionMap.put(session.getUsername(), session);
        if (!this.sortedUsernames.contains(session.getUsername())) {
            this.sortedUsernames.add(session.getUsername());
        }
        final UUID uuid = session.getPlayerUUID();

        if (!this.gameProfileCache.containsKey(uuid))
        {
            LogManager.getLogger().info("Skin Lookup: " + uuid.toString());
            GameProfile gameprofile = new GameProfile(uuid, session.getUsername());
            this.gameProfileCache.put(uuid, gameprofile);
            Minecraft.getMinecraft().getSkinManager().loadProfileTextures(gameprofile, new SkinManager.SkinAvailableCallback()
            {
                public void skinAvailable(Type type, ResourceLocation location, MinecraftProfileTexture profileTexture)
                {
                    if (type.equals(Type.SKIN))
                    {
                        AccountManager.this.cachedSkinResources.put(uuid, location);
                    }
                }
            }, false, true);
        }

        Collections.sort(this.sortedUsernames, new Comparator<String>() {
            public int compare(String a, String b) { return a.compareToIgnoreCase(b); }
        });
    }

    public Session getSession(String username)
    {
        return this.sessionMap.get(username);
    }

    /**
     * Rebuild the in-memory session list from {@code badlion-accounts.json}.
     * Each persisted refresh token is exchanged for a fresh MC session via
     * {@link MicrosoftAuth#refreshMSAccessTokens}. Calls are async; the
     * sessionMap fills in as each account's chain completes.
     */
    public void reloadSessions()
    {
        loadAccountsFromDisk();
        this.sessionMap.clear();
        this.sortedUsernames.clear();

        // Always include the currently-active session so the UI never looks empty.
        Session current = Minecraft.getMinecraft().getSession();
        if (current != null && current.getUsername() != null && !current.getUsername().isEmpty()) {
            addSession(current);
        }

        for (final Account account : new ArrayList<Account>(accounts)) {
            // Optimistic: show the persisted username immediately as a placeholder.
            if (!sessionMap.containsKey(account.getUsername()) && account.getUsername() != null && !account.getUsername().isEmpty()) {
                // Build a "best-known" session from the stored access token. The token may
                // be stale; switchToAccount() will refresh on demand before actually using it.
                try {
                    addSession(new Session(
                            account.getUsername(),
                            uuidPlaceholder(account.getUsername()).toString().replace("-", ""),
                            account.getAccessToken() == null ? "0" : account.getAccessToken(),
                            "MOJANG"
                    ));
                } catch (Throwable t) {
                    // ignore; account will simply not show up
                }
            }
        }
    }

    /**
     * Triggered by the "Add Account" button in {@code GuiAccountLogin}.
     * Email + password are ignored — we always run the Microsoft OAuth2
     * flow instead. Returns true immediately; the new account appears in
     * the UI on the next {@link #reloadSessions()} once the flow completes.
     */
    public boolean loginProfile(String emailIgnored, String passwordIgnored)
    {
        authExecutor.submit(new Runnable() {
            public void run() {
                runMicrosoftLogin();
            }
        });
        return true;
    }

    public boolean switchToAccount(String username)
    {
        if (Minecraft.getMinecraft().getSession().getUsername().equals(username))
        {
            return false;
        }

        // Try to refresh the stored Microsoft tokens first so we hand MC a valid session.
        Account stored = null;
        for (Account a : accounts) {
            if (username.equals(a.getUsername())) { stored = a; break; }
        }
        if (stored != null) {
            try {
                Session refreshed = refreshAccount(stored).get(60, TimeUnit.SECONDS);
                if (refreshed != null) {
                    addSession(refreshed);
                    Minecraft.getMinecraft().setSession(refreshed);
                    saveAccountsToDisk();
                    return true;
                }
            } catch (Throwable t) {
                LogManager.getLogger().warn("Microsoft session refresh failed for " + username + ": " + t.getMessage());
            }
        }

        // Fall back to whatever session is in the map (e.g. the currently-active one).
        Session cached = this.sessionMap.get(username);
        if (cached != null) {
            Minecraft.getMinecraft().setSession(cached);
            return true;
        }
        return false;
    }

    public Map<String, Session> getSessionMap() { return this.sessionMap; }
    public List<String> getSortedUsernames() { return this.sortedUsernames; }
    public Map<UUID, ResourceLocation> getCachedSkinResources() { return this.cachedSkinResources; }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void runMicrosoftLogin() {
        try {
            String state = UUID.randomUUID().toString();
            java.net.URI authUri = MicrosoftAuth.getMSAuthLink(state);
            LogManager.getLogger().info("Opening Microsoft sign-in: " + authUri);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(authUri);
            } else {
                LogManager.getLogger().warn("Open this URL to sign in: " + authUri);
            }

            String code = MicrosoftAuth.acquireMSAuthCode(state, authExecutor).get(5, TimeUnit.MINUTES);
            Map<String, String> tokens = MicrosoftAuth.acquireMSAccessTokens(code, authExecutor).get(30, TimeUnit.SECONDS);

            Account account = new Account(
                    tokens.get("refresh_token"),
                    tokens.get("access_token"),
                    "", // username filled in below
                    MicrosoftAuth.CLIENT_ID,
                    MicrosoftAuth.SCOPE
            );

            Session session = refreshAccount(account).get(60, TimeUnit.SECONDS);
            if (session != null) {
                account.setUsername(session.getUsername());
                account.setAccessToken(session.getToken());
                synchronized (accounts) {
                    boolean exists = false;
                    for (Account a : accounts) {
                        if (session.getUsername().equals(a.getUsername())) {
                            a.setRefreshToken(account.getRefreshToken());
                            a.setAccessToken(account.getAccessToken());
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) accounts.add(account);
                }
                saveAccountsToDisk();
                addSession(session);
                Minecraft.getMinecraft().setSession(session);
                LogManager.getLogger().info("Microsoft sign-in complete: " + session.getUsername());
            }
        } catch (Throwable t) {
            LogManager.getLogger().error("Microsoft sign-in failed", t);
        }
    }

    /**
     * Run the full Microsoft → Xbox → XSTS → Minecraft token chain, using
     * the account's current refresh token. Returns a fresh {@link Session}.
     * Side-effect: updates the account's stored access + refresh tokens.
     */
    private java.util.concurrent.CompletableFuture<Session> refreshAccount(final Account account) {
        return MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), authExecutor)
                .thenCompose(new java.util.function.Function<Map<String, String>, java.util.concurrent.CompletableFuture<String>>() {
                    public java.util.concurrent.CompletableFuture<String> apply(Map<String, String> ms) {
                        account.setAccessToken(ms.get("access_token"));
                        account.setRefreshToken(ms.get("refresh_token"));
                        return MicrosoftAuth.acquireXboxAccessToken(ms.get("access_token"), authExecutor);
                    }
                })
                .thenCompose(new java.util.function.Function<String, java.util.concurrent.CompletableFuture<Map<String, String>>>() {
                    public java.util.concurrent.CompletableFuture<Map<String, String>> apply(String xbl) {
                        return MicrosoftAuth.acquireXboxXstsToken(xbl, authExecutor);
                    }
                })
                .thenCompose(new java.util.function.Function<Map<String, String>, java.util.concurrent.CompletableFuture<String>>() {
                    public java.util.concurrent.CompletableFuture<String> apply(Map<String, String> xsts) {
                        return MicrosoftAuth.acquireMCAccessToken(xsts.get("xsts_token"), xsts.get("user_hash"), authExecutor);
                    }
                })
                .thenCompose(new java.util.function.Function<String, java.util.concurrent.CompletableFuture<Session>>() {
                    public java.util.concurrent.CompletableFuture<Session> apply(String mc) {
                        return MicrosoftAuth.login(mc, authExecutor);
                    }
                });
    }

    private void loadAccountsFromDisk() {
        accounts.clear();
        if (!accountsFile.exists()) return;
        try {
            JsonElement json = new JsonParser().parse(new BufferedReader(new FileReader(accountsFile)));
            if (!(json instanceof JsonArray)) return;
            for (JsonElement el : json.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                accounts.add(new Account(
                        Optional.ofNullable(o.get("refreshToken")).map(JsonElement::getAsString).orElse(""),
                        Optional.ofNullable(o.get("accessToken")).map(JsonElement::getAsString).orElse(""),
                        Optional.ofNullable(o.get("username")).map(JsonElement::getAsString).orElse(""),
                        Optional.ofNullable(o.get("unban")).map(JsonElement::getAsLong).orElse(0L),
                        Optional.ofNullable(o.get("clientId")).map(JsonElement::getAsString).orElse(MicrosoftAuth.CLIENT_ID),
                        Optional.ofNullable(o.get("scope")).map(JsonElement::getAsString).orElse(MicrosoftAuth.SCOPE)
                ));
            }
        } catch (IOException e) {
            LogManager.getLogger().warn("Could not read badlion-accounts.json", e);
        }
    }

    private void saveAccountsToDisk() {
        try {
            if (!accountsFile.exists()) {
                accountsFile.getParentFile().mkdirs();
                accountsFile.createNewFile();
            }
            JsonArray array = new JsonArray();
            synchronized (accounts) {
                for (Account a : accounts) {
                    JsonObject o = new JsonObject();
                    o.addProperty("refreshToken", a.getRefreshToken());
                    o.addProperty("accessToken", a.getAccessToken());
                    o.addProperty("username", a.getUsername());
                    o.addProperty("unban", a.getUnban());
                    o.addProperty("clientId", a.getClientId());
                    o.addProperty("scope", a.getScope());
                    array.add(o);
                }
            }
            PrintWriter pw = new PrintWriter(new FileWriter(accountsFile));
            try { pw.println(gson.toJson(array)); } finally { pw.close(); }
        } catch (IOException e) {
            LogManager.getLogger().warn("Could not write badlion-accounts.json", e);
        }
    }

    /** Deterministic UUID from a username, used only when no real one is cached yet. */
    private static UUID uuidPlaceholder(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
