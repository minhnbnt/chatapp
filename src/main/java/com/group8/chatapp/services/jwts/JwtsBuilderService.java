package com.group8.chatapp.services.jwts;

import io.jsonwebtoken.JwtBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class JwtsBuilderService {

    private final JwtBuilder jwtBuilder;

    public Map<String, String> getTokenPair(UserDetails user) {

        var accessToken = getTokenFromUser(user, false);
        var refreshToken = getTokenFromUser(user, true);

        var result = new TreeMap<String, String>();

        result.put("access", accessToken);
        result.put("refresh", refreshToken);

        return result;
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

        return jwtBuilder
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date(issued))
                .expiration(new Date(expiration))
                .compact();
    }

}
