package com.group8.chatapp.services.jwts;

import io.jsonwebtoken.JwtBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class JwtsBuilderService {

    private final JwtBuilder jwtBuilder;

    public Map<String, String> getTokenPair(UserDetails user) {
        return Map.of(
            "access", getTokenFromUser(user, false),
            "refresh", getTokenFromUser(user, true)
        );
    }

    private long getTokenLifetime(boolean isRefresh) {

        final long THIRTY_MINUTES = 1000 * 60 * 30;
        final long THIRTY_DAYS = 1000L * 60 * 60 * 24 * 30;

        return isRefresh ? THIRTY_DAYS : THIRTY_MINUTES;
    }

    public String getTokenFromUser(UserDetails user, boolean isRefresh) {
        return generateToken(user, new TreeMap<>(), isRefresh);
    }

    public String generateToken(UserDetails user,
                                Map<String, ?> claims,
                                boolean isRefresh) {

        long lifetime = getTokenLifetime(isRefresh);

        long issued = System.currentTimeMillis();
        long expiration = issued + lifetime;

        try {

            return jwtBuilder
                    .claims(claims)
                    .subject(user.getUsername())
                    .issuedAt(new Date(issued))
                    .expiration(new Date(expiration))
                    .compact();

        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create the JWT token"
            );
        }
    }

}
