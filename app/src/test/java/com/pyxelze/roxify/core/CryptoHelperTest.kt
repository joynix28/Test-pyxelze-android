package com.pyxelze.roxify.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.SecureRandom

class CryptoHelperTest {

    @Test
    fun testEncryptionAndDecryption() {
        val plaintext = "Secret Message".toByteArray(Charsets.UTF_8)
        val password = "StrongPassword123".toCharArray()

        val encryptedData = CryptoHelper.encrypt(plaintext, password)

        // Encrypted data should be longer than plaintext due to salt and IV and GCM tag
        assertTrue(encryptedData.size > plaintext.size)
        // Should not be equal to plaintext
        val plainString = String(plaintext, Charsets.UTF_8)
        val encString = String(encryptedData, Charsets.UTF_8)
        assertNotEquals(plainString, encString)

        val decryptedData = CryptoHelper.decrypt(encryptedData, password)
        assertArrayEquals(plaintext, decryptedData)
    }

    @Test(expected = Exception::class)
    fun testDecryptionFailsWithWrongPassword() {
        val plaintext = "Secret Message".toByteArray(Charsets.UTF_8)
        val password = "StrongPassword123".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()

        val encryptedData = CryptoHelper.encrypt(plaintext, password)

        // This should throw AEADBadTagException
        CryptoHelper.decrypt(encryptedData, wrongPassword)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
