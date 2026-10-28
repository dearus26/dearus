-- 대화 세션과 메시지 (DearUs_ERD.html 기준)
-- 세션을 만들면 PREPARING으로 시작하고, 시점 분석이 끝나면 READY(실패하면 FAILED)가 돼요.
-- 기록이 전혀 없으면(NONE) 분석 없이 analysis_id가 빈 채로 바로 READY예요.

CREATE TABLE chat_sessions (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 페르소나 주인. 지금은 항상 user_id와 같아요
    persona_owner_id uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 커플 모드의 같은 날 비교 세션이면 커플 ID. couples 테이블을 만들 때 외래 키를 더해요
    couple_id        uuid,
    -- 동의 철회로 분석이 지워져도 대화 기록은 남도록 지워질 때 비워요
    analysis_id      uuid         REFERENCES persona_analyses (id) ON DELETE SET NULL,
    anchor_date      date         NOT NULL,
    -- 14: 기준일 전후 2주(바로가기), 0: 그날 하루(캘린더에서 고른 날)
    window_days      smallint     NOT NULL,
    shortcut_key     varchar(20),
    status           varchar(20)  NOT NULL,
    failure_code     varchar(40),
    -- 세션을 만들 때의 기록 수준
    record_level     varchar(20)  NOT NULL,
    started_at       timestamptz  NOT NULL DEFAULT now(),
    ended_at         timestamptz,
    CONSTRAINT ck_chat_sessions_window CHECK (window_days IN (0, 14)),
    CONSTRAINT ck_chat_sessions_status CHECK (status IN ('PREPARING', 'READY', 'FAILED')),
    CONSTRAINT ck_chat_sessions_level CHECK (record_level IN ('NONE', 'LOW', 'ENOUGH', 'RICH'))
);

CREATE INDEX ix_chat_sessions_user ON chat_sessions (user_id, started_at DESC);

CREATE TABLE chat_messages (
    id         bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id uuid         NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    -- 세션 안에서 1부터 늘어나는 순서 (1번은 페르소나의 첫 인사)
    seq        int          NOT NULL,
    role       varchar(10)  NOT NULL,
    content    text         NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_messages_seq UNIQUE (session_id, seq),
    CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'PERSONA')),
    CONSTRAINT ck_chat_messages_seq CHECK (seq >= 1)
);

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE chat_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
