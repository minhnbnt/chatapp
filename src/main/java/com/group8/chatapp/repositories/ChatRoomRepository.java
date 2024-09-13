package com.group8.chatapp.repositories;

import com.group8.chatapp.models.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    boolean existsByIdAndMembersUsername(Long id, String username);
}
