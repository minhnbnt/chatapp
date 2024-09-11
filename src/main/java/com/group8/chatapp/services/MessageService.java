package com.group8.chatapp.services;

import com.group8.chatapp.dtos.MessageDto;
import com.group8.chatapp.models.ChatMessage;
import com.group8.chatapp.repositories.ChatMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

	private final ChatMessageRepository repository;
	private final ModelMapper modelMapper;

	public MessageDto convertToDto(ChatMessage message) {
		return modelMapper.map(message, MessageDto.class);
	}

	@Transactional(readOnly = true)
	public List<MessageDto> getMessages(long roomId) {
		return repository.findByChatRoomId(roomId)
		    .map(this::convertToDto)
		    .toList();
	}
}
