package dev.mohsin.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfiguration {
    public static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceCheck = token -> token.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> expiryRequired = token -> token.getExpiresAt() != null
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Expiry required", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                audienceCheck, expiryRequired);
    }
    @Bean JwtDecoder jwtDecoder(@Value("${security.issuer}") String issuer,
                                @Value("${security.jwk-set-uri}") String jwks,
                                @Value("${security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(validators(issuer, audience));
        return decoder;
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Bearer tokens only: no cookie/session authentication on this API.
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/collections").hasAuthority("SCOPE_collections.read")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/collections").hasAuthority("SCOPE_collections.write")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .build();
    }
}

