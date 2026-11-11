# Dear Us 앱

Expo SDK 58 · React Native 0.88 · TypeScript · Expo Router · TanStack Query v5로 만드는 모바일 앱이에요. UI/UX는 레포 맨 위의 `DearUs_스토리보드.html`을 따르고, 서버와의 약속은 `server/openapi.yaml`이에요. 프로젝트 전체 안내는 [레포 README](../README.md)와 [AGENTS.md](../AGENTS.md)에 있어요.

## 실행하기

```bash
npm install
npx expo start
```

터미널에 뜨는 QR을 실제 폰의 Expo Go(SDK 58)로 찍어요. 폰과 PC는 같은 Wi-Fi여야 해요.

- **아이폰 실기기**는 Expo CLI와 Expo Go에 **같은 Expo 계정**으로 로그인해야 열려요. PC에서 `npx expo login`, 폰의 Expo Go에서 계정 아이콘으로 로그인해요. Windows에서 브라우저 로그인이 죽으면 `npx expo login --no-browser`를 써요. (안드로이드와 시뮬레이터는 로그인이 필요 없어요.)
- 스토어의 Expo Go는 최신 SDK 하나만 지원해서, 폰의 Expo Go가 SDK 58이 아니면 열리지 않아요.
- 소셜 로그인은 Expo Go에서 안 돼서 개발 빌드(EAS)가 필요해요. 메신저 업로드(이미지 · txt)는 글자 읽기 모듈이 필요 없어서 Expo Go에서도 만들 수 있어요.

## 가짜 데이터 모드와 실제 서버 모드

앱은 기본으로 **가짜 데이터(`mock`)**로 동작해서 서버 없이도 화면을 볼 수 있어요. 실제 서버에 붙이려면 `.env.example`을 `.env`로 복사해서 값을 바꿔요(`.env`는 git에 올라가지 않아요).

```bash
EXPO_PUBLIC_API_MODE=live
EXPO_PUBLIC_API_URL=http://<내 PC의 IP>:8080
```

- 폰에서는 `localhost`가 PC가 아니라 폰 자신이라서 PC의 IP(PowerShell의 `ipconfig`)를 써요.
- 값을 바꾸면 `npx expo start -c`로 다시 켜요.
- 서버는 `server/`에서 `./gradlew bootRun`으로 켜요. AI 키 없이 보려면 `AI_PROVIDER=fake`, 가짜 로그인은 `DEV_LOGIN_ENABLED=true`예요. 지금 로그인 버튼은 가짜라서 서버의 개발용 로그인(`POST /auth/dev`)을 써요.

## 스크립트

| 명령 | 하는 일 |
|---|---|
| `npm run typecheck` | 타입 검사 (`tsc --noEmit`) |
| `npm run lint` | 린트 |
| `npm run gen:api` | `../server/openapi.yaml`에서 `src/api/schema.d.ts`를 다시 만들어요. 서버 명세가 바뀌면 꼭 해요 |

## 폴더 구조

| 폴더 | 내용 |
|---|---|
| `src/app/` | 화면(Expo Router 파일 라우팅). 탭은 `(tabs)/`, 그 밖에 `login` · `consent` · `diary/[date]` · `persona/pick` · `chat/[id]` · `report/[id]` · `settings` · `legal/[type]` |
| `src/api/` | 서버 호출의 유일한 입구예요. 화면은 `fetch`를 직접 쓰지 않고 여기의 함수(`chats.ts`, `diaries.ts` …)만 불러요. `mock/`은 가짜 서버, `schema.d.ts`는 명세에서 만든 타입이에요 |
| `src/features/` | 화면을 이루는 기능 묶음(일기 `diary/`, 과거의 나 `past/`)과 데이터 훅 |
| `src/components/` | 여러 화면이 같이 쓰는 작은 부품 |
| `src/session/` | 로그인 상태와 토큰 보관 |
| `src/theme/` · `src/lib/` · `src/legal/` | 색 · 글꼴, 날짜 · 알림창 도우미, 약관 문서 |

## 지켜야 할 규칙

- 앱은 Spring Boot 서버만 불러요. AI API나 Supabase를 직접 부르지 않아요.
- 메신저 업로드는 두 갈래예요. txt는 폰 안에서 읽어 내 말만 추리고, 사용자가 확인한 글자를 그대로 `POST /uploads/messenger`로 보내요(상대 말은 폰에서 버려요). 이미지는 가공하지 않고 `POST /uploads/messenger/images`(multipart, 한 번에 최대 100장, 대화 순서대로)로 보내요. 메신저 업로드는 개인정보 이용에 동의해야만 쓸 수 있어요.
- 채팅 답변은 SSE로 와요. React Native 기본 `fetch`는 스트림이 불안정해서 `expo/fetch`로 받아요(`src/api/client.ts`).
- 명세에 없는 필드나 경로는 앱에서 마음대로 만들지 않고, 필요하면 명세를 먼저 고쳐요.
- Expo 패키지는 `npx expo install`로 설치해요(SDK에 맞는 버전이 들어가요).
- React Native 0.88부터 `ref`에는 컴포넌트가 아니라 인스턴스 타입을 써요(예: `useRef<ScrollViewInstance>`).
- API 키, DB 비밀번호 같은 비밀 값은 앱에 넣지 않아요.

## 자주 만나는 문제

- **새 화면을 만들었는데 `"/…" is not assignable to type …` 타입 오류가 나요.** 자동 생성 타입(`.expo/types/router.d.ts`)이 오래된 거예요. `npx expo start`를 한 번 켜면 다시 만들어져요.
- **연결 시간이 초과돼요.** 폰과 PC가 같은 Wi-Fi인지, Windows 방화벽이 Node를 막지 않았는지 확인해요. 안 되면 `npx expo start --tunnel`을 써요(Expo 계정 로그인이 필요할 수 있어요).
- **`live` 모드인데 서버에 닿지 않아요.** `EXPO_PUBLIC_API_URL`이 PC의 IP인지, 서버가 켜져 있는지, 방화벽이 8080 포트를 막지 않았는지 확인해요.
