package Frxme.guardian.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtUtil {
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirationMs;

    public JwtUtil(String secret, long expirationMinutes) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.expirationMs = expirationMinutes * 60 * 1000;
    }

    public String generateToken(int userId, String username, String role) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMs))
                .sign(algorithm);
    }

    public DecodedJWT verifyToken(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    public static class TokenPayload {
        public final int userId;
        public final String username;
        public final String role;

        public TokenPayload(int userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }
    }

    public TokenPayload extractPayload(String token) {
        DecodedJWT jwt = verifyToken(token);
        if (jwt == null)
            return null;
        return new TokenPayload(
                jwt.getClaim("userId").asInt(),
                jwt.getClaim("username").asString(),
                jwt.getClaim("role").asString());
    }
}
