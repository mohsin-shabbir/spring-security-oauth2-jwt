package dev.mohsin.security;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityTest.Keys.class)
class SecurityTest {
    static final KeyPair KEY = key();
    static KeyPair key() {
        try { var g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); return g.generateKeyPair(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @TestConfiguration
    static class Keys {
        @Bean @Primary JwtDecoder testDecoder() {
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
            decoder.setJwtValidator(SecurityConfiguration.validators("https://issuer.example", "collections-api"));
            return decoder;
        }
    }
    String token(String scope, String issuer, String audience, long expiry, KeyPair key) {
        var rsa = new RSAKey.Builder((RSAPublicKey) key.getPublic()).privateKey((RSAPrivateKey) key.getPrivate()).keyID("test").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("test-service")
            .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(Instant.now().plusSeconds(expiry)).claim("scope", scope).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims)).getTokenValue();
    }
    String token(String scope) { return token(scope, "https://issuer.example", "collections-api", 300, KEY); }
    @Autowired MockMvc mvc;
    @Test void healthIsPublic() throws Exception { mvc.perform(get("/health")).andExpect(status().isOk()); }
    @Test void anonymousIs401() throws Exception { mvc.perform(get("/api/collections")).andExpect(status().isUnauthorized()); }
    @Test void readScopeCanRead() throws Exception {
        mvc.perform(get("/api/collections").header("Authorization", "Bearer " + token("collections.read"))).andExpect(status().isOk());
    }
    @Test void readScopeCannotWrite() throws Exception {
        mvc.perform(post("/api/collections").header("Authorization", "Bearer " + token("collections.read"))
            .contentType("application/json").content("{\"agreementId\":\"DEMO\",\"amountMinor\":100}")).andExpect(status().isForbidden());
    }
    @Test void writeScopeCanWriteAndValidationApplies() throws Exception {
        mvc.perform(post("/api/collections").header("Authorization", "Bearer " + token("collections.write"))
            .contentType("application/json").content("{\"agreementId\":\"DEMO\",\"amountMinor\":100}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/collections").header("Authorization", "Bearer " + token("collections.write"))
            .contentType("application/json").content("{\"agreementId\":\"\",\"amountMinor\":-1}")).andExpect(status().isBadRequest());
    }
    @Test void invalidTokensAreRejected() throws Exception {
        for (String invalid : List.of(
                token("collections.read", "https://wrong.example", "collections-api", 300, KEY),
                token("collections.read", "https://issuer.example", "other-api", 300, KEY),
                token("collections.read", "https://issuer.example", "collections-api", -120, KEY),
                token("collections.read", "https://issuer.example", "collections-api", 300, key()), "malformed")) {
            mvc.perform(get("/api/collections").header("Authorization", "Bearer " + invalid))
                .andExpect(status().isUnauthorized());
        }
    }
}

