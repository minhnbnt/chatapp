package com.group8.chatapp.controllers.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class BadRequestController {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, ?> handleValidationExceptions(MethodArgumentNotValidException e) {

        Map<String, String> errors = new TreeMap<>();

        e
                .getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> (FieldError) error)
                .forEach((error) -> {

                    String fieldName = error.getField();
                    String errorMessage = error.getDefaultMessage();

                    errors.put(fieldName, errorMessage);
                });

        return errors;
    }
}
