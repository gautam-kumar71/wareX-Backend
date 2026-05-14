package com.inventory.auth.security;

import com.inventory.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
@Slf4j
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiry-ms}")
    private long accessExpiryMs;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    private SecretKey signingKey() {
        String normalizedSecret = secret == null ? "" : secret.trim();
        if (normalizedSecret.isEmpty()) {
            throw new IllegalStateException("JWT secret must not be blank");
        }

        try {
            return Keys.hmacShaKeyFor(HexFormat.of().parseHex(normalizedSecret));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(normalizedSecret));
        } catch (IllegalArgumentException ignored) {
        }

        return Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed access token embedding userId, email, role, fullName.
     * Expiry: 15 minutes (configurable).
     */
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("fullName", user.getFullName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiryMs))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Generates an opaque refresh token (UUID).
     * Stored as bcrypt hash in DB — never exposed in JWT form.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validates signature + expiry and returns claims.
     * Throws ExpiredJwtException, MalformedJwtException, SignatureException —
     * all caught by GlobalExceptionHandler.
     */
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts claims without throwing on expiry — used for logout
     * where we want to blocklist even an expired token.
     */
    public Claims extractWithoutValidation(String token) {
        try {
            return Jwts.parser()
                    .build()
                    .parseUnsecuredClaims(toUnsecuredToken(token))
                    .getPayload();
        } catch (Exception e) {
            try {
                return validateAndExtract(token);
            } catch (ExpiredJwtException ex) {
                return ex.getClaims();
            }
        }
    }

    private String toUnsecuredToken(String token) {
        int firstDot = token.indexOf('.');
        int lastDot = token.lastIndexOf('.');
        if (firstDot < 0 || lastDot <= firstDot) {
            throw new JwtException("Invalid JWT format");
        }
        return token.substring(0, lastDot + 1);
    }

    public long getAccessExpiryMs() {
        return accessExpiryMs;
    }

    public long getRefreshExpiryMs() {
        return refreshExpiryMs;
    }
}
