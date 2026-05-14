package com.inventory.auth.security;

import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    @Test
    void generateAndValidateAccessToken_roundTripsClaims() {
        JwtProvider provider = providerWith("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .fullName("John")
                .role(Role.ADMIN)
                .build();

        String token = provider.generateAccessToken(user);
        Claims claims = provider.validateAndExtract(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("john@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(provider.getAccessExpiryMs()).isEqualTo(900_000L);
        assertThat(provider.getRefreshExpiryMs()).isEqualTo(604_800_000L);
    }

    @Test
    void generateRefreshToken_returnsUuidString() {
        JwtProvider provider = providerWith("plain-text-secret-with-enough-length-1234567890");

        assertThat(provider.generateRefreshToken()).contains("-");
    }

    @Test
    void blankSecret_throwsHelpfulError() {
        JwtProvider provider = providerWith(" ");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .fullName("John")
                .role(Role.ADMIN)
                .build();

        assertThatThrownBy(() -> provider.generateAccessToken(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void extractWithoutValidation_fallsBackGracefullyForInvalidToken() {
        JwtProvider provider = providerWith("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> provider.extractWithoutValidation("invalid.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateAndValidateAccessToken_supportsBase64Secret() {
        String base64Secret = Base64.getEncoder().encodeToString("base64-secret-key-for-auth-signing-1234567890".getBytes(StandardCharsets.UTF_8));
        JwtProvider provider = providerWith(base64Secret);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .fullName("John")
                .role(Role.ADMIN)
                .build();

        String token = provider.generateAccessToken(user);

        assertThat(provider.validateAndExtract(token).getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    void extractWithoutValidation_returnsClaimsForExpiredToken() {
        String base64Secret = Base64.getEncoder().encodeToString("base64-secret-key-for-auth-signing-1234567890".getBytes(StandardCharsets.UTF_8));
        JwtProvider provider = providerWith(base64Secret);
        byte[] signingBytes = Base64.getDecoder().decode(base64Secret);
        String token = Jwts.builder()
                .subject("expired-user")
                .claim("email", "john@test.com")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(signingBytes), Jwts.SIG.HS256)
                .compact();

        Claims claims = provider.extractWithoutValidation(token);

        assertThat(claims.getSubject()).isEqualTo("expired-user");
        assertThat(claims.get("email", String.class)).isEqualTo("john@test.com");
    }

    private JwtProvider providerWith(String secret) {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "secret", secret);
        ReflectionTestUtils.setField(provider, "accessExpiryMs", 900_000L);
        ReflectionTestUtils.setField(provider, "refreshExpiryMs", 604_800_000L);
        return provider;
    }
}
