package com.group8.chatapp.services.jwts;

import com.group8.chatapp.models.User;
import com.group8.chatapp.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JwtsParserService {

    private final JwtParser jwtParser;
    private final UserRepository userRepository;

    private Claims extractAllClaims(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(Claims claims) {
        Date now = new Date();
        return claims.getExpiration().before(now);
    }

    private String getUserName(Claims claim) {
        return claim.getSubject();
    }

    public Optional<User> getUserFromToken(String token) {

        Claims claims = extractAllClaims(token);
        if (isTokenExpired(claims)) {
            return Optional.empty();
        }

        String username = getUserName(claims);
        return userRepository.findByUsername(username);
    }
}
