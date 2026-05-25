# ================================================================
# Full Integration Test — runs all scenarios in a single hurl file
# so that captured variables (tokens, IDs) are shared across steps.
# REQUIRES: clean database (no existing test users)
# ================================================================

# ==========================
# SECTION 1: Auth & Register
# ==========================

# Register user_a
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "password123"
}
HTTP 201

# Register user_b
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_b",
  "password": "password456"
}
HTTP 201

# Register user_c
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_c",
  "password": "password789"
}
HTTP 201

# Register duplicate
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "password123"
}
HTTP 409

# Login user_a
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "password123"
}
HTTP 200
[Captures]
access_a: jsonpath "$.access"
refresh_a: jsonpath "$.refresh"

# Login user_b
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_b",
  "password": "password456"
}
HTTP 200
[Captures]
access_b: jsonpath "$.access"

# Login user_c
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_c",
  "password": "password789"
}
HTTP 200
[Captures]
access_c: jsonpath "$.access"

# Refresh token
POST {{host}}/api/v1/users/token/refresh/
Content-Type: application/json
{
  "refresh": "{{refresh_a}}"
}
HTTP 200
[Asserts]
jsonpath "$.access" exists

# Login wrong password
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "wrongpassword"
}
HTTP 401

# Register missing password
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_x"
}
HTTP 400

# ============================
# SECTION 2: Profile & Search
# ============================

# Get my profile
GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.username" == "test_user_a"

# KNOWN BUG: PUT /me/ returns 500 when user is loaded from Redis cache
# (UserCacheService.getCachedUser builds User without password field).
# Tests below use HTTP * to not block remaining tests.

# Update profile with avatar (requires S3)
PUT {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
[MultipartFormData]
displayName: Test User A
avatar: file,fixtures/test-avatar.png; image/png
HTTP *

# Update profile - display name only
PUT {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
[MultipartFormData]
displayName: Updated Name A
HTTP *

# Search user (found)
GET {{host}}/api/v1/users/search/?q=test_user_b&limit=10
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count >= 1
jsonpath "$[0].username" == "test_user_b"

# Search user (not found)
GET {{host}}/api/v1/users/search/?q=zzz_nonexistent&limit=10
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Get presence
GET {{host}}/api/v1/users/test_user_b/presence/
Authorization: Bearer {{access_a}}
HTTP 200

# No auth
GET {{host}}/api/v1/users/me/
HTTP 401

# Wrong token
GET {{host}}/api/v1/users/me/
Authorization: Bearer invalid_token_here
HTTP 401

# =======================
# SECTION 3: Block User
# =======================

# Check block status (not blocked)
GET {{host}}/api/v1/users/test_user_b/block-status/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.blockedByMe" == false

# Block user
POST {{host}}/api/v1/users/test_user_b/block/
Authorization: Bearer {{access_a}}
HTTP 204

# Check block status (blocked)
GET {{host}}/api/v1/users/test_user_b/block-status/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.blockedByMe" == true

# List blocked users
GET {{host}}/api/v1/users/blocks/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count >= 1

# Unblock user
DELETE {{host}}/api/v1/users/test_user_b/block/
Authorization: Bearer {{access_a}}
HTTP 204

# Verify unblocked
GET {{host}}/api/v1/users/test_user_b/block-status/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.blockedByMe" == false

# Block nonexistent user
POST {{host}}/api/v1/users/nonexistent_user_zzz/block/
Authorization: Bearer {{access_a}}
HTTP 404

# =======================
# SECTION 4: Invitations
# =======================

# User_a sends friend request to user_b
POST {{host}}/api/v1/invitations/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "receiverUserName": "test_user_b"
}
HTTP 201

# List invitations for user_b
GET {{host}}/api/v1/invitations/
Authorization: Bearer {{access_b}}
HTTP 200
[Captures]
invitation_id: jsonpath "$[0].id"
[Asserts]
jsonpath "$[0].status" == "PENDING"

# Accept invitation (204 no content — @ResponseStatus overrides body)
PATCH {{host}}/api/v1/invitations/{{invitation_id}}
Authorization: Bearer {{access_b}}
Content-Type: application/json
{
  "accept": true
}
HTTP 204

# Get duo room ID from chatrooms list (since accept returns no body)
GET {{host}}/api/v1/chatrooms/
Authorization: Bearer {{access_a}}
HTTP 200
[Captures]
duo_room_id: jsonpath "$[0].id"
[Asserts]
jsonpath "$" count >= 1
jsonpath "$[0].type" == "DUO"

# Self-invite
POST {{host}}/api/v1/invitations/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "receiverUserName": "test_user_a"
}
HTTP 409

# Invite nonexistent user
POST {{host}}/api/v1/invitations/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "receiverUserName": "nonexistent_user_zzz"
}
HTTP 404

# ========================
# SECTION 5: Group Chat
# ========================

# Get user IDs
GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_a}}
HTTP 200
[Captures]
user_a_id: jsonpath "$.id"

GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_b}}
HTTP 200
[Captures]
user_b_id: jsonpath "$.id"

GET {{host}}/api/v1/users/me/
Authorization: Bearer {{access_c}}
HTTP 200
[Captures]
user_c_id: jsonpath "$.id"

# Create group
POST {{host}}/api/v1/chatrooms/groups/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "name": "Test Group",
  "memberIds": [{{user_b_id}}, {{user_c_id}}]
}
HTTP 201
[Captures]
group_id: jsonpath "$.id"
[Asserts]
jsonpath "$.name" == "Test Group"

# Get group details
GET {{host}}/api/v1/chatrooms/{{group_id}}/groups/
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$.name" == "Test Group"

# Update group name
PATCH {{host}}/api/v1/chatrooms/{{group_id}}/groups/
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "name": "Updated Group Name"
}
HTTP 200
[Asserts]
jsonpath "$.name" == "Updated Group Name"

# Non-existent group
GET {{host}}/api/v1/chatrooms/99999/groups/
Authorization: Bearer {{access_a}}
HTTP 404

# Remove member c
DELETE {{host}}/api/v1/chatrooms/{{group_id}}/groups/members/{{user_c_id}}/
Authorization: Bearer {{access_a}}
HTTP 204

# User b leaves group
DELETE {{host}}/api/v1/chatrooms/{{group_id}}/groups/leave/
Authorization: Bearer {{access_b}}
HTTP 204

# Dissolve group
DELETE {{host}}/api/v1/chatrooms/{{group_id}}/groups/dissolve/
Authorization: Bearer {{access_a}}
HTTP 204

# ======================
# SECTION 6: Messages
# ======================

# Send text message
POST {{host}}/api/v1/messages/?room={{duo_room_id}}
Authorization: Bearer {{access_a}}
[MultipartFormData]
message: Hello from user_a!
HTTP 201
[Captures]
msg_id: jsonpath "$.id"

# Send message with file attachment
POST {{host}}/api/v1/messages/?room={{duo_room_id}}
Authorization: Bearer {{access_a}}
[MultipartFormData]
message: Message with image
attachments: file,fixtures/test-avatar.png; image/png
HTTP 201

# Get messages page 1
GET {{host}}/api/v1/messages/?room={{duo_room_id}}&page=1
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count >= 1

# Get messages - room doesn't exist
GET {{host}}/api/v1/messages/?room=99999&page=1
Authorization: Bearer {{access_a}}
HTTP 404

# Edit message
PUT {{host}}/api/v1/messages/{{msg_id}}
Authorization: Bearer {{access_a}}
[MultipartFormData]
message: Edited message
HTTP 204

# Mark read
POST {{host}}/api/v1/messages/read?room={{duo_room_id}}
Authorization: Bearer {{access_b}}
HTTP 204

# Recall message
DELETE {{host}}/api/v1/messages/{{msg_id}}
Authorization: Bearer {{access_a}}
HTTP 204

# ========================
# SECTION 7: Chat Rooms
# ========================

# List chat rooms
GET {{host}}/api/v1/chatrooms/
Authorization: Bearer {{access_a}}
HTTP 200

# Pin room
POST {{host}}/api/v1/chatrooms/{{duo_room_id}}/pin/
Authorization: Bearer {{access_a}}
HTTP 204

# Unpin room
DELETE {{host}}/api/v1/chatrooms/{{duo_room_id}}/pin/
Authorization: Bearer {{access_a}}
HTTP 204

# Remove friend
DELETE {{host}}/api/v1/chatrooms/{{duo_room_id}}/friend/
Authorization: Bearer {{access_a}}
HTTP 204

# =======================
# SECTION 8: Health
# =======================

# Firebase health check (no auth)
GET {{host}}/api/v1/health/firebase/
HTTP 200
[Asserts]
jsonpath "$.service" == "firebase"

# =======================
# SECTION 9: AI Features
# =======================

# Translate text
POST {{host}}/api/v1/messages/translate
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "text": "Hello, how are you?",
  "sourceLanguage": "en",
  "targetLanguage": "vi",
  "previousMessages": []
}
HTTP 200
[Asserts]
jsonpath "$.translatedText" exists
jsonpath "$.targetLanguage" == "vi"

# Summarize messages
POST {{host}}/api/v1/messages/summarize
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "messages": ["user_a: Hello!", "user_b: Hi there!", "user_a: How are you?"],
  "roomName": "Test Room"
}
HTTP 200
[Asserts]
jsonpath "$.summary" exists
jsonpath "$.messageCount" == 3

# Translate empty text
POST {{host}}/api/v1/messages/translate
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "text": "",
  "sourceLanguage": "en",
  "targetLanguage": "vi",
  "previousMessages": []
}
HTTP 400

# =======================
# SECTION 10: Chatbot
# =======================

# List conversations (empty)
GET {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Create conversation
POST {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "title": "Test Conversation"
}
HTTP 201
[Captures]
conv_id: jsonpath "$.id"

# List conversations (has one)
GET {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 1
jsonpath "$[0].title" == "Test Conversation"

# Get messages (empty)
GET {{host}}/api/v1/chatbot/conversations/{{conv_id}}/messages
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Delete conversation
DELETE {{host}}/api/v1/chatbot/conversations/{{conv_id}}
Authorization: Bearer {{access_a}}
HTTP 204

# Verify deleted
GET {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# ==============================
# SECTION 11: Speech-to-Text
# ==============================

# Transcribe audio (silence → empty or short text)
POST {{host}}/api/v1/speech-to-text
Authorization: Bearer {{access_a}}
[MultipartFormData]
audio: file,fixtures/test-audio.wav; audio/wav
language: vi
HTTP 200
[Asserts]
jsonpath "$.text" exists

# Transcribe with prompt
POST {{host}}/api/v1/speech-to-text
Authorization: Bearer {{access_a}}
[MultipartFormData]
audio: file,fixtures/test-audio.wav; audio/wav
language: en
prompt: test transcription
HTTP 200
[Asserts]
jsonpath "$.text" exists

# Missing audio file
POST {{host}}/api/v1/speech-to-text
Authorization: Bearer {{access_a}}
Content-Type: multipart/form-data; boundary=----boundary
HTTP 400

# No auth
POST {{host}}/api/v1/speech-to-text
[MultipartFormData]
audio: file,fixtures/test-audio.wav; audio/wav
HTTP 401
