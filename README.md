# H-codeLab Backend

## 목차

- [프로젝트 소개](#프로젝트-소개)
- [핵심 기능](#핵심-기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [개발 기간](#개발-기간)
- [배포 주소](#배포-주소)
- [개발자](#개발자)
- [시작 가이드](#시작-가이드)
- [API 문서](#api-문서)
- [API 모듈 개요](#api-모듈-개요)
- [기술 스택](#기술-스택)
- [디렉토리 구조](#디렉토리-구조)
- [빌드 및 배포](#빌드-및-배포)

# 프로젝트 소개

H-CodeLab의 **Backend API 서버**이다. Spring Boot 기반으로 수업·과제·코딩 테스트·커뮤니티 등 핵심 비즈니스 로직을 제공하며, **DOMjudge**와 연동해 코드 제출 및 자동 채점을 처리한다.

[Frontend Repository](https://github.com/walab-Capstone1/Handongjudge_FE)

## 핵심 기능

- **인증/인가**: JWT Access·Refresh Token, Google/GitHub OAuth2, 역할 기반 접근 제어
- **수업 운영**: Course, Section, 수강생 등록 및 분반별 권한 관리
- **문제/과제**: 문제 CRUD, 문제집, 과제 배포, 제출 현황·채점·성적 관리
- **코딩 테스트**: Quiz 생성·문제 구성·학생 진행 현황·성적 집계
- **자동 채점**: DOMjudge API 연동, 제출 결과 폴링·SSE 기반 테스트 출력
- **커뮤니티**: Q&A, 댓글, 추천, 알림
- **시스템 관리**: SuperAdmin API, 시스템 공지·가이드

## 시스템 아키텍처

<p align="center">
  <img src="./docs/HCL%20architecture.jpg" width="900" alt="H-CodeLab 시스템 아키텍처"/>
</p>

React Frontend → Nginx → Spring Boot API → MariaDB / Redis / DOMjudge(Judgehost) 구조로 구성되어 있으며, GitHub Actions + Jenkins를 통해 Blue/Green 배포를 수행한다.

## 개발 기간

- 2025.09 ~ 진행 중

## 배포 주소

- API: [`https://hcl.walab.info/api`](https://hcl.walab.info/api)
- 서비스: [`https://hcl.walab.info`](https://hcl.walab.info)

## 개발자

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/dnqudgml12">
        <img src="https://github.com/dnqudgml12.png" width="100" alt="우병희"/>
      </a>
      <br />
      <b>우병희</b>
      <br />
      Frontend, Backend
    </td>
    <td align="center">
      <a href="https://github.com/seowon1112">
        <img src="https://github.com/seowon1112.png" width="100" alt="곽서원"/>
      </a>
      <br />
      <b>곽서원</b>
      <br />
      Frontend, Backend
    </td>
    <td align="center">
      <a href="https://github.com/Diggydogg">
        <img src="https://github.com/Diggydogg.png" width="100" alt="윤동혁"/>
      </a>
      <br />
      <b>윤동혁</b>
      <br />
      Infra, Backend
    </td>
  </tr>
</table>

---

## 시작 가이드

### 사전 요구사항

- Java 11+
- MariaDB
- Redis (로컬 설치 또는 SSH 터널)
- DOMjudge 인스턴스 접근 권한
- (Frontend 연동 시) Node.js 18+

### 설치 및 실행

```bash
git clone https://github.com/walab-Capstone1/Handongjudge_BE.git
cd Handongjudge_BE
# .env 파일 설정 (아래 참고)
./gradlew bootRun
```

서버는 기본적으로 `http://localhost:8080` 에서 실행됩니다.

### Spring Profile

| Profile | 용도 | 설정 파일 |
|---------|------|-----------|
| `local` | 로컬 개발 | `application-local.yml` |
| `deploy` | 운영 배포 | `application-deploy.yml` |

로컬 개발 시 `.env`에 `spring.profiles.active=local` 을 설정합니다.

### 환경 변수

프로젝트 루트에 `.env` 파일을 생성합니다. **실제 값은 팀 내부에서 공유**하고, 저장소에는 커밋하지 않습니다.

```bash
# Database
DB_URL=jdbc:mariadb://localhost:3306/handongjudge
DB_USERNAME=your_username
DB_PASSWORD=your_password

# JWT
JWT_SECRET_KEY=your_jwt_secret_key

# OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret

# Frontend (OAuth redirect)
FRONTEND_URL=http://localhost:3000

# DOMjudge
DOMJUDGE_URL=https://your-domjudge-url
DOMJUDGE_ID=admin
DOMJUDGE_PW=your_password

# Redis
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=

# Profile
spring.profiles.active=local

# SSH Tunnel (로컬 Redis 접근 시, 선택)
SSH_TUNNEL_ENABLED=false
SSH_TUNNEL_HOST=
SSH_TUNNEL_USER=
SSH_TUNNEL_PASSWORD=
SSH_TUNNEL_PORT=22
SSH_TUNNEL_LOCAL_PORT=6379
SSH_REMOTE_REDIS_HOST=
SSH_REMOTE_REDIS_PORT=6379
```

`application.yml`에서 `.env` import 주석을 해제하면 Spring Boot가 `.env`를 자동으로 읽습니다.

```yaml
spring:
  config:
    import: "optional:file:.env[.properties]"
```

### Frontend 연동

Frontend `.env`에 아래를 설정합니다.

```bash
REACT_APP_API_URL=http://localhost:8080/api
```

`local` 프로파일에서는 `http://localhost:3000` CORS가 허용됩니다.

---

## API 문서

서버 실행 후 Swagger UI에서 API를 확인할 수 있습니다.

- 로컬: [`http://localhost:8080/swagger-ui/index.html`](http://localhost:8080/swagger-ui/index.html)
- 인증: JWT Bearer Token (`Authorization: Bearer {accessToken}`)

---

## API 모듈 개요

| 모듈 | Base Path | 설명 |
|------|-----------|------|
| Auth | `/api/auth` | 회원가입, 로그인, 토큰 갱신 |
| User | `/api/user` | 사용자 정보, 마이페이지 |
| Course | `/api/courses` | 수업 관리 |
| Section | `/api/sections` | 분반·수강생·역할 관리 |
| Assignment | `/api/sections/{sectionId}/assignments` | 과제 CRUD·배포 |
| Problem | `/api/problems` | 문제 CRUD·가져오기 |
| Problem Set | `/api/problem-sets` | 문제집 관리 |
| Quiz | `/api/sections/{sectionId}/quizzes` | 코딩 테스트 관리 |
| Submission | `/api/submissions` | 코드 제출·결과 조회 |
| Grade | `/api/sections/{sectionId}/assignments/{assignmentId}/grades` | 성적 관리 |
| Community | `/api/community/*` | Q&A, 댓글, 추천, 알림 |
| Notice | `/api/notices` | 수업 공지 |
| System | `/api/system-notices`, `/api/system-guides` | 시스템 공지·가이드 |
| Admin | `/api/admin`, `/api/admin/system-admin` | 관리자·SuperAdmin |

> 상세 요청/응답 스펙은 Swagger UI를 참고하세요.

---

# 기술 스택

### Environment
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white)
![Git](https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white)
![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white)

### Language / Framework
![Java](https://img.shields.io/badge/Java_11-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-2.7.18-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

### Security / Auth
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![OAuth2](https://img.shields.io/badge/OAuth2-4285F4?style=for-the-badge&logo=google&logoColor=white)

### Database / Cache
![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)

### Integration / Docs
![DOMjudge](https://img.shields.io/badge/DOMjudge-00599C?style=for-the-badge&logo=codeforces&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![WebFlux](https://img.shields.io/badge/WebFlux-6DB33F?style=for-the-badge&logo=spring&logoColor=white)

### Deploy
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

---

# 디렉토리 구조

```text
📁 Handongjudge_BE
├── 📁 src/main/java/com/project/handongjudge
│   ├── 📁 auth/           # JWT, OAuth2, Security
│   ├── 📁 user/           # 사용자, 수강생 관리
│   ├── 📁 course/         # 수업
│   ├── 📁 section/        # 분반, 역할, Contest
│   ├── 📁 assignment/     # 과제
│   ├── 📁 problem/        # 문제, 문제집, 파일 파싱
│   ├── 📁 quiz/           # 코딩 테스트
│   ├── 📁 submission/     # 제출, SSE, 채점 폴링
│   ├── 📁 domjudge/       # DOMjudge API 연동
│   ├── 📁 grade/          # 성적
│   ├── 📁 community/      # Q&A, 댓글, 알림
│   ├── 📁 notice/         # 공지, 시스템 가이드
│   ├── 📁 systemadmin/    # SuperAdmin
│   ├── 📁 mypage/         # 마이페이지
│   ├── 📁 progress/       # 코드 진행 저장
│   ├── 📁 config/         # Security, Swagger, CORS, Jackson
│   └── 📁 common/         # 예외 처리, 유틸
├── 📁 src/main/resources
│   ├── application.yml
│   ├── application-local.yml
│   └── application-deploy.yml
├── 🐳 Dockerfile-backend
├── ⚙️ .github/workflows/deploy-backend.yml
└── 📦 build.gradle
```

---

# 빌드 및 배포

### 빌드

```bash
./gradlew build
```

### 테스트

```bash
./gradlew test
```

### Docker

```bash
docker build -f Dockerfile-backend -t handongjudge-be .
```

### CI/CD

`deploy-hj`,`deploy` 브랜치 push 시 GitHub Actions가 서버에 SSH 접속 후 Docker Compose로 Backend를 재배포합니다.

---
