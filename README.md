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
