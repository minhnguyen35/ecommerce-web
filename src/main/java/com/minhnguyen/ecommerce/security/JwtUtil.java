package com.minhnguyen.ecommerce.security;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${app.jwt-secret}")
    private String secret;

    @Value("${app.jwt-expiration-ms}")
    private String expirationMs;

    public String generateToken(String email) throws ParseException {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(DateFormat.getInstance().parse(System.currentTimeMillis() + expirationMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .build().parseEncryptedClaims(token).getPayload().getSubject();
    }

    public boolean isValid(String token) {
        try {
            extractEmail(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
