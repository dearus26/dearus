-- 메신저 업로드를 이미지 · txt 방식으로 바꾸면서 글자 가림을 없앴어요(2026.11.8 결정).
-- 받은(또는 이미지에서 읽어낸) 내 말 원문을 업로드 행에 암호화해 1년 보관해요. 요약이 끝나 DONE이 될 때 채워요.
--   내용: [{"date":"2026-09-28","text":"..."}, ...] JSON을 CryptoService로 암호화한 값
--   지우기: 업로드 행과 함께 지워져요(직접 삭제 · 동의 철회 30일 뒤 · 회원 탈퇴 · 1년 만료)
-- 시점 분석이 고른 구간의 원문 일부를 페르소나 만들기에 함께 써요.
-- 가림 개수 컬럼 3개는 더 쓰지 않아서 지워요. photo_count는 이미지 장수로 남겨요(txt는 0).

ALTER TABLE messenger_uploads DROP CONSTRAINT ck_messenger_uploads_counts;

ALTER TABLE messenger_uploads
    DROP COLUMN masked_count,
    DROP COLUMN user_masked_count,
    DROP COLUMN server_masked_count,
    ADD COLUMN original_enc bytea;

ALTER TABLE messenger_uploads
    ADD CONSTRAINT ck_messenger_uploads_photo_count CHECK (photo_count BETWEEN 0 AND 100);
