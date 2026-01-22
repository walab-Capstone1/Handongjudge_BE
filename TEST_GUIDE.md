# 권한 시스템 변경 테스트 가이드

## 테스트 전 준비사항

1. **데이터베이스 백업**
   ```bash
   mysqldump -u [username] -p handongjudge > backup_before_migration.sql
   ```

2. **마이그레이션 스크립트 실행**
   ```bash
   mysql -u [username] -p handongjudge < migration_section_user_role.sql
   ```

3. **애플리케이션 재시작**
   - 백엔드 서버 재시작하여 새로운 엔티티 및 서비스 로드

---

## 1단계: 마이그레이션 데이터 검증

### 1.1 테이블 생성 확인
```sql
-- section_user_role 테이블이 생성되었는지 확인
SHOW TABLES LIKE 'section_user_role';

-- 테이블 구조 확인
DESCRIBE section_user_role;

-- creator_id 컬럼이 추가되었는지 확인
DESCRIBE sections;
```

### 1.2 데이터 마이그레이션 확인
```sql
-- 각 section에 ADMIN이 있는지 확인
SELECT 
    s.id as section_id,
    s.enrollment_code,
    COUNT(CASE WHEN sur.role = 'ADMIN' THEN 1 END) as admin_count,
    COUNT(CASE WHEN sur.role = 'TUTOR' THEN 1 END) as tutor_count,
    COUNT(CASE WHEN sur.role = 'STUDENT' THEN 1 END) as student_count
FROM sections s
LEFT JOIN section_user_role sur ON s.id = sur.section_id
GROUP BY s.id, s.enrollment_code
ORDER BY s.id;

-- creator_id가 설정되었는지 확인
SELECT 
    s.id,
    s.enrollment_code,
    s.creator_id,
    u.name as creator_name
FROM sections s
LEFT JOIN users u ON s.creator_id = u.user_id
WHERE s.creator_id IS NULL;  -- NULL이면 문제

-- Enrollment와 SectionUserRole의 일관성 확인
SELECT 
    e.section_id,
    e.user_id,
    e.roleInCourse as enrollment_role,
    sur.role as section_role
FROM enrollments e
LEFT JOIN section_user_role sur ON e.section_id = sur.section_id AND e.user_id = sur.user_id
ORDER BY e.section_id, e.user_id;
```

---

## 2단계: API 엔드포인트 테스트

### 2.1 인증 토큰 준비
먼저 로그인하여 JWT 토큰을 받아야 합니다.

```bash
# 로그인 (예시)
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password"
  }'

# 응답에서 accessToken 추출
TOKEN="your_access_token_here"
```

### 2.2 수업별 역할 조회 API 테스트

#### GET /api/sections/{sectionId}/my-role
```bash
# 특정 수업에서 내 역할 조회
curl -X GET "http://localhost:8080/api/sections/1/my-role" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"

# 예상 응답:
# {
#   "success": true,
#   "sectionId": 1,
#   "role": "ADMIN" | "TUTOR" | "STUDENT" | null
# }
```

#### GET /api/sections/{sectionId}/admins
```bash
# 수업의 관리자 목록 조회
curl -X GET "http://localhost:8080/api/sections/1/admins" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"

# 예상 응답:
# {
#   "success": true,
#   "sectionId": 1,
#   "admins": [
#     {
#       "userId": 1,
#       "name": "교수님",
#       "email": "prof@example.com"
#     }
#   ]
# }
```

#### POST /api/sections/{sectionId}/tutors
```bash
# 튜터 추가 (ADMIN 권한 필요)
curl -X POST "http://localhost:8080/api/sections/1/tutors" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2
  }'

# 예상 응답:
# {
#   "success": true,
#   "message": "튜터가 추가되었습니다."
# }
```

### 2.3 사용자별 수업 목록 API 테스트

#### GET /api/user/sections/roles
```bash
# 모든 수업별 역할 목록
curl -X GET "http://localhost:8080/api/user/sections/roles" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

#### GET /api/user/sections/enrolled
```bash
# 수강 중인 수업 목록 (STUDENT)
curl -X GET "http://localhost:8080/api/user/sections/enrolled" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

#### GET /api/user/sections/managing
```bash
# 관리 중인 수업 목록 (ADMIN/TUTOR)
curl -X GET "http://localhost:8080/api/user/sections/managing" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

---

## 3단계: 권한 체크 기능 테스트

### 3.1 수업 생성 시 ADMIN 역할 자동 부여

```bash
# 1. 수업 생성
curl -X POST "http://localhost:8080/api/sections" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "courseId": 1,
    "instructorId": 1,
    "sectionNumber": 1,
    "year": 2024,
    "semester": "SPRING"
  }'

# 2. 생성된 수업 ID 확인 (예: 10)
# 3. 내 역할 확인
curl -X GET "http://localhost:8080/api/sections/10/my-role" \
  -H "Authorization: Bearer $TOKEN"

# 예상: role이 "ADMIN"이어야 함
```

### 3.2 수강 신청 시 STUDENT 역할 자동 부여

```bash
# 1. 수업의 enrollment_code 확인
curl -X GET "http://localhost:8080/api/sections/1" \
  -H "Authorization: Bearer $TOKEN"

# 2. 수강 신청
curl -X POST "http://localhost:8080/api/sections/enroll/ABC12345" \
  -H "Authorization: Bearer $TOKEN"

# 3. 역할 확인
curl -X GET "http://localhost:8080/api/sections/1/my-role" \
  -H "Authorization: Bearer $TOKEN"

# 예상: role이 "STUDENT"이어야 함
```

### 3.3 권한 체크 테스트 시나리오

#### 시나리오 1: ADMIN 권한 체크
```bash
# ADMIN인 사용자로 과제 생성 시도
curl -X POST "http://localhost:8080/api/assignments" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sectionId": 1,
    "assignmentNumber": 1,
    "title": "테스트 과제",
    "description": "테스트",
    "startDate": "2024-01-01T00:00:00",
    "endDate": "2024-12-31T23:59:59"
  }'

# 예상: 성공 (201 Created)
```

#### 시나리오 2: STUDENT 권한 체크
```bash
# STUDENT인 사용자로 과제 생성 시도
curl -X POST "http://localhost:8080/api/assignments" \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sectionId": 1,
    "assignmentNumber": 1,
    "title": "테스트 과제",
    ...
  }'

# 예상: 실패 (400 Bad Request) - "해당 분반의 과제를 생성할 권한이 없습니다"
```

#### 시나리오 3: TUTOR 권한 체크
```bash
# 1. ADMIN이 TUTOR 추가
curl -X POST "http://localhost:8080/api/sections/1/tutors" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId": 3}'

# 2. TUTOR로 과제 생성 시도
curl -X POST "http://localhost:8080/api/assignments" \
  -H "Authorization: Bearer $TUTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...}'

# 예상: 성공 (TUTOR도 관리자 권한)
```

---

## 4단계: 데이터베이스 직접 확인

### 4.1 SectionUserRole 데이터 확인
```sql
-- 특정 사용자의 모든 수업별 역할
SELECT 
    sur.id,
    sur.section_id,
    s.enrollment_code,
    sur.user_id,
    u.name as user_name,
    sur.role,
    sur.created_at
FROM section_user_role sur
JOIN sections s ON sur.section_id = s.id
JOIN users u ON sur.user_id = u.user_id
WHERE sur.user_id = 1  -- 테스트할 사용자 ID
ORDER BY sur.section_id;

-- 특정 수업의 모든 역할
SELECT 
    sur.id,
    sur.section_id,
    sur.user_id,
    u.name as user_name,
    u.email,
    sur.role,
    sur.created_at
FROM section_user_role sur
JOIN users u ON sur.user_id = u.user_id
WHERE sur.section_id = 1  -- 테스트할 수업 ID
ORDER BY sur.role, sur.user_id;
```

### 4.2 creator_id 확인
```sql
-- 모든 section의 creator 확인
SELECT 
    s.id,
    s.enrollment_code,
    s.creator_id,
    u.name as creator_name,
    COUNT(sur.id) as total_roles,
    COUNT(CASE WHEN sur.role = 'ADMIN' THEN 1 END) as admin_count
FROM sections s
LEFT JOIN users u ON s.creator_id = u.user_id
LEFT JOIN section_user_role sur ON s.id = sur.section_id
GROUP BY s.id, s.enrollment_code, s.creator_id, u.name
ORDER BY s.id;
```

---

## 5단계: 통합 테스트 시나리오

### 시나리오 A: 수업 생성 → 튜터 추가 → 과제 생성

```bash
# 1. 수업 생성 (ADMIN 자동 부여)
SECTION_ID=$(curl -X POST "http://localhost:8080/api/sections" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...}' | jq -r '.id')

# 2. 튜터 추가
curl -X POST "http://localhost:8080/api/sections/$SECTION_ID/tutors" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId": 2}'

# 3. 튜터로 과제 생성
curl -X POST "http://localhost:8080/api/assignments" \
  -H "Authorization: Bearer $TUTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sectionId": '$SECTION_ID',
    ...
  }'
```

### 시나리오 B: 수강 신청 → 역할 확인 → 과제 조회

```bash
# 1. 수강 신청
curl -X POST "http://localhost:8080/api/sections/enroll/CODE123" \
  -H "Authorization: Bearer $STUDENT_TOKEN"

# 2. 역할 확인
curl -X GET "http://localhost:8080/api/sections/1/my-role" \
  -H "Authorization: Bearer $STUDENT_TOKEN"

# 3. 과제 목록 조회 (학생은 active만)
curl -X GET "http://localhost:8080/api/assignments?sectionId=1" \
  -H "Authorization: Bearer $STUDENT_TOKEN"
```

---

## 6단계: 에러 케이스 테스트

### 6.1 권한 없는 사용자의 작업 시도
```bash
# STUDENT가 과제 삭제 시도
curl -X DELETE "http://localhost:8080/api/sections/1/assignments/1" \
  -H "Authorization: Bearer $STUDENT_TOKEN"

# 예상: 400 Bad Request - "해당 과제를 삭제할 권한이 없습니다"
```

### 6.2 마지막 ADMIN 제거 시도
```bash
# 마지막 ADMIN을 제거하려고 시도
curl -X DELETE "http://localhost:8080/api/sections/1/users/1/role" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 예상: 400 Bad Request - "최소 1명의 ADMIN이 필요합니다"
```

### 6.3 존재하지 않는 수업의 역할 조회
```bash
curl -X GET "http://localhost:8080/api/sections/99999/my-role" \
  -H "Authorization: Bearer $TOKEN"

# 예상: 404 Not Found 또는 적절한 에러 메시지
```

---

## 7단계: 성능 확인

### 7.1 대량 데이터 테스트
```sql
-- 많은 수업과 사용자가 있을 때 쿼리 성능 확인
EXPLAIN SELECT * FROM section_user_role 
WHERE user_id = 1 AND role IN ('ADMIN', 'TUTOR');

-- 인덱스 사용 확인
SHOW INDEX FROM section_user_role;
```

---

## 체크리스트

- [ ] 마이그레이션 스크립트 실행 성공
- [ ] 모든 section에 최소 1명의 ADMIN 존재
- [ ] creator_id가 모든 section에 설정됨
- [ ] GET /api/sections/{id}/my-role 정상 동작
- [ ] GET /api/user/sections/roles 정상 동작
- [ ] POST /api/sections/{id}/tutors 정상 동작
- [ ] 수업 생성 시 ADMIN 자동 부여 확인
- [ ] 수강 신청 시 STUDENT 자동 부여 확인
- [ ] 권한 없는 사용자의 작업 차단 확인
- [ ] 기존 기능(과제 생성, 수정, 삭제 등) 정상 동작 확인

---

## 문제 발생 시 확인사항

1. **마이그레이션 실패**
   - SQL 에러 로그 확인
   - 외래 키 제약 조건 확인
   - 데이터 타입 불일치 확인

2. **권한 체크 실패**
   - SectionRoleService 로그 확인
   - 데이터베이스에 역할이 제대로 저장되었는지 확인
   - SUPER_ADMIN 처리 로직 확인

3. **API 에러**
   - 서버 로그 확인
   - 인증 토큰 유효성 확인
   - 요청 파라미터 확인

