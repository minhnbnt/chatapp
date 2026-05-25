# AI message features — requires OpenAI API key configured

# ===== Translate text =====
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

# ===== Summarize messages =====
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

# ===== Translate empty text =====
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
