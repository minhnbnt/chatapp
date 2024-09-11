package com.group8.chatapp.repositories;

import com.group8.chatapp.models.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Stream<ChatMessage> findByChatRoomId(long chatRoomId);
}
