package com.fptpost.modules.identity.service;

import com.fptpost.modules.identity.config.AuthenticationProperties;
import com.fptpost.modules.identity.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final AuthenticationProperties properties;
    private final Clock identityClock;

    public Jwt issue(User user, AuthSession session) {
        Instant now = identityClock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getIssuer()).audience(List.of("fptpost-api"))
                .subject(user.getPublicId().toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(properties.getAccessTtlMinutes() * 60L))
                .id(UUID.randomUUID().toString()).claim("sid", session.getPublicId().toString())
                .claim("roles", user.getRoles().stream().map(Role::getCode).sorted().toList()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    }
}
