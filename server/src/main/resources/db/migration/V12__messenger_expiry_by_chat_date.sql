-- 메신저 원문 · 요약의 보관 기간을 대화한 날 기준으로 바꿔요 (2026.11.28 결정)
-- 예전: 올린 날 + 1년. 이제: 대화한 마지막 날(period_end) + 1년 2주가 지난 자정(한국 시간), 다만 올린 날 + 1년보다 늦지 않게.
-- 과거의 나는 오늘부터 365일 전까지만 고를 수 있어서, 그보다 오래된 날의 말은 분석에 쓰이지 않는데 보관만 되고 있었어요.
-- 새 업로드는 서버가 요약을 끝낼 때(MessengerUpload.complete) 같은 규칙으로 정해요. 여기서는 이미 있는 행만 당겨요(늘리지 않아요).

UPDATE messenger_uploads
SET expires_at = LEAST(
        expires_at,
        ((period_end + INTERVAL '1 year 15 days')::timestamp) AT TIME ZONE 'Asia/Seoul')
WHERE period_end IS NOT NULL;

-- 요약은 자기 업로드와 같은 시각에 지워져요
UPDATE messenger_weekly_summaries s
SET expires_at = u.expires_at
FROM messenger_uploads u
WHERE s.upload_id = u.id
  AND s.expires_at > u.expires_at;
