-- 메신저(카톡) 업로드와 주 단위 요약 (DearUs_ERD.html 기준)
-- 사진과 가림 영역은 폰 안에서만 다루므로 서버에는 사진 · 글자를 담는 컬럼이 없어요.
-- 서버가 받은 글자는 요약이 끝날 때까지만 메모리에 두고, DB에는 개수와 암호화한 주 단위 요약만 남겨요.

CREATE TABLE messenger_uploads (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source              varchar(20)  NOT NULL,
    uploaded_at         timestamptz  NOT NULL DEFAULT now(),
    status              varchar(20)  NOT NULL,
    failure_code        varchar(40),
    photo_count         int          NOT NULL,
    period_start        date,
    period_end          date,
    masked_count        int          NOT NULL,
    user_masked_count   int          NOT NULL,
    server_masked_count int,
    expires_at          timestamptz  NOT NULL,
    CONSTRAINT ck_messenger_uploads_status CHECK (status IN ('PROCESSING', 'DONE', 'FAILED')),
    CONSTRAINT ck_messenger_uploads_counts CHECK (photo_count >= 0 AND masked_count >= 0 AND user_masked_count BETWEEN 0 AND masked_count)
);

CREATE INDEX ix_messenger_uploads_user ON messenger_uploads (user_id, uploaded_at DESC);

CREATE TABLE messenger_weekly_summaries (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    upload_id   uuid        NOT NULL REFERENCES messenger_uploads (id) ON DELETE CASCADE,
    week_start  date        NOT NULL,
    summary_enc bytea       NOT NULL,
    traits_enc  bytea       NOT NULL,
    sample_enc  bytea       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    -- 한 업로드 안에서 같은 주는 한 건이에요
    CONSTRAINT uq_messenger_weekly_upload_week UNIQUE (upload_id, week_start)
);

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE messenger_uploads          ENABLE ROW LEVEL SECURITY;
ALTER TABLE messenger_weekly_summaries ENABLE ROW LEVEL SECURITY;
