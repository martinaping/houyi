package com.ruoran.houyi.admin.rbac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashMatchesOriginalPassword() {
        String hash = PasswordHasher.hash("Abcdef123456");
        assertTrue(PasswordHasher.matches("Abcdef123456", hash));
        assertFalse(PasswordHasher.matches("Abcdef123457", hash));
    }

    @Test
    void saltMakesHashesDifferent() {
        assertNotEquals(PasswordHasher.hash("Abcdef123456"), PasswordHasher.hash("Abcdef123456"));
    }

    @Test
    void rejectsMalformedHash() {
        assertFalse(PasswordHasher.matches("Abcdef123456", null));
        assertFalse(PasswordHasher.matches("Abcdef123456", "plain"));
        assertFalse(PasswordHasher.matches("Abcdef123456", "pbkdf2$abc$xx$yy"));
    }
}
