package com.lorenzflow.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for SecurityUtil — crypto requires the AndroidKeyStore
 * provider and android.util.Base64, neither of which exist in plain JVM unit
 * tests, so these run on a device or emulator.
 */
@RunWith(AndroidJUnit4.class)
public class SecurityUtilTest {

    @Test
    public void encryptDecrypt_roundtrip_returnsOriginal() {
        String secret = "p@ssw0rd Ünïcode 123!";
        SecurityUtil.EncryptedData data = SecurityUtil.encrypt(secret);

        assertNotNull(data);
        assertNotNull(data.encryptedText);
        assertNotNull(data.iv);
        assertEquals(secret, SecurityUtil.decrypt(data.encryptedText, data.iv));
    }

    @Test
    public void encrypt_emptyString_roundtrips() {
        SecurityUtil.EncryptedData data = SecurityUtil.encrypt("");
        assertEquals("", SecurityUtil.decrypt(data.encryptedText, data.iv));
    }

    @Test
    public void encrypt_uniqueIvPerCall_sameInput() {
        SecurityUtil.EncryptedData first = SecurityUtil.encrypt("same input");
        SecurityUtil.EncryptedData second = SecurityUtil.encrypt("same input");

        assertNotEquals(first.iv, second.iv);
        assertNotEquals(first.encryptedText, second.encryptedText);
        assertEquals("same input", SecurityUtil.decrypt(first.encryptedText, first.iv));
        assertEquals("same input", SecurityUtil.decrypt(second.encryptedText, second.iv));
    }

    @Test
    public void decrypt_badIv_returnsNull_notGarbage() {
        SecurityUtil.EncryptedData data = SecurityUtil.encrypt("secret");
        assertNull(SecurityUtil.decrypt(data.encryptedText, "not-a-valid-iv"));
    }
}
