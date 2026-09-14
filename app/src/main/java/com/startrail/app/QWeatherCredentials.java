package com.startrail.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Persists QWeather account settings and keeps its signing key encrypted at rest. */
public final class QWeatherCredentials {
    public static final int MAX_PEM_BYTES = 16 * 1024;
    static final String PREFS_NAME = "qweather_credentials";
    private static final String KEY_ALIAS = "startrail.qweather.private-key.v1";
    private static final String KEY_HOST = "host";
    private static final String KEY_DEVELOPER = "developer_id";
    private static final String KEY_PROJECT = "project_id";
    private static final String KEY_CREDENTIAL = "credential_id";
    private static final String KEY_CIPHERTEXT = "private_key_ciphertext";
    private static final String KEY_IV = "private_key_iv";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private final SharedPreferences prefs;

    public QWeatherCredentials(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Config getConfig() {
        return new Config(prefs.getString(KEY_HOST, ""), prefs.getString(KEY_DEVELOPER, ""),
                prefs.getString(KEY_PROJECT, ""), prefs.getString(KEY_CREDENTIAL, ""));
    }

    public void saveConfig(String host, String developerId, String projectId, String credentialId) {
        saveConfig(new Config(host, developerId, projectId, credentialId));
    }

    @SuppressLint("ApplySharedPref") // Credential updates must detect failure and synchronously restore all prior fields.
    public void saveConfig(Config config) {
        if (config == null) throw new IllegalArgumentException("config is required");
        config.validate();
        StoredString oldHost = storedString(KEY_HOST);
        StoredString oldDeveloper = storedString(KEY_DEVELOPER);
        StoredString oldProject = storedString(KEY_PROJECT);
        StoredString oldCredential = storedString(KEY_CREDENTIAL);
        if (!prefs.edit().putString(KEY_HOST, config.host).putString(KEY_DEVELOPER, config.developerId)
                .putString(KEY_PROJECT, config.projectId).putString(KEY_CREDENTIAL, config.credentialId).commit()) {
            SharedPreferences.Editor rollback = prefs.edit();
            restore(rollback, KEY_HOST, oldHost);
            restore(rollback, KEY_DEVELOPER, oldDeveloper);
            restore(rollback, KEY_PROJECT, oldProject);
            restore(rollback, KEY_CREDENTIAL, oldCredential);
            rollback.commit();
            throw new IllegalStateException("Unable to save QWeather configuration");
        }
    }

    public boolean hasPrivateKey() {
        return prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV);
    }

    @SuppressLint("ApplySharedPref") // Ciphertext and IV form one value; commit enables failure-aware rollback.
    public void importPrivateKey(byte[] pemBytes) {
        byte[] pkcs8 = decodePem(pemBytes);
        try {
            QWeatherJwt.parsePrivateKey(pkcs8); // Validate before touching the existing ciphertext.
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
            byte[] ciphertext = cipher.doFinal(pkcs8);
            StoredString oldCiphertext = storedString(KEY_CIPHERTEXT);
            StoredString oldIv = storedString(KEY_IV);
            boolean saved = prefs.edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit();
            Arrays.fill(ciphertext, (byte) 0);
            if (!saved) {
                SharedPreferences.Editor rollback = prefs.edit();
                restore(rollback, KEY_CIPHERTEXT, oldCiphertext);
                restore(rollback, KEY_IV, oldIv);
                rollback.commit();
                throw new IllegalStateException("Unable to save private key");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt private key", e);
        } finally {
            Arrays.fill(pkcs8, (byte) 0);
        }
    }

    public String signToken(Instant now) {
        Config config = getConfig();
        config.validate();
        byte[] key = decryptPrivateKey();
        try {
            return QWeatherJwt.sign(key, config.developerId, config.projectId, config.credentialId, now);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @SuppressLint("ApplySharedPref") // Clearing both encrypted fields must report failure and restore them together.
    public void clearPrivateKey() {
        StoredString oldCiphertext = storedString(KEY_CIPHERTEXT);
        StoredString oldIv = storedString(KEY_IV);
        if (!prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).commit()) {
            SharedPreferences.Editor rollback = prefs.edit();
            restore(rollback, KEY_CIPHERTEXT, oldCiphertext);
            restore(rollback, KEY_IV, oldIv);
            rollback.commit();
            throw new IllegalStateException("Unable to clear private key");
        }
    }

    private StoredString storedString(String key) {
        return new StoredString(prefs.contains(key), prefs.getString(key, null));
    }

    private static void restore(SharedPreferences.Editor editor, String key, StoredString old) {
        if (old.present) editor.putString(key, old.value);
        else editor.remove(key);
    }

    private static final class StoredString {
        final boolean present;
        final String value;

        StoredString(boolean present, String value) {
            this.present = present;
            this.value = value;
        }
    }

    private byte[] decryptPrivateKey() {
        String ciphertext = prefs.getString(KEY_CIPHERTEXT, null);
        String iv = prefs.getString(KEY_IV, null);
        if (ciphertext == null || iv == null) throw new IllegalStateException("Private key is not configured");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt private key", e);
        }
    }

    private static SecretKey encryptionKey() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEY_STORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private static byte[] decodePem(byte[] input) {
        if (input == null || input.length == 0) throw new IllegalArgumentException("Private key is required");
        if (input.length > MAX_PEM_BYTES) throw new IllegalArgumentException("Private key is too large");
        String pem = new String(input, StandardCharsets.US_ASCII).trim();
        String begin = "-----BEGIN PRIVATE KEY-----";
        String end = "-----END PRIVATE KEY-----";
        if (!pem.startsWith(begin) || !pem.endsWith(end)) {
            throw new IllegalArgumentException("Expected a PKCS#8 PRIVATE KEY PEM");
        }
        String body = pem.substring(begin.length(), pem.length() - end.length()).replaceAll("\\s", "");
        if (body.isEmpty()) throw new IllegalArgumentException("Private key is empty");
        try {
            return Base64.decode(body, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Private key PEM is malformed", e);
        }
    }

    public static final class Config {
        public final String host;
        public final String developerId;
        public final String projectId;
        public final String credentialId;

        public Config(String host, String developerId, String projectId, String credentialId) {
            this.host = clean(host).toLowerCase(java.util.Locale.US).replaceFirst("^https://", "");
            this.developerId = clean(developerId);
            this.projectId = clean(projectId);
            this.credentialId = clean(credentialId);
        }

        public String getHost() { return host; }
        public String getDeveloperId() { return developerId; }
        public String getProjectId() { return projectId; }
        public String getCredentialId() { return credentialId; }

        private void validate() {
            if (!host.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.qweatherapi\\.com")) {
                throw new IllegalArgumentException("Host must be a qweatherapi.com subdomain without a path");
            }
            requireIdentifier(developerId, "developerId");
            requireIdentifier(projectId, "projectId");
            requireIdentifier(credentialId, "credentialId");
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
        private static void requireIdentifier(String value, String name) {
            if (value.isEmpty() || value.length() > 256) throw new IllegalArgumentException(name + " is invalid");
            for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i)))
                throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
