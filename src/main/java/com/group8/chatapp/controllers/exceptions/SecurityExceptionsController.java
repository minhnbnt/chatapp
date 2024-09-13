package com.group8.chatapp.controllers.exceptions;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SecurityExceptionsController {

    private ProblemDetail getProblemDetail(
            Exception e,
            HttpStatus status,
            @Nullable String description
    ) {

        var errorDetail = ProblemDetail.forStatusAndDetail(
                status, e.getMessage()
        );

        if (description != null) {
            errorDetail.setProperty("description", description);
        }

        return errorDetail;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleWrongLogin(BadCredentialsException e) {
        return getProblemDetail(
                e, HttpStatus.UNAUTHORIZED,
                "The username or password is incorrect."
        );
    }

    @ExceptionHandler(AccountStatusException.class)
    public ProblemDetail handleAccountDisabled(AccountStatusException e) {
        return getProblemDetail(
                e, HttpStatus.FORBIDDEN,
                "Your account is disabled."
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handlePermissionDenied(AccessDeniedException e) {
        return getProblemDetail(
                e, HttpStatus.FORBIDDEN,
                "You don't have permission to access this resource."
        );
    }

    @ExceptionHandler(JwtException.class)
    public ProblemDetail handleExpiredToken(JwtException e) {
        return getProblemDetail(
                e, HttpStatus.FORBIDDEN,
                "The JWT token is invalid or expired."
        );
    }
}
