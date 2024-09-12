package com.group8.chatapp.controllers;

import com.group8.chatapp.dtos.UserDto;
import com.group8.chatapp.services.jwts.JwtsBuilderService;
import com.group8.chatapp.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtsBuilderService jwtsBuilderService;

    @PostMapping("/api/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody UserDto dto) {
        userService.registerUser(dto);
    }

    @PostMapping("/api/token")
    public Map<String, String> obtainToken(@Valid @RequestBody UserDto dto) {
        return userService.getUserByDto(dto)
                .map(jwtsBuilderService::getTokenPair)
                .orElseThrow(() -> {
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                });
    }
}
