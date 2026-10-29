package com.jjteo.phototodocs.chatgpt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class ChatGptIdentityTest {
    companion object {
        private val key = RSAKeyGenerator(2048).keyID("test-key").generate()
        private val keys = JWKSet(key.toPublicJWK()).toString()
        private const val now = 1_800_000_000_000L
    }
    private fun token(issuer: String = ChatGptProtocol.ISSUER, audience: String = "issued-client", nonce: String = "nonce", expires: Long = now + 60_000): String {
        val claims = JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("account-identity").expirationTime(Date(expires))
            .issueTime(Date(now - 10_000)).claim("nonce", nonce).claim("email", "test@example.com").build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(key)) }.serialize()
    }
    @Test fun verifiesSignedIdentityAndReturnsOnlyDisplayFields() {
        assertEquals(ChatGptIdentity("account-identity", "test@example.com"), ChatGptIdentityVerifier.verify(token(), "issued-client", "nonce", keys, now))
    }
    @Test fun rejectsWrongIssuerAudienceNonceAndExpiredTokens() {
        for (bad in listOf(token(issuer = "https://example.com"), token(audience = "another-client"), token(nonce = "wrong"), token(expires = now - 1))) {
            assertThrows(IllegalArgumentException::class.java) { ChatGptIdentityVerifier.verify(bad, "issued-client", "nonce", keys, now) }
        }
    }
    @Test fun rejectsWrongSigningKeyAndDamagedSignature() {
        val other = JWKSet(RSAKeyGenerator(2048).keyID("test-key").generate().toPublicJWK()).toString()
        assertThrows(IllegalArgumentException::class.java) { ChatGptIdentityVerifier.verify(token(), "issued-client", "nonce", other, now) }
        val signed = token().split('.')
        val damaged = signed.take(2).joinToString(".") + "." + (if (signed[2][0] == 'A') "B" else "A") + signed[2].substring(1)
        assertThrows(IllegalArgumentException::class.java) { ChatGptIdentityVerifier.verify(damaged, "issued-client", "nonce", keys, now) }
    }
}
