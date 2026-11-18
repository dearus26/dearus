-- 부적절한 답변 신고 (2026.11.11 결정, 2026.11.18 처리 방식 확정)
-- 사용자가 채팅에서 AI의 답변(PERSONA 메시지)을 신고하면 한 행이 생겨요. Dear Us 관리자가 확인하고(reviewed_at)
-- AI가 바르게 답하도록 프롬프트를 고쳐요. 신고한 사람에게 따로 답하지 않아요.
-- 신고는 대화와 함께 지워져요(메시지가 지워지면 ON DELETE CASCADE): 탈퇴는 바로, 동의 철회는 30일 뒤, 커플 해제 때.

CREATE TABLE message_reports (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id  bigint       NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      varchar(20)  NOT NULL,
    detail      text,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    -- 관리자가 확인한 시각. 확인하기 전에는 비어 있어요(Supabase SQL 편집기에서 채워요)
    reviewed_at timestamptz,
    -- 같은 답변은 한 사람이 한 번만 신고해요
    CONSTRAINT uq_message_reports_message_user UNIQUE (message_id, user_id),
    CONSTRAINT ck_message_reports_reason CHECK (reason IN ('HURTFUL', 'INACCURATE', 'HARMFUL', 'PRIVACY', 'OTHER')),
    CONSTRAINT ck_message_reports_detail CHECK (detail IS NULL OR char_length(detail) BETWEEN 1 AND 500)
);

-- 관리자가 아직 확인하지 않은 신고를 오래된 순으로 봐요
CREATE INDEX ix_message_reports_unreviewed ON message_reports (created_at) WHERE reviewed_at IS NULL;

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE message_reports ENABLE ROW LEVEL SECURITY;
