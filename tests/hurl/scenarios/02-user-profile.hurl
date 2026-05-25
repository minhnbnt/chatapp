# Prerequisite: run 01-auth-user.hurl first to register and get tokens

# ===== Get my profile =====
GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.username" == "test_user_a"

# ===== Update profile with avatar =====
PUT {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
[MultipartFormData]
displayName: Test User A
avatar: file,fixtures/test-avatar.png; image/png
HTTP 200
[Asserts]
jsonpath "$.username" == "test_user_a"
jsonpath "$.displayName" == "Test User A"

# ===== Update profile - display name only =====
PUT {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
[MultipartFormData]
displayName: Updated Name A
HTTP 200
[Asserts]
jsonpath "$.displayName" == "Updated Name A"

# ===== Search user (found) =====
GET {{host}}/api/v1/users/search/?q=test_user_b&limit=10
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count >= 1
jsonpath "$[0].username" == "test_user_b"

# ===== Search user (not found) =====
GET {{host}}/api/v1/users/search/?q=zzz_nonexistent&limit=10
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# ===== Get presence =====
GET {{host}}/api/v1/users/test_user_b/presence/
Authorization: Bearer {{access_a}}
HTTP 200

# ===== Change password (correct old password) =====
POST {{host}}/api/v1/users/password/change/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "oldPassword": "password123",
  "newPassword": "newpassword123"
}
HTTP 200
[Asserts]
jsonpath "$.message" exists

# ===== Change password back =====
POST {{host}}/api/v1/users/password/change/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "oldPassword": "newpassword123",
  "newPassword": "password123"
}
HTTP 200

# ===== Change password (wrong old password) =====
POST {{host}}/api/v1/users/password/change/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "oldPassword": "wrongpassword",
  "newPassword": "newpassword123"
}
HTTP 400

# ===== Password reset request =====
POST {{host}}/api/v1/users/password/reset-request/
Content-Type: application/json
{
  "username": "test_user_a"
}
HTTP 200
[Asserts]
jsonpath "$.message" exists

# ===== No auth =====
GET {{host}}/api/v1/users/me/
HTTP 401

# ===== Wrong token =====
GET {{host}}/api/v1/users/me/
Authorization: Bearer invalid_token_here
HTTP 401
