package com.group4.chatapp.dtos;

import com.group4.chatapp.dtos.messages.MessageReceiveDto;
import com.group4.chatapp.models.ChatMessage;
import com.group4.chatapp.models.ChatRoom;
import com.group4.chatapp.models.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.util.List;

/**
 * DTO biểu diễn dữ liệu một phòng chat để trả về cho client.
 *
 * Class này gom các thông tin hiển thị của phòng chat như tên phòng, avatar,
 * danh sách thành viên, loại phòng, thời điểm tạo và tin nhắn mới nhất.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatRoomDto {

    private long id;

    @Nullable
    private String name;

    private AttachmentDto avatar;
    private List<String> membersUsername;

    private ChatRoom.Type type;
    private Timestamp createdOn;

    private boolean pinned;

    private MessageReceiveDto latestMessage;

    /**
     * Tạo DTO từ một thực thể phòng chat và tin nhắn mới nhất của phòng đó.
     *
     * Constructor này sao chép dữ liệu cần thiết từ domain model sang DTO,
     * đồng thời chuyển avatar và tin nhắn cuối thành các DTO tương ứng nếu có.
     * Nếu phòng không có avatar hoặc không có tin nhắn mới nhất, các field
     * liên quan sẽ được giữ ở trạng thái rỗng theo quy ước của DTO.
     *
     * @param room Phòng chat nguồn dùng để lấy thông tin hiển thị.
     * @param latestMessage Tin nhắn mới nhất của phòng, có thể là null.
     */
    public ChatRoomDto(ChatRoom room,@Nullable ChatMessage latestMessage) {

        this(
            room.getId(),
            room.getName(),
            null,
            room.getMembers().stream().map(User::getUsername).toList(),
            room.getType(),
            room.getCreatedOn(),
            false,
            null
        );

        var avatar = room.getAvatar();
        if (avatar != null) {
            this.avatar = new AttachmentDto(avatar);
        }

        if (latestMessage != null) {
            this.latestMessage = new MessageReceiveDto(latestMessage);
        }
    }
}
