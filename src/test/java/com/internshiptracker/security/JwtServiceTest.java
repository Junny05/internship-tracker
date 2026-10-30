package com.internshiptracker.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-secret-key-do-not-use-in-prod-32bytes";

    @Test
    void isTokenValidReturnsFalseForExpiredToken() throws InterruptedException {
        JwtService jwtService = new JwtService(SECRET, 1);
        String token = jwtService.generateToken("user@example.com");

        Thread.sleep(50);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void extractEmailThrowsForExpiredTokenInsteadOfSilentlyReturningIt() throws InterruptedException {
        JwtService jwtService = new JwtService(SECRET, 1);
        String token = jwtService.generateToken("user@example.com");

        Thread.sleep(50);

        assertThatThrownBy(() -> jwtService.extractEmail(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validTokenRoundTrips() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken("user@example.com");

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
    }
}
