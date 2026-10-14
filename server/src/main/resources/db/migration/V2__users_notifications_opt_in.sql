-- 서비스 이용 동의 화면의 "일기 작성 알림 받기"(선택)를 저장해요.
ALTER TABLE users ADD COLUMN notifications_opt_in boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN users.ai_consent_at IS
    '필수 동의 3개(이용약관 · 개인정보 수집/이용 · 일기/대화 AI 분석)를 한 번에 받은 시각. 철회 뒤 다시 동의하면 새로 기록해요';
COMMENT ON COLUMN users.ai_revoked_at IS
    'AI 분석 동의를 철회한 시각. 비어 있으면 동의가 유효해요';
