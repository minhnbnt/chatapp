# ===== Register user_a =====
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "password123"
}
HTTP 201

# ===== Register user_b =====
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_b",
  "password": "password456"
}
HTTP 201

# ===== Register user_c (for group tests) =====
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_c",
  "password": "password789"
}
HTTP 201

# ===== Register duplicate =====
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "password123"
}
HTTP 409

# ===== Login user_a =====
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

# ===== Login user_b =====
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_b",
  "password": "password456"
}
HTTP 200
[Captures]
access_b: jsonpath "$.access"
refresh_b: jsonpath "$.refresh"

# ===== Login user_c =====
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_c",
  "password": "password789"
}
HTTP 200
[Captures]
access_c: jsonpath "$.access"

# ===== Refresh token =====
POST {{host}}/api/v1/users/token/refresh/
Content-Type: application/json
{
  "refresh": "{{refresh_a}}"
}
HTTP 200
[Asserts]
jsonpath "$.access" exists

# ===== Login wrong password =====
POST {{host}}/api/v1/users/token/
Content-Type: application/json
{
  "username": "test_user_a",
  "password": "wrongpassword"
}
HTTP 401

# ===== Register missing password =====
POST {{host}}/api/v1/users/register/
Content-Type: application/json
{
  "username": "test_user_x"
}
HTTP 400
