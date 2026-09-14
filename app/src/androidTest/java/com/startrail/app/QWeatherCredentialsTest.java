package com.startrail.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;

public final class QWeatherCredentialsTest extends InstrumentationTestCase {
    private static final String ED25519_PEM = "-----BEGIN PRIVATE KEY-----\n"
            + "MC4CAQAwBQYDK2VwBCIEIJ1hsZ3v/VpguoRK9JLsLMREScVpc7MmkXA7rAMcp/Zg\n"
            + "-----END PRIVATE KEY-----\n";
    private Context context;
    private SharedPreferences prefs;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences(QWeatherCredentials.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Override protected void tearDown() throws Exception {
        prefs.edit().clear().commit();
        super.tearDown();
    }

    public void testConfigAndEncryptedKeyPersistAndClear() {
        QWeatherCredentials store = new QWeatherCredentials(context);
        store.saveConfig("https://abc.qweatherapi.com", "developer", "project", "credential");
        store.importPrivateKey(ED25519_PEM.getBytes(StandardCharsets.US_ASCII));
        assertTrue(store.hasPrivateKey());

        QWeatherCredentials reloaded = new QWeatherCredentials(context);
        assertEquals("abc.qweatherapi.com", reloaded.getConfig().getHost());
        assertEquals(store.signToken(Instant.EPOCH), reloaded.signToken(Instant.EPOCH));
        String allValues = prefs.getAll().toString();
        assertFalse(allValues.contains("BEGIN PRIVATE KEY"));
        assertFalse(allValues.contains("9d61b19d"));
        assertFalse(allValues.contains("nWGxne/9WmC6hEr0"));

        reloaded.clearPrivateKey();
        assertFalse(new QWeatherCredentials(context).hasPrivateKey());
    }

    public void testInvalidImportPreservesExistingKey() throws Exception {
        QWeatherCredentials store = configuredStore();
        String before = store.signToken(Instant.ofEpochSecond(42));
        assertImportRejected(store, "not a pem".getBytes(StandardCharsets.US_ASCII));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair rsa = generator.generateKeyPair();
        String rsaPem = pem(rsa.getPrivate().getEncoded());
        assertImportRejected(store, rsaPem.getBytes(StandardCharsets.US_ASCII));
        assertEquals(before, store.signToken(Instant.ofEpochSecond(42)));
    }

    public void testInputAndHostBoundariesAreRejected() {
        QWeatherCredentials store = new QWeatherCredentials(context);
        for (String host : Arrays.asList("http://abc.qweatherapi.com", "https://qweatherapi.com",
                "https://abc.qweatherapi.com/path", "https://abc.qweatherapi.com.evil.test")) {
            try { store.saveConfig(host, "d", "p", "c"); fail("Expected host rejection: " + host); }
            catch (IllegalArgumentException expected) { }
        }
        assertImportRejected(store, new byte[QWeatherCredentials.MAX_PEM_BYTES + 1]);
        assertFalse(store.hasPrivateKey());
    }

    private QWeatherCredentials configuredStore() {
        QWeatherCredentials store = new QWeatherCredentials(context);
        store.saveConfig("https://abc.qweatherapi.com", "developer", "project", "credential");
        store.importPrivateKey(ED25519_PEM.getBytes(StandardCharsets.US_ASCII));
        return store;
    }

    private static String pem(byte[] der) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + android.util.Base64.encodeToString(der, android.util.Base64.NO_WRAP)
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static void assertImportRejected(QWeatherCredentials store, byte[] input) {
        try { store.importPrivateKey(input); fail("Expected private key rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}
