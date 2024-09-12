package com.group8.chatapp.configs;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
public class JwtConfig {

    @Value("${jwts.secret-key}")
    private String secretKey;

    private SecretKey signature;

    private SecretKey getSignInKey() {

        if (signature == null) {
            var bytes = Decoders.BASE64.decode(secretKey);
            signature = Keys.hmacShaKeyFor(bytes);
        }

        return signature;
    }

    @Bean
    public JwtBuilder jwtBuilder() {
        return Jwts.builder().signWith(getSignInKey());
    }

    @Bean
    public JwtParser jwtParserBuilder() {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build();
    }
}