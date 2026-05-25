# Luồng hoạt động tính năng – Thành viên 2 (Chat 1–1)

Phạm vi tài liệu:

- Backend: `chatapp/chatapp`
- Frontend: `chatapp/chatapp-flutter`
- Tập trung các tính năng:
  - Gửi và nhận tin nhắn văn bản 1–1
  - Hiển thị giao diện chat (bubble trái/phải, thời gian)
  - Hiển thị trạng thái “đang nhập…”
  - Trạng thái tin nhắn: đã gửi, đã xem
  - Xóa tin nhắn cho riêng người dùng
  - Đồng bộ trạng thái tin nhắn giữa hai phía
  - Dịch tin nhắn theo yêu cầu người dùng (AI)

> Ghi chú: tổng hợp từ code hiện có, không dùng `.env`.

---

## 1) Gửi và nhận tin nhắn văn bản 1–1

## Luồng backend
1. Client gọi `POST /api/v1/messages/?room={roomId}` (multipart), gửi field `message`.
2. `MessageController.sendMessage()` -> `MessageService.sendMessage()` -> `MessageChangesService.sendMessage()`.
3. Backend check:
   - người gửi có trong room (`MessageCheckService.receiveChatRoomAndCheck`),
   - với room `DUO`: không bị block hai chiều (`ensureRoomMessagingAllowed`).
4. Lưu message vào DB (`ChatMessage`, status `NORMAL`).
5. Push realtime tới từng member room qua user-queue `/user/queue/chat/{roomId}`.
6. Member còn lại nhận message realtime trong `RealtimeService.roomMessageStream(roomId)`.

## Luồng frontend
1. `ChatScreen` gọi `ChatProvider.sendMessage()`.
2. `MessageService.sendMessage()` gọi API `/api/v1/messages/`.
3. Cả 2 phía đang subscribe stream room (`RealtimeService.roomMessageStream`) nên message được append vào UI gần như realtime.

---

## 2) Hiển thị giao diện chat (bubble trái/phải, thời gian)

1. `ChatScreen` render danh sách tin nhắn theo timeline (có marker ngày).
2. Với mỗi message:
   - `isMine = sender == myUsername`.
   - `MessageBubble(isMine: true)` -> bubble phải.
   - `MessageBubble(isMine: false)` -> bubble trái.
3. Bubble hiển thị:
   - text message,
   - ảnh đính kèm (nếu có),
   - fallback “Tin nhan da bi xoa” nếu message rỗng + không có ảnh,
   - thời gian gửi `HH:mm` từ `sentOn`.
4. Grouping UI:
   - gom message cùng sender để giảm khoảng cách,
   - chat nhóm có tên/avatar sender theo block.

---

## 3) Hiển thị trạng thái “đang nhập…”

## Luồng backend
1. Người dùng A nhập text -> client gọi `POST /api/v1/messages/typing?room={roomId}` body `{typing: true/false}`.
2. `MessageController.setTypingStatus()` -> `MessageService.setTypingStatus()`.
3. Backend gửi event `MessageTypingEventDto(roomId, sender, typing)` tới member còn lại qua `/user/queue/chat/{roomId}/typing`.

## Luồng frontend
1. `ChatScreen._onTextChanged`:
   - khi bắt đầu nhập: gửi `typing=true`,
   - debounce 1200ms ngừng nhập: gửi `typing=false`.
2. `ChatScreen` subscribe `RealtimeService.roomTypingStream(roomId)`.
3. Khi nhận event từ peer:
   - bật text `“{tên} đang nhập...”`,
   - có timer auto-hide (~4s) để tránh bị kẹt trạng thái.

---

## 4) Trạng thái tin nhắn: đã gửi, đã xem

## Luồng read receipt backend
1. Khi đọc chat, client gọi `POST /api/v1/messages/read?room={roomId}`.
2. `MessageService.setReadStatus()` cập nhật `ChatRoomReadState.lastReadAt` của user hiện tại.
3. Backend push event `MessageReadEventDto(roomId, reader, readAt)` cho member còn lại qua `/user/queue/chat/{roomId}/read`.

## Cách frontend suy ra trạng thái
1. `ChatScreen` subscribe `roomReadStream(roomId)`, lưu map `reader -> readAt`.
2. Với message của mình, frontend gắn avatar “seen” của peer vào message mới nhất mà `sentOn <= readAt`.
3. Chỉ message của chính mình ở cuối chuỗi mới show text status:
   - có seen avatar -> **Đã xem**
   - chưa có -> **Đã gửi**

> Nghĩa là “Đã gửi/Đã xem” là trạng thái UI suy diễn từ read event + seenBy data, không phải cột status riêng như delivered/read trong DB.

---

## 5) Xóa tin nhắn cho riêng người dùng

## Hiện trạng code
- **Chưa thấy backend endpoint hoặc model cho “delete for me only”.**
- Endpoint đang có là `DELETE /api/v1/messages/{messageId}` -> `MessageService.deleteMessage()` -> `MessageChangesService.recallMessage()`.
- Hành vi hiện tại là **recall cho mọi người** (xóa nội dung + attachments, status `RECALLED`) và broadcast lại message đã recall cho tất cả member.

## Kết luận
- Tính năng “xóa tin nhắn chỉ cho bản thân” hiện **chưa được implement** trong code hiện tại.
- Tính năng hiện có: **“xóa/thu hồi cho tất cả”**.

---

## 6) Đồng bộ trạng thái tin nhắn giữa hai phía

Có 3 lớp đồng bộ chính:

1. **Nội dung message realtime**
   - Kênh `/user/queue/chat/{roomId}`.
   - Gửi/sửa/thu hồi đều phát lại message state mới cho toàn bộ member.

2. **Typing state realtime**
   - Kênh `/user/queue/chat/{roomId}/typing`.

3. **Read state realtime**
   - Kênh `/user/queue/chat/{roomId}/read`.

Ngoài realtime, frontend còn có sync chủ động:
- khi mở room / có message mới / resume app -> debounce gọi `setReadStatus()` để giữ read state nhất quán.

Kết quả:
- Hai phía thấy cùng nội dung chat,
- cùng trạng thái recall/edit,
- cùng read receipt theo thời gian gần realtime.

---

## 7) Dịch tin nhắn theo yêu cầu người dùng (AI)

## Luồng backend
1. Client gọi `POST /api/v1/messages/translate` với payload:
   - `text`, `targetLanguage`, `sourceLanguage`, `previousMessages` (optional context).
2. `MessageController.translateMessage()` -> `TranslationService.translate()`.
3. Backend gọi lớp AI client và trả `MessageTranslationDto` gồm:
   - `translatedText`,
   - `detectedSourceLanguage`,
   - `targetLanguage`.

## Luồng frontend
1. User long-press message -> chọn action “Dịch ...”.
2. `ChatProvider.translateMessage()` gọi `MessageService.translateMessage()`.
3. Trong lúc dịch: bubble hiện trạng thái loading “Đang dịch...”.
4. Xong: bubble hiển thị block “Bản dịch (ngôn ngữ)”.
5. Bản dịch được cache local (SharedPreferences) theo `roomId + user + messageId + targetLanguage`.

> Dịch là thao tác **theo yêu cầu người dùng** (on-demand), không tự động dịch toàn bộ message trừ khi bạn bật logic bổ sung.

---

## Tệp nguồn chính đã đối chiếu cho phần này

### Backend
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/controllers/MessageController.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/messages/MessageService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/messages/MessageChangesService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/services/messages/MessageCheckService.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/dtos/messages/MessageReadEventDto.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/dtos/messages/MessageTypingEventDto.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/dtos/messages/MessageReceiveDto.java`
- `chatapp/chatapp/src/main/java+kotlin/com/group4/chatapp/models/ChatMessage.java`

### Flutter
- `chatapp/chatapp-flutter/lib/screens/chat/chat_screen.dart`
- `chatapp/chatapp-flutter/lib/widgets/message_bubble.dart`
- `chatapp/chatapp-flutter/lib/providers/chat_provider.dart`
- `chatapp/chatapp-flutter/lib/services/message_service.dart`
- `chatapp/chatapp-flutter/lib/services/realtime_service.dart`
- `chatapp/chatapp-flutter/lib/models/message_receive_model.dart`
