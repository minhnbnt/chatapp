# Get user IDs first (needed for group creation)

# ===== Get user_a ID =====
GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
HTTP 200
[Captures]
user_a_id: jsonpath "$.id"

# ===== Get user_b ID =====
GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_b}}
HTTP 200
[Captures]
user_b_id: jsonpath "$.id"

# ===== Get user_c ID =====
GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_c}}
HTTP 200
[Captures]
user_c_id: jsonpath "$.id"

# ===== Create group (a creates with b and c) =====
POST {{host}}/api/v1/chatrooms/groups/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "name": "Test Group",
  "memberIds": [{{user_b_id}}, {{user_c_id}}]
}
HTTP 201
[Captures]
group_id: jsonpath "$.chatRoom.id"
[Asserts]
jsonpath "$.chatRoom.name" == "Test Group"

# ===== Get group details =====
GET {{host}}/api/v1/chatrooms/{{group_id}}/groups/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.chatRoom.name" == "Test Group"

# ===== Update group name =====
PATCH {{host}}/api/v1/chatrooms/{{group_id}}/groups/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "name": "Updated Group Name"
}
HTTP 200
[Asserts]
jsonpath "$.chatRoom.name" == "Updated Group Name"

# ===== Non-member tries to get group =====
GET {{host}}/api/v1/chatrooms/99999/groups/
Authorization: Bearer {{access_a}}
HTTP 404

# ===== Remove member c =====
DELETE {{host}}/api/v1/chatrooms/{{group_id}}/groups/members/{{user_c_id}}/
Authorization: Bearer {{access_a}}
HTTP 204

# ===== User b leaves group =====
DELETE {{host}}/api/v1/chatrooms/{{group_id}}/groups/leave/
Authorization: Bearer {{access_b}}
HTTP 204

# ===== Dissolve group (creator a) =====
DELETE {{host}}/api/v1/chatrooms/{{group_id}}/groups/dissolve/
Authorization: Bearer {{access_a}}
HTTP 204
