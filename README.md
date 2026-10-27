# submanager-server

구독관리 앱의 API 서버입니다. Spring Boot 4.1 · Java 25 · PostgreSQL 17 · Flyway.

## 로컬 실행

```bash
docker compose up -d          # PostgreSQL 17 (DB submanager, submanager_test)
./gradlew bootRun             # 기본 프로필 local, Flyway가 스키마를 만든다
./gradlew test                # submanager_test DB에 마이그레이션 적용 후 엔티티 검증
```

JDK 25가 필요합니다. IntelliJ에서는 Project SDK를 25로 지정하면 됩니다.

## 패키지 구성

```
com.subin.submanager
├── auth          로그인 4종, JWT 발급·갱신 (user_identities, refresh_tokens)
├── user          내 정보, 알림 설정, 탈퇴 (users)
├── catalog       서비스 목록, 환율 (services, exchange_rates)
├── subscription  구독 CRUD, 결제일 계산 (subscriptions)
├── notification  알림 센터, FCM 토큰 (notifications, device_tokens)
├── scheduler     매시 리마인더, 매일 결제일 갱신·환율·탈퇴 삭제
└── global        설정(Security, JPA, 스케줄링), 공통 엔티티
```

각 기능 패키지는 `domain`(엔티티·리포지토리) 아래에 시작하고, API를 붙일 때 `api`(컨트롤러·DTO)와 `application`(서비스)을 추가합니다.

## 인증

세션 없이 JWT로만 인증합니다. 명세: [로그인·JWT 인증 명세 (COD-7)](https://claude.ai/artifact/2NYC5qdKKZBbQyqkCp61Fq).

- 액세스 토큰: HS256 JWT, 1시간. `Authorization: Bearer <토큰>` 헤더로 보냅니다. 필터(`JwtAuthenticationFilter`)는 DB를 읽지 않습니다.
- 리프레시 토큰: 32바이트 난수, 30일. DB에는 SHA-256 해시만 저장하고 `POST /auth/refresh`마다 새로 발급합니다(회전). 폐기된 토큰이 다시 들어오면 그 회원의 토큰을 전부 폐기합니다.
- 로그인 4종은 회원을 찾거나 만든 뒤 `AuthTokenService.issueTokens()`만 호출하면 됩니다.
- 에러는 모두 `{ "code", "message" }` 형식입니다. 코드 목록은 `AuthErrorCode`, `CommonErrorCode`.
- `JWT_SECRET`은 32자 이상이어야 하며, 로컬 프로필은 `application-local.yml`의 개발용 값을 씁니다.

## DB 스키마

마이그레이션은 `src/main/resources/db/migration`에 있고, JPA는 `ddl-auto=validate`로 스키마를 바꾸지 않습니다. 이미 적용된 파일은 고치지 말고 `V3__...sql`처럼 새 파일을 추가하세요.

- 시각은 `timestamptz`(UTC), 결제일은 한국 날짜 기준 `date`입니다.
- 열거형 값은 문자열로 저장하고 CHECK 제약으로 막습니다.
- `notifications`의 (구독, 결제일, 종류) 유일 제약이 중복 발송을 막습니다.
- 결제일이 31일인데 그 달에 없으면 말일로 당기고, 다음 달에는 첫 결제일 기준으로 다시 계산합니다(`BillingCycle`).
- `V2__seed_services.sql`은 서비스 이름만 넣습니다. 가격은 운영자가 확인 후 채웁니다.

## 운영 환경변수

EC2의 `/home/ec2-user/.env.prod`에서 주입합니다(`--spring.profiles.active=prod`).

| 변수 | 용도 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | RDS 접속 |
| `JWT_SECRET` | JWT 서명 키 |
| `FIREBASE_CREDENTIALS_BASE64` | FCM 서비스 계정 키 (base64) |
| `RESEND_API_KEY`, `MAIL_FROM` | 인증 메일 발송 (Resend SMTP) |
