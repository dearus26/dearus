const fs = require('fs');
const path = require('path');
const cp = require('child_process');
const { S, flows, css } = require('./gen_storyboard.js');

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const OUT = 'C:/Users/Note/Desktop/dearus/storyboard_images';
const TMP = path.join(__dirname, 'render').replace(/\\/g, '/');
fs.mkdirSync(OUT + '/slides', { recursive: true });
fs.mkdirSync(OUT + '/screens', { recursive: true });
fs.mkdirSync(TMP, { recursive: true });

const FONTS = `<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@600;700&family=Noto+Sans+KR:wght@400;500;700&display=swap">`;

const slugs = ['login', 'home', 'diary', 'kakao_upload', 'analysis_done', 'pick_time', 'loading', 'chat', 'safety', 'report',
  'couple_connect', 'couple_consent', 'partner_pick', 'couple_report', 'revoke'];

// flatten steps
let no = 0;
const steps = [];
flows.forEach(f => f.steps.forEach(s => { no++; steps.push({ ...s, no, flow: f, slug: slugs[no - 1] }); }));

const slideCss = `
body{margin:0;background:var(--cream)}
.slide{width:1920px;height:1080px;padding:44px 84px 36px;position:relative;overflow:hidden;background:var(--cream)}
.sl-head{display:flex;align-items:center;gap:20px;height:100px}
.sl-head .fid{width:58px;height:58px;font-size:28px;flex:0 0 auto}
.sl-head h2{font-size:44px}.sl-head p{margin:2px 0 0;font-size:22px;color:var(--brown)}
.sl-meta{margin-left:auto;font-size:18px;color:var(--brown);text-align:right;line-height:1.5}
.sl-body{display:flex;justify-content:center;gap:64px;margin-top:14px}
.col{width:520px;position:relative}
.col.ar::after{content:"›";position:absolute;right:-41px;top:calc(54px + 914px*var(--s)/2 - 24px);font-size:48px;line-height:1;color:var(--gold);font-weight:700}
.col .sh{margin-bottom:14px}.col .sh h3{font-size:30px}.col .no{width:40px;height:40px;font-size:20px}
.col .phone-wrap{margin:0 auto}
.col .meta{font-size:20px;grid-template-columns:72px 1fr;margin-top:18px;gap:8px 10px}
.col .meta code{font-size:16.5px}
.callout{width:520px;align-self:flex-start;margin-top:54px;background:var(--panel);border:1px solid var(--beige);border-radius:28px;padding:34px 36px}
.callout h3{font-size:30px;margin-bottom:12px}
.callout ul{margin:0;padding-left:26px;font-size:23px;line-height:1.7}.callout li{margin:8px 0}
/* overview */
.ov{width:fit-content;margin:22px auto 0;display:flex;flex-direction:column;gap:16px}
.orow{display:flex;align-items:center}
.olabel{width:300px;padding-right:20px}
.olabel .fid{width:46px;height:46px;font-size:22px;margin-bottom:8px}
.olabel b{display:block;font-family:"Noto Serif KR",serif;font-size:26px}
.olabel small{display:block;font-size:15px;color:var(--brown);line-height:1.45;margin-top:4px}
.oi{display:flex;flex-direction:column;align-items:center;gap:8px;width:130px}
.oi .olab{display:flex;gap:6px;align-items:center;font-size:15px;font-weight:700;white-space:nowrap}
.oi .no{width:22px;height:22px;font-size:12px}
.oarrow{width:104px;text-align:center;color:var(--gold);font-size:40px;font-weight:700;line-height:1;margin-top:-28px}
`;

const callouts = {
  A: ['개인정보를 지키는 방식', ['분석 전에 별도 동의를 받아요', '내 발화만 쓰고, 상대 메시지 원문은 보관하지 않아요', '전화번호 · 계좌 · 주소는 자동으로 가려요', '원본 파일은 요약이 만들어지면 바로 삭제해요 (분석에 실패해도 삭제)', '요약은 암호화해서 보관하고, 1년 뒤 자동으로 지워요']],
  B: ['믿고 쓸 수 있게 하는 장치', ['그 무렵 기록이 적으면 정확도가 낮을 수 있다고 알려줘요', '같은 시점은 저장한 분석을 다시 써서 더 빨리 만나요', '힘든 신호가 보이면 전문 상담 기관을 안내해요', '하루 대화 시간을 안내해 과몰입을 막아요', '대화가 끝나면 3개 항목 리포트를 자동으로 만들어요']],
  C: ['커플 모드의 약속', ['두 사람이 모두 동의하기 전에는 상대 페르소나에 접근할 수 없어요', '동의는 언제든 철회할 수 있고, 철회하면 즉시 차단돼요', '메신저 속 상대 메시지는 원문을 보관하지 않아요', '앱은 대화의 계기만 주고, 이야기는 현실에서 이어가요']],
};

const page = (body, extraCss = '', bodyStyle = '') => `<!doctype html><html lang="ko"><head><meta charset="utf-8">${FONTS}<style>${css}${slideCss}${extraCss}</style></head><body ${bodyStyle}>${body}</body></html>`;

const slides = [];

// 1) overview
{
  const rows = flows.map(f => {
    const items = steps.filter(s => s.flow === f).map((s, i, arr) => `
      <div class="oi"><div class="phone-wrap">${S[s.k]}</div><div class="olab"><span class="no">${s.no}</span>${s.t}</div></div>${i < arr.length - 1 ? '<div class="oarrow">›</div>' : ''}`).join('');
    return `<div class="orow"><div class="olabel"><span class="fid">${f.id}</span><b>${f.title}</b><small>${f.sub}</small></div>${items}</div>`;
  }).join('');
  slides.push({ name: 'slide_01_overview', html: `<div class="slide" style="--s:.27">
    <div class="sl-head"><div><h2>전체 화면 흐름</h2><p>화면 15장을 기록하기 · 과거의 나와 대화하기 · 커플 모드의 세 흐름으로 나눴어요</p></div><div class="sl-meta">Dear Us 스토리보드 · iPhone 17 Pro<br>1 / 7</div></div>
    <div class="ov">${rows}</div></div>` });
}

// 2) detail slides: 3 + 2 per flow
let page_no = 1;
flows.forEach(f => {
  const fs_ = steps.filter(s => s.flow === f);
  const groups = [fs_.slice(0, 3), fs_.slice(3)];
  groups.forEach((g, gi) => {
    page_no++;
    const cols = g.map((s, i) => `
      <div class="col${i < g.length - 1 || (g.length === 2) ? ' ar' : ''}">
        <div class="sh"><span class="no">${s.no}</span><h3>${s.t}</h3></div>
        <div class="phone-wrap">${S[s.k]}</div>
        <dl class="meta"><dt>사용자</dt><dd>${s.who}</dd><dt>시스템</dt><dd>${s.sys}</dd><dt>연결</dt><dd>${s.link}</dd></dl>
      </div>`);
    let extra = '';
    if (g.length === 2) {
      const [ct, items] = callouts[f.id];
      extra = `<div class="callout"><h3>${ct}</h3><ul>${items.map(x => `<li>${x}</li>`).join('')}</ul></div>`;
      // the last screen should not have an arrow into the callout
      cols[1] = cols[1].replace(' ar"', '"');
    } else {
      cols[2] = cols[2].replace(' ar"', '"');
    }
    slides.push({ name: `slide_${String(page_no).padStart(2, '0')}_${f.id}${gi + 1}`, html: `<div class="slide" style="--s:.64">
      <div class="sl-head"><span class="fid">${f.id}</span><div><h2>${f.title}</h2><p>${f.sub}</p></div><div class="sl-meta">Dear Us 스토리보드 · iPhone 17 Pro<br>${page_no} / 7</div></div>
      <div class="sl-body">${cols.join('')}${extra}</div></div>` });
  });
});

// write + render
const png = (file) => {
  const b = fs.readFileSync(file);
  return b.readUInt32BE(16) + 'x' + b.readUInt32BE(20);
};
const render = (htmlPath, outPath, w, h, dsf, transparent) => {
  const args = ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-sandbox',
    `--force-device-scale-factor=${dsf}`, `--window-size=${w},${h}`, '--virtual-time-budget=12000',
    ...(transparent ? ['--default-background-color=00000000'] : []),
    `--screenshot=${outPath}`, 'file:///' + htmlPath];
  try {
    cp.execFileSync(CHROME, args, { stdio: 'pipe', timeout: 90000 });
  } catch (e) {
    console.log('render error', path.basename(outPath), String(e.message).slice(0, 200));
  }
  console.log(path.basename(outPath), fs.existsSync(outPath) ? png(outPath) : 'MISSING');
};

const which = process.argv[2] || 'all';
if (which === 'all' || which === 'slides') {
  slides.forEach(s => {
    const hp = `${TMP}/${s.name}.html`;
    fs.writeFileSync(hp, page(s.html, '', ''), 'utf8');
    render(hp, `${OUT}/slides/${s.name}.png`, 1920, 1080, 2, false);
  });
}
if (which === 'all' || which === 'screens') {
  steps.forEach(s => {
    const name = `screen_${String(s.no).padStart(2, '0')}_${s.slug}`;
    const hp = `${TMP}/${name}.html`;
    const body = `<div style="padding:40px;--s:1"><div class="phone-wrap" style="width:450px;height:914px">${S[s.k]}</div></div>`;
    fs.writeFileSync(hp, page(body, '.phone{box-shadow:0 10px 24px rgba(58,42,31,.28)!important}body{background:transparent!important}', 'style="background:transparent"'), 'utf8');
    render(hp, `${OUT}/screens/${name}.png`, 530, 994, 2, true);
  });
}
