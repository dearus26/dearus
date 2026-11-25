// Dear Us 관리자 페이지 (2026.11.25). 관리자 API(/admin/api/**)만 불러요.
// 관리자 토큰은 이 탭의 sessionStorage에만 둬서 탭을 닫으면 사라져요. 화면의 글은 모두 textContent로 넣어서 HTML로 읽히지 않아요.
'use strict';

const TOKEN_KEY = 'dearus-admin-token';
const REASONS = {
  HURTFUL: '상처 주는 말',
  INACCURATE: '내 기록과 다른 내용',
  HARMFUL: '위험하거나 해로운 내용',
  PRIVACY: '개인정보가 드러남',
  OTHER: '기타',
};
const KST = { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' };

const state = { status: 'OPEN', offset: 0 };
const $ = (id) => document.getElementById(id);

function getToken() {
  try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
}
function setToken(value) {
  try {
    if (value) sessionStorage.setItem(TOKEN_KEY, value);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch { /* 저장소를 못 쓰면 새로 고칠 때 다시 로그인해요 */ }
  memoryToken = value;
}
let memoryToken = getToken();

class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || '문제가 생겼어요. 잠시 뒤에 다시 해 주세요.');
    this.status = status;
  }
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (memoryToken) headers.Authorization = 'Bearer ' + memoryToken;
  const res = await fetch('/admin/api' + path, { ...options, headers });
  if (res.status === 204) return null;
  const body = await res.json().catch(() => null);
  if (res.status === 401 && path !== '/login') {
    setToken(null);
    show('login');
    throw new ApiError(401, { message: '로그인이 끝났어요. 다시 로그인해 주세요.' });
  }
  if (!res.ok) throw new ApiError(res.status, body);
  return body;
}

function show(screen) {
  $('login').hidden = screen !== 'login';
  $('reports').hidden = screen !== 'reports';
  $('logout').hidden = screen !== 'reports';
  if (screen === 'login') $('password').focus();
}

function formatTime(iso) {
  return iso ? new Date(iso).toLocaleString('ko-KR', KST) : '';
}

function setText(el, value, fallback) {
  el.textContent = value || fallback;
  el.classList.toggle('empty', !value);
}

function renderItem(item) {
  const node = $('report-template').content.firstElementChild.cloneNode(true);
  node.querySelector('.reason').textContent = REASONS[item.reason] || item.reason;
  node.querySelector('.when').textContent = '신고 ' + formatTime(item.reportedAt);
  const mode = item.mode === 'COUPLE' ? '커플 대화' : '개인 대화';
  const scope = item.scope === 'DAY' ? '그날 하루' : '전후 2주';
  node.querySelector('.context').textContent = `${mode} · ${item.anchorDate} ${scope} · ${item.seq}번째 메시지 · 대화 ${item.chatId}`;
  setText(node.querySelector('.previous'), item.previousUserMessage, '(앞 말 없음: 첫 인사예요)');
  setText(node.querySelector('.reply'), item.reply, '');
  setText(node.querySelector('.detail'), item.detail, '(적은 내용 없음)');
  paintReviewed(node, item);
  const button = node.querySelector('.review');
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      const next = !item.reviewedAt;
      await api(`/reports/${item.id}/review`, { method: 'POST', body: JSON.stringify({ reviewed: next }) });
      item.reviewedAt = next ? new Date().toISOString() : null;
      paintReviewed(node, item);
      const count = $('open-count');
      count.textContent = String(Math.max(0, Number(count.textContent) + (next ? -1 : 1)));
    } catch (error) {
      $('list-error').textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });
  return node;
}

function paintReviewed(node, item) {
  node.classList.toggle('done', Boolean(item.reviewedAt));
  node.querySelector('.reviewed').textContent = item.reviewedAt ? '확인함 ' + formatTime(item.reviewedAt) : '아직 확인하지 않았어요';
  node.querySelector('.review').textContent = item.reviewedAt ? '확인 전으로 되돌리기' : '확인함';
}

async function load(reset) {
  if (reset) {
    state.offset = 0;
    $('list').replaceChildren();
  }
  $('list-error').textContent = '';
  $('more').disabled = true;
  try {
    const page = await api(`/reports?status=${state.status}&offset=${state.offset}`);
    $('open-count').textContent = String(page.openCount);
    for (const item of page.items) $('list').appendChild(renderItem(item));
    state.offset += page.items.length;
    $('more').hidden = !page.hasMore;
    $('empty').hidden = $('list').children.length > 0;
  } catch (error) {
    $('list-error').textContent = error.message;
  } finally {
    $('more').disabled = false;
  }
}

function selectTab(status) {
  state.status = status;
  for (const tab of document.querySelectorAll('.tabs button')) {
    tab.setAttribute('aria-selected', String(tab.dataset.status === status));
  }
  void load(true);
}

$('login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter || $('login-form').querySelector('button');
  button.disabled = true;
  $('login-error').textContent = '';
  try {
    const result = await api('/login', { method: 'POST', body: JSON.stringify({ password: $('password').value }) });
    setToken(result.value);
    $('password').value = '';
    show('reports');
    selectTab('OPEN');
  } catch (error) {
    $('login-error').textContent = error.message;
  } finally {
    button.disabled = false;
  }
});

$('logout').addEventListener('click', () => {
  setToken(null);
  $('list').replaceChildren();
  show('login');
});

$('more').addEventListener('click', () => void load(false));
for (const tab of document.querySelectorAll('.tabs button')) {
  tab.addEventListener('click', () => selectTab(tab.dataset.status));
}

if (memoryToken) {
  show('reports');
  selectTab('OPEN');
} else {
  show('login');
}
