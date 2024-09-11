package com.group8.chatapp.controllers;

import com.group8.chatapp.dtos.MessageDto;
import com.group8.chatapp.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/api/messages/{roomId}")
    public List<MessageDto> getChatMessages(@PathVariable long roomId) {
        return messageService.getMessages(roomId);
    }
}
