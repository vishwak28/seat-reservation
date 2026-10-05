package com.seatreservation.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-which-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "a-completely-different-secret-also-32-bytes-or-more";

    @Test
    void tokenRoundTripKeepsUserAndRole() {
        JwtService service = new JwtService(SECRET, 60);

        String token = service.createToken("alice", AuthenticatedUser.ROLE_USER);
        AuthenticatedUser user = service.parse(token);

        assertEquals("alice", user.getUserId());
        assertEquals(AuthenticatedUser.ROLE_USER, user.getRole());
    }

    @Test
    void adminRoleIsRecognised() {
        JwtService service = new JwtService(SECRET, 60);

        AuthenticatedUser admin = service.parse(service.createToken("boss", AuthenticatedUser.ROLE_ADMIN));

        assertTrue(admin.isAdmin());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = new JwtService(OTHER_SECRET, 60).createToken("alice", AuthenticatedUser.ROLE_ADMIN);

        assertThrows(JwtException.class, () -> new JwtService(SECRET, 60).parse(forged));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1);

        String token = service.createToken("alice", AuthenticatedUser.ROLE_USER);

        assertThrows(JwtException.class, () -> service.parse(token));
    }
}