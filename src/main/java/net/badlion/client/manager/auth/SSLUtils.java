package net.badlion.client.manager.auth;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/*
 * Ported from OpenMyau / ksyzov AccountManager. See Account.java header.
 *
 * Loads a JKS keystore from /ssl.jks (bundled in resources) that pins the
 * Microsoft / Mojang / Xbox cert chain. Used by MicrosoftAuth so the OAuth
 * flow keeps working even on systems with messed-up root CA stores.
 */
public class SSLUtils {
    private static final SSLContext ctx;

    public static SSLContext getSSLContext() {
        return ctx;
    }

    static {
        try {
            KeyStore jks = KeyStore.getInstance("JKS");
            InputStream stream = SSLUtils.class.getResourceAsStream("/ssl.jks");
            if (stream == null) {
                throw new RuntimeException("Couldn't find ssl.jks in resources");
            }
            jks.load(stream, "changeit".toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(jks);

            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);

            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize custom SSLContext", e);
        }
    }
}
