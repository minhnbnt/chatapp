# Luồng hoạt động tính năng – Thành viên còn lại (Media + Notification + Chatbot AI)

Phạm vi tài liệu:

- Backend: `chatapp/chatapp`
- Frontend: `chatapp/chatapp-flutter`
- Tập trung các tính năng:
  - Gửi hình ảnh trong chat cá nhân và chat nhóm
  - Hiển thị hình ảnh trong giao diện chat
  - Xem ảnh toàn màn hình
  - Thông báo khi có tin nhắn mới
  - Thông báo khi có lời mời kết bạn
  - Thông báo khi được thêm vào nhóm
  - Bật / tắt thông báo theo từng cuộc trò chuyện
  - Chatbot AI: cuộc trò chuyện riêng với AI

> Ghi chú: tổng hợp từ code hiện tại, không đọc `.env`.

---

## 1) Gửi hình ảnh trong chat cá nhân và chat nhóm

## Backend
1. Client gửi `POST /api/v1/messages/?room={roomId}` (multipart).
2. Payload có thể gồm:
   - `message` (text, optional),
   - `attachments` (1..n file ảnh/tệp),
   - `replyTo` (optional).
3. `MessageController.sendMessage()` -> `MessageService.sendMessage()` -> `MessageChangesService.sendMessage()`.
4. `AttachmentService.getAttachments(dto)` xử lý upload/attachment trước khi tạo `ChatMessage`.
5. Lưu message và broadcast realtime tới room members.

## Frontend
1. `ChatScreen` chọn ảnh từ gallery (`ImagePicker`).
2. `ChatProvider.sendMessage()` -> `MessageService.sendMessage()`.
3. `MessageService.sendMessage()` dùng multipart field `attachments`.
4. Luồng này dùng chung cho cả room `DUO` và `GROUP` (khác nhau ở `roomId`).

---

## 2) Hiển thị hình ảnh trong giao diện chat

1. Message nhận về có `attachments`.
2. `MessageBubble` lọc attachment ảnh (`AttachmentType.image` hoặc theo extension).
3. Ảnh được render bằng `Image.network(...)` trong bubble.
4. Nếu tải lỗi: hiển thị placeholder "Cannot load image".

---

## 3) Xem ảnh toàn màn hình

1. Người dùng tap vào ảnh trong `MessageBubble`.
2. Gọi `_openImageViewer(...)` để `Navigator.push(...)` sang widget full-screen (`_FullScreenImageView`).
3. Ảnh được hiển thị toàn màn hình để xem chi tiết.

---

## 4) Thông báo khi có tin nhắn mới

## Backend
1. Sau khi gửi message thành công, `MessageChangesService.sendToMembers()`:
   - push realtime WebSocket đến member,
   - gọi `NotificationService.pushNewMessage(...)` cho receiver.
2. `NotificationService.sendPushIfOffline(...)` chỉ gửi FCM nếu:
   - Firebase đang enabled,
   - user không active app (`presenceService.isAppActive == false`),
   - user bật push setting,
   - có FCM token hợp lệ.

## Frontend
1. `FirebaseMessagingService` nhận FCM foreground/background.
2. Foreground: hiển thị local notification qua `LocalNotificationService.showMessageNotification(...)`.
3. Khi user tap notification:
   - parse payload type `message`,
   - điều hướng vào `ChatScreen(roomId)` trong `app.dart`.

---

## 5) Thông báo khi có lời mời kết bạn

## Backend
1. Khi gửi invitation: `InvitationSendService` gọi `notificationService.pushInvitation(...)`.
2. FCM payload type:
   - `invitation` (friend),
   - `group_invitation` (group invite).

## Frontend
1. `FirebaseMessagingService` xử lý type `invitation` / `group_invitation`.
2. Hiển thị local notification qua `showInvitationNotification(...)`.
3. Khi tap notification, app nhận payload để điều hướng luồng phù hợp.

---

## 6) Thông báo khi được thêm vào nhóm

## Backend
1. Khi add member vào group: `GroupChatService.addMembers()`.
2. Service đẩy realtime `/user/queue/groups/added/` và có push FCM `pushGroupEvent(..., eventType="added_to_group")` cho user offline.

## Frontend
1. `RealtimeService` subscribe `groupAddedStream`.
2. `InvitationProvider` nhận event, tạo group-added notification item trong tab thông báo.
3. Với FCM foreground, `FirebaseMessagingService` cũng hiển thị local notification nhóm.

---

## 7) Bật / tắt thông báo theo từng cuộc trò chuyện

## Hiện trạng code
- Backend có API cài đặt push **toàn cục theo user**:
  - `GET /api/v1/users/me/notification-settings/`
  - `PUT /api/v1/users/me/notification-settings/`
- Frontend `SettingsScreen` dùng `NotificationSettingsService` để bật/tắt push toàn cục.


---

## 8) Gọi video (1-1 và nhóm)

## Backend
1. Caller gọi `POST /api/v1/chatrooms/{roomId}/call`.
2. `ChatRoomService.initiateVideoCall()`:
   - verify caller là member của room,
   - tạo `channelName` theo room (`room_{roomId}`),
   - generate Agora token riêng cho từng receiver,
   - push realtime tới từng member còn lại qua `/user/queue/calls/video` với payload:
     - `roomId`, `channelName`, `agoraToken`, `uid`, `senderUsername`, `senderDisplayName`, `senderAvatar`.
   - đồng thời gửi push notification (`NotificationService.pushVideoCall`) cho user offline.
3. Nếu callee từ chối: gọi `POST /api/v1/chatrooms/{roomId}/call/reject`.
4. `ChatRoomService.rejectVideoCall()` push event `/user/queue/calls/video_rejected` cho các member còn lại.

## Frontend
1. `ChatScreen` có nút video call, gọi `ChatRoomService.initiateVideoCall(roomId)`.
2. Caller nhận lại `channelName/token/uid` và vào `VideoCallScreen` qua `VideoCallProvider.initializeCall(...)`.
3. Callee nhận event realtime từ `RealtimeService.videoCallStream`.
4. `HomeScreen` hiển thị incoming-call dialog:
   - **Accept**: init Agora bằng token nhận được rồi mở `VideoCallScreen`.
   - **Decline**: gọi `ChatRoomService.rejectVideoCall(roomId)`.
5. Khi nhận sự kiện `video_call_rejected`, app có thể hiển thị trạng thái người kia đã từ chối.

> Cơ chế signaling hiện dùng backend WebSocket + payload call event; media stream do Agora xử lý.

---

## 9) Chatbot AI – Cuộc trò chuyện riêng với AI

## Backend
1. API namespace: `/api/v1/chatbot/`.
2. Mỗi user có conversation riêng theo owner:
   - list: `GET /conversations`
   - create: `POST /conversations`
   - list messages: `GET /conversations/{id}/messages`
   - delete: `DELETE /conversations/{id}`
   - stream reply: `POST /conversations/{id}/stream` (SSE)
3. `ChatbotService` đảm bảo chỉ owner mới truy cập conversation đó.
4. Khi stream:
   - lưu user message,
   - gọi LLM upstream,
   - phát token SSE (`event: token`) + done,
   - fallback nếu upstream lỗi.

## Frontend
1. `ChatbotScreen` + `ChatbotProvider` quản lý phiên chat AI riêng.
2. `ChatbotProvider.bootstrap()`:
   - load conversation list,
   - nếu chưa có thì tạo mới conversation cho user hiện tại.
3. Gửi message:
   - `ChatbotProvider.sendMessage()` gọi `ChatbotService.streamAssistantResponse(...)`.
   - UI render token streaming realtime.
4. Có thao tác tạo/xóa/chuyển conversation; dữ liệu conversation độc lập với chat người-người.

---

## Tệp nguồn chính đã đối chiếu cho phần này

### Backend
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/controllers/MessageController.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/messages/MessageChangesService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/NotificationService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/GroupChatService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/ChatRoomService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/controllers/UserController.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/controllers/ChatbotController.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/chatbot/ChatbotService.java`

### Flutter
- `chatapp/chatapp-flutter/lib/screens/chat/chat_screen.dart`
- `chatapp/chatapp-flutter/lib/widgets/message_bubble.dart`
- `chatapp/chatapp-flutter/lib/services/message_service.dart`
- `chatapp/chatapp-flutter/lib/services/firebase_messaging_service_native.dart`
- `chatapp/chatapp-flutter/lib/services/local_notification_service.dart`
- `chatapp/chatapp-flutter/lib/services/notification_settings_service.dart`
- `chatapp/chatapp-flutter/lib/providers/invitation_provider.dart`
- `chatapp/chatapp-flutter/lib/services/realtime_service.dart`
- `chatapp/chatapp-flutter/lib/services/chat_room_service.dart`
- `chatapp/chatapp-flutter/lib/providers/video_call_provider.dart`
- `chatapp/chatapp-flutter/lib/screens/chat/video_call_screen.dart`
- `chatapp/chatapp-flutter/lib/screens/chat/join_video_call_screen.dart`
- `chatapp/chatapp-flutter/lib/screens/home/home_screen.dart`
- `chatapp/chatapp-flutter/lib/screens/home/chatbot_screen.dart`
- `chatapp/chatapp-flutter/lib/providers/chatbot_provider.dart`
- `chatapp/chatapp-flutter/lib/services/chatbot_service.dart`
