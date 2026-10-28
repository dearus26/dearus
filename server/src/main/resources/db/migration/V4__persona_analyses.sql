-- 시점 분석 결과 (DearUs_ERD.html 기준)
-- 같은 시점(user_id, anchor_date, window_days)의 분석은 한 행이고, 기록이 바뀌어 다시 분석하면 같은 행을 덮어써요.
-- source_digest가 같으면 AI를 다시 부르지 않고 이 행을 재사용해요. 분석 내용(analysis_enc)은 암호화해서 저장해요.

CREATE TABLE persona_analyses (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    anchor_date     date         NOT NULL,
    -- 14: 기준일 전후 2주(바로가기), 0: 그날 하루(캘린더에서 고른 날)
    window_days     smallint     NOT NULL,
    analysis_enc    bytea        NOT NULL,
    source_digest   varchar(64)  NOT NULL,
    diary_count     int          NOT NULL,
    messenger_weeks int          NOT NULL,
    model           varchar(60)  NOT NULL,
    analyzed_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_persona_analyses_key UNIQUE (user_id, anchor_date, window_days),
    CONSTRAINT ck_persona_analyses_window CHECK (window_days IN (0, 14)),
    CONSTRAINT ck_persona_analyses_counts CHECK (diary_count >= 0 AND messenger_weeks >= 0)
);

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE persona_analyses ENABLE ROW LEVEL SECURITY;
