# Luồng hoạt động tính năng – Thành viên 3 (Chat list + Group chat)

Phạm vi tài liệu:

- Backend: `chatapp/chatapp`
- Frontend: `chatapp/chatapp-flutter`
- Tập trung các tính năng:
  - Hiển thị danh sách các cuộc trò chuyện
  - Hiển thị tin nhắn cuối cùng và thời gian
  - Badge số tin nhắn chưa đọc
  - Tìm kiếm người dùng và cuộc trò chuyện
  - Ghim cuộc trò chuyện quan trọng
  - Chat nhóm:
    - Tạo nhóm chat
    - Thêm / xóa thành viên
    - Phân quyền admin / member
    - Đổi tên nhóm, ảnh nhóm
    - Rời nhóm / giải tán nhóm

> Ghi chú: tổng hợp từ code hiện tại, không đọc `.env`.

---

## 1) Hiển thị danh sách các cuộc trò chuyện

## Backend
1. Client gọi `GET /api/v1/chatrooms/`.
2. `ChatRoomController.listChatRooms()` -> `ChatRoomService.listRoomsWithLatestMessage()`.
3. Backend trả `ChatRoomDto` gồm:
   - `id`, `name`, `avatar`, `membersUsername`, `type`, `createdOn`, `pinned`, `latestMessage`.
4. Với room `DUO`, backend enrich tên/avatar theo peer (`enrichDuoPreview`).

## Frontend
1. `ChatListScreen` gọi `ChatRoomsProvider.loadRooms()`.
2. Provider lưu `_rooms`, sắp xếp theo:
   - pinned trước,
   - sau đó theo thời gian message mới nhất.
3. `ChatRoomTile` render từng cuộc trò chuyện.

---

## 2) Hiển thị tin nhắn cuối cùng và thời gian

## Dữ liệu
- Backend gắn `latestMessage` vào `ChatRoomDto`.
- `latestMessage` đến từ `MessageRepository.findFirstByRoom_IdOrderBySentOnDescIdDesc`.

## Render frontend
1. `ChatRoomModel.latestPreviewFor(...)` tạo preview:
   - có text: hiện text (hoặc `sender: text` khi cần),
   - có attachment: “Sent an attachment”,
   - recall: “Message recalled”.
2. `ChatRoomTile` format thời gian:
   - hôm nay: `HH:mm`,
   - hôm qua: `Hôm qua`,
   - < 7 ngày: thứ,
   - xa hơn: `dd/MM`.

---

## 3) Badge số tin nhắn chưa đọc

## Cơ chế
1. Local unread được giữ trong `ChatRoomsProvider._unreadCounts` (theo roomId).
2. Khi nhận message realtime (`roomMessageStream`):
   - nếu room không mở và không phải tin của mình -> tăng unread.
3. Khi mở room (`markRoomOpened`) hoặc bấm vào room (`markRoomRead`):
   - unread = 0,
   - lưu `lastReadAt` vào local (`UnreadStateService`, SharedPreferences).
4. Khi load lại app/room list:
   - provider hydrate unread từ `lastReadAt` local + `latestMessage`.

## UI
- `ChatRoomTile` hiển thị badge `unreadCount` (giới hạn `99+`).

> Lưu ý: unread badge hiện thiên về phía client/local state, không phải counter server-side riêng.

---

## 4) Tìm kiếm người dùng và cuộc trò chuyện

## Tìm kiếm cuộc trò chuyện
- Tại `ChatListScreen`:
  - filter theo tên room,
  - username peer (DUO),
  - preview tin nhắn cuối,
  - danh sách member username.

## Tìm kiếm người dùng
- API backend: `GET /api/v1/users/search/?q=...&limit=...`.
- Dùng trong các màn:
  - `PeopleScreen`,
  - `AddFriendScreen`,
  - `CreateGroupScreen`,
  - `GroupMembersScreen`.
- Debounce client (300–350ms) trước khi gọi API.

---

## 5) Ghim cuộc trò chuyện quan trọng

## Backend
- Ghim: `POST /api/v1/chatrooms/{roomId}/pin/`
- Bỏ ghim: `DELETE /api/v1/chatrooms/{roomId}/pin/`
- `ChatRoomService.pinRoom/unpinRoom` lưu DB và push realtime `/user/queue/chatrooms/pinned/`.

## Frontend
1. `ChatListScreen` long-press room -> chọn ghim/bỏ ghim.
2. `ChatRoomsProvider.togglePinnedRoom(roomId)`:
   - optimistic update,
   - gọi API,
   - rollback nếu lỗi.
3. Khi thành công, room pinned được đưa lên section “Đã ghim”.

---

## 6) Chat nhóm

## 6.1) Tạo nhóm chat

### Backend
1. `POST /api/v1/chatrooms/groups/`.
2. `GroupChatService.createGroup()`:
   - creator + danh sách memberIds,
   - validate user tồn tại,
   - tạo room type `GROUP`,
   - tạo bản ghi `ChatRoomMember` (creator là admin),
   - push room created cho thành viên (`/queue/chatrooms/created/`),
   - push notification “added to group”.

### Frontend
1. `CreateGroupScreen`:
   - nhập tên nhóm,
   - tìm user và chọn >= 2 thành viên.
2. Gọi `GroupChatService.createGroup(...)`.
3. Upsert room vào `ChatRoomsProvider` và điều hướng vào `ChatScreen`.

---

## 6.2) Thêm thành viên

### Backend
- API: `POST /api/v1/chatrooms/{roomId}/groups/members/`.
- `GroupChatService.addMembers()`:
  - bất kỳ member hiện tại đều có quyền thêm,
  - bỏ qua user đã là member,
  - thêm vào room + tạo `ChatRoomMember` role mặc định member,
  - push các event:
    - `/queue/chatrooms/created/` cho user mới,
    - `/queue/groups/members_added` cho room,
    - message hệ thống `[GROUP_EVENT:ADDED] ...`.

### Frontend
- `GroupMembersScreen` chọn user -> add.
- Realtime cập nhật qua `groupMembersAddedStream` và reload group/room list.

---

## 6.3) Xóa thành viên

### Backend
- API: `DELETE /api/v1/chatrooms/{roomId}/groups/members/{userId}/`.
- `GroupChatService.removeMember()`:
  - caller phải là member,
  - **chỉ owner** mới xóa được member,
  - không cho xóa chính owner,
  - xóa membership + read-state + pin của user bị xóa,
  - push:
    - `/queue/friends/removed/` cho user bị xóa,
    - `/queue/groups/member_removed` cho member còn lại,
    - message hệ thống `[GROUP_EVENT:REMOVED] ...`.

### Frontend
- `GroupMembersScreen` action remove member.
- Nhận realtime `groupMemberRemovedStream` để cập nhật UI.

---

## 6.4) Phân quyền admin / member

## Hiện trạng code
- Model có role ở `ChatRoomMember.isAdmin`.
- Quyền hiện dùng như sau:
  - admin: được sửa thông tin nhóm (tên/ảnh),
  - owner: được xóa member, giải tán nhóm,
  - member: có thể thêm thành viên.

---

## 6.5) Đổi tên nhóm, ảnh nhóm

### Backend
- JSON patch: `PATCH /api/v1/chatrooms/{roomId}/groups/`
- Multipart update: `PUT /api/v1/chatrooms/{roomId}/groups/`
- `GroupChatService.updateGroupInternal()`:
  - chỉ admin mới được đổi,
  - validate tên (max 100),
  - validate avatar là ảnh,
  - lưu DB và push `/queue/groups/group_updated`.

### Frontend
- `GroupMembersScreen` có form đổi tên + chọn ảnh.
- Gọi `GroupChatService.updateGroupProfile(...)`.
- Nhận event `groupUpdatedStream`, refresh UI + chat list.

---

## 6.6) Rời nhóm / giải tán nhóm

### Rời nhóm
- API: `DELETE /api/v1/chatrooms/{roomId}/groups/leave/`.
- `GroupChatService.leaveGroup()`:
  - creator/owner **không được rời**,
  - member thường được rời,
  - xóa membership, pin,
  - push `/queue/friends/removed/` (action=left),
  - push `/queue/groups/member_removed` + message hệ thống `[GROUP_EVENT:LEFT] ...`.

### Giải tán nhóm
- API: `DELETE /api/v1/chatrooms/{roomId}/groups/dissolve/`.
- `GroupChatService.dissolveGroup()`:
  - chỉ owner được giải tán,
  - xóa read-state, invitation, pin, messages, memberships, room,
  - push `/queue/friends/removed/` với `dissolvedBy` cho tất cả member.

### Frontend
- `GroupMembersScreen` gọi leave/dissolve.
- `ChatRoomsProvider` nhận `friendRemoved` event:
  - gỡ room khỏi danh sách,
  - nếu có `dissolvedBy` thì hiện system notice ở chat list.

---

## Tệp nguồn chính đã đối chiếu cho phần này

### Backend
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/controllers/ChatRoomController.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/controllers/GroupChatController.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/ChatRoomService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/GroupChatService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/dtos/ChatRoomDto.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/dtos/group/GroupChatDto.java`

### Flutter
- `chatapp/chatapp-flutter/lib/screens/home/chat_list_screen.dart`
- `chatapp/chatapp-flutter/lib/widgets/chat_room_tile.dart`
- `chatapp/chatapp-flutter/lib/providers/chat_rooms_provider.dart`
- `chatapp/chatapp-flutter/lib/services/unread_state_service.dart`
- `chatapp/chatapp-flutter/lib/services/group_chat_service.dart`
- `chatapp/chatapp-flutter/lib/screens/home/create_group_screen.dart`
- `chatapp/chatapp-flutter/lib/screens/chat/group_members_screen.dart`
- `chatapp/chatapp-flutter/lib/services/user_service.dart`
