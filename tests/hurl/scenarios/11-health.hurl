# ===== Firebase health check (no auth required) =====
GET {{host}}/api/v1/health/firebase/
HTTP 200
[Asserts]
jsonpath "$.service" == "firebase"
