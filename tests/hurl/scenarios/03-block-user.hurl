# ===== Check block status (not blocked) =====
GET {{host}}/api/v1/users/test_user_b/block-status/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.blockedByMe" == false

# ===== Block user =====
POST {{host}}/api/v1/users/test_user_b/block/
Authorization: Bearer {{access_a}}
HTTP 204

# ===== Check block status (blocked) =====
GET {{host}}/api/v1/users/test_user_b/block-status/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.blockedByMe" == true

# ===== List blocked users =====
GET {{host}}/api/v1/users/blocks/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count >= 1
jsonpath "$[0].username" == "test_user_b"

# ===== Unblock user =====
DELETE {{host}}/api/v1/users/test_user_b/block/
Authorization: Bearer {{access_a}}
HTTP 204

# ===== Verify unblocked =====
GET {{host}}/api/v1/users/test_user_b/block-status/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.blockedByMe" == false

# ===== Block nonexistent user =====
POST {{host}}/api/v1/users/nonexistent_user_zzz/block/
Authorization: Bearer {{access_a}}
HTTP 404
