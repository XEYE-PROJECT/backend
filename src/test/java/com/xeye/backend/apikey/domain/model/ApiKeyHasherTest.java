package com.xeye.backend.apikey.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Fija el formato del hash: SHA-256 hex en minúsculas. El search-service (hash_api_key) y la
 * migración V6 (SHA2(api_key, 256)) producen exactamente esto; cambiarlo rompe ambos.
 */
class ApiKeyHasherTest {

    @Test
    void hashIsLowercaseHexSha256() {
        // Vector conocido de SHA-256("abc").
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApiKeyHasher.hash("abc"));
        assertEquals(64, ApiKeyHasher.hash("xeye_Wq3kAbcdefghijklmnopqrstuvwxyz01").length());
        assertNotEquals(ApiKeyHasher.hash("xeye_a"), ApiKeyHasher.hash("xeye_b"));
    }

    @Test
    void prefixKeepsTheFirstTwelveCharacters() {
        assertEquals("xeye_Wq3kAbc", ApiKeyHasher.prefixOf("xeye_Wq3kAbcdefghijklmnopqrstuvwxyz01"));
        assertEquals("short", ApiKeyHasher.prefixOf("short"));
    }

    @Test
    void createDerivesHashAndPrefixAndDropsTheRawValue() {
        ApiKey key = ApiKey.create(7L, "Producción", "xeye_Wq3kAbcdefghijklmnopqrstuvwxyz01");

        assertEquals(ApiKeyHasher.hash("xeye_Wq3kAbcdefghijklmnopqrstuvwxyz01"), key.keyHash());
        assertEquals("xeye_Wq3kAbc", key.prefix());
    }
}
