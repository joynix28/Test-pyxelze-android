package com.pyxelze.roxify.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoHelperTest {

    @Test
    fun testEncryptionAndDecryption() {
        val plaintext = "Secret Message".toByteArray(Charsets.UTF_8)
        val password = "StrongPassword123".toCharArray()
        val entryCount = 5

        val encryptedData = CryptoHelper.encrypt(plaintext, password, entryCount, 1000) // Lower iterations for fast test

        // Header is 52 bytes, plus AES GCM tag is 16 bytes
        assertTrue(encryptedData.size == 52 + 16 + plaintext.size)

        val (decryptedData, outEntryCount) = CryptoHelper.decrypt(encryptedData, password)
        assertArrayEquals(plaintext, decryptedData)
        assertEquals(entryCount, outEntryCount)
    }

    @Test(expected = Exception::class)
    fun testDecryptionFailsWithWrongPassword() {
        val plaintext = "Secret Message".toByteArray(Charsets.UTF_8)
        val password = "StrongPassword123".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()

        val encryptedData = CryptoHelper.encrypt(plaintext, password, 1, 1000)

        CryptoHelper.decrypt(encryptedData, wrongPassword)
    }
}
