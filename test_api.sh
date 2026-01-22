#!/bin/bash

# 권한 시스템 API 테스트 스크립트
# 사용법: ./test_api.sh [BASE_URL] [TOKEN]
# 예시: ./test_api.sh http://localhost:8080 "Bearer your_token_here"

BASE_URL=${1:-"http://localhost:8080"}
TOKEN=${2:-""}

if [ -z "$TOKEN" ]; then
    echo "❌ 토큰이 필요합니다."
    echo "사용법: ./test_api.sh [BASE_URL] [TOKEN]"
    echo "예시: ./test_api.sh http://localhost:8080 'Bearer eyJhbGc...'"
    exit 1
fi

echo "=========================================="
echo "권한 시스템 API 테스트"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo ""

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 테스트 함수
test_api() {
    local method=$1
    local endpoint=$2
    local data=$3
    local description=$4
    
    echo -e "${YELLOW}테스트: $description${NC}"
    echo "요청: $method $endpoint"
    
    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method \
            "$BASE_URL$endpoint" \
            -H "Authorization: $TOKEN" \
            -H "Content-Type: application/json")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method \
            "$BASE_URL$endpoint" \
            -H "Authorization: $TOKEN" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✓ 성공 (HTTP $http_code)${NC}"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    else
        echo -e "${RED}✗ 실패 (HTTP $http_code)${NC}"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    fi
    echo ""
}

# 1. 내 역할 조회 테스트
echo "=========================================="
echo "1. 수업별 역할 조회 테스트"
echo "=========================================="

# SECTION_ID를 환경변수로 받거나 기본값 사용
SECTION_ID=${SECTION_ID:-1}

test_api "GET" "/api/sections/$SECTION_ID/my-role" "" "내 역할 조회"

# 2. 관리자 목록 조회
test_api "GET" "/api/sections/$SECTION_ID/admins" "" "관리자 목록 조회"

# 3. 사용자별 수업 목록 조회
echo "=========================================="
echo "2. 사용자별 수업 목록 조회 테스트"
echo "=========================================="

test_api "GET" "/api/user/sections/roles" "" "모든 수업별 역할 목록"
test_api "GET" "/api/user/sections/enrolled" "" "수강 중인 수업 목록"
test_api "GET" "/api/user/sections/managing" "" "관리 중인 수업 목록"

# 4. 튜터 추가 테스트 (ADMIN 권한 필요)
echo "=========================================="
echo "3. 튜터 추가 테스트 (ADMIN 권한 필요)"
echo "=========================================="

# TUTOR_USER_ID를 환경변수로 받거나 기본값 사용
TUTOR_USER_ID=${TUTOR_USER_ID:-2}

test_api "POST" "/api/sections/$SECTION_ID/tutors" "{\"userId\": $TUTOR_USER_ID}" "튜터 추가"

# 5. 권한 체크 테스트 (과제 생성)
echo "=========================================="
echo "4. 권한 체크 테스트"
echo "=========================================="

# 과제 생성 시도 (관리자 권한 필요)
test_api "POST" "/api/assignments" "{
  \"sectionId\": $SECTION_ID,
  \"assignmentNumber\": 1,
  \"title\": \"테스트 과제\",
  \"description\": \"권한 테스트용 과제\",
  \"startDate\": \"2024-01-01T00:00:00\",
  \"endDate\": \"2024-12-31T23:59:59\"
}" "과제 생성 (권한 체크)"

echo "=========================================="
echo "테스트 완료"
echo "=========================================="
echo ""
echo "💡 팁:"
echo "  - SECTION_ID 환경변수로 테스트할 수업 ID 지정: SECTION_ID=1 ./test_api.sh ..."
echo "  - TUTOR_USER_ID 환경변수로 튜터로 추가할 사용자 ID 지정"
echo "  - jq가 설치되어 있으면 JSON 응답이 예쁘게 출력됩니다"

