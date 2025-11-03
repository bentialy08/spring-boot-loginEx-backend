#!/bin/bash

API_URL="http://localhost:8080"
TEST_USER_PREFIX="testuser"
TEST_PASS="password123"

UNIQUE_ID=$(date +%s)
TEST_USERNAME="${TEST_USER_PREFIX}${UNIQUE_ID}"
TEST_EMAIL="test${UNIQUE_ID}@example.com"

ACCESS_TOKEN=""
REFRESH_TOKEN=""
ACCESS_TOKEN_2=""
REFRESH_TOKEN_2=""
ACCESS_TOKEN_3=""
REFRESH_TOKEN_3=""
ACCESS_TOKEN_4=""
REFRESH_TOKEN_4=""
SESSION_ID=""

echo "=========================================="
echo "   Multi-Device Login & Session Test"
echo "=========================================="

echo -e "\n### 0. Health Check ###"
HEALTH=$(curl -s $API_URL/api/health)
echo $HEALTH | jq
if echo $HEALTH | jq -e '.status == "UP"' > /dev/null; then
    echo "✅ Health check passed"
else
    echo "🚨 Health check failed. Exiting."
    exit 1
fi

echo -e "\n### 1. Register User ###"
REGISTER_RESPONSE=$(curl -s -X POST $API_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "'"$TEST_USERNAME"'",
    "email": "'"$TEST_EMAIL"'",
    "password": "'"$TEST_PASS"'"
  }')

echo $REGISTER_RESPONSE | jq
if echo $REGISTER_RESPONSE | jq -e '.id' > /dev/null; then
    echo "✅ User registered successfully"
else
    echo "🚨 Registration failed. Exiting."
    exit 1
fi

echo -e "\n### 2. Login from Device 1 (Mac Chrome) ###"
LOGIN_RESPONSE=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" \
  -d '{
    "username": "'"$TEST_USERNAME"'",
    "password": "'"$TEST_PASS"'"
  }')

echo $LOGIN_RESPONSE | jq

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refreshToken')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" == "null" ]; then
    echo "🚨 Failed to get Access Token from Device 1. Exiting."
    exit 1
fi
echo "✅ Device 1 logged in successfully"

# ✅ Add 1 second delay to ensure different JWT timestamp
sleep 1

echo -e "\n### 3. Login from Device 2 (iPhone) ###"
LOGIN_RESPONSE_2=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1" \
  -d '{
    "username": "'"$TEST_USERNAME"'",
    "password": "'"$TEST_PASS"'"
  }')

echo $LOGIN_RESPONSE_2 | jq

ACCESS_TOKEN_2=$(echo $LOGIN_RESPONSE_2 | jq -r '.accessToken')
REFRESH_TOKEN_2=$(echo $LOGIN_RESPONSE_2 | jq -r '.refreshToken')

if [ -z "$ACCESS_TOKEN_2" ] || [ "$ACCESS_TOKEN_2" == "null" ]; then
    echo "🚨 Failed to get Access Token from Device 2. Exiting."
    exit 1
fi
echo "✅ Device 2 logged in successfully"

echo -e "\n### 4. Access Protected Endpoint from Device 1 ###"
ME_RESPONSE=$(curl -s $API_URL/api/users/me -H "Authorization: Bearer $ACCESS_TOKEN")
echo $ME_RESPONSE | jq
if echo $ME_RESPONSE | jq -e '.username' > /dev/null; then
    echo "✅ Device 1 can access protected endpoint"
else
    echo "🚨 Device 1 failed to access protected endpoint"
fi

echo -e "\n### 5. Access Protected Endpoint from Device 2 ###"
ME_RESPONSE_2=$(curl -s $API_URL/api/users/me -H "Authorization: Bearer $ACCESS_TOKEN_2")
echo $ME_RESPONSE_2 | jq
if echo $ME_RESPONSE_2 | jq -e '.username' > /dev/null; then
    echo "✅ Device 2 can access protected endpoint"
else
    echo "🚨 Device 2 failed to access protected endpoint"
fi

echo -e "\n### 6. Get Active Sessions from Device 1 ###"
SESSIONS_RESPONSE=$(curl -s $API_URL/api/auth/sessions -H "Authorization: Bearer $ACCESS_TOKEN")
echo $SESSIONS_RESPONSE | jq

DEVICE_COUNT=$(echo "$SESSIONS_RESPONSE" | jq -r '.activeDeviceCount')
if [ "$DEVICE_COUNT" == "2" ]; then
    echo "✅ Correct number of active sessions: $DEVICE_COUNT"
else
    echo "⚠️ Expected 2 active sessions, got: $DEVICE_COUNT"
fi

SESSION_ID=$(echo "$SESSIONS_RESPONSE" | jq -r '.sessions[0].id')
if [ -z "$SESSION_ID" ] || [ "$SESSION_ID" == "null" ]; then
    echo "🚨 Could not retrieve a Session ID."
else
    echo "✅ Session ID: $SESSION_ID"

    echo -e "\n### 7. Revoke Device 1 Session ###"
    REVOKE_RESPONSE=$(curl -s -X DELETE "$API_URL/api/auth/sessions/$SESSION_ID" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -w "\nHTTP_CODE:%{http_code}")

    HTTP_CODE=$(echo "$REVOKE_RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)
    if [ "$HTTP_CODE" == "204" ]; then
        echo "✅ Session revoked successfully"
    else
        echo "🚨 Failed to revoke session. HTTP Code: $HTTP_CODE"
    fi

    echo -e "\n### 8. Verify Active Sessions After Revoke ###"
    SESSIONS_AFTER=$(curl -s $API_URL/api/auth/sessions -H "Authorization: Bearer $ACCESS_TOKEN_2")
    echo $SESSIONS_AFTER | jq

    DEVICE_COUNT_AFTER=$(echo "$SESSIONS_AFTER" | jq -r '.activeDeviceCount')
    if [ "$DEVICE_COUNT_AFTER" == "1" ]; then
        echo "✅ Correct: Only 1 active session remaining"
    else
        echo "⚠️ Expected 1 active session, got: $DEVICE_COUNT_AFTER"
    fi
fi

echo -e "\n### 9. Test Refresh Token from Device 2 ###"
REFRESH_RESPONSE=$(curl -s -X POST $API_URL/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "'"$REFRESH_TOKEN_2"'"
  }')

echo $REFRESH_RESPONSE | jq

NEW_ACCESS_TOKEN=$(echo $REFRESH_RESPONSE | jq -r '.accessToken')
if [ -z "$NEW_ACCESS_TOKEN" ] || [ "$NEW_ACCESS_TOKEN" == "null" ]; then
    echo "🚨 Failed to refresh token"
else
    echo "✅ Token refreshed successfully"
    ACCESS_TOKEN_2=$NEW_ACCESS_TOKEN
fi

echo -e "\n### 10. Logout from Device 2 (Current Session) ###"
LOGOUT_RESPONSE=$(curl -s -X POST $API_URL/api/auth/logout \
  -H "Authorization: Bearer $ACCESS_TOKEN_2" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "'"$REFRESH_TOKEN_2"'"
  }' \
  -w "\nHTTP_CODE:%{http_code}")

HTTP_CODE=$(echo "$LOGOUT_RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)
if [ "$HTTP_CODE" == "204" ]; then
    echo "✅ Logged out successfully from Device 2"
else
    echo "🚨 Logout failed. HTTP Code: $HTTP_CODE"
fi

echo -e "\n### 11. Verify Device 2 Cannot Access After Logout ###"
ACCESS_RESPONSE=$(curl -s $API_URL/api/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN_2" \
  -w "\nHTTP_CODE:%{http_code}")

HTTP_CODE=$(echo "$ACCESS_RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)
if [ "$HTTP_CODE" == "401" ]; then
    echo "✅ Correct: Device 2 cannot access protected endpoint (401)"
else
    echo "🚨 Expected 401, got: $HTTP_CODE"
    echo "$ACCESS_RESPONSE" | grep -v "HTTP_CODE"
fi

# ✅ Add delay before next logins
sleep 2

echo -e "\n### 12. Login Again and Test Logout All Devices ###"
LOGIN_RESPONSE_3=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
  -d '{
    "username": "'"$TEST_USERNAME"'",
    "password": "'"$TEST_PASS"'"
  }')

ACCESS_TOKEN_3=$(echo $LOGIN_RESPONSE_3 | jq -r '.accessToken')
REFRESH_TOKEN_3=$(echo $LOGIN_RESPONSE_3 | jq -r '.refreshToken')
echo "✅ Logged in from Device 3 (Windows)"

# ✅ Add delay between logins
sleep 1

LOGIN_RESPONSE_4=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36" \
  -d '{
    "username": "'"$TEST_USERNAME"'",
    "password": "'"$TEST_PASS"'"
  }')

ACCESS_TOKEN_4=$(echo $LOGIN_RESPONSE_4 | jq -r '.accessToken')
REFRESH_TOKEN_4=$(echo $LOGIN_RESPONSE_4 | jq -r '.refreshToken')
echo "✅ Logged in from Device 4 (Linux)"

echo -e "\n### 13. Check Active Sessions ###"
SESSIONS_FINAL=$(curl -s $API_URL/api/auth/sessions -H "Authorization: Bearer $ACCESS_TOKEN_3")
echo $SESSIONS_FINAL | jq

FINAL_COUNT=$(echo "$SESSIONS_FINAL" | jq -r '.activeDeviceCount')
if [ -z "$FINAL_COUNT" ] || [ "$FINAL_COUNT" == "null" ]; then
    echo "🚨 Failed to get session count"
else
    echo "📱 Total active devices: $FINAL_COUNT"
fi

echo -e "\n### 14. Logout All Devices ###"
LOGOUT_ALL=$(curl -s -X POST $API_URL/api/auth/logout-all \
  -H "Authorization: Bearer $ACCESS_TOKEN_3" \
  -w "\nHTTP_CODE:%{http_code}")

HTTP_CODE=$(echo "$LOGOUT_ALL" | grep "HTTP_CODE" | cut -d':' -f2)
if [ "$HTTP_CODE" == "204" ]; then
    echo "✅ All devices logged out successfully"
else
    echo "🚨 Logout all failed. HTTP Code: $HTTP_CODE"
    echo "$LOGOUT_ALL" | grep -v "HTTP_CODE"
fi

# ✅ Add delay to let blacklist propagate
sleep 1

echo -e "\n### 15. Verify All Devices Cannot Access ###"
for i in 3 4; do
    TOKEN_VAR="ACCESS_TOKEN_$i"
    TOKEN="${!TOKEN_VAR}"

    ACCESS_CHECK=$(curl -s $API_URL/api/users/me \
      -H "Authorization: Bearer $TOKEN" \
      -w "\nHTTP_CODE:%{http_code}")

    HTTP_CODE=$(echo "$ACCESS_CHECK" | grep "HTTP_CODE" | cut -d':' -f2)
    if [ "$HTTP_CODE" == "401" ]; then
        echo "✅ Device $i cannot access (401)"
    else
        echo "🚨 Device $i can still access. HTTP Code: $HTTP_CODE"
    fi
done

echo -e "\n=========================================="
echo "           Test Summary"
echo "=========================================="
echo "✅ Multi-device login tested"
echo "✅ Session management tested"
echo "✅ Individual logout tested"
echo "✅ Logout all devices tested"
echo "✅ Token refresh tested"
echo "✅ Token blacklist tested"
echo "=========================================="