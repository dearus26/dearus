-- 로그인과 일기에 필요한 테이블 (DearUs_ERD.html 기준)
-- 나머지 테이블(카톡 · 페르소나 · 대화 · 리포트 · 커플)은 단계마다 새 마이그레이션으로 추가해요.

CREATE TABLE users (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider         varchar(20)  NOT NULL,
    provider_user_id varchar(255) NOT NULL,
    nickname         varchar(50)  NOT NULL,
    ai_consent_at    timestamptz,
    ai_revoked_at    timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    -- 같은 소셜 계정으로 로그인하면 같은 사용자여야 해요 (ERD에는 없지만 로그인에 필요해요)
    CONSTRAINT uq_users_provider UNIQUE (provider, provider_user_id)
);

CREATE TABLE diaries (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    diary_date date        NOT NULL,
    content    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- 일기는 하루 한 건 (ERD 가정)
    CONSTRAINT uq_diaries_user_date UNIQUE (user_id, diary_date),
    CONSTRAINT ck_diaries_content_length CHECK (char_length(content) BETWEEN 1 AND 5000)
);

CREATE TABLE diary_emotions (
    diary_id uuid        NOT NULL REFERENCES diaries (id) ON DELETE CASCADE,
    emotion  varchar(20) NOT NULL,
    PRIMARY KEY (diary_id, emotion)
);

-- Supabase는 public 스키마의 테이블을 외부 REST API(Data API)에 기본으로 노출해요.
-- 이 앱은 DB에 Spring Boot 서버만 붙으므로, 행 수준 보안(RLS)을 켜고 정책은 만들지 않아서
-- 외부(anon · authenticated 키)에서는 아무것도 읽거나 쓸 수 없게 막아요.
-- 서버는 postgres 역할로 접속하고 이 역할은 RLS를 건너뛰어서 영향이 없어요.
-- 앞으로 만드는 모든 테이블에도 같은 줄을 넣어요.
ALTER TABLE users          ENABLE ROW LEVEL SECURITY;
ALTER TABLE diaries        ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary_emotions ENABLE ROW LEVEL SECURITY;
