package com.group8.chatapp.dtos;

import jakarta.validation.constraints.NotEmpty;

public record UserDto(

        @NotEmpty
        String username,

        @NotEmpty
        String password
) {}