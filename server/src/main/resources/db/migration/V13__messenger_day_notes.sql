-- 메신저로 올린 날마다의 AI 추천 감정 · 한 줄 요약 (2026.11.28 결정)
-- 홈 캘린더가 일기뿐 아니라 메신저 올린 날도 보여주고, 그날의 감정을 AI가 추천해요.
-- 업로드를 요약하는 같은 AI 호출이 만들어서 AI 호출이 늘지 않아요.
-- 업로드에 딸려 있어서 업로드를 지우거나(사용자 · 1년 만료 · 동의 철회 30일 뒤 · 탈퇴) 함께 지워져요(ON DELETE CASCADE).
-- 감정은 쉼표로 이은 이름(JOY,TIRED)이고 없으면 빈 문자열이에요. 한 줄 요약은 내 말에서 나온 글이라 암호화해요.

CREATE TABLE messenger_day_notes (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    upload_id  uuid        NOT NULL REFERENCES messenger_uploads (id) ON DELETE CASCADE,
    day        date        NOT NULL,
    emotions   varchar(60) NOT NULL DEFAULT '',
    line_enc   bytea,
    created_at timestamptz NOT NULL DEFAULT now(),
    -- 한 업로드 안에서 같은 날은 한 건이에요
    CONSTRAINT uq_messenger_day_notes_upload_day UNIQUE (upload_id, day)
);

-- V1 마이그레이션의 설명처럼 새 테이블에는 행 수준 보안(RLS)을 켜고 정책은 만들지 않아요.
ALTER TABLE messenger_day_notes ENABLE ROW LEVEL SECURITY;
