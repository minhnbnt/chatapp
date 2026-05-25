package com.group4.chatapp.dtos.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về thông tin cần thiết cho một phiên gọi video.
 *
 * DTO này chứa channelName, token, roomId và uid để client tham gia cuộc gọi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoCallResponseDto {
    private String channelName;
    private String token;
    private long roomId;
    private int uid;
}
