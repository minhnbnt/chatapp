package com.group8.chatapp.configs;

import com.group8.chatapp.services.jwts.JwtsParserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtsParserService jwtsParserService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final WebAuthenticationDetailsSource authenticationDetailsSource;

    private String getTokenFromRequest(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        return authHeader.substring(7);
    }

    private void setAuthenticationInfo(HttpServletRequest request) {

        String token = getTokenFromRequest(request);
        if (token == null) {
            return;
        }

        var user = jwtsParserService
                .getUserFromToken(token)
                .orElse(null);

        if (user == null) {
            return;
        }

        var authToken = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()
        );

        var authDetail = authenticationDetailsSource.buildDetails(request);
        authToken.setDetails(authDetail);

        SecurityContextHolder
                .getContext()
                .setAuthentication(authToken);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) {

        try {

            setAuthenticationInfo(request);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}
