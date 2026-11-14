# Dear Us 프로젝트

수업(실감디지털콘텐츠기획) 팀 프로젝트의 모바일 앱이에요. 앱 코드는 `app/`, 서버 코드는 `server/`, 기획 · 설계 · 운영 문서(md)는 `skills/`에 두고, HTML 산출물은 레포 맨 위에 있어요. 이 파일은 팀원 두 명이 함께 쓰는 공용 지침이에요(여러 AI 도구가 함께 읽는 `AGENTS.md` 이름을 써요. Claude Code는 `CLAUDE.md`에서 이 파일을 불러와요).

## 표기 규칙

- 발표 · 문서 표기는 `Dear Us`(띄어쓰기), 코드 식별자(패키지 `com.dearus`, 저장소, 환경변수 등)는 `dearus`예요.
- 앱 색상: 크림 `#FEF7EE` · 베이지 `#CDB69C` · 골드 `#D9A869` · 브라운 `#6F5139` · 다크브라운 `#3A2A1F`
- 앱 폰트: 본문 Noto Sans KR, 제목 Noto Serif KR (스토리보드 HTML 기준)
- 문서와 앱 문구는 `~해요` 체로 써요.

## 역할 분담

팀원은 두 명이고, 각자 한 쪽을 맡아요.

| 담당 | 폴더 | 기술 |
|---|---|---|
| 백엔드 | `server/` | Spring Boot · Supabase · AI API 연동 |
| 프론트엔드 | `app/` | Expo · React Native |

- 지금 대화하는 사람이 어느 쪽 담당인지 모르면 먼저 물어봐요.
- 자기 담당 폴더를 주로 고쳐요. 상대 담당 폴더나 공통 파일(`server/openapi.yaml`, ERD, 기획서 등)을 고칠 때는 먼저 확인하고, 바꾼 내용이 커밋 메시지에 드러나게 적어요.
- 작업을 시작하기 전에 `git pull`로 최신 상태를 받아요. 커밋과 푸시는 지금 작업하는 사람이 요청할 때만 해요.

## 파일

| 파일 | 내용 |
|---|---|
| `app/` | 앱 코드 (Expo · React Native) |
| `server/` | 서버 코드 (Spring Boot) |
| `server/openapi.yaml` | API 요청 · 응답 명세 (OpenAPI 3.0). 앱과 서버의 약속이에요 |
| `skills/decisions.md` | 팀이 정한 결정과 아직 확정되지 않은 가정 (날짜와 함께). 작업 전에 읽어요 |
| `skills/plan/` | 앱 개발 기획서 (원본 기준 문서). `index.md`가 목차이고 장마다 파일이 나뉘어 있어요(`01-overview.md` ~ `10-references.md`, 장 · 절 번호는 그대로예요) |
| `skills/tech-stack.md` | 기술 스택 (Expo · Spring Boot · Supabase · AI API) |
| `skills/schedule.md` | 10월~12월 주 단위 개발 진행 계획 (개발 일정표를 풀어 쓴 것) |
| `skills/backlog.md` | 일부러 미룬 일, 아직 만들지 않은 기능, 정해야 할 것, 코드에서 일부러 단순하게 둔 부분을 한곳에 모은 목록. 미루는 일이 생기면 여기에 먼저 적어요 |
| `skills/test-runs.md` | 끝난 큰 시험(진짜 서버 · DB · Claude로 앱 순서대로 불러 본 것)의 결과 |
| `skills/privacy/policy-draft.md` | 실제 개인정보처리방침 초안(아직 공개 전). 맨 위 "공개 전에 할 일" 표와 `[ ]` 칸을 채운 뒤 공개해요. 앱 화면용은 `app/src/legal/documents.ts`예요 |
| `skills/privacy/background.md` | 비슷한 서비스(boba · What Brandon Thinks) 방침 조사와 2026.11.11 개인정보 결정 |
| `DearUs_기능블록도.html` | 기능 블록도 3장 (시스템 구조 · 데이터 흐름 · 개인/커플 모드) |
| `DearUs_ERD.html` | ERD (테이블 11개). `tools/gen_erd.js`로 만들어요 |
| `DearUs_스토리보드.html` | 아이폰 17 프로 프레임의 화면 19장(커플 연결 한 장은 갤럭시 S25 울트라). **앱 UI/UX의 기준**이고, 화면마다 "연결" 칸에 필요한 API와 테이블이 적혀 있어요 |
| `storyboard_images/` | 스토리보드를 PPT용으로 렌더링한 이미지 (`slides/` 16:9 3840×2160 7장, `screens/` 투명 배경 폰 PNG 15장). 10월 초 15장 버전이라 지금 스토리보드(19장)와 달라요 |
| `tools/` | 위 HTML과 이미지를 만든 스크립트 (아래 참고) |

## 개발 환경

- Node.js 24 LTS, npm
- 앱: Expo SDK 58 · React Native 0.88 · React 19.3 · TypeScript · Expo Router · TanStack Query v5 (자세한 버전은 `skills/tech-stack.md`)
- 서버: Java 21 · Spring Boot 4.1 · Gradle
- 서버를 로컬에서 켜려면 `server/.env.example`을 복사해 `server/.env`를 채우고(DB 접속 값은 백엔드 담당에게 받아요) `cd server` 뒤 `./gradlew bootRun`을 실행해요. `ENCRYPTION_KEY`(암호화 키)는 같은 DB를 쓰는 사람 모두가 같은 값이어야 서로 저장한 요약을 읽어요. AI 키(`ANTHROPIC_API_KEY`) 없이 개발하려면 `AI_PROVIDER=fake`로 켜면 가짜 요약이 나와요. 로컬 가짜 로그인은 `POST /auth/dev`예요(`DEV_LOGIN_ENABLED=true`일 때만 켜져요). 소셜 로그인을 켜려면 `KAKAO_APP_ID`와 `GOOGLE_CLIENT_IDS`도 채워요(비워 두면 그 방식은 501이에요). 단위 테스트는 DB 없이 `./gradlew test`로 돌려요. 진짜 DB로 도는 시험은 `DEARUS_DB_TEST=true`, 진짜 Claude를 부르는 시험은 `ANTHROPIC_API_KEY`가 있을 때만 돌아요(비용이 들어요. 없으면 건너뛰어요).
- 앱은 기본으로 가짜 데이터(`mock`)로 동작하고, `app/.env`의 `EXPO_PUBLIC_API_MODE=live`와 `EXPO_PUBLIC_API_URL`로 실제 서버에 붙어요(폰에서는 `localhost`가 PC가 아니라서 PC의 IP를 써요). 아이폰 실기기는 Expo CLI(`npx expo login`)와 Expo Go에 같은 계정으로 로그인해야 열려요(Windows에서 브라우저 로그인이 죽으면 `npx expo login --no-browser`). 새 화면(라우트)을 만들면 자동 생성 타입(`.expo/types`)이 낡아 타입 오류가 날 수 있어서 `npx expo start`를 한 번 켜요.
- 앱은 실제 폰에서 Expo Go로 확인해요(스토어의 Expo Go는 최신 SDK 하나만 지원하니 폰의 Expo Go가 SDK 58인지 먼저 봐요). 소셜 로그인처럼 네이티브 모듈이 필요한 기능은 Expo Go에서 안 돼서, 그때는 개발 빌드(EAS)를 써요. 메신저 업로드(txt)는 글자 읽기 모듈이 필요 없어서 Expo Go에서도 만들 수 있어요.

## 확정된 결정

팀이 정한 결정(AI 모델, 메신저 업로드, 페르소나 구간, 동의 철회, 대화 · 리포트, 커플 모드, 개인정보처리방침, 로그인, API 형태 등)은 `skills/decisions.md`에 날짜와 함께 있어요. **작업을 시작하기 전에 꼭 읽어요.** 결정이 새로 생기거나 바뀌면 그 문서에 적어요.

## 앱 작업 규칙

- UI/UX는 `DearUs_스토리보드.html`을 따라요. 이미지(`storyboard_images/`)와 다르면 HTML이 맞아요.
- Expo 패키지는 `npx expo install`로 설치해요.
- 앱은 Spring Boot 서버만 호출해요. AI API나 Supabase를 앱에서 직접 부르지 않아요.
- 서버 호출의 요청 · 응답은 `server/openapi.yaml`을 따라요. 명세에 없는 필드나 경로는 앱에서 마음대로 만들지 않고, 필요하면 명세를 먼저 고쳐요(백엔드 · 프론트엔드 담당이 함께 합의). 명세가 바뀌면 `app/`에서 `npm run gen:api`로 `src/api/schema.d.ts`를 다시 만들어요.
- React Native 0.88부터 `ref`에는 컴포넌트가 아니라 인스턴스 타입을 써요(예: `useRef<ScrollViewInstance>`).
- 채팅 답변은 SSE로 와요. React Native의 기본 `fetch`는 스트림이 불안정해서 `expo/fetch`로 받아요.
- API 키, DB 비밀번호 같은 비밀 값은 앱에 넣지 않아요. 앱 환경변수는 `.env`에 두고 git에는 `.env.example`만 올려요.
- 메신저 업로드는 txt만 받아요(이미지는 2026.11.11에 하지 않기로 했어요). 폰 안에서 txt를 읽어 내 말만 추리고, 사용자가 확인한 글자를 그대로 `POST /uploads/messenger`로 보내요(상대 말은 폰에서 버려요). 개발 중에 txt 요청에는 내 말만 있는지 앱의 네트워크 요청으로 확인해요.

## 서버 작업 규칙

- API는 `server/openapi.yaml`대로 구현해요. 경로나 응답 형태를 바꿔야 하면 명세를 먼저 고치고 프론트엔드 담당과 합의해요.
- 패키지는 `com.dearus` 아래 `auth`, `diary`, `upload`, `persona`, `chat`, `report`, `couple`, `common`으로 나눠요 (`skills/tech-stack.md`의 패키지 구조).
- 테이블과 컬럼은 `DearUs_ERD.html`을 따라요. 컬럼 이름은 snake_case예요.
- Supabase는 DB 용도로만 쓰고, 인증과 비즈니스 로직은 Spring Boot가 맡아요.
- AI API 키(`ANTHROPIC_API_KEY`), DB 비밀번호, JWT 키, 암호화 키(`ENCRYPTION_KEY`) 같은 비밀 값은 환경변수로 관리해요. `application-local.*`처럼 비밀 값이 든 설정 파일은 git에 올리지 않아요.
- 글자를 가리는 처리(마스킹)는 하지 않아요. 일기 · 대화 · 리포트도 원문 그대로 AI로 보내요. 메신저 업로드는 앱이 txt에서 추린 내 말만 `POST /uploads/messenger`로 받아요(스크린샷 올리기는 2026.11.11에 하지 않기로 했고, 서버의 `POST /uploads/messenger/images`와 이미지 읽기 코드도 2026.11.14에 지웠어요. 사진이나 multipart를 보내면 415예요). txt로 받은 내 말 원문은 `messenger_uploads.original_enc`에 암호화해 1년 보관하고, 만료되면 매일 새벽 4시에 도는 `MessengerExpiryJob`이 지워요. 요약은 암호화해서 저장해요. 메신저 업로드는 개인정보 이용 · AI 전송 동의가 있어야만 받아요.
- AI를 부르는 API(메신저 업로드 · 대화 · 리포트)는 먼저 AI 분석 동의가 유효한지(`users`의 `aiConsentActive`) 확인하고, 아니면 403 `AI_CONSENT_REQUIRED`를 돌려줘요. 동의를 철회하면 AI를 부르는 API는 바로 막고, 저장된 분석 데이터와 대화 기록은 30일 보관한 뒤 지워요(`ConsentPurgeJob`, 그 안에 다시 동의하면 이어서 써요). 일기는 지우지 않아요.
- AI 프롬프트는 `chat/ChatPromptBuilder`(대화), `persona/PersonaAnalyzer`(시점 분석), `upload/WeeklySummarizer`(메신저 주간 요약), `report/ReportGenerator`(회고 리포트)에 있어요. 시점 분석 프롬프트를 고치면 `PersonaAnalyzer.PROMPT_VERSION`을 올려야 저장된 분석이 새 프롬프트로 다시 만들어져요.
- AI나 소셜 제공자(카카오 · 구글)처럼 느린 외부를 부르는 동안에는 DB 연결(트랜잭션)을 잡고 있지 않아요. 연결 풀이 5개뿐이라 외부 호출이 느리면 서버 전체가 멈춰요. 소셜 토큰과 제공자 응답은 로그에 남기지 않아요.
- 새 테이블을 만들면 Flyway 마이그레이션에서 행 수준 보안(`ENABLE ROW LEVEL SECURITY`)을 켜요. Supabase가 `public` 테이블을 외부 REST API에 열어 두기 때문이에요. 서버만 DB에 붙으니 정책은 만들지 않아요.

## 아직 확정되지 않은 가정

지금 그대로 가정하고 진행하는 것들은 `skills/decisions.md`의 "아직 확정되지 않은 가정"에 있어요. 바뀌면 팀이 알려주고, 그때 그 문서와 관련 파일을 함께 고쳐요.

## git에 올리지 않는 파일

`.gitignore`로 md 파일, `.env`, 서명 · 키 파일, pptx 같은 문서 파일을 막아 뒀어요. md는 예외로 `README.md`, `AGENTS.md`, `CLAUDE.md`(이 파일을 불러오는 한 줄짜리)와 `skills/` 안의 md만 올라가요(2026.11.11 문서를 `skills/`로 모으면서 바꿨어요). 그래서 팀이 같이 볼 문서는 `skills/` 안에 만들고, 그 밖의 곳에 만든 md나 문서 파일은 올라가지 않아요. 이미 올린 개발일정 pptx는 일반 파일처럼 변경 내역이 잡혀요. 그 밖의 새 문서 파일을 올리려면 지금 작업하는 사람에게 먼저 물어봐요.

## tools/ 스크립트

지금도 쓰는 스크립트는 `gen_erd.js` 하나예요. ERD의 원본이라서, 표나 설명을 고칠 때는 `DearUs_ERD.html`을 직접 고치지 말고 `gen_erd.js`를 고친 뒤 Node.js로 실행해서 HTML을 다시 만들어요.

**`gen_storyboard.js`와 `gen_slides.js`는 돌리지 마요.** 이 둘은 10월 초 15장 버전을 만들어서, 실행하면 지금 `DearUs_스토리보드.html`(19장, 팀원이 직접 고쳤어요)을 덮어써요. 스토리보드는 HTML을 직접 고치고, `storyboard_images/`는 새 방식으로 다시 렌더링하기 전까지 낡은 채로 둬요.

스크립트 안의 `C:/Users/Note/...` 경로는 스크립트를 만든 PC 기준이라서, 다른 PC에서 실행하려면 상수를 먼저 바꿔야 해요. 이미지 렌더링(`gen_slides.js`)에는 Google Chrome(headless) 경로도 필요해요.
