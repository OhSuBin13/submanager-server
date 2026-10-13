-- 구독관리 MVP 초기 스키마
-- 시각은 모두 timestamptz(UTC 저장), 결제일 같은 날짜는 한국 날짜 기준 date.

-- 회원 ------------------------------------------------------------------------
CREATE TABLE users
(
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nickname                   VARCHAR(30)  NOT NULL,
    role                       VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    status                     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    default_remind_days_before INTEGER      NOT NULL DEFAULT 3,
    remind_time                TIME         NOT NULL DEFAULT '09:00',
    push_enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    suspended_at               TIMESTAMPTZ,
    suspension_reason          VARCHAR(255),
    withdrawn_at               TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('MEMBER', 'OPERATOR')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_users_remind_days CHECK (default_remind_days_before BETWEEN 0 AND 30),
    CONSTRAINT ck_users_withdrawn_at CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL))
);

-- 탈퇴 30일 경과 삭제 작업용
CREATE INDEX ix_users_withdrawn_at ON users (withdrawn_at) WHERE status = 'WITHDRAWN';

-- 로그인 수단. 로그인 수단마다 별도 계정이므로 회원당 1개.
-- provider_user_id: 소셜은 각 회사의 사용자 id, 이메일은 소문자로 정규화한 이메일 주소.
CREATE TABLE user_identities
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    email             VARCHAR(320),
    password_hash     VARCHAR(100),
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_identities_user UNIQUE (user_id),
    CONSTRAINT uk_user_identities_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_user_identities_provider CHECK (provider IN ('KAKAO', 'NAVER', 'GOOGLE', 'EMAIL')),
    CONSTRAINT ck_user_identities_password CHECK ((provider = 'EMAIL') = (password_hash IS NOT NULL))
);

-- 리프레시 토큰(원문 대신 SHA-256 해시 저장). 갱신 시 기존 행은 revoked_at 기록 후 새 행 발급.
CREATE TABLE refresh_tokens
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    device_id  VARCHAR(100),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- 서비스 카탈로그 ---------------------------------------------------------------
-- 운영자가 DB에서 관리, 회원은 읽기만.
CREATE TABLE services
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                  VARCHAR(100)  NOT NULL,
    category              VARCHAR(30)   NOT NULL,
    logo_url              VARCHAR(500),
    default_price         NUMERIC(12, 2),
    default_currency      VARCHAR(3),
    default_billing_cycle VARCHAR(10),
    display_order         INTEGER       NOT NULL DEFAULT 0,
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_services_name UNIQUE (name),
    CONSTRAINT ck_services_billing_cycle CHECK (default_billing_cycle IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_services_price CHECK (default_price >= 0)
);

-- 구독 --------------------------------------------------------------------------
CREATE TABLE subscriptions
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    service_id         BIGINT REFERENCES services (id) ON DELETE SET NULL, -- 직접 입력이면 NULL
    name               VARCHAR(100)   NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL DEFAULT 'KRW',
    billing_cycle      VARCHAR(10)    NOT NULL,
    first_billing_date DATE           NOT NULL,
    next_billing_date  DATE           NOT NULL,
    remind_days_before INTEGER,                                            -- NULL이면 users.default_remind_days_before
    memo               VARCHAR(500),
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscriptions_amount CHECK (amount >= 0),
    CONSTRAINT ck_subscriptions_billing_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('ACTIVE', 'CANCELED')),
    CONSTRAINT ck_subscriptions_remind_days CHECK (remind_days_before BETWEEN 0 AND 30),
    CONSTRAINT ck_subscriptions_next_billing CHECK (next_billing_date >= first_billing_date)
);

CREATE INDEX ix_subscriptions_user ON subscriptions (user_id, next_billing_date);
-- 매일 결제일 갱신·매시 리마인더 작업용
CREATE INDEX ix_subscriptions_next_billing ON subscriptions (next_billing_date) WHERE status = 'ACTIVE';

-- 알림 --------------------------------------------------------------------------
CREATE TABLE device_tokens
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fcm_token    VARCHAR(512) NOT NULL,
    platform     VARCHAR(20)  NOT NULL DEFAULT 'ANDROID',
    device_model VARCHAR(100),
    app_version  VARCHAR(30),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_device_tokens_token UNIQUE (fcm_token),
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX ix_device_tokens_user ON device_tokens (user_id);

-- 발송 기록 겸 알림 센터 목록.
-- (구독, 대상 결제일, 종류) 유일 제약으로 작업이 두 번 돌아도 같은 알림을 두 번 보내지 않는다.
CREATE TABLE notifications
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subscription_id BIGINT REFERENCES subscriptions (id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL,
    billing_date    DATE,
    title           VARCHAR(100) NOT NULL,
    body            VARCHAR(500) NOT NULL,
    send_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_notifications_sub_date_type UNIQUE (subscription_id, billing_date, type),
    CONSTRAINT ck_notifications_type CHECK (type IN ('BILLING_REMINDER', 'ANNOUNCEMENT')),
    CONSTRAINT ck_notifications_send_status CHECK (send_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notifications_reminder_target CHECK (
        type <> 'BILLING_REMINDER' OR (subscription_id IS NOT NULL AND billing_date IS NOT NULL))
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
-- 실패 건 재시도용
CREATE INDEX ix_notifications_retry ON notifications (created_at) WHERE send_status IN ('PENDING', 'FAILED');

-- 환율 --------------------------------------------------------------------------
-- 한국수출입은행 API 기준. JPY처럼 100단위로 고시되는 통화도 1단위 원화 금액으로 환산해 저장.
-- 주말·공휴일은 행이 없으며 조회 시 가장 최근 날짜 값을 쓴다.
CREATE TABLE exchange_rates
(
    currency     VARCHAR(3)     NOT NULL,
    rate_date    DATE           NOT NULL,
    krw_per_unit NUMERIC(18, 6) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (currency, rate_date),
    CONSTRAINT ck_exchange_rates_positive CHECK (krw_per_unit > 0)
);
