# Uses duo_room_id from 04-invitation.hurl

# ===== List chat rooms =====
GET {{host}}/api/v1/chatrooms/
Authorization: Bearer {{access_a}}
HTTP 200

# ===== Pin room =====
POST {{host}}/api/v1/chatrooms/{{duo_room_id}}/pin/
Authorization: Bearer {{access_a}}
HTTP 204

# ===== Unpin room =====
DELETE {{host}}/api/v1/chatrooms/{{duo_room_id}}/pin/
Authorization: Bearer {{access_a}}
HTTP 204

# ===== Remove friend =====
DELETE {{host}}/api/v1/chatrooms/{{duo_room_id}}/friend/
Authorization: Bearer {{access_a}}
HTTP 204
