package org.example.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DocumentCryptoServiceTest {

    private DocumentCryptoService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DocumentCryptoService();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        setField(service, "configuredKeyBase64", Optional.of(Base64.getEncoder().encodeToString(key)));
        service.init();
    }

    @Test
    void roundTripPreservesPlaintext() {
        byte[] plain = "passaporte-secreto-pdf".getBytes(StandardCharsets.UTF_8);
        byte[] enc = service.encrypt(plain);
        assertNotEquals(new String(plain, StandardCharsets.UTF_8), new String(enc, StandardCharsets.UTF_8));
        byte[] dec = service.decrypt(enc, DocumentCryptoService.ENCRYPTION_VERSION_AES_GCM);
        assertArrayEquals(plain, dec);
    }

    @Test
    void plaintextVersionReturnsBytesUnchanged() {
        byte[] plain = {1, 2, 3};
        assertArrayEquals(plain, service.decrypt(plain, DocumentCryptoService.ENCRYPTION_VERSION_PLAINTEXT));
    }

    @Test
    void encryptProducesDifferentCiphertexts() {
        byte[] plain = "same".getBytes(StandardCharsets.UTF_8);
        byte[] a = service.encrypt(plain);
        byte[] b = service.encrypt(plain);
        assertFalse(java.util.Arrays.equals(a, b));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = DocumentCryptoService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
