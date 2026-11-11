-- 커플 모드 (DearUs_ERD.html 기준, 2026.11.7 확정 · 2026.11.11 설계)
-- 한 사람이 초대 코드를 만들면 couples(PENDING)와 couple_members(INVITER)가 생기고, 상대가 코드를 입력하면
-- couple_members(INVITEE)가 더해지고 CONNECTED가 돼요. 두 사람이 각자 동의하면(consented_at) 서로의 과거 페르소나와 대화할 수 있어요.
-- 철회 · 해제하면 이 커플의 대화 세션(메시지 · 리포트 포함)을 지우고 DISCONNECTED로 남겨요(멤버 행은 revoked_at을 채워요).
-- 아직 연결 전에 취소한 초대는 행째 지워요.

CREATE TABLE couples (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    invite_code     varchar(20)  NOT NULL,
    code_expires_at timestamptz  NOT NULL,
    status          varchar(20)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    connected_at    timestamptz,
    disconnected_at timestamptz,
    -- EXPIRED는 저장하지 않고 code_expires_at으로 계산해요
    CONSTRAINT ck_couples_status CHECK (status IN ('PENDING', 'CONNECTED', 'DISCONNECTED'))
);

-- 기다리는 초대끼리는 코드가 겹치지 않아요(끝난 커플의 예전 코드는 다시 나와도 돼요)
CREATE UNIQUE INDEX uq_couples_pending_code ON couples (invite_code) WHERE status = 'PENDING';

CREATE TABLE couple_members (
    couple_id    uuid         NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    user_id      uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         varchar(10)  NOT NULL,
    joined_at    timestamptz  NOT NULL DEFAULT now(),
    consented_at timestamptz,
    revoked_at   timestamptz,
    PRIMARY KEY (couple_id, user_id),
    CONSTRAINT ck_couple_members_role CHECK (role IN ('INVITER', 'INVITEE'))
);

-- 한 사람은 활성 커플(기다리는 초대 포함)을 하나만 가져요
CREATE UNIQUE INDEX uq_couple_members_active_user ON couple_members (user_id) WHERE revoked_at IS NULL;

-- V5에서 미뤄 둔 외래 키예요. 커플 행이 지워지면 그 커플의 대화도 함께 지워져요
ALTER TABLE chat_sessions
    ADD CONSTRAINT fk_chat_sessions_couple FOREIGN KEY (couple_id) REFERENCES couples (id) ON DELETE CASCADE;

-- 두 시점 비교가 같은 날의 커플 세션을 찾을 때 써요
CREATE INDEX ix_chat_sessions_couple_day ON chat_sessions (couple_id, anchor_date, window_days) WHERE couple_id IS NOT NULL;

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE couples        ENABLE ROW LEVEL SECURITY;
ALTER TABLE couple_members ENABLE ROW LEVEL SECURITY;
