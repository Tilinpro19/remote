package com.universalremote.adapters.androidtv

import java.security.KeyPairGenerator
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfSignedCertificateTest {

    @Test
    fun `genera un X509 v3 autofirmado SHA256withRSA verificable`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val cert = SelfSignedCertificate.create(keyPair, "atvremote/UniversalRemote", Date(now - 1_000), Date(now + 86_400_000))

        assertEquals(3, cert.version)
        assertEquals("SHA256withRSA", cert.sigAlgName)
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        assertEquals(keyPair.public, cert.publicKey)
        assertEquals(-1, cert.basicConstraints)
        cert.checkValidity()
        cert.verify(keyPair.public)
    }
}
