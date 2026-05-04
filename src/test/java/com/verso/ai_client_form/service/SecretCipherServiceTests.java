package com.verso.ai_client_form.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SecretCipherServiceTests {

    @Test
    void encryptAndDecryptRoundTrip() {
        SecretCipherService service = new SecretCipherService("test-secret-key-with-at-least-32-chars");

        String encrypted = service.encryptNullable("super-secret-value");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("super-secret-value", encrypted);
        assertEquals("super-secret-value", service.decryptNullable(encrypted));
    }

    @Test
    void decryptKeepsLegacyPlainTextReadable() {
        SecretCipherService service = new SecretCipherService("test-secret-key-with-at-least-32-chars");

        assertEquals("legacy-value", service.decryptNullable("legacy-value"));
    }
}
