package org.example.infrastructure.crypto;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Criptografia de documentos pessoais (AES-256-GCM) antes do armazenamento no R2.
 *
 * <p>Formato do blob: {@code magic(3) | version(1) | iv(12) | ciphertext+tag}.
 * A chave mestra vem de {@code DOCUMENTS_ENCRYPTION_KEY} (Base64 de 32 bytes).
 */
@Slf4j
@ApplicationScoped
public class DocumentCryptoService {

    public static final int ENCRYPTION_VERSION_AES_GCM = 1;
    public static final int ENCRYPTION_VERSION_PLAINTEXT = 0;

    private static final byte[] MAGIC = {'B', 'G', '1'};
    private static final int VERSION_BYTE = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    @ConfigProperty(name = "documents.encryption.key")
    Optional<String> configuredKeyBase64;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        String raw = configuredKeyBase64.map(String::strip).filter(s -> !s.isBlank()).orElse(null);
        if (raw == null) {
            log.warn(
                    "DOCUMENTS_ENCRYPTION_KEY não configurada — upload de documentos criptografados indisponível. "
                            + "Gere com: openssl rand -base64 32");
            return;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(raw);
            if (keyBytes.length != KEY_BYTES) {
                log.error(
                        "DOCUMENTS_ENCRYPTION_KEY inválida: esperado {} bytes após Base64, obtido {}",
                        KEY_BYTES,
                        keyBytes.length);
                return;
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Document encryption (AES-256-GCM) ready");
        } catch (IllegalArgumentException e) {
            log.error("DOCUMENTS_ENCRYPTION_KEY não é Base64 válido", e);
        }
    }

    public boolean isConfigured() {
        return secretKey != null;
    }

    /** Versão gravada em {@code trip_documents.encryption_version} para novos uploads. */
    public int currentVersion() {
        return ENCRYPTION_VERSION_AES_GCM;
    }

    public byte[] encrypt(byte[] plaintext) {
        ensureConfigured();
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext is required");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext);

            byte[] out = new byte[MAGIC.length + 1 + IV_LENGTH + cipherText.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            out[MAGIC.length] = (byte) VERSION_BYTE;
            System.arraycopy(iv, 0, out, MAGIC.length + 1, IV_LENGTH);
            System.arraycopy(cipherText, 0, out, MAGIC.length + 1 + IV_LENGTH, cipherText.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt document: " + e.getMessage(), e);
        }
    }

    public byte[] decrypt(byte[] stored, int encryptionVersion) {
        if (stored == null) {
            throw new IllegalArgumentException("stored bytes are required");
        }
        if (encryptionVersion <= ENCRYPTION_VERSION_PLAINTEXT) {
            return stored;
        }
        if (encryptionVersion != ENCRYPTION_VERSION_AES_GCM) {
            throw new IllegalStateException("Unsupported document encryption version: " + encryptionVersion);
        }
        ensureConfigured();
        if (stored.length < MAGIC.length + 1 + IV_LENGTH + 16) {
            throw new IllegalStateException("Encrypted document payload is truncated");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (stored[i] != MAGIC[i]) {
                throw new IllegalStateException("Encrypted document magic mismatch");
            }
        }
        int formatVersion = stored[MAGIC.length] & 0xff;
        if (formatVersion != VERSION_BYTE) {
            throw new IllegalStateException("Unsupported ciphertext format version: " + formatVersion);
        }
        byte[] iv = Arrays.copyOfRange(stored, MAGIC.length + 1, MAGIC.length + 1 + IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(stored, MAGIC.length + 1 + IV_LENGTH, stored.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt document: " + e.getMessage(), e);
        }
    }

    private void ensureConfigured() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "Document encryption is not configured — set DOCUMENTS_ENCRYPTION_KEY (openssl rand -base64 32)");
        }
    }
}
