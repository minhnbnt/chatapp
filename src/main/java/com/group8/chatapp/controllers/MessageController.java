package com.group8.chatapp.controllers;

import com.group8.chatapp.dtos.MessageDto;
import com.group8.chatapp.services.MessageService;
import com.group8.chatapp.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final UserService userService;
    private final MessageService messageService;

    @GetMapping("/api/messages/{roomId}")
    public List<MessageDto> getChatMessages(@PathVariable long roomId) {
        return messageService.getMessages(roomId);
    }

    @PostMapping("/api/messages/{roomId}")
    public void sendMessage(@Valid MessageDto dto, @PathVariable long roomId) {
        messageService.sendMessage(dto, roomId);
    }

    @GetMapping("/api/exists/{roomId}")
    public Map<String, String> checkIfExists(@PathVariable long roomId) {

        var user = userService.getUserByContext()
                .orElseThrow(() -> {
                    return new ResponseStatusException(HttpStatus.FORBIDDEN);
                });

        String message;
        if (messageService.userInChatRoom(user, roomId)) {
            message = String.format("User %s exists in room %d.", user.getUsername(), roomId);
        } else {
            message = String.format("User %s doesn't exist in room %d.", user.getUsername(), roomId);
        }

        return Map.of("message", message);
    }
}
