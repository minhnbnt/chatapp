package com.group8.chatapp.dtos;

import jakarta.validation.constraints.NotEmpty;

public record MessageDto(

        @NotEmpty
        String content
) {}