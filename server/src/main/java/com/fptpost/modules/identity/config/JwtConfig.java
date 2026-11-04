package com.fptpost.modules.identity.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.repository.AuthSessionRepository;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Configuration
public class JwtConfig {
    @Bean
    SecretKey jwtSigningKey(AuthenticationProperties properties, Environment environment, SecureRandom identityRandom) {
        if (environment.matchesProfiles("prod") && !properties.isCookieSecure())
            throw new IllegalStateException("AUTH_COOKIE_SECURE must be true in prod");
        byte[] bytes;
        if (properties.getJwtSecret().isBlank()) {
            if (environment.matchesProfiles("prod")) throw new IllegalStateException("JWT_SECRET is required in prod");
            bytes = new byte[32];
            identityRandom.nextBytes(bytes);
        } else {
            try {
                bytes = Base64.getDecoder().decode(properties.getJwtSecret());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("JWT_SECRET must be Base64", exception);
            }
        }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AuthenticationProperties properties,
                          Clock identityClock, AuthSessionRepository sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(identityClock);
        OAuth2TokenValidator<Jwt> sessionValidator = jwt -> {
            UUID sessionId;
            try {
                sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return invalidSession();
            }
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getId() == null
                    || jwt.getAudience() == null || !jwt.getAudience().contains("fptpost-api")) return invalidSession();
            if (!identityClock.instant().isBefore(jwt.getExpiresAt())) return invalidSession();
            var session = sessions.findByPublicId(sessionId).orElse(null);
            if (session == null || session.getRevokedAt() != null || session.getReplacedBy() != null
                    || !identityClock.instant().isBefore(session.getExpiresAt())) return invalidSession();
            var user = session.getUser();
            if (!user.getPublicId().toString().equals(jwt.getSubject()) || user.isDeleted()
                    || user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null)
                return invalidSession();
            var roles = jwt.getClaimAsStringList("roles");
            if (roles == null || !new HashSet<>(roles).equals(user.getRoles().stream()
                    .map(role -> role.getCode()).collect(java.util.stream.Collectors.toSet()))) return invalidSession();
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.getIssuer()), sessionValidator));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid authentication session", null));
    }
}
