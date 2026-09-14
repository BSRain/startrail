package com.startrail.app;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.Assert.*;

public final class QWeatherJwtTest {
    // RFC 8032 section 7.1, test vector 1, wrapped in the standard PKCS#8 Ed25519 envelope.
    private static final byte[] KEY = hex("302e020100300506032b657004220420"
            + "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");

    @Test public void tokenHasOfficialFieldsAndVerifiableEd25519Signature() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String token = QWeatherJwt.sign(KEY, "developer", "project", "credential", now);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        assertEquals("{\"alg\":\"EdDSA\",\"kid\":\"credential\"}", decode(parts[0]));
        assertEquals("{\"iss\":\"developer\",\"sub\":\"project\",\"iat\":1799999970,\"exp\":1800000900}",
                decode(parts[1]));

        Ed25519PrivateKeyParameters privateKey = QWeatherJwt.parsePrivateKey(KEY);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, privateKey.generatePublicKey());
        byte[] message = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        verifier.update(message, 0, message.length);
        assertTrue(verifier.verifySignature(Base64.getUrlDecoder().decode(parts[2])));
    }

    @Test public void signatureDoesNotVerifyWithAnotherKey() {
        String[] parts = QWeatherJwt.sign(KEY, "d", "p", "c", Instant.EPOCH).split("\\.");
        byte[] otherSeed = new byte[32];
        otherSeed[0] = 1;
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PrivateKeyParameters(otherSeed, 0).generatePublicKey());
        byte[] message = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        verifier.update(message, 0, message.length);
        assertFalse(verifier.verifySignature(Base64.getUrlDecoder().decode(parts[2])));
    }

    @Test public void rejectsNonEd25519AndMalformedPkcs8() {
        assertRejected(hex("3013020100300d06092a864886f70d01010105000400"));
        assertRejected(new byte[] { 1, 2, 3 });
    }

    private static void assertRejected(byte[] value) {
        try { QWeatherJwt.parsePrivateKey(value); fail("Expected key rejection"); }
        catch (IllegalArgumentException expected) { }
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++)
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
