package com.group4.chatapp.dtos.invitation;

import com.group4.chatapp.dtos.ChatRoomDto;
import org.jspecify.annotations.Nullable;

/**
 * DTO trả về sau khi phản hồi lời mời.
 *
 * Nếu việc chấp nhận lời mời tạo ra phòng chat mới thì phòng đó được gắn vào
 * DTO này để client có thể chuyển sang màn hình chat ngay.
 */
public record ReplyResponse(@Nullable ChatRoomDto newChatRoom) {}
