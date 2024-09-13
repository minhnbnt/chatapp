package com.group8.chatapp.services;

import com.group8.chatapp.dtos.MessageDto;
import com.group8.chatapp.models.ChatMessage;
import com.group8.chatapp.models.User;
import com.group8.chatapp.repositories.ChatMessageRepository;
import java.util.List;

import com.group8.chatapp.repositories.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageService {

	private final ModelMapper modelMapper;

	private final UserService userService;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository messageRepository;

	private MessageDto convertToDto(ChatMessage message) {
		return modelMapper.map(message, MessageDto.class);
	}

	public void sendMessage(MessageDto dto, long roomId) {

		var chatRoom = chatRoomRepository.getReferenceById(roomId);
		var user = userService.getUserByContext().orElseThrow();

		var record = ChatMessage.builder()
				.user(user)
				.chatRoom(chatRoom)
				.content(dto.content())
				.build();

		messageRepository.save(record);

		// TODO: send message to websocket
	}

	@Transactional(readOnly = true)
	public List<MessageDto> getMessages(long roomId) {

		var user = userService.getUserByContext().orElseThrow();

		if (!userInChatRoom(user, roomId)) {
			// TODO: throw access denied,
			return List.of();
		}

		return messageRepository.findByChatRoomId(roomId)
		    .map(this::convertToDto)
		    .toList();
	}

	public boolean userInChatRoom(User user, long roomId) {
		return chatRoomRepository.existsByIdAndMembersId(roomId, user.getId());
	}
}
