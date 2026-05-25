# Uses duo_room_id from 04-invitation.hurl (friendship room between a and b)

# ===== Send text message =====
POST {{host}}/api/v1/messages/?room={{duo_room_id}}
Authorization: Bearer {{access_a}}
Content-Type: multipart/form-data; boundary=boundary
```
--boundary
Content-Disposition: form-data; name="message"

Hello from user_a!
--boundary--
```
HTTP 201
[Captures]
msg_id: jsonpath "$.id"

# ===== Send message with file attachment =====
POST {{host}}/api/v1/messages/?room={{duo_room_id}}
Authorization: Bearer {{access_a}}
[MultipartFormData]
message: Message with image
attachments: file,fixtures/test-avatar.png; image/png
HTTP 201
[Captures]
msg_with_file_id: jsonpath "$.id"

# ===== Get messages page 1 =====
GET {{host}}/api/v1/messages/?room={{duo_room_id}}&page=1
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count >= 1

# ===== Get messages - not a member =====
GET {{host}}/api/v1/messages/?room=99999&page=1
Authorization: Bearer {{access_a}}
HTTP 403

# ===== Edit message =====
PUT {{host}}/api/v1/messages/{{msg_id}}
Authorization: Bearer {{access_a}}
Content-Type: multipart/form-data; boundary=boundary
```
--boundary
Content-Disposition: form-data; name="message"

Edited message
--boundary--
```
HTTP 204

# ===== Mark read =====
POST {{host}}/api/v1/messages/read?room={{duo_room_id}}
Authorization: Bearer {{access_b}}
HTTP 204

# ===== Recall message =====
DELETE {{host}}/api/v1/messages/{{msg_id}}
Authorization: Bearer {{access_a}}
HTTP 204
