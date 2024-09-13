package com.group8.chatapp.services;

import com.group8.chatapp.repositories.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    public boolean userInChatRoom(UserDetails userDetails, long chatRoomId) {
        var username = userDetails.getUsername();
        return chatRoomRepository.existsByIdAndMembersUsername(chatRoomId, username);
    }
}
