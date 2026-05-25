# Luồng hoạt động tính năng (Backend + Flutter)

Tài liệu này mô tả luồng end-to-end cho 2 dự án:

- Backend: `chatapp/chatapp` (Spring Boot Java/Kotlin)
- Frontend: `chatapp/chatapp-flutter` (Flutter)

> Ghi chú: tài liệu được tổng hợp từ code, **không đọc `.env`**.

---

## 1) Đăng ký, đăng nhập, đăng xuất

### Đăng ký
1. Flutter `RegisterScreen` validate form cơ bản (username/password/confirm).
2. `AuthProvider.register()` gọi `AuthService.register()`.
3. `AuthService.register()` gửi `POST /api/v1/users/register/` với `UserDto`.
4. Backend `UserController.registerUser()` -> `UserService.createUser()`:
   - kiểm tra username đã tồn tại chưa,
   - hash password (Argon2),
   - tạo user mới với `displayName = username`.
5. Trả về `201 CREATED`.

### Đăng nhập
1. Flutter `LoginScreen` submit -> `AuthProvider.login()`.
2. `AuthService.login()` gọi `POST /api/v1/users/token/`.
3. Backend `UserController.obtainToken()` -> `JwtsService.tokenObtainPair()`:
   - authenticate username/password,
   - phát hành access token + refresh token,
   - lưu refresh `jti` vào Redis (`RefreshTokenService`).
4. Flutter lưu token vào `TokenStorageService`, connect WebSocket (`RealtimeService.connect()`), tải profile `/api/v1/users/me/`.

### Đăng xuất
1. Flutter `AuthProvider.logout()`:
   - ngắt WebSocket,
   - xoá token local.
2. Hiện trạng: Flutter **chưa gọi** `POST /api/v1/users/token/revoke/` nên refresh token phía server chưa bị revoke khi logout local.

---

## 2) Kiểm tra và validate dữ liệu người dùng

### Backend
- `UserDto`: bắt buộc `username`, `password` (`@NotEmpty`).
- `UserService.createUser()`: chặn trùng username.
- `UserService.validateNewPassword()`: password mới tối thiểu 8 ký tự.
- `UserService.updateCurrentProfile()`:
  - `displayName` không rỗng, tối đa 60 ký tự,
  - avatar phải là file ảnh.
- `UserService.searchUser()`: `limit` trong [1..20].
- Invitation validations (`InvitationSendService`):
  - không tự mời,
  - user nhận phải tồn tại,
  - không gửi trùng pending,
  - không mời khi đã là bạn,
  - chặn khi block 2 chiều.

### Flutter
- `LoginScreen`: bắt buộc nhập username/password.
- `RegisterScreen`: username >= 3, password >= 4, confirm phải khớp.
- `ProfileScreen`: display name không rỗng, max 60.

> Lưu ý lệch chuẩn: password min client (4 hoặc 6 ở một số màn) < backend (8).

---

## 3) Quên mật khẩu

### Backend (đã có đầy đủ)
1. `POST /api/v1/users/password/reset-request/` với username.
2. `UserService.requestPasswordReset()`:
   - generate reset token (`PasswordResetTokenService`, lưu Redis có TTL),
   - gửi email reset qua `EmailService`.
3. `POST /api/v1/users/password/reset-confirm/` với `token`, `newPassword`.
4. `UserService.resetPassword()`:
   - validate token,
   - validate password mới,
   - đổi password,
   - revoke các reset token liên quan username.

### Flutter (hiện trạng)
- Nút “Quên mật khẩu?” ở `LoginScreen` đang để trống callback (`onPressed: () {}`), chưa có flow gọi 2 endpoint reset.

---

## 4) Cập nhật thông tin cá nhân (tên hiển thị, ảnh đại diện)

1. Flutter `ProfileScreen` cho nhập display name, chọn ảnh.
2. `AuthProvider.updateMyProfile()` -> `UserService.updateMyProfile()`.
3. Flutter gửi `PUT /api/v1/users/me/` dạng multipart:
   - field `displayName`,
   - file `avatar`.
4. Backend `UserController.updateMyProfile()` -> `UserService.updateCurrentProfile()`:
   - normalize + validate display name,
   - kiểm tra loại file ảnh,
   - upload avatar S3,
   - lưu DB.
5. Backend phát sự kiện realtime `/queue/users/profile/`.
6. Flutter nhận stream profile và cập nhật UI ở nhiều provider/screen.

---

## 5) Hiển thị danh sách bạn bè

1. Flutter `ChatRoomsProvider.loadRooms()` gọi `GET /api/v1/chatrooms/`.
2. Backend `ChatRoomService.listRoomsWithLatestMessage()` trả danh sách room user tham gia.
3. Room DUO được enrich tên/avatar peer (`enrichDuoPreview`).
4. Flutter `PeopleScreen` lọc room `type == duo` để hiển thị danh sách bạn bè.

> Trong hệ thống này, “bạn bè” thực chất là quan hệ thành viên trong room DUO.

---

## 6) Gửi lời mời kết bạn

1. Flutter `PeopleScreen` -> `InvitationService.sendInvitation(receiverUserName, chatGroupId?)`.
2. Backend `POST /api/v1/invitations/` -> `InvitationService.sendInvitation()` -> `InvitationSendService.sendInvitation()`.
3. Validate nghiệp vụ:
   - self-invite,
   - user tồn tại,
   - không blocked hai chiều,
   - không trùng pending,
   - friend request: chưa là bạn.
4. Lưu invitation trạng thái `PENDING`.
5. Push realtime `/user/queue/invitations/` cho người nhận.
6. Đồng thời đẩy push notification qua `NotificationService`.

---

## 7) Chấp nhận / từ chối lời mời kết bạn

1. Flutter `InvitationsScreen` bấm accept/decline.
2. `InvitationService.replyInvitation(invitationId, accept)` gọi `PATCH /api/v1/invitations/{id}`.
3. Backend `InvitationReplyService.replyInvitation()`:
   - chỉ receiver mới được reply,
   - chỉ invitation pending mới xử lý,
   - nếu accept:
     - friend request -> tạo room DUO mới,
     - group invite -> thêm member vào group,
   - cập nhật status (`ACCEPTED`/`REJECTED`).
4. Backend push realtime `/user/queue/invitationReplies/`.
5. Flutter refresh room list để đảm bảo UI cập nhật ngay cả khi thiếu event ở một số flow.

---

## 8) Xóa bạn bè

1. Flutter `PeopleScreen` xác nhận xóa -> `ChatRoomService.removeFriend(roomId)`.
2. Gọi `DELETE /api/v1/chatrooms/{roomId}/friend/`.
3. Backend `ChatRoomService.removeFriend()`:
   - room phải là DUO,
   - requester phải là member,
   - xóa pin/readstate/messages/room.
4. Push realtime `/user/queue/friends/removed/` cho các member liên quan.
5. Flutter nhận event, gỡ room khỏi danh sách và điều hướng phù hợp nếu đang đứng trong chat đó.

---

## 9) Chặn người dùng

### API backend
- `POST /api/v1/users/{username}/block/`
- `DELETE /api/v1/users/{username}/block/`
- `GET /api/v1/users/{username}/block-status/`
- `GET /api/v1/users/blocks/`

### Luồng
1. Flutter `PeopleScreen` gọi block/unblock qua `UserService`.
2. Backend `UserBlockService`:
   - chặn self bị từ chối,
   - tạo/xóa bản ghi block,
   - khi block sẽ xóa pending invitation giữa hai user.
3. Backend push realtime block status `/user/queue/users/block/` cho cả hai bên.
4. Flutter `ChatScreen` subscribe block-status:
   - cập nhật cờ `blockedByMe/blockedByPeer`,
   - disable gửi tin nhắn khi bị block,
   - hiển thị banner trạng thái blocked.

---

## 10) Quản lý trạng thái Online / Offline, Last seen

### Backend
1. Presence lấy theo kết nối WebSocket (`SimpUserRegistry`) + Redis last-seen.
2. API query presence: `GET /api/v1/users/{username}/presence/`.
3. `PresenceWebSocketController` nhận message `/app/app-presence` payload `{active: bool}`.
4. Khi disconnect, `PresenceService.markDisconnected()` ghi `last_seen` vào Redis.
5. Broadcast presence updates lên `/queue/presence/`.

### Flutter
1. `RealtimeService` subscribe `/queue/presence/`.
2. `app.dart` gửi app lifecycle presence (`active: true/false`) theo trạng thái app.
3. `ChatScreen`:
   - load presence ban đầu qua REST,
   - nhận update realtime,
   - hiển thị “Đang hoạt động / Hoạt động lần cuối ...”.

---

## 11) Tóm tắt cuộc trò chuyện (AI)

1. Flutter `ChatScreen` gom recent text messages và gọi `ChatProvider.summarizeRecentMessages()`.
2. `MessageService.summarizeRecentMessages()` gửi `POST /api/v1/messages/summarize`.
3. Backend `MessageController.summarizeMessages()` -> `SummaryService.summarize()`:
   - normalize input,
   - giới hạn số lượng/kích thước message,
   - build prompt tiếng Việt (`PromptService`),
   - gọi OpenAI (`OpenAIClientService`).
4. Nếu AI lỗi, backend trả fallback summary (không fail cứng).
5. Flutter hiển thị kết quả trong dialog markdown.
