# Chatbot conversations — requires OpenAI API key

# ===== List conversations (empty) =====
GET {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# ===== Create conversation =====
POST {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
Content-Type: application/json
{
  "title": "Test Conversation"
}
HTTP 201
[Captures]
conv_id: jsonpath "$.id"

# ===== List conversations (has one) =====
GET {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 1
jsonpath "$[0].title" == "Test Conversation"

# ===== Get messages (empty) =====
GET {{host}}/api/v1/chatbot/conversations/{{conv_id}}/messages
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# ===== Delete conversation =====
DELETE {{host}}/api/v1/chatbot/conversations/{{conv_id}}
Authorization: Bearer {{access_a}}
HTTP 204

# ===== Verify deleted =====
GET {{host}}/api/v1/chatbot/conversations
Authorization: Bearer {{access_a}}
HTTP 200
[Asserts]
jsonpath "$" count == 0
