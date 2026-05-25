# ===== User_a sends friend request to user_b =====
POST {{host}}/api/v1/invitations/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "receiverUserName": "test_user_b"
}
HTTP 201

# ===== List invitations for user_b =====
GET {{host}}/api/v1/invitations/
Authorization: Bearer {{access_b}}
HTTP 200
[Captures]
invitation_id: jsonpath "$[0].id"
[Asserts]
jsonpath "$[0].status" == "PENDING"

# ===== Accept invitation =====
PATCH {{host}}/api/v1/invitations/{{invitation_id}}
Authorization: Bearer {{access_b}}
Content-Type: application/json
{
  "accept": true
}
HTTP 204
[Captures]
duo_room_id: jsonpath "$.newChatRoom.id"

# ===== Self-invite =====
POST {{host}}/api/v1/invitations/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "receiverUserName": "test_user_a"
}
HTTP 409

# ===== Invite nonexistent user =====
POST {{host}}/api/v1/invitations/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "receiverUserName": "nonexistent_user_zzz"
}
HTTP 404
