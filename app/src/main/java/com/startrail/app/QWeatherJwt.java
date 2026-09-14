package com.startrail.app;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Creates the short-lived EdDSA JWT accepted by the QWeather API. */
public final class QWeatherJwt {
    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final long LIFETIME_SECONDS = 15 * 60;

    private QWeatherJwt() {}

    public static String sign(byte[] pkcs8PrivateKey, String developerId, String projectId,
                              String credentialId, Instant now) {
        if (pkcs8PrivateKey == null) throw new IllegalArgumentException("Private key is required");
        requireValue(developerId, "developerId");
        requireValue(projectId, "projectId");
        requireValue(credentialId, "credentialId");
        if (now == null) throw new IllegalArgumentException("now is required");

        Ed25519PrivateKeyParameters key = parsePrivateKey(pkcs8PrivateKey);
        long epoch = now.getEpochSecond();
        String header = "{\"alg\":\"EdDSA\",\"kid\":\"" + json(credentialId) + "\"}";
        String payload = "{\"iss\":\"" + json(developerId) + "\",\"sub\":\""
                + json(projectId) + "\",\"iat\":" + (epoch - CLOCK_SKEW_SECONDS)
                + ",\"exp\":" + (epoch + LIFETIME_SECONDS) + "}";
        String signingInput = encode(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encode(payload.getBytes(StandardCharsets.UTF_8));
        byte[] message = signingInput.getBytes(StandardCharsets.US_ASCII);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, key);
        signer.update(message, 0, message.length);
        return signingInput + "." + encode(signer.generateSignature());
    }

    static Ed25519PrivateKeyParameters parsePrivateKey(byte[] pkcs8) {
        try {
            AsymmetricKeyParameter key = PrivateKeyFactory.createKey(pkcs8);
            if (!(key instanceof Ed25519PrivateKeyParameters)) {
                throw new IllegalArgumentException("Private key must be Ed25519");
            }
            return (Ed25519PrivateKeyParameters) key;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid PKCS#8 private key", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
