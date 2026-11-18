-- 민감정보 동의와 생년월일 (2026.11.18 결정)
-- 민감정보(일기 · 대화 속 건강 · 정신 상태 등) 처리는 다른 동의와 따로 받는 필수 동의예요. 받은 시각을 남겨요.
-- 생년월일은 간편 로그인 뒤 처음 동의할 때 한 번만 받아 나이를 확인해요(만 14세 미만은 법정대리인 동의가 필요해요).
-- 이미 동의한 계정은 두 값이 비어 있어서, 앱이 동의 화면을 다시 보여주고 그때 채워져요(needsConsent).

ALTER TABLE users
    ADD COLUMN sensitive_consent_at timestamptz,
    ADD COLUMN birth_date           date,
    ADD CONSTRAINT ck_users_birth_date CHECK (birth_date IS NULL OR birth_date >= DATE '1900-01-01');
