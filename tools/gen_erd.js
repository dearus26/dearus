const fs = require('fs');

const W = 235, ROW = 20, HEAD = 30, PAD = 6;

// kind: rec (brown) | hub (gold) | couple (ink)
const T = [
  { id: 'diary_emotions', x: 30, y: 40, kind: 'rec', cols: [
    ['diary_id', 'uuid', 'PF'], ['emotion', 'varchar', 'PK'] ] },
  { id: 'diaries', x: 30, y: 290, kind: 'rec', cols: [
    ['id', 'uuid', 'PK'], ['user_id', 'uuid', 'FK'], ['diary_date', 'date', ''],
    ['content', 'text', ''], ['created_at', 'timestamptz', ''], ['updated_at', 'timestamptz', ''] ] },
  { id: 'kakao_weekly_summaries', x: 30, y: 590, kind: 'rec', cols: [
    ['id', 'uuid', 'PK'], ['upload_id', 'uuid', 'FK'], ['week_start', 'date', ''],
    ['summary_enc', 'bytea', 'ENC'], ['traits_enc', 'bytea', 'ENC'], ['sample_enc', 'bytea', 'ENC'],
    ['created_at', 'timestamptz', ''], ['expires_at', 'timestamptz', ''] ] },

  { id: 'couple_members', x: 300, y: 40, kind: 'couple', cols: [
    ['couple_id', 'uuid', 'PF'], ['user_id', 'uuid', 'PF'], ['role', 'varchar', ''],
    ['joined_at', 'timestamptz', ''], ['consented_at', 'timestamptz?', ''], ['revoked_at', 'timestamptz?', ''] ] },
  { id: 'users', x: 300, y: 290, kind: 'rec', cols: [
    ['id', 'uuid', 'PK'], ['provider', 'varchar', ''], ['provider_user_id', 'varchar', ''],
    ['nickname', 'varchar', ''], ['ai_consent_at', 'timestamptz?', ''], ['ai_revoked_at', 'timestamptz?', ''],
    ['created_at', 'timestamptz', ''] ] },
  { id: 'kakao_uploads', x: 300, y: 590, kind: 'rec', cols: [
    ['id', 'uuid', 'PK'], ['user_id', 'uuid', 'FK'], ['uploaded_at', 'timestamptz', ''],
    ['period_start', 'date', ''], ['period_end', 'date', ''], ['status', 'varchar', ''],
    ['expires_at', 'timestamptz', ''] ] },

  { id: 'couples', x: 570, y: 40, kind: 'couple', cols: [
    ['id', 'uuid', 'PK'], ['invite_code', 'varchar', ''], ['code_expires_at', 'timestamptz', ''],
    ['status', 'varchar', ''], ['created_at', 'timestamptz', ''],
    ['connected_at', 'timestamptz?', ''], ['disconnected_at', 'timestamptz?', ''] ] },
  { id: 'persona_analyses', x: 570, y: 290, kind: 'hub', cols: [
    ['id', 'uuid', 'PK'], ['user_id', 'uuid', 'FK'], ['anchor_date', 'date', ''],
    ['window_days', 'smallint', ''], ['analysis_enc', 'bytea', 'ENC'], ['source_digest', 'varchar(64)', ''],
    ['diary_count', 'int', ''], ['kakao_weeks', 'int', ''], ['model', 'varchar', ''],
    ['analyzed_at', 'timestamptz', ''] ] },

  { id: 'reports', x: 840, y: 40, kind: 'rec', cols: [
    ['id', 'uuid', 'PK'], ['session_id', 'uuid', 'FK'], ['past_concern', 'text', ''],
    ['present_change', 'text', ''], ['one_line_summary', 'varchar', ''], ['created_at', 'timestamptz', ''] ] },
  { id: 'chat_sessions', x: 840, y: 290, kind: 'rec', cols: [
    ['id', 'uuid', 'PK'], ['user_id', 'uuid', 'FK'], ['persona_owner_id', 'uuid', 'FK'],
    ['couple_id', 'uuid?', 'FK'], ['analysis_id', 'uuid?', 'FK'], ['anchor_date', 'date', ''],
    ['started_at', 'timestamptz', ''], ['ended_at', 'timestamptz?', ''] ] },
  { id: 'chat_messages', x: 840, y: 590, kind: 'rec', cols: [
    ['id', 'bigint', 'PK'], ['session_id', 'uuid', 'FK'], ['seq', 'int', ''],
    ['role', 'varchar', ''], ['content', 'text', ''], ['created_at', 'timestamptz', ''] ] },
];

const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function tableSvg(t) {
  const h = HEAD + t.cols.length * ROW + PAD;
  let s = `<g>\n`;
  s += `<rect class="tbl" x="${t.x}" y="${t.y}" width="${W}" height="${h}" rx="10"/>\n`;
  s += `<path class="th th-${t.kind}" d="M${t.x},${t.y + HEAD} V${t.y + 10} Q${t.x},${t.y} ${t.x + 10},${t.y} H${t.x + W - 10} Q${t.x + W},${t.y} ${t.x + W},${t.y + 10} V${t.y + HEAD} Z"/>\n`;
  s += `<text class="tname tname-${t.kind} mono" x="${t.x + 12}" y="${t.y + 20}">${t.id}</text>\n`;
  t.cols.forEach((c, i) => {
    const ry = t.y + HEAD + i * ROW;
    if (i > 0) s += `<line class="sep" x1="${t.x + 8}" y1="${ry}" x2="${t.x + W - 8}" y2="${ry}"/>\n`;
    const f = c[2];
    if (f) {
      const label = f === 'PF' ? 'PK·FK' : f;
      const bw = f === 'PF' ? 38 : f === 'ENC' ? 28 : 22;
      s += `<rect class="bdg bdg-${f}" x="${t.x + 8}" y="${ry + 4}" width="${bw}" height="12" rx="3"/>\n`;
      s += `<text class="btxt btxt-${f}" x="${t.x + 8 + bw / 2}" y="${ry + 13.2}">${label}</text>\n`;
    }
    const nx = t.x + 8 + (f === 'PF' ? 44 : f === 'ENC' ? 34 : 28);
    s += `<text class="cname mono" x="${nx}" y="${ry + 14.2}">${c[0]}</text>\n`;
    s += `<text class="ctype" x="${t.x + W - 10}" y="${ry + 14}">${esc(c[1])}</text>\n`;
  });
  s += `</g>\n`;
  return s;
}

// edges: path + cardinality labels
const E = [
  { d: 'M130,116 V290', l: [['N', 136, 130, 's'], ['1', 136, 284, 's']] },
  { d: 'M300,340 H265', l: [['1', 294, 334, 'e'], ['N', 271, 334, 's']] },
  { d: 'M360,466 V590', l: [['1', 366, 482, 's'], ['N', 366, 584, 's']] },
  { d: 'M300,650 H265', l: [['1', 294, 644, 'e'], ['N', 271, 644, 's']] },
  { d: 'M420,290 V196', l: [['1', 426, 284, 's'], ['N', 426, 212, 's']] },
  { d: 'M535,80 H570', l: [['N', 541, 74, 's'], ['1', 564, 74, 'e']] },
  { d: 'M535,340 H570', l: [['1', 541, 334, 's'], ['N', 564, 334, 'e']] },
  { d: 'M805,400 H840', dash: true, l: [['1', 811, 394, 's'], ['N', 834, 394, 'e']] },
  { d: 'M805,100 H822 V370 H840', dash: true, l: [['1', 811, 94, 's'], ['N', 834, 364, 'e']] },
  { d: 'M960,196 V290', dash: true, l: [['0..1', 966, 212, 's'], ['1', 966, 284, 's']] },
  { d: 'M1000,486 V590', l: [['1', 1006, 502, 's'], ['N', 1006, 584, 's']] },
  { d: 'M510,466 V546 H900 V487', l: [['1', 516, 482, 's'], ['N', 906, 502, 's']] },
  { d: 'M470,466 V562 H940 V487', l: [['1', 476, 482, 's'], ['N', 946, 502, 's']] },
];

function edgeSvg(e) {
  let s = `<path class="edge${e.dash ? ' dash' : ''}" d="${e.d}"/>\n`;
  e.l.forEach(([t, x, y, a]) => {
    s += `<text class="card" x="${x}" y="${y}" text-anchor="${a === 'e' ? 'end' : 'start'}">${t}</text>\n`;
  });
  return s;
}

const svg = `<svg viewBox="0 0 1110 810" role="img" aria-label="열한 개 테이블의 관계도. users를 중심으로 일기, 카톡 업로드, 페르소나 분석, 대화, 커플 테이블이 연결되고, 카톡 원본을 담는 테이블은 없다">
${T.map(tableSvg).join('')}
${E.map(edgeSvg).join('')}
<text class="lane" x="560" y="541">chat_sessions.user_id — 대화하는 사람</text>
<text class="lane" x="560" y="577">chat_sessions.persona_owner_id — 페르소나 주인 (개인 모드는 user_id와 같음)</text>
</svg>`;

const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dear Us ERD</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@600;700&family=Noto+Sans+KR:wght@400;500;700&display=swap">
<style>
  :root {
    --cream: #FEF7EE; --beige: #CDB69C; --gold: #D9A869; --brown: #6F5139; --ink: #3A2A1F;
    --paper: #FFFCF7; --panel: #F7EDE0;
  }
  * { box-sizing: border-box; }
  html { color-scheme: light; }
  body { margin: 0; background: var(--cream); color: var(--ink);
    font-family: "Noto Sans KR", "Pretendard", "Malgun Gothic", system-ui, sans-serif; line-height: 1.6; }
  main { max-width: 1120px; margin: 0 auto; padding: 40px 16px 72px; }
  h1, h2 { font-family: "Noto Serif KR", "Nanum Myeongjo", serif; margin: 0; }
  h1 { font-size: 28px; }
  h1 small { font-family: "Noto Sans KR", sans-serif; font-size: 13px; font-weight: 400; color: var(--brown); margin-left: 10px; }
  h2 { font-size: 20px; }
  h2 span { color: var(--gold); margin-right: 8px; }
  .lede { margin: 6px 0 0; color: var(--brown); font-size: 14px; }
  .lede a { color: var(--brown); }
  section { margin-top: 40px; border-top: 1px solid var(--beige); padding-top: 22px; }
  figure { margin: 16px 0 0; }
  .scroll { overflow-x: auto; }
  .scroll svg { display: block; width: 100%; min-width: 900px; height: auto; color: var(--brown); }
  figcaption { margin-top: 10px; font-size: 13.5px; color: var(--brown); max-width: 80ch; }
  figcaption b { color: var(--ink); }
  .legend { display: flex; flex-wrap: wrap; gap: 6px 18px; margin: 12px 0 0; font-size: 12.5px; color: var(--brown); }
  .legend i { display: inline-block; width: 14px; height: 14px; border-radius: 4px; vertical-align: -2px; margin-right: 6px; }
  .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px; margin-top: 16px; }
  .card-box { background: var(--panel); border: 1px solid var(--beige); border-radius: 12px; padding: 14px 18px; font-size: 13.5px; }
  .card-box h3 { margin: 0 0 6px; font-size: 14.5px; }
  .card-box p { margin: 4px 0; }
  .card-box ul { margin: 6px 0 0; padding-left: 18px; }
  .card-box li { margin: 3px 0; }
  code { font-family: ui-monospace, "Cascadia Mono", Consolas, monospace; font-size: 12px; background: rgba(205,182,156,.28); padding: 1px 5px; border-radius: 4px; }
  .notes { margin: 16px 0 0; padding: 14px 18px 14px 34px; background: var(--panel); border: 1px solid var(--beige); border-radius: 12px; font-size: 13.5px; }
  .notes li { margin: 3px 0; }

  /* svg */
  .mono { font-family: ui-monospace, "Cascadia Mono", Consolas, monospace; }
  .tbl { fill: var(--paper); stroke: var(--beige); stroke-width: 1.4; }
  .th-rec { fill: var(--brown); }
  .th-hub { fill: var(--gold); }
  .th-couple { fill: var(--ink); }
  .tname { font-size: 13px; font-weight: 700; }
  .tname-rec, .tname-couple { fill: var(--cream); }
  .tname-hub { fill: var(--ink); }
  .cname { font-size: 12px; fill: var(--ink); }
  .ctype { font-size: 10.5px; fill: var(--brown); text-anchor: end; }
  .sep { stroke: var(--beige); stroke-width: 1; opacity: .55; }
  .bdg-PK, .bdg-PF { fill: var(--gold); }
  .bdg-FK { fill: var(--beige); }
  .bdg-ENC { fill: var(--ink); }
  .btxt { font-size: 8.5px; font-weight: 700; text-anchor: middle; fill: var(--ink); }
  .btxt-ENC { fill: var(--cream); }
  .edge { stroke: currentColor; stroke-width: 1.6; fill: none; }
  .edge.dash { stroke-dasharray: 5 4; }
  .card { font-size: 11px; font-weight: 700; fill: var(--ink); }
  .lane { font-size: 11px; fill: var(--brown); }
</style>
</head>
<body>
<main>
  <h1>Dear Us ERD<small>PostgreSQL 17 · 테이블 11개 · 기획서와 <a href="DearUs_기능블록도.html" style="color:#6F5139">기능 블록도</a> 기준 초안</small></h1>
  <p class="lede">일기, 카톡 요약, 페르소나 분석(재사용 저장), 대화, 커플 접근 제어가 어떻게 연결되는지 한 장에 모았습니다. 카톡 원본을 담는 테이블이 없다는 점이 이 설계의 핵심입니다.</p>

  <section>
    <h2><span>1</span>관계도</h2>
    <figure>
      <div class="scroll">
${svg}
      </div>
      <div class="legend">
        <span><i style="background:#6F5139"></i>일기 · 카톡 · 대화</span>
        <span><i style="background:#D9A869"></i>페르소나 분석 (재사용 저장소)</span>
        <span><i style="background:#3A2A1F"></i>커플 접근 제어</span>
        <span><b>PK·FK</b> 기본키이면서 외래키</span>
        <span><b>ENC</b> 암호화 저장 컬럼</span>
        <span><b>?</b> 비어 있을 수 있음</span>
        <span><b>점선</b> 선택적 관계</span>
      </div>
      <figcaption><b>한 줄 요약:</b> <code>users</code>가 중심이고, 대화(<code>chat_sessions</code>)는 “누가 말하는가”와 “누구의 페르소나인가”를 따로 가리킵니다. 개인 모드는 두 값이 같고 커플 모드는 다릅니다(<code>couple_id</code>가 채워짐). 금색 <code>persona_analyses</code>는 말투 분석 결과를 저장해 두는 곳이라, 같은 시점의 대화는 Gemini를 다시 부르지 않고 이 행을 재사용합니다.</figcaption>
    </figure>
  </section>

  <section>
    <h2><span>2</span>핵심 규칙</h2>
    <div class="cards">
      <div class="card-box">
        <h3>분석 결과 재사용</h3>
        <p><code>persona_analyses</code>의 유일 키는 <code>(user_id, anchor_date, window_days)</code>입니다. <code>window_days</code>는 14(전후 2주)입니다.</p>
        <ul>
          <li>바로가기(1 · 6 · 12개월 전)는 <code>anchor_date</code>를 그 주 월요일로 맞춥니다. 직접 고른 날짜는 그대로 씁니다.</li>
          <li><code>source_digest</code> = 해시(구간 내 일기의 <code>id, updated_at</code> + 겹치는 <code>kakao_weekly_summaries</code>의 <code>id, created_at</code>)</li>
          <li>키와 digest가 모두 같으면 재사용, 다르면 다시 분석해 같은 행을 덮어씁니다. 일기를 고치거나 지우면, 또는 그 기간 카톡을 올리면 digest가 달라집니다.</li>
        </ul>
      </div>
      <div class="card-box">
        <h3>카톡 원본은 어디에도 없음</h3>
        <p><code>kakao_uploads</code>에는 파일이나 본문을 담는 컬럼이 없습니다. 주 단위 요약(<code>summary_enc</code>, <code>traits_enc</code>, <code>sample_enc</code>)만 남습니다.</p>
        <ul>
          <li>요약은 마스킹한 본인 발화에서 만든 것이고, 상대 메시지는 저장하지 않습니다.</li>
          <li><code>expires_at</code> = 업로드일 + 1년. 배치가 지난 행을 지우고, 업로드 행을 지우면 요약도 함께 지워집니다.</li>
          <li><code>sample_enc</code>의 대표 문장도 마스킹된 것입니다.</li>
        </ul>
      </div>
      <div class="card-box">
        <h3>커플 접근 규칙</h3>
        <p>상대의 <code>persona_analyses</code>를 볼 수 있는 조건은 다음 세 가지가 모두 참일 때입니다.</p>
        <ul>
          <li><code>couples.status = 'CONNECTED'</code></li>
          <li><code>couple_members</code> 두 행 모두 <code>consented_at</code>이 채워져 있음</li>
          <li>두 행 모두 <code>revoked_at</code>이 비어 있음</li>
        </ul>
        <p>한 사용자는 활성 커플을 하나만 가집니다. <code>couple_members(user_id)</code>에 <code>WHERE revoked_at IS NULL</code> 조건의 부분 유니크 인덱스를 둡니다.</p>
        <p>철회 · 해제 시 해당 <code>couple_id</code>의 <code>chat_sessions</code>(와 메시지 · 리포트)를 지우고 접근을 끊습니다.</p>
      </div>
      <div class="card-box">
        <h3>삭제와 보관</h3>
        <ul>
          <li>회원 탈퇴 시 <code>users</code> 행을 지우면 모든 테이블이 <code>ON DELETE CASCADE</code>로 즉시 지워집니다.</li>
          <li>예외로 <code>chat_sessions.analysis_id</code>는 <code>ON DELETE SET NULL</code>입니다. 분석 행이 재분석으로 바뀌어도 대화 기록은 남기 위해서입니다.</li>
          <li>암호화는 애플리케이션 레벨(AES-GCM 가정)이고, 키는 Render 환경변수에 둡니다. <code>*_enc</code> 컬럼만 해당합니다.</li>
          <li>컬럼 이름과 테이블 이름은 snake_case이고 코드 식별자 규칙(<code>dearus</code>)을 따릅니다.</li>
        </ul>
      </div>
      <div class="card-box">
        <h3>한 번에 보는 대화 흐름</h3>
        <ul>
          <li>시점 선택 → <code>persona_analyses</code> 조회 (키 + digest 확인)</li>
          <li>없거나 다르면 → 일기(<code>diaries</code>) + 겹치는 주 요약 읽기 → Gemini 분석 → 같은 행에 저장</li>
          <li><code>chat_sessions</code> 생성 (<code>analysis_id</code> 연결) → <code>chat_messages</code> 누적</li>
          <li>대화 종료 → <code>reports</code> 한 행 (세션당 하나)</li>
        </ul>
      </div>
    </div>
  </section>

  <section>
    <h2><span>3</span>가정한 부분</h2>
    <ul class="notes">
      <li><b>일기는 하루 한 건</b> — <code>diaries</code>에 <code>(user_id, diary_date)</code> 유일 제약을 두었습니다. 하루에 여러 번 쓸 수 있다면 제약을 빼야 하고, 캘린더 작성 표시는 그대로 날짜 기준입니다.</li>
      <li><b>감정 태그는 여러 개</b> — 한 일기에 여러 태그를 붙일 수 있게 <code>diary_emotions</code>를 따로 뒀습니다. 태그는 마스터 테이블 없이 코드의 enum(<code>JOY, SAD, TIRED</code> 등)으로 관리합니다. 하나만 고르는 방식이면 <code>diaries.emotion</code> 컬럼으로 줄일 수 있습니다.</li>
      <li><b>일기 본문과 대화 내용의 암호화</b> — 기획서 7장은 요약 · 말투 특징만 암호화한다고 적었습니다. <code>diaries.content</code>와 <code>chat_messages.content</code>는 평문으로 두었습니다. 암호화하려면 컬럼을 <code>*_enc</code>로 바꿔야 합니다.</li>
      <li><b>커플 철회 시 본인 분석은 유지</b> — 철회하면 그 커플의 대화 · 리포트를 지우지만, 상대가 본 적이 있는 내 <code>persona_analyses</code> 행은 개인 모드에서도 쓰므로 남겨 둡니다. “관련 분석 데이터 파기”(기획서 6.3)를 더 넓게 해석한다면 규칙이 달라집니다.</li>
      <li><b>없는 것</b> — 위기 신호 감지 기록(기획서 5.6), 로그인 갱신 토큰 저장, 이용 시간 집계 전용 테이블은 넣지 않았습니다. 이용 시간은 <code>chat_sessions</code>의 시작 · 종료 시각으로 계산할 수 있습니다.</li>
      <li><b>커플 리포트 확장</b> — 기획서 9장의 “연인에게 물어보면 좋을 질문”은 아직 검토 중이라 <code>reports</code>에 컬럼을 두지 않았습니다.</li>
    </ul>
  </section>
</main>
</body>
</html>
`;

fs.writeFileSync('C:/Users/Note/Desktop/dearus/DearUs_ERD.html', html, 'utf8');
console.log('written', html.length, 'chars;', T.length, 'tables;', E.length, 'edges');
