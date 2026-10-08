const fs = require('fs');

/* ───────── icons ───────── */
const svg = (p, w = 22, extra = '') =>
  `<svg viewBox="0 0 24 24" width="${w}" height="${w}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ${extra}>${p}</svg>`;
const ic = {
  back: svg('<path d="M15 5l-7 7 7 7"/>', 22, 'stroke-width="2.4"'),
  next: svg('<path d="M9 5l7 7-7 7"/>', 16, 'stroke-width="2.4"'),
  home: svg('<path d="M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/>', 24),
  book: svg('<path d="M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"/><path d="M8 4v13M12 8h4"/>', 24),
  chat: svg('<path d="M4 5h16v11H9l-5 4z"/><path d="M8 10h8"/>', 24),
  heart: svg('<path d="M12 20s-7-4.6-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.4-7 10-7 10z"/>', 24),
  check: svg('<path d="M5 12.5l4.5 4.5L19 7.5"/>', 18, 'stroke-width="3"'),
  send: svg('<path d="M5 12h13M13 6l6 6-6 6"/>', 20, 'stroke-width="2.6"'),
  file: svg('<path d="M7 3h7l4 4v14H7z"/><path d="M14 3v4h4"/>', 22),
  lock: svg('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>', 18),
  copy: svg('<rect x="8" y="8" width="11" height="12" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h8"/>', 18),
  phone: svg('<path d="M6 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L16 13l5 2v4a2 2 0 0 1-2 2A15 15 0 0 1 4 6a2 2 0 0 1 2-2z"/>', 18),
  user: svg('<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>', 20),
};

const signal = `<svg width="18" height="12" viewBox="0 0 18 12"><rect x="0" y="8" width="3" height="4" rx="1" fill="currentColor"/><rect x="5" y="5.5" width="3" height="6.5" rx="1" fill="currentColor"/><rect x="10" y="3" width="3" height="9" rx="1" fill="currentColor"/><rect x="15" y="0" width="3" height="12" rx="1" fill="currentColor"/></svg>`;
const wifi = `<svg width="17" height="12" viewBox="0 0 17 12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M1.5 4.2a10 10 0 0 1 14 0"/><path d="M4.2 7a6 6 0 0 1 8.6 0"/><circle cx="8.5" cy="10" r="1" fill="currentColor" stroke="none"/></svg>`;
const battery = `<svg width="27" height="13" viewBox="0 0 27 13"><rect x="0.5" y="0.5" width="22" height="12" rx="3.5" fill="none" stroke="currentColor" opacity=".4"/><rect x="2" y="2" width="19" height="9" rx="2.2" fill="currentColor"/><rect x="24" y="4" width="2" height="5" rx="1" fill="currentColor" opacity=".4"/></svg>`;

/* ───────── phone chrome (iPhone 17 Pro: 402×874 pt screen) ───────── */
const phone = (inner, tabs = false) => `
<div class="phone-wrap"><div class="phone">
  <i class="sb-btn act"></i><i class="sb-btn vup"></i><i class="sb-btn vdn"></i><i class="sb-btn pwr"></i><i class="sb-btn cam"></i>
  <div class="bezel"><div class="scr">
    <div class="sb"><span>9:41</span><span class="sbr">${signal}${wifi}${battery}</span></div>
    <i class="island"></i>
    ${inner}
    <i class="home-ind"></i>
  </div></div>
</div></div>`;

const tabbar = (active) => {
  const items = [['홈', ic.home], ['기록', ic.book], ['과거의 나', ic.chat], ['커플', ic.heart]];
  return `<nav class="tabbar">${items.map(([l, i]) =>
    `<div class="tab${l === active ? ' on' : ''}"><span class="ti">${i}</span><span>${l}</span></div>`).join('')}</nav>`;
};

/* ───────── screens ───────── */
const S = {};

S.login = phone(`
<div class="body" style="padding:0 28px">
  <div style="margin-top:78px;text-align:center">
    <div class="orb"><i class="o1"></i><i class="o2"></i></div>
    <div class="serif brand">Dear Us</div>
    <div class="tag">과거의 나, 그리고 그때의 우리와<br>다시 대화하는 회고 일기</div>
  </div>
  <div style="margin-top:auto;padding-bottom:36px;display:flex;flex-direction:column;gap:12px">
    <div class="btn kakao">카카오로 시작하기</div>
    <div class="btn google">Google로 시작하기</div>
    <div class="btn apple">Apple로 계속하기</div>
    <p class="fine" style="text-align:center">계속하면 이용약관과 개인정보 처리방침에<br>동의하는 것으로 봅니다.</p>
  </div>
</div>`);

// calendar for October 2026 (Oct 1 = Thursday)
const calCells = (written, today) => {
  let h = '';
  for (let i = 0; i < 4; i++) h += '<div class="d empty"></div>';
  for (let d = 1; d <= 31; d++) {
    const cls = ['d'];
    if (d === today) cls.push('today');
    h += `<div class="${cls.join(' ')}"><span>${d}</span>${written.includes(d) ? '<b class="dot"></b>' : ''}</div>`;
  }
  return h;
};

S.home = phone(`
<div class="body tabs">
  <div class="navbar" style="height:50px">
    <div><div class="fine" style="margin:0">2026년 10월 6일 화요일</div><div class="serif h2" style="margin-top:2px">서연님, 오늘도 한 줄 남겨요</div></div>
    <div class="avatar">${ic.user}</div>
  </div>
  <div class="card" style="margin-top:14px;padding:14px 14px 10px">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
      <b class="serif" style="font-size:17px">2026년 10월</b>
      <span class="fine" style="margin:0"><span class="dot" style="display:inline-block;vertical-align:1px"></span> 일기 쓴 날</span>
    </div>
    <div class="cal wk"><div>일</div><div>월</div><div>화</div><div>수</div><div>목</div><div>금</div><div>토</div></div>
    <div class="cal">${calCells([1, 2, 4, 5], 6)}</div>
  </div>
  <div style="display:flex;justify-content:space-between;align-items:baseline;margin:16px 2px 8px">
    <b class="serif" style="font-size:17px">과거의 나와 대화하기</b><span class="fine" style="margin:0;color:var(--brown);font-weight:700">직접 고르기 ›</span>
  </div>
  <div style="display:flex;gap:10px">
    <div class="short"><b>1개월 전</b><span>9월 6일 무렵</span><em class="tg ok">기록 풍부</em></div>
    <div class="short"><b>6개월 전</b><span>4월 6일 무렵</span><em class="tg low">기록 부족</em></div>
    <div class="short"><b>1년 전</b><span>2025년 10월</span><em class="tg">카톡 6주치</em></div>
  </div>
  <div class="btn gold" style="margin-top:auto;margin-bottom:12px">오늘의 일기 쓰기</div>
</div>${tabbar('홈')}`);

S.diary = phone(`
<div class="body">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">일기 쓰기</span><span class="pill">저장</span></div>
  <div class="serif h2" style="margin-top:10px">2026년 10월 6일 화요일</div>
  <div class="fine" style="margin:12px 0 8px">오늘의 기분 (여러 개 고를 수 있어요)</div>
  <div class="chips"><span class="chip">기쁨</span><span class="chip">설렘</span><span class="chip on">평온</span><span class="chip on">피곤</span><span class="chip">슬픔</span><span class="chip">불안</span><span class="chip">화남</span></div>
  <div class="card" style="margin-top:14px;flex:1;max-height:300px;line-height:1.75;font-size:15.5px">
    오늘은 팀원들이랑 발표 준비를 했다. 스토리보드를 같이 보면서 이야기하는데 생각보다 재미있었다.<br><br>그래도 하루 종일 앉아 있었더니 많이 피곤하다. 내일은 일찍 자야지<span class="caret"></span>
    <div class="fine" style="position:absolute;right:34px;margin-top:12px">74자</div>
  </div>
  <div class="card tint" style="margin-top:14px;display:flex;gap:12px;align-items:center">
    <div style="flex:1"><b style="font-size:14.5px">쓰기 귀찮은 날엔?</b><div class="fine" style="margin:3px 0 0">카톡 대화를 올리면 대신 분석해 둘게요</div></div>
    <span class="pill line">카톡 올리기</span>
  </div>
</div>`);

S.upload = phone(`
<div class="body">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">카톡 대화 올리기</span><span style="width:22px"></span></div>
  <div class="card" style="margin-top:6px;font-size:14px;line-height:1.65">
    <b>이렇게 해주세요</b><br>① 카톡 대화방 → 메뉴 → 대화 내보내기(txt)<br>② 아래에서 파일을 선택<br>③ 분석 시작을 누르기
  </div>
  <div class="card" style="margin-top:12px;font-size:13.5px;line-height:1.6">
    <b style="font-size:14.5px">분석 전에 확인해 주세요</b>
    <ul class="ck"><li>내가 쓴 말만 사용해요. 상대 메시지는 저장하지 않아요</li><li>전화번호 · 계좌 · 주소는 자동으로 가려요</li><li>가린 내용만 AI(Gemini)로 보내고, 학습에는 쓰이지 않아요</li><li>원본 파일은 분석이 끝나면 바로 삭제돼요</li></ul>
    <div class="agree"><span class="box on">${ic.check}</span><span>위 내용을 이해했고 대화 분석에 동의해요 (필수)</span></div>
  </div>
  <div class="file"><span style="color:var(--brown)">${ic.file}</span><div style="flex:1"><b style="font-size:14px">KakaoTalk_2026-09.txt</b><div class="fine" style="margin:0">1.2MB · 8주 분량</div></div><span class="fine" style="margin:0">변경</span></div>
  <div class="btn gold" style="margin-top:auto;margin-bottom:12px">분석 시작</div>
</div>`);

S.done = phone(`
<div class="body" style="align-items:center">
  <div style="margin-top:70px" class="okmark">${ic.check.replace('width="18" height="18"', 'width="40" height="40"')}</div>
  <div class="serif h1" style="margin-top:18px;text-align:center">분석이 끝났어요</div>
  <div class="fine" style="text-align:center;margin-top:6px">2026년 8월 4일 ~ 9월 28일 · 8주 요약을 저장했어요</div>
  <div class="card" style="width:100%;margin-top:26px;font-size:14px">
    <div class="row"><span>사용한 내 발화</span><b>3,214개</b></div>
    <div class="row"><span>가려진 민감정보</span><b>47건</b></div>
    <div class="row"><span>상대 메시지</span><b>저장 안 함</b></div>
    <div class="row last"><span>원본 파일</span><b class="gone">방금 삭제됨</b></div>
  </div>
  <div class="fine" style="text-align:center;margin-top:14px;line-height:1.6">요약은 암호화해서 보관하고,<br>업로드 1년 뒤에 자동으로 지워져요.<br>캘린더에는 표시되지 않아요.</div>
  <div class="btn brown" style="width:100%;margin-top:auto;margin-bottom:12px">홈으로</div>
</div>`);

// weekly record density bars (oldest → newest), selected 13 weeks ago
const bars = (selIdx) => {
  let h = '';
  for (let i = 0; i < 52; i++) {
    const v = 6 + ((i * 37 + (i % 5) * 11) % 30);
    const on = i >= selIdx - 2 && i <= selIdx + 2;
    h += `<i class="${on ? 'on' : ''}" style="height:${v}px"></i>`;
  }
  return h;
};

S.pick = phone(`
<div class="body">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">과거의 나 만나기</span><span style="width:22px"></span></div>
  <div class="serif h1" style="margin-top:8px">언제의 나를<br>만나볼까요?</div>
  <div class="chips" style="margin-top:16px"><span class="chip">1개월 전</span><span class="chip">6개월 전</span><span class="chip">1년 전</span><span class="chip on">직접 고르기</span></div>
  <div class="card" style="margin-top:16px;padding:16px 16px 14px">
    <div style="text-align:center"><b class="serif" style="font-size:26px">3개월 전</b><div class="fine" style="margin:2px 0 0">2026년 7월 6일 무렵</div></div>
    <div class="bars">${bars(39)}</div>
    <div class="slider"><i class="trk"></i><i class="fill" style="left:75%"></i><i class="thumb" style="left:75%"></i></div>
    <div style="display:flex;justify-content:space-between" class="fine"><span style="margin:0">1년 전</span><span style="margin:0">오늘</span></div>
  </div>
  <div class="card tint" style="margin-top:12px;font-size:13.5px;line-height:1.6">
    <div style="display:flex;justify-content:space-between;align-items:center"><b>이 무렵의 기록</b><em class="tg ok" style="margin:0">충분해요</em></div>
    일기 9건 · 카톡 4주치<br><span class="fine" style="margin:0">6월 22일 ~ 7월 20일(전후 2주)의 기록으로 그때의 말투를 만들어요</span>
  </div>
  <div class="btn gold" style="margin-top:auto;margin-bottom:12px">만나러 가기</div>
</div>`);

S.loading = phone(`
<div class="body" style="align-items:center">
  <div class="rings" style="margin-top:96px"><i class="r3"></i><i class="r2"></i><i class="r1"></i><span class="serif">3개월<br>전</span></div>
  <div class="serif h1" style="margin-top:26px;text-align:center">3개월 전의 나를<br>불러오는 중이에요</div>
  <div class="steps">
    <div class="st done"><span class="sd">${ic.check}</span>6월 22일 ~ 7월 20일 기록 모으기</div>
    <div class="st done"><span class="sd">${ic.check}</span>민감정보 가리기</div>
    <div class="st now"><span class="sd"><i></i></span>말투와 대화 방식 분석하기</div>
    <div class="st"><span class="sd"></span>그때의 나 완성하기</div>
  </div>
  <div class="card tint" style="margin-top:auto;margin-bottom:14px;width:100%;font-size:13px;line-height:1.55;text-align:center">처음 한 번만 오래 걸려요.<br>같은 시점은 저장해 두었다가 바로 만나요.</div>
</div>`);

const chatBody = (extra = '') => `
<div class="chat-top">
  <div class="navbar" style="height:46px"><span style="color:var(--brown)">${ic.back}</span>
    <div style="text-align:center"><div class="t" style="line-height:1.2">3개월 전의 나</div><div class="fine" style="margin:0">2026년 7월 무렵 · 정확도 보통</div></div>
    <span class="pill line" style="padding:5px 12px">종료</span></div>
  <div class="usage">오늘 대화 12분 · 하루 30분 정도를 권장해요</div>
</div>
<div class="msgs">
  <div class="sep">7월 6일 무렵의 나</div>
  <div class="m p">어, 왔어? 요즘 학기 끝나가서 정신이 하나도 없어 ㅋㅋ</div>
  <div class="m u">응, 3개월 뒤의 너야. 그때 제일 신경 쓰이던 게 뭐였어?</div>
  <div class="m p">음… 솔직히 진로. 뭘 해야 할지 모르겠는데 티는 못 내겠더라</div>
  <div class="m u">지금은 어느 정도 방향 잡았어. 너무 걱정 마</div>
  <div class="m p typ"><i></i><i></i><i></i></div>
</div>
<div class="inputbar"><div class="field">과거의 나에게 말 걸기</div><span class="sendb">${ic.send}</span></div>
${extra}`;

S.chat = phone(`<div class="body chat" style="padding:0">${chatBody()}</div>`);

S.safety = phone(`<div class="body chat" style="padding:0">${chatBody()}</div>
<div class="scrim"></div>
<div class="sheet" style="padding-bottom:44px">
  <div class="grab"></div>
  <div class="heartmark">${ic.heart.replace('width="24" height="24"', 'width="28" height="28"')}</div>
  <div class="serif h1" style="text-align:center;font-size:23px;margin-top:10px">많이 힘들어 보여요</div>
  <div style="text-align:center;font-size:14px;line-height:1.65;margin-top:8px;color:var(--brown)">마음이 무겁게 느껴진다면 혼자 견디지 않아도 괜찮아요.<br>전문 상담 기관과 이야기해 보세요.</div>
  <div class="hot"><span style="color:var(--brown)">${ic.phone}</span><div style="flex:1"><b>자살예방 상담전화</b><div class="fine" style="margin:0">24시간</div></div><b class="serif" style="font-size:20px">109</b></div>
  <div class="hot"><span style="color:var(--brown)">${ic.phone}</span><div style="flex:1"><b>정신건강 위기상담전화</b><div class="fine" style="margin:0">24시간</div></div><b class="serif" style="font-size:20px">1577-0199</b></div>
  <div class="btn gold" style="margin-top:14px">전화 연결하기</div>
  <div class="btn line" style="margin-top:10px">대화 계속하기</div>
</div>`);

S.report = phone(`
<div class="body">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">회고 리포트</span><span style="width:22px"></span></div>
  <div class="fine" style="margin:4px 0 0">3개월 전의 나와 18분 대화했어요</div>
  <div class="serif h1" style="margin-top:4px">2026년 10월 6일</div>
  <div class="rcard"><span class="rl">당시 고민</span><p>학기 말에 진로가 정해지지 않아 막막했지만, 겉으로는 티를 내지 않았어요.</p></div>
  <div class="rcard"><span class="rl">현재 변화</span><p>지금은 방향을 조금 잡았고, 걱정을 혼자 안고 있지 않게 됐어요.</p></div>
  <div class="rcard gold"><span class="rl">한 줄 요약</span><p class="serif" style="font-size:18px;font-weight:700;line-height:1.5">막막함은 줄고, 해 본 일은 늘었어요.</p></div>
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:10px;margin-bottom:12px">
    <div class="btn brown">리포트 저장</div><div class="btn line">다른 시점 만나보기</div>
  </div>
</div>`);

S.couple1 = phone(`
<div class="body tabs">
  <div class="navbar"><span style="width:22px"></span><span class="t">커플 모드</span><span style="width:22px"></span></div>
  <div class="orb" style="margin:6px auto 0;transform:scale(.8)"><i class="o1"></i><i class="o2"></i></div>
  <div class="serif h1" style="text-align:center;margin-top:2px">그때의 우리를<br>함께 만나봐요</div>
  <div class="seg"><span class="on">초대 코드 만들기</span><span>코드 입력하기</span></div>
  <div class="card" style="margin-top:14px;text-align:center">
    <div class="fine" style="margin:0">내 초대 코드</div>
    <div class="code">DU-7K4Q-2M9X</div>
    <div style="display:flex;gap:8px;justify-content:center;margin-top:10px"><span class="pill line">${ic.copy} 복사</span><span class="pill line">공유</span></div>
  </div>
  <div class="card tint" style="margin-top:12px;font-size:13.5px;line-height:1.6">
    <div style="display:flex;align-items:center;gap:8px"><span class="pulse"></span><b>도윤님이 코드를 입력하길 기다리는 중</b></div>
    <span class="fine" style="margin:4px 0 0;display:block">코드는 24시간 동안 유효해요. 연결만 되고, 서로 동의해야 대화할 수 있어요.</span>
  </div>
</div>${tabbar('커플')}`);

S.couple2 = phone(`
<div class="body">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">시작하기 전에</span><span style="width:22px"></span></div>
  <div class="pair">
    <div><span class="av">서</span><b>나</b><em class="tg low">동의 전</em></div>
    <span class="link"></span>
    <div><span class="av alt">도</span><b>도윤</b><em class="tg">대기 중</em></div>
  </div>
  <div class="card" style="margin-top:14px;font-size:14px;line-height:1.7">
    상대방이 회원님의 일기와 대화 기록으로 만들어진 <b>‘과거의 회원님’</b>과 대화하게 됩니다.<br><br>대화나 리포트 중에 상대방이 몰랐던 생각이나 고민이 드러날 수 있어요.<br><br>동의는 언제든 철회할 수 있으며, 철회하면 상대방의 접근은 즉시 차단됩니다.
  </div>
  <div class="card tint" style="margin-top:10px;font-size:13px;line-height:1.55"><b>예를 들면</b><br>3개월 전 일기에 적은 불안이 대화에서 그대로 나올 수 있어요.</div>
  <div class="agree" style="margin-top:14px"><span class="box">${ic.check}</span><span style="font-size:14px">위 내용을 이해했으며 커플 모드 데이터 활용에 동의합니다</span></div>
  <div class="btn gold dis" style="margin-top:auto;margin-bottom:12px">동의하고 계속하기</div>
</div>`);

S.couple3 = phone(`
<div class="body tabs">
  <div class="navbar"><span style="width:22px"></span><span class="t">커플 모드</span><span style="width:22px"></span></div>
  <div class="pair" style="margin-top:2px">
    <div><span class="av">서</span><b>나</b><em class="tg ok">동의 완료</em></div>
    <span class="link on"></span>
    <div><span class="av alt">도</span><b>도윤</b><em class="tg ok">동의 완료</em></div>
  </div>
  <div class="serif h1" style="margin-top:20px">도윤님의 과거를<br>만나볼래요?</div>
  <div class="fine" style="margin:6px 0 0">도윤님이 허락한 기록 안에서만 열려요</div>
  <div class="chips" style="margin-top:16px"><span class="chip">1개월 전</span><span class="chip on">6개월 전</span><span class="chip">1년 전</span><span class="chip">직접 고르기</span></div>
  <div class="card" style="margin-top:16px">
    <div style="display:flex;justify-content:space-between;align-items:center"><b class="serif" style="font-size:19px">6개월 전의 도윤</b><em class="tg ok" style="margin:0">기록 충분</em></div>
    <div class="fine" style="margin:4px 0 0">2026년 4월 무렵</div>
    <div class="fine" style="margin:10px 0 0;line-height:1.55">앱에서 이야기를 나눈 뒤, 직접 만나서 소감을 나눠 보세요.</div>
  </div>
  <div class="btn gold" style="margin-top:auto;margin-bottom:12px">만나러 가기</div>
</div>${tabbar('커플')}`);

S.couple4 = phone(`
<div class="body">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">회고 리포트</span><span style="width:22px"></span></div>
  <div class="fine" style="margin:4px 0 0">6개월 전의 도윤과 15분 대화했어요</div>
  <div class="serif h1" style="margin-top:4px">그때의 도윤은</div>
  <div class="rcard"><span class="rl">그때의 고민</span><p>새로 시작한 일이 잘 맞는지 걱정했고, 그 마음을 말로 꺼내진 못했어요.</p></div>
  <div class="rcard"><span class="rl">지금과 달라진 점</span><p>요즘의 도윤은 고민을 먼저 이야기하는 편이에요.</p></div>
  <div class="rcard gold"><span class="rl">한 줄 요약</span><p class="serif" style="font-size:17px;font-weight:700;line-height:1.5">말하지 못한 걱정이 말할 수 있는 이야기가 됐어요.</p></div>
  <div class="card tint" style="margin-top:12px;font-size:13.5px;line-height:1.6"><b>이제 직접 이야기해 보세요</b><br>오늘 만난 6개월 전의 도윤 이야기를, 지금의 도윤에게 들려주세요.</div>
  <div class="card ghost" style="margin-top:10px;font-size:12.5px;line-height:1.5"><em class="tg" style="margin:0 6px 0 0">검토 중</em>연인에게 물어보면 좋을 질문 (확장 후보)</div>
  <div class="btn brown" style="margin-top:auto;margin-bottom:12px">리포트 저장</div>
</div>`);

S.revoke = phone(`
<div class="body" style="padding:0 22px">
  <div class="navbar"><span style="color:var(--brown)">${ic.back}</span><span class="t">설정</span><span style="width:22px"></span></div>
  <div class="list">
    <div class="li"><span>계정</span><span class="fine" style="margin:0">서연 · 카카오</span></div>
    <div class="li"><span>내 데이터 보기 · 삭제</span>${ic.next}</div>
    <div class="li"><span>알림</span>${ic.next}</div>
    <div class="li"><span>커플 모드</span><span class="fine" style="margin:0">도윤님과 연결됨</span></div>
    <div class="li"><span>동의 철회 · 연결 해제</span>${ic.next}</div>
    <div class="li"><span>회원 탈퇴</span>${ic.next}</div>
  </div>
</div>
<div class="scrim"></div>
<div class="sheet" style="padding-bottom:44px">
  <div class="grab"></div>
  <div class="serif h1" style="text-align:center;font-size:23px;margin-top:10px">동의를 철회할까요?</div>
  <ul class="warn">
    <li>도윤님의 접근이 <b>즉시 차단</b>돼요</li>
    <li>이 커플의 대화와 리포트가 삭제돼요</li>
    <li>내 일기와 개인 모드 기록은 그대로 남아요</li>
  </ul>
  <div class="btn brown" style="margin-top:12px">철회하기</div>
  <div class="btn line" style="margin-top:10px">취소</div>
</div>`);

/* ───────── storyboard content ───────── */
const flows = [
  { id: 'A', title: '기록하기', sub: '일기를 쓰는 것이 기본이고, 쓰기 귀찮은 날엔 카톡 대화를 올립니다.', steps: [
    { k: 'login', t: '로그인', who: '카카오 · 구글 · 애플 중 하나로 시작', sys: '소셜 토큰을 검증하고 JWT를 발급해요. 처음이면 계정이 만들어져요.', link: '<code>POST /auth/{provider}</code> · <code>users</code>' },
    { k: 'home', t: '홈', who: '캘린더에서 지난 기록을 보고, 바로가기 카드를 눌러요.', sys: '앱에서 쓴 일기에만 <b style="color:#D9A869">●</b> 표시가 붙어요. 카드에는 그 시점의 기록량을 보여줘요.', link: '<code>GET /diaries</code> · <code>diaries</code>' },
    { k: 'diary', t: '일기 쓰기', who: '감정 태그를 고르고 일기를 쓴 뒤 저장해요.', sys: '3초 안에 저장되고 캘린더에 <b style="color:#D9A869">●</b>가 생겨요. 쓰기 귀찮으면 카톡 올리기로 넘어가요.', link: '<code>POST /diaries</code> · <code>diaries</code>, <code>diary_emotions</code>' },
    { k: 'upload', t: '카톡 올리기', who: '내용을 확인하고 동의한 뒤 txt 파일을 골라 분석을 시작해요.', sys: '내 발화만 뽑아 민감정보를 가리고, Gemini로 주 단위 요약을 만들어요. 원본은 바로 지워요.', link: '<code>POST /uploads/kakao</code> · <code>kakao_uploads</code>, <code>kakao_weekly_summaries</code>' },
    { k: 'done', t: '분석 완료', who: '무엇이 저장되고 무엇이 지워졌는지 확인해요.', sys: '요약만 암호화해 보관하고 원본 삭제를 보여줘요. 캘린더에는 표시하지 않아요.', link: '<code>kakao_weekly_summaries</code> (암호화)' },
  ] },
  { id: 'B', title: '과거의 나와 대화하기', sub: '시점을 고르면 그 무렵 전후 2주의 기록으로 그때의 나를 만들고, 대화가 끝나면 리포트를 받습니다.', steps: [
    { k: 'pick', t: '시점 고르기', who: '바로가기나 슬라이더로 최대 1년 전까지 시점을 골라요.', sys: '전후 2주의 기록량으로 신뢰도를 알려줘요. 바로가기는 기준일을 주 단위로 맞춰요.', link: '<code>diaries</code> · <code>kakao_weekly_summaries</code> 조회' },
    { k: 'loading', t: '불러오는 중', who: '잠시 기다려요.', sys: '저장된 분석이 있으면 바로 입장하고, 없거나 기록이 바뀌었으면 새로 분석해 저장해요.', link: '<code>persona_analyses</code> (재사용 확인 → 분석 → 저장)' },
    { k: 'chat', t: 'Time-Slip Chat', who: '그때의 나에게 말을 걸고 대화해요.', sys: '프롬프트를 만들어 Gemini 스트리밍으로 답해요. 첫 응답 5초, 20턴 이상 유지가 목표예요.', link: '<code>POST /chats</code> · <code>chat_sessions</code>, <code>chat_messages</code>' },
    { k: 'safety', t: '안전 안내', who: '힘든 마음을 털어놓는 대화가 이어져요.', sys: '일기나 대화에서 위기 신호를 감지하면 상담 기관을 안내해요. 대화 시간 안내도 함께 보여줘요.', link: '기획서 5.6 안전장치' },
    { k: 'report', t: '회고 리포트', who: '대화를 끝내고 리포트를 확인해요.', sys: '당시 고민 · 현재 변화 · 한 줄 요약 3개 항목을 자동으로 만들어요.', link: '<code>reports</code> (세션당 1건)' },
  ] },
  { id: 'C', title: '커플 모드', sub: '같은 엔진 앞에 동의 게이트 하나만 추가됩니다. 대화 화면은 개인 모드와 같고 상대 이름만 바뀝니다.', steps: [
    { k: 'couple1', t: '초대 코드 연결', who: '코드를 만들어 보내거나, 받은 코드를 입력해요.', sys: '코드는 24시간 동안 유효해요. 연결만 되고 아직 상대 페르소나는 열리지 않아요.', link: '<code>POST /couples/invite</code> · <code>couples</code>' },
    { k: 'couple2', t: '동의 안내', who: '속마음이 드러날 수 있다는 안내를 읽고 각자 동의해요.', sys: '두 사람이 모두 동의해야 다음으로 넘어가요. 한쪽만 동의하면 접근은 0%예요.', link: '<code>POST /couples/consent</code> · <code>couple_members</code>' },
    { k: 'couple3', t: '상대의 시점 고르기', who: '도윤님의 1개월·6개월·1년 전 또는 직접 고른 시점을 만나요.', sys: '연결됨 + 양측 동의 + 철회 없음일 때만 열려요. 상대가 허락한 기록만 써요.', link: '<code>couples.status</code> · <code>couple_members</code> 확인' },
    { k: 'couple4', t: '커플 리포트', who: '대화를 마치고 리포트를 봐요. 이제 직접 만나서 이야기해요.', sys: '과거의 연인과 나눈 대화 요약을 주고, 현실의 대화로 이어지게 해요.', link: '<code>reports</code> · <code>chat_sessions.couple_id</code>' },
    { k: 'revoke', t: '동의 철회', who: '설정에서 언제든 동의를 철회하거나 연결을 해제해요.', sys: '상대의 접근을 즉시 끊고, 이 커플의 대화와 리포트를 삭제해요.', link: '<code>couple_members.revoked_at</code> · 해당 <code>couple_id</code> 세션 삭제' },
  ] },
];

let n = 0;
const flowHtml = flows.map(f => `
<section class="flow-sec">
  <div class="flow-head"><span class="fid">${f.id}</span><div><h2>${f.title}</h2><p>${f.sub}</p></div></div>
  <div class="flow">
${f.steps.map(s => { n++; return `    <article class="step">
      <div class="sh"><span class="no">${n}</span><h3>${s.t}</h3></div>
      ${S[s.k]}
      <dl class="meta"><dt>사용자</dt><dd>${s.who}</dd><dt>시스템</dt><dd>${s.sys}</dd><dt>연결</dt><dd>${s.link}</dd></dl>
    </article>`; }).join('\n')}
  </div>
</section>`).join('\n');

const css = `
:root{--cream:#FEF7EE;--beige:#CDB69C;--gold:#D9A869;--brown:#6F5139;--ink:#3A2A1F;--paper:#FFFCF7;--panel:#F7EDE0;--s:.62}
*{box-sizing:border-box}
html{color-scheme:light}
body{margin:0;background:var(--cream);color:var(--ink);font-family:"Noto Sans KR","Pretendard","Malgun Gothic",system-ui,sans-serif;line-height:1.6}
code{font-family:ui-monospace,"Cascadia Mono",Consolas,monospace;font-size:11.5px;background:rgba(205,182,156,.28);padding:1px 5px;border-radius:4px}
.page{max-width:1500px;margin:0 auto;padding:36px 20px 80px}
h1,h2,h3{font-family:"Noto Serif KR","Nanum Myeongjo",serif;margin:0}
h1{font-size:28px}
h1 small{font-family:"Noto Sans KR",sans-serif;font-size:13px;font-weight:400;color:var(--brown);margin-left:10px}
.lede{margin:6px 0 0;color:var(--brown);font-size:14px;max-width:80ch}
.bar{display:flex;flex-wrap:wrap;gap:12px 28px;align-items:center;margin-top:18px;font-size:12.5px;color:var(--brown)}
.bar .sw{display:flex;align-items:center;gap:6px}.bar .sw i{width:18px;height:18px;border-radius:5px;border:1px solid rgba(58,42,31,.18);display:inline-block}
.bar label{display:flex;align-items:center;gap:8px}
.bar input[type=range]{accent-color:var(--gold);width:150px}
.flow-sec{margin-top:44px;border-top:1px solid var(--beige);padding-top:22px}
.flow-head{display:flex;gap:14px;align-items:flex-start}
.fid{flex:0 0 auto;width:36px;height:36px;border-radius:50%;background:var(--ink);color:var(--cream);display:flex;align-items:center;justify-content:center;font-family:"Noto Serif KR",serif;font-weight:700}
.flow-head h2{font-size:21px}.flow-head p{margin:2px 0 0;font-size:13.5px;color:var(--brown);max-width:90ch}
.flow{display:flex;gap:46px;overflow-x:auto;padding:18px 40px 26px 4px;scroll-snap-type:x proximity}
.step{position:relative;flex:0 0 auto;width:calc(450px*var(--s));scroll-snap-align:start}
.step:not(:last-child)::after{content:"›";position:absolute;right:-34px;top:calc(60px + 457px*var(--s));font-size:30px;line-height:1;color:var(--gold);font-weight:700}
.sh{display:flex;align-items:center;gap:10px;margin-bottom:12px}
.no{width:26px;height:26px;border-radius:50%;background:var(--gold);color:var(--ink);font-weight:700;font-size:13px;display:flex;align-items:center;justify-content:center}
.sh h3{font-size:16px}
.meta{margin:14px 0 0;font-size:12.5px;line-height:1.55;display:grid;grid-template-columns:44px 1fr;gap:6px 8px}
.meta dt{font-weight:700;color:var(--brown)}.meta dd{margin:0}
/* phone */
.phone-wrap{width:calc(450px*var(--s));height:calc(914px*var(--s))}
.phone{position:relative;width:442px;height:914px;border-radius:82px;padding:6px;transform:scale(var(--s));transform-origin:0 0;
  background:linear-gradient(140deg,#7a716a 0%,#2c2825 28%,#58504a 55%,#2c2825 80%,#6a625b 100%);box-shadow:0 18px 40px rgba(58,42,31,.28)}
.bezel{width:100%;height:100%;background:#000;border-radius:76px;padding:14px}
.scr{position:relative;width:402px;height:874px;border-radius:62px;overflow:hidden;background:var(--cream);color:var(--ink);font-size:15px}
.sb-btn{position:absolute;background:linear-gradient(90deg,#4a433e,#8b817a);border-radius:3px;width:4px}
.sb-btn.act{left:-3px;top:150px;height:32px}.sb-btn.vup{left:-3px;top:214px;height:62px}.sb-btn.vdn{left:-3px;top:290px;height:62px}
.sb-btn.pwr{right:-3px;top:250px;height:96px;transform:scaleX(-1)}.sb-btn.cam{right:-3px;top:600px;height:70px;transform:scaleX(-1)}
.sb{position:absolute;top:0;left:0;right:0;height:54px;display:flex;justify-content:space-between;align-items:center;padding:6px 36px 0 52px;font-weight:600;font-size:17px;z-index:6}
.sbr{display:flex;gap:6px;align-items:center}
.island{position:absolute;top:11px;left:50%;transform:translateX(-50%);width:126px;height:37px;background:#000;border-radius:20px;z-index:7}
.home-ind{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);width:134px;height:5px;border-radius:3px;background:var(--ink);z-index:9}
/* layout */
.body{position:absolute;top:62px;left:0;right:0;bottom:34px;padding:0 22px;display:flex;flex-direction:column}
.body.tabs{bottom:83px}
.navbar{display:flex;align-items:center;justify-content:space-between;height:44px;flex:0 0 auto}
.t{font-weight:700;font-size:17px}
.serif{font-family:"Noto Serif KR","Nanum Myeongjo",serif}
.h1{font-size:27px;font-weight:700;line-height:1.35}.h2{font-size:19px;font-weight:700;line-height:1.35}
.fine{font-size:12.5px;color:var(--brown);margin:6px 0}
.tabbar{position:absolute;left:0;right:0;bottom:0;height:83px;background:var(--paper);border-top:1px solid var(--beige);display:flex;padding:8px 10px 0;z-index:4}
.tab{flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;font-size:11px;color:var(--brown);opacity:.65}
.tab .ti{padding:3px 16px;border-radius:14px;display:flex}.tab.on{opacity:1;color:var(--ink);font-weight:700}.tab.on .ti{background:var(--gold)}
.btn{height:52px;border-radius:16px;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:16px;flex:0 0 auto}
.btn.gold{background:var(--gold);color:var(--ink)}.btn.brown{background:var(--brown);color:var(--cream)}
.btn.line{border:1.5px solid var(--beige);color:var(--brown)}.btn.dis{opacity:.45}
.btn.kakao{background:#FEE500;color:#191919}.btn.google{background:#fff;border:1.5px solid var(--beige);color:#3c4043}.btn.apple{background:#000;color:#fff}
.card{background:var(--paper);border:1px solid var(--beige);border-radius:20px;padding:16px;position:relative;flex:0 0 auto}
.card.tint{background:var(--panel)}.card.ghost{background:transparent;border-style:dashed}
.pill{display:inline-flex;align-items:center;gap:4px;padding:7px 14px;border-radius:999px;background:var(--gold);color:var(--ink);font-weight:700;font-size:13.5px}
.pill.line{background:transparent;border:1.5px solid var(--beige);color:var(--brown)}
.chips{display:flex;flex-wrap:wrap;gap:8px}
.chip{padding:8px 14px;border-radius:999px;border:1.5px solid var(--beige);font-size:14px;color:var(--brown);background:var(--paper)}
.chip.on{background:var(--gold);border-color:var(--gold);color:var(--ink);font-weight:700}
.tg{font-style:normal;font-size:11.5px;font-weight:700;padding:2px 8px;border-radius:999px;border:1px solid var(--beige);color:var(--brown);display:inline-block;margin-top:4px}
.tg.ok{background:var(--gold);border-color:var(--gold);color:var(--ink)}.tg.low{background:var(--panel);border-color:var(--brown)}
.avatar{width:38px;height:38px;border-radius:50%;background:var(--panel);border:1px solid var(--beige);display:flex;align-items:center;justify-content:center;color:var(--brown)}
.dot{display:block;width:6px;height:6px;border-radius:50%;background:var(--gold);margin:0 auto}
/* login */
.orb{position:relative;width:120px;height:80px;margin:0 auto}
.orb i{position:absolute;top:8px;width:64px;height:64px;border-radius:50%}
.orb .o1{left:10px;border:2px dashed var(--brown);opacity:.7}.orb .o2{left:44px;background:var(--gold)}
.brand{font-size:40px;font-weight:700;margin-top:10px;letter-spacing:.01em}
.tag{margin-top:10px;font-size:15px;color:var(--brown);line-height:1.6}
.fine+.fine{margin-top:0}
/* calendar */
.cal{display:grid;grid-template-columns:repeat(7,1fr);text-align:center}
.cal.wk{font-size:12px;color:var(--brown);margin-bottom:2px}
.cal .d{height:38px;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:14.5px}
.cal .d span{display:flex;width:28px;height:28px;align-items:center;justify-content:center;border-radius:50%}
.cal .d.today span{border:2px solid var(--ink);font-weight:700}
.cal .d .dot{margin-top:1px}
.short{flex:1;background:var(--paper);border:1px solid var(--beige);border-radius:18px;padding:12px 10px;display:flex;flex-direction:column;gap:2px}
.short b{font-family:"Noto Serif KR",serif;font-size:16px}.short span{font-size:11.5px;color:var(--brown)}
/* diary */
.caret{display:inline-block;width:2px;height:18px;background:var(--ink);vertical-align:-3px;margin-left:2px}
/* upload */
.ck{margin:8px 0 10px;padding-left:18px}.ck li{margin:3px 0}
.agree{display:flex;gap:10px;align-items:flex-start;font-size:13.5px;line-height:1.5}
.box{flex:0 0 auto;width:22px;height:22px;border-radius:7px;border:2px solid var(--beige);display:flex;align-items:center;justify-content:center;color:transparent;background:var(--paper)}
.box.on{background:var(--gold);border-color:var(--gold);color:var(--ink)}
.file{display:flex;gap:12px;align-items:center;margin-top:12px;padding:12px 14px;border:1.5px dashed var(--brown);border-radius:16px;background:var(--paper)}
/* done */
.okmark{width:76px;height:76px;border-radius:50%;background:var(--gold);color:var(--ink);display:flex;align-items:center;justify-content:center}
.row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid rgba(205,182,156,.6)}.row.last{border-bottom:0}
.gone{color:var(--brown);text-decoration:line-through;text-decoration-color:var(--gold)}
/* pick */
.bars{display:flex;align-items:flex-end;gap:2px;height:38px;margin:14px 0 6px}
.bars i{flex:1;background:var(--beige);border-radius:2px;opacity:.7}.bars i.on{background:var(--gold);opacity:1}
.slider{position:relative;height:26px}
.slider .trk{position:absolute;left:0;right:0;top:11px;height:4px;border-radius:2px;background:var(--beige)}
.slider .fill{position:absolute;right:0;top:11px;height:4px;border-radius:2px;background:var(--gold)}
.slider .thumb{position:absolute;top:2px;width:22px;height:22px;margin-left:-11px;border-radius:50%;background:var(--paper);border:3px solid var(--ink)}
/* loading */
.rings{position:relative;width:170px;height:170px;display:flex;align-items:center;justify-content:center;text-align:center;font-weight:700;font-size:19px;line-height:1.3}
.rings i{position:absolute;border-radius:50%;border:2px dashed var(--brown)}
.rings .r1{inset:34px;border-style:solid;border-color:var(--gold);background:var(--panel)}.rings .r2{inset:14px;opacity:.6}.rings .r3{inset:-8px;opacity:.3}
.rings span{position:relative}
.steps{margin-top:26px;width:100%;display:flex;flex-direction:column;gap:12px;font-size:14.5px}
.st{display:flex;gap:10px;align-items:center;color:var(--brown);opacity:.6}.st.done,.st.now{opacity:1;color:var(--ink)}
.sd{width:22px;height:22px;border-radius:50%;border:2px solid var(--beige);display:flex;align-items:center;justify-content:center}
.st.done .sd{background:var(--gold);border-color:var(--gold)}.st.now .sd{border-color:var(--gold)}.st.now .sd i{width:8px;height:8px;border-radius:50%;background:var(--gold)}
/* chat */
.chat{background:var(--panel);display:block}
.chat-top{position:absolute;top:0;left:0;right:0;padding:0 18px 8px;background:var(--paper);border-bottom:1px solid var(--beige);z-index:2}
.usage{margin-top:2px;text-align:center;font-size:11.5px;color:var(--brown);background:var(--panel);border-radius:10px;padding:3px 8px}
.msgs{position:absolute;top:104px;left:0;right:0;bottom:84px;padding:12px 16px;display:flex;flex-direction:column;gap:10px;overflow:hidden}
.sep{align-self:center;font-size:11.5px;color:var(--brown);background:rgba(205,182,156,.4);padding:3px 12px;border-radius:999px}
.m{max-width:78%;padding:11px 14px;border-radius:20px;font-size:15px;line-height:1.5;word-break:keep-all}
.m.p{align-self:flex-start;background:var(--paper);border:1px solid var(--beige);border-bottom-left-radius:6px}
.m.u{align-self:flex-end;background:var(--gold);border-bottom-right-radius:6px}
.m.typ{display:flex;gap:5px;padding:14px 16px}.typ i{width:7px;height:7px;border-radius:50%;background:var(--brown);opacity:.5}.typ i:nth-child(2){opacity:.75}.typ i:nth-child(3){opacity:1}
.inputbar{position:absolute;left:0;right:0;bottom:0;height:84px;padding:10px 14px 0;background:var(--paper);border-top:1px solid var(--beige);display:flex;gap:10px;align-items:flex-start}
.field{flex:1;height:44px;border-radius:22px;border:1.5px solid var(--beige);padding:0 16px;display:flex;align-items:center;color:var(--brown);opacity:.75;font-size:14.5px}
.sendb{width:44px;height:44px;border-radius:50%;background:var(--gold);display:flex;align-items:center;justify-content:center}
/* sheets */
.scrim{position:absolute;inset:0;background:rgba(58,42,31,.45);z-index:10}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--cream);border-radius:30px 30px 0 0;padding:10px 22px 22px;z-index:11;box-shadow:0 -8px 30px rgba(58,42,31,.25)}
.grab{width:42px;height:5px;border-radius:3px;background:var(--beige);margin:0 auto}
.heartmark{width:56px;height:56px;border-radius:50%;background:var(--gold);display:flex;align-items:center;justify-content:center;margin:14px auto 0;color:var(--ink)}
.hot{display:flex;gap:12px;align-items:center;margin-top:12px;padding:12px 14px;border-radius:16px;background:var(--paper);border:1px solid var(--beige);font-size:14.5px}
.warn{margin:14px 0 0;padding:0;list-style:none;font-size:14.5px}.warn li{padding:12px 14px;margin-top:8px;border-radius:14px;background:var(--paper);border:1px solid var(--beige)}
/* report */
.rcard{margin-top:12px;background:var(--paper);border:1px solid var(--beige);border-radius:20px;padding:14px 16px}
.rcard p{margin:6px 0 0;font-size:14.5px;line-height:1.6}.rcard.gold{background:var(--gold);border-color:var(--gold)}
.rl{font-size:12px;font-weight:700;color:var(--brown);letter-spacing:.02em}.rcard.gold .rl{color:var(--ink)}
/* couple */
.seg{display:flex;margin-top:10px;background:var(--panel);border:1px solid var(--beige);border-radius:14px;padding:4px}
.seg span{flex:1;text-align:center;padding:9px 0;border-radius:10px;font-size:14px;color:var(--brown)}.seg .on{background:var(--paper);color:var(--ink);font-weight:700;box-shadow:0 1px 3px rgba(58,42,31,.15)}
.code{font-family:ui-monospace,"Cascadia Mono",Consolas,monospace;font-size:26px;font-weight:700;letter-spacing:.06em;margin-top:6px}
.pulse{width:10px;height:10px;border-radius:50%;background:var(--gold);box-shadow:0 0 0 5px rgba(217,168,105,.35)}
.pair{display:flex;align-items:center;justify-content:center;gap:14px;margin-top:6px}
.pair>div{display:flex;flex-direction:column;align-items:center;gap:2px;font-size:14px}
.av{width:56px;height:56px;border-radius:50%;background:var(--gold);display:flex;align-items:center;justify-content:center;font-family:"Noto Serif KR",serif;font-weight:700;font-size:20px}
.av.alt{background:var(--brown);color:var(--cream)}
.link{width:54px;border-top:2px dashed var(--beige);margin-top:-30px}.link.on{border-top:3px solid var(--gold)}
/* settings */
.list{margin-top:8px;background:var(--paper);border:1px solid var(--beige);border-radius:20px;overflow:hidden}
.li{display:flex;justify-content:space-between;align-items:center;padding:16px;border-bottom:1px solid rgba(205,182,156,.6);font-size:15px}.li:last-child{border-bottom:0}
`;

const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dear Us 스토리보드</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@600;700&family=Noto+Sans+KR:wght@400;500;700&display=swap">
<style>${css}</style>
</head>
<body>
<main class="page">
  <h1>Dear Us 스토리보드<small>iPhone 17 Pro · 화면 15장 · HTML 초안</small></h1>
  <p class="lede">기획서의 서비스 흐름을 따라 화면 열다섯 장을 세 흐름으로 나눴습니다. 각 화면 아래에 사용자가 하는 일, 시스템이 하는 일, 연결되는 API와 테이블을 적었습니다. 이 문서의 <a href="DearUs_기능블록도.html" style="color:#6F5139">기능 블록도</a>, <a href="DearUs_ERD.html" style="color:#6F5139">ERD</a>와 같은 용어를 씁니다.</p>
  <div class="bar">
    <span class="sw"><i style="background:#FEF7EE"></i>#FEF7EE</span><span class="sw"><i style="background:#CDB69C"></i>#CDB69C</span><span class="sw"><i style="background:#D9A869"></i>#D9A869</span><span class="sw"><i style="background:#6F5139"></i>#6F5139</span><span class="sw"><i style="background:#3A2A1F"></i>#3A2A1F</span>
    <label>화면 크기 <input id="zoom" type="range" min="40" max="100" value="62" aria-label="화면 크기"> <span id="zv">62%</span></label>
  </div>
${flowHtml}
</main>
<script>
(function(){
  var z=document.getElementById('zoom'),v=document.getElementById('zv');
  function set(x){document.documentElement.style.setProperty('--s',x/100);v.textContent=x+'%';}
  z.addEventListener('input',function(){set(+z.value);});
})();
</script>
</body>
</html>
`;

fs.writeFileSync('C:/Users/Note/Desktop/dearus/DearUs_스토리보드.html', html, 'utf8');
console.log('written', html.length, 'chars;', n, 'screens');
module.exports = { S, flows, css };
