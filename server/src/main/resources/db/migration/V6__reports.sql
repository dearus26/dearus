-- 회고 리포트 (DearUs_ERD.html 기준)
-- 대화 세션(chat_sessions) 하나에 리포트 한 건이에요. "끝내기"를 누를 때 행이 만들어져요.
--   GENERATING: AI가 만드는 중 / READY: 완성 / FAILED: 실패(끝내기를 다시 부르면 재시도) / NONE: 내 말이 너무 적어서 만들지 않음
-- 리포트는 대화 메시지와 같이 암호화하지 않고 저장해요. 세션이 지워지면(동의 철회 30일 뒤 폐기, 회원 탈퇴) 함께 지워져요.

CREATE TABLE reports (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       uuid         NOT NULL UNIQUE REFERENCES chat_sessions (id) ON DELETE CASCADE,
    status           varchar(20)  NOT NULL,
    failure_code     varchar(40),
    -- 대화 시간(분). 끝낼 때 한 번 계산해 두어요
    duration_minutes int          NOT NULL DEFAULT 0,
    past_concern     text,
    present_change   text,
    one_line_summary varchar(100),
    -- 아래 둘은 커플 모드(같은 날 기억 비교)에서 써요. 개인 모드에서는 비어 있어요.
    memory_summary   text,
    gap_summary      varchar(200),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    -- 마지막으로 상태가 바뀐 시각. GENERATING이 오래 지속되면 실패로 보는 기준이에요.
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_status CHECK (status IN ('GENERATING', 'READY', 'FAILED', 'NONE')),
    CONSTRAINT ck_reports_duration CHECK (duration_minutes >= 0)
);

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
