package com.group8.chatapp.controllers;

import com.group8.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

@RestController
@RequiredArgsConstructor
public class HelloController {

    private final UserService userService;

    @GetMapping("/api/hi")
    public Map<String, String> sayHello() {

        var username = userService.getUserByContext()
                .map(UserDetails::getUsername)
                .orElse(null);

        var message = "Hello, world!";
        if (username != null) {
            message = String.format("Hello, your username is %s.", username);
        }

        var response = new TreeMap<String, String>();
        response.put("message", message);

        return response;
    }
}
