package com.group4.chatapp.dtos.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.group4.chatapp.dtos.AttachmentDto;
import com.group4.chatapp.dtos.messages.MessageReceiveDto;
import com.group4.chatapp.dtos.user.UserWithAvatarDto;
import com.group4.chatapp.models.ChatMessage;
import com.group4.chatapp.models.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.util.List;

/**
 * DTO biểu diễn chi tiết một group chat.
 *
 * DTO này cung cấp thông tin nhóm, avatar, thành viên, trạng thái admin/owner
 * và tin nhắn gần nhất để client render màn hình group chat.
 */
@Schema(description = "Group chat details response")
public class GroupChatDto {

    @Schema(description = "Room ID")
    private long id;

    @Schema(description = "Group name")
    private String name;

    @Schema(description = "Group avatar")
    private AttachmentDto avatar;

    @Schema(description = "List of members with avatars")
    private List<UserWithAvatarDto> members;

    @Schema(description = "Room type (DUO or GROUP)")
    private ChatRoom.Type type;

    @Schema(description = "Creation timestamp")
    private Timestamp createdOn;

    @Schema(description = "Latest message")
    private MessageReceiveDto latestMessage;

    @Schema(description = "Is current user an admin")
    private boolean isAdmin;

    @Schema(description = "Is current user the group creator")
    private boolean isOwner;

    /**
     * Tạo DTO rỗng cho serializer/deserializer.
     */
    public GroupChatDto() {}

    /**
     * Tạo DTO từ room và dữ liệu bổ sung của group.
     *
     * Behavior của method:
     * - Sao chép thông tin room và member list vào DTO.
     * - Chuyển avatar và latestMessage sang DTO nếu có.
     * - Ghi nhận trạng thái admin và owner của user hiện tại.
     *
     * @param room Phòng chat nguồn.
     * @param latestMessage Tin nhắn gần nhất, có thể null.
     * @param members Danh sách thành viên đã được dựng DTO.
     * @param isAdmin Người dùng hiện tại có quyền admin hay không.
     * @param isOwner Người dùng hiện tại có phải chủ nhóm hay không.
     */
    public GroupChatDto(
        ChatRoom room,
        @Nullable ChatMessage latestMessage,
        List<UserWithAvatarDto> members,
        boolean isAdmin,
        boolean isOwner
    ) {
        this.id = room.getId();
        this.name = room.getName();
        this.type = room.getType();
        this.createdOn = room.getCreatedOn();
        this.members = members;
        this.isAdmin = isAdmin;
        this.isOwner = isOwner;

        var avatar = room.getAvatar();
        if (avatar != null) {
            this.avatar = new AttachmentDto(avatar);
        }

        if (latestMessage != null) {
            this.latestMessage = new MessageReceiveDto(latestMessage);
        }
    }

    // Getters
    public long getId() { return id; }
    public String getName() { return name; }
    public AttachmentDto getAvatar() { return avatar; }
    public List<UserWithAvatarDto> getMembers() { return members; }
    public ChatRoom.Type getType() { return type; }
    public Timestamp getCreatedOn() { return createdOn; }
    public MessageReceiveDto getLatestMessage() { return latestMessage; }
    @JsonProperty("isAdmin")
    public boolean isAdmin() { return isAdmin; }

    @JsonProperty("isOwner")
    public boolean isOwner() { return isOwner; }
}
