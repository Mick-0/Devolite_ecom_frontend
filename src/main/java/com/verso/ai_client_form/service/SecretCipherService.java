package com.verso.ai_client_form.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SecretCipherService {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipherService(@Value("${app.secrets.encryption-key:}") String rawKey) {
        String normalizedKey = rawKey == null ? "" : rawKey.trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalStateException("""
                Missing app.secrets.encryption-key.
                Set APP_SECRETS_ENCRYPTION_KEY before starting the application.
                """.trim());
        }
        if (normalizedKey.length() < 32) {
            throw new IllegalStateException("APP_SECRETS_ENCRYPTION_KEY must be at least 32 characters long.");
        }
        this.keySpec = new SecretKeySpec(sha256(normalizedKey), "AES");
    }

    public String encryptNullable(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt secret value.", ex);
        }
    }

    public String decryptNullable(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(storedValue.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt stored secret value.", ex);
        }
    }

    public boolean hasStoredValue(String storedValue) {
        return storedValue != null && !storedValue.isBlank();
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize secret encryption.", ex);
        }
    }
}
