package com.jjteo.phototodocs.chatgpt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jwt.SignedJWT

internal data class ChatGptIdentity(val subject: String, val email: String)

internal object ChatGptIdentityVerifier {
    fun verify(token: String, clientId: String, nonce: String, jwks: String, now: Long = System.currentTimeMillis()): ChatGptIdentity {
        require(token.length <= 32_000) { "The sign-in identity is too large." }
        val jwt = SignedJWT.parse(token)
        require(jwt.header.algorithm == JWSAlgorithm.RS256) { "The sign-in signature algorithm was not accepted." }
        val key = JWKSet.parse(jwks).keys.filterIsInstance<RSAKey>().singleOrNull {
            it.keyID == jwt.header.keyID && it.size() >= 2048 && !it.isPrivate &&
                (it.algorithm == null || it.algorithm == JWSAlgorithm.RS256) &&
                (it.keyUse == null || it.keyUse == KeyUse.SIGNATURE)
        } ?: throw IllegalArgumentException("The sign-in signing key was not accepted.")
        require(jwt.verify(RSASSAVerifier(key.toRSAPublicKey()))) { "The sign-in signature is invalid." }
        val claims = jwt.jwtClaimsSet
        require(claims.issuer == ChatGptProtocol.ISSUER && clientId in claims.audience) { "The sign-in identity does not belong to this app." }
        if (claims.audience.size > 1) require(claims.getStringClaim("azp") == clientId) { "The authorized client did not match." }
        require(claims.expirationTime?.time?.let { it > now } == true &&
            (claims.notBeforeTime?.time ?: 0) <= now + 30_000 &&
            (claims.issueTime?.time ?: 0) <= now + 30_000) { "The sign-in identity expired or the phone clock is incorrect." }
        require(claims.getStringClaim("nonce") == nonce && nonce.isNotBlank()) { "The sign-in identity did not match this attempt." }
        val subject = claims.subject.orEmpty()
        require(subject.isNotBlank() && subject.length <= 512) { "The sign-in account identity is missing." }
        return ChatGptIdentity(subject, claims.getStringClaim("email").orEmpty().take(254))
    }
}
