# 나에게, Dear Us — 기술 스택

> 기준일: 2026년 9월 / 플랫폼: 모바일 앱 전용 (iOS · Android, 웹 제외)
>
> 표기 규칙: 발표 · 문서에서는 'Dear Us', 코드 식별자(패키지 `com.dearus`, 저장소, 환경변수 등)는 `dearus`
>
> 최신화: 2026.10.14 스토리보드 기준으로 메신저 캡처 업로드, 시점 선택, 커플 부분을 고쳤어요. 2026.10.28에 사진은 폰 밖으로 보내지 않기로 했고, 2026.11.8에 메신저 업로드를 이미지(AI가 읽음)와 카카오톡 txt(폰 안에서 추림) 방식으로 바꾸면서 글자 읽기(OCR)와 글자 가림(마스킹)을 없앴어요. AI는 Claude Haiku 5.5로 정했어요. 2026.11.2에 서버 구현(대화 SSE, Anthropic Java SDK 스트리밍)에 맞춰 서버 표와 AI 호출 규칙을 고쳤어요. 2026.11.4에 앱을 Expo SDK 58(React Native 0.88)로 올렸고, 소셜 로그인(카카오 · 구글) 서버 검증을 만들었어요.

## 한 줄 요약

```
앱: Expo SDK 58 · React Native 0.88 · React 19.3 · TypeScript · Node.js 24 LTS
메신저 입력: 이미지(스크린샷)는 그대로 서버로 보내 AI가 읽고, 카카오톡 txt는 폰 안에서 내 말만 추려 전송 (Expo Go에서 동작)
서버: Spring Boot 4.1 · Spring Framework 7.0 · Java 21 LTS · Gradle
DB: Supabase (PostgreSQL 17)
AI: Anthropic Claude Haiku 5.5 (claude-haiku-5-5)
배포: Render (Docker, 싱가포르 지역) · Expo EAS Build
```

## 전체 구조

```
React Native 앱 (Expo) → Spring Boot 서버 (Render) → Supabase (PostgreSQL)
                                     ↘ Anthropic API (Haiku 5.5)
```

- Supabase는 **DB 용도로만** 사용하고, 인증과 비즈니스 로직은 Spring Boot가 담당해요.
- LLM API 키, DB 비밀번호 등 모든 비밀 정보는 서버에만 둬요.

---

## 1. 앱 (React Native)

| 기술 | 버전 | 비고 |
|---|---|---|
| Node.js | 24 LTS (24.3 이상) | Expo SDK 58은 Node 22.13 이상 · 24.3 이상 · 26 이상이 필요하고 홀수 버전(23 · 25)은 지원하지 않아요 |
| Expo SDK | 58 | 2026년 10월 15일 출시, React Native 0.88 포함. 2026.11.4에 57에서 올렸어요 |
| React Native | 0.88 | Expo SDK 58 기본값. 0.87부터 Strict TypeScript API가 기본이라 `react-native/Libraries/*` 깊은 import는 타입 오류예요 |
| React | 19.3 | Expo SDK 58 기본값 |
| TypeScript | Expo 기본 템플릿 버전 | `create-expo-app`이 자동 설정 |
| Expo Router | SDK 58 포함 버전 | 화면 이동. SDK 58에서 내비게이션 코어를 개편했고 네이티브 탭 경로는 `expo-router/native-tabs`예요 |
| TanStack Query | v5 | 서버 데이터 조회 · 캐싱 |
| expo-secure-store | SDK 58 호환 버전 | 로그인 토큰 저장 |
| 이미지 · 파일 선택 | Expo 이미지 · 파일 선택 기능 (패키지는 화면을 만들 때 확정) | 대화 스크린샷 또는 카카오톡 대화 내보내기 txt 고르기 |
| txt 읽기 · 내 말 추리기 | 직접 만든 읽기 규칙 (Expo Go에서 동작, 개발 빌드 불필요) | 카카오톡 대화 내보내기 txt에서 말한 사람 · 시각 · 내용을 읽고, 사용자가 고른 내 이름의 말만 남겨요. 상대 말은 폰에서 버리고, 글자를 가리는 처리는 하지 않아요. 파일 형식이 기기 · 버전마다 다를 수 있어서 안드로이드 · 아이폰에서 내보낸 파일로 시험하고, 읽지 못한 줄은 사용자에게 알려요 |
| 카카오 로그인 | @react-native-seoul/kakao-login | 개발 빌드 필요 |
| 구글 로그인 | @react-native-google-signin/google-signin | 개발 빌드 필요 |
| 애플 로그인 | expo-apple-authentication | 소셜 로그인 제공 시 iOS 필수. 서버 검증은 아직 없어요(2026.11.4 이번 범위에서 제외) |

**주의**

- Expo 패키지는 `npx expo install`로 설치해야 SDK에 맞는 버전이 들어가요.
- 스토어의 Expo Go는 최신 SDK 하나만 지원해요. 폰의 Expo Go가 SDK 58인지 먼저 확인하고, 아니면 개발 빌드(또는 `eas go`)를 써요.
- SDK 58로 정하고 발표가 끝날 때까지 고정해요(다음 SDK가 나와도 올리지 않아요).

---

## 2. 서버 (Spring Boot)

| 기술 | 버전 | 비고 |
|---|---|---|
| Java | 21 LTS | Spring Boot 4 최소 17, 공식 권장 21 또는 25 |
| Spring Boot | 4.1.x | 2026년 6월 10일 출시, 새 프로젝트 권장 버전 |
| Spring Framework | 7.0.x (7.0.8 이상) | Spring Boot가 자동 관리 |
| Spring Security | 7.x | Spring Boot가 자동 관리 |
| Spring Data JPA | Spring Boot 관리 버전 | DB 접근 |
| JWT | Spring Security OAuth2 Resource Server | 기본 제공 기능으로 JWT 발급 · 검증 |
| Jackson | 3.x | Spring 7부터 Jackson 2.x 지원 제외 |
| SSE (`SseEmitter`) | Spring MVC(`spring-boot-starter-webmvc`) | AI 응답 스트리밍 전달. WebClient · WebFlux는 쓰지 않아요 |
| 소셜 로그인 검증 | `RestClient`(Spring Web) · `NimbusJwtDecoder`(Spring Security) | 카카오는 `RestClient`로 카카오 API를 불러 토큰을 확인하고(연결 3초 · 응답 5초 제한), 구글은 `NimbusJwtDecoder`로 구글 공개키(JWKS)를 확인해요. 새 의존성은 없어요 |
| Anthropic Java SDK | 2.34.0 | `com.anthropic:anthropic-java`. 일반 호출 `complete`, 스트리밍 `createStreaming`. 공식 예제는 3.x라 올릴 때 실제 호출 시험과 함께 해요 |
| Flyway | Spring Boot 관리 버전 | 테이블을 SQL 파일(마이그레이션)로 만들고 버전 관리 |
| Actuator | Spring Boot 관리 버전 | 서버 상태 확인(`/actuator/health`), Render 배포 점검 |
| 이미지 읽기 | 서버가 사진을 받아 AI로 읽음 | 서버가 앱이 보낸 이미지를 받아 AI의 이미지 입력으로 내 말을 읽고, 읽은 직후 사진을 지워요(이미지 올리기 API `POST /uploads/messenger/images`, 2026.11.9에 만들었어요. 사진은 메모리에서만 다루고 AI에 10장씩 나눠 보내요). txt는 앱이 추린 내 말 글자(JSON)를 받아요. 서버는 글자를 가리는 처리(마스킹)를 하지 않아요. 사진을 직접 가공하거나 글자 인식을 하지 않아서 Render 서버의 이미지 처리 메모리는 걱정하지 않아도 돼요 |
| 빌드 도구 | Gradle | Spring Initializr 기본 래퍼 버전 |

**Spring Initializr 의존성**

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Spring Security
- OAuth2 Resource Server
- Validation
- Lombok
- Flyway Migration
- Spring Boot Actuator

Initializr에는 없지만 직접 더한 의존성은 `com.anthropic:anthropic-java`예요.

**주의**

- 인터넷 튜토리얼 대부분이 Spring Boot 3 기준이에요. Spring Boot 4에서는 javax 패키지가 사라지고 JUnit 4 지원이 제외돼서, 예제 코드보다 공식 문서를 우선해요.

### 패키지 구조

```
com.dearus
├── auth       # 소셜 로그인, JWT 발급
├── diary      # 일기 CRUD, 감정 태그, 캘린더 조회
├── upload     # 메신저 내 말 글자 · 스크린샷 수신, 스크린샷에서 내 말 읽기, 원문 1년 보관 · 만료 삭제, 요약
├── persona    # 선택 구간(전후 2주 또는 그날 하루) 일기 · 메신저 주간 요약 분석, 분석 결과 재사용, 페르소나 생성
├── chat       # Time-Slip Chat (LLM 호출)
├── report     # 회고 리포트 생성
├── couple     # 초대 코드, 양측 동의, 함께 볼 날, 상대 페르소나 대화 조건 확인, 같은 날 두 대화 비교
└── common     # 암호화, 예외 처리, 설정
```

### 주요 API

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | /auth/{provider} | 소셜 로그인. 카카오는 access token을 카카오 API로, 구글은 ID token을 구글 공개키로 서버가 확인해요(애플은 아직 501). 제공자마다 별개 계정이고 이메일은 받지 않아요 |
| PUT / DELETE | /me/consent | 서비스 이용 동의 · AI 분석 동의 철회 |
| GET / POST / PUT / DELETE | /diaries | 일기 조회 · 작성 · 수정 · 삭제 (캘린더용 작성일 · 감정 태그 포함) |
| POST | /uploads/messenger | 메신저 대화 올리기. txt는 앱이 폰 안에서 추린 내 말 글자(JSON)를 보내고, 이미지는 `POST /uploads/messenger/images`(multipart, 대화 순서대로)로 받아요. 요약은 비동기(202 + 상태 조회) |
| GET | /personas/shortcuts · /personas/preview | 시점 고르기 (바로가기는 전후 2주, 캘린더 날짜는 그날 하루) |
| GET | /chats | 과거의 나 목록 |
| POST | /chats | 과거의 나와 대화 세션 생성. 요청에 시점 포함, 최대 1년 전까지. 답변 스트리밍은 `POST /chats/{chatId}/messages` (SSE `token` · `safety` · `done` · `error`, 또는 `Accept: application/json`) |
| POST | /chats/{chatId}/end | 대화 끝내기. 리포트 만들기를 시작하고(뒤에서 AI가 만들어요), 실패한 리포트는 다시 부르면 재시도해요 |
| GET | /chats/{chatId}/report | 회고 리포트. 만드는 중이면 202(1~2초 뒤 다시), 완성이면 200 |
| GET | /me/usage/today | 오늘 대화 시간(분). 메시지 사이 간격의 합이고 한 간격은 5분까지만 인정해요 |
| POST | /couples/invite · /couples/invite/accept | 초대 코드 발급 · 입력 |
| POST | /couples/consent | 동의 처리 |
| GET | /couples/days · /couples/comparisons | 함께 볼 날, 같은 날 두 사람이 각자 상대 페르소나와 나눈 대화 비교. 상대 페르소나와의 대화는 `POST /chats`(`forCouple: true`)로 시작해요 |

전체 API(요청 · 응답 형태 포함)는 `server/openapi.yaml`에 있어요. 위 표는 요약이에요.

---

## 3. DB

| 기술 | 버전 | 비고 |
|---|---|---|
| Supabase | 무료 플랜 | DB 500MB, 7일간 활동 없으면 일시 정지, 자동 백업 없음(백업 없이 진행하기로 했어요, 2026.11.11). 지역은 서울(`ap-northeast-2`) |
| PostgreSQL | 17 | Supabase 플랫폼 기본 버전 |

**테이블 설계:** [DearUs_ERD.html](../DearUs_ERD.html) 참고

**연결 설정**

- Supabase 직접 연결 주소는 IPv6 전용이라 Render에서 연결되지 않을 수 있어요.
- 대시보드의 Connect 메뉴에서 **Connection pooler (Session mode)** 주소를 JDBC URL로 사용해요.

---

## 4. AI

> AI는 Claude Haiku 5.5로 정했어요(2026.10.28). 글자(텍스트)와 이미지로 올린 메신저 스크린샷(이미지 입력)을 보내요. 회사별 가격 · 약관 비교와 말투 품질 시험은 기획서(`skills/plan/`) 8.3 · 8.5에 있어요.

| 기술 | 버전 | 비고 |
|---|---|---|
| Anthropic Claude API | claude-haiku-5-5 | 서버에서만 호출. Java 공식 SDK(`com.anthropic:anthropic-java`)를 쓰고 키는 환경변수 `ANTHROPIC_API_KEY`로만 둬요 |

**선정 근거**

- **데이터 정책:** 상업 약관이라 기본값으로 모델 학습에 쓰지 않아요. 입력 · 출력은 Anthropic 서버에서 30일 안에 삭제되고 정책 위반으로 걸리면 최대 2년 남아요. 보관 기간을 숫자로 밝혀서 동의 화면에 그대로 쓸 수 있어요. 기획서 파기 정책과 바로 이어져요.
- **한국어 말투 재현:** 카카오톡 말투, 반말 · 존댓말 재현 품질을 기획서 8.5의 블라인드 시험으로 확인해요 (미흡하면 대안이나 한 단계 위 모델을 검토해요)
- **입력 형태:** 글자(텍스트)와, 이미지로 올린 메신저 스크린샷(이미지 입력)을 보내요.
- **스트리밍 지원:** 스트리밍 응답을 SSE로 앱에 전달해요. 개발 목표 "첫 응답 5초 이내"를 달성하는 데 필요해요.
- **비용:** 입력 $0.10 · 출력 $0.50 (100만 토큰당, 요청 한 번의 프롬프트가 10만 토큰 이하일 때. thinking 토큰은 출력 단가로 청구)
- **호출 규칙:** `temperature` · `top_p` · `top_k`는 못 보내요(400). 마지막 assistant 메시지를 미리 채우는 방식(prefill)도 못 써요. thinking은 기본으로 켜져 있고 `max_tokens`에 포함되니 대화는 `effort: low`로 시작해요. 응답이 `refusal`로 끝날 수 있고 서버 쪽 자동 대체 모델이 없어요. 지금은 서버 오류(`AI_REFUSED`)로 알리고, 대체 답을 쓸지는 베타에서 얼마나 나는지 보고 정해요(`skills/backlog.md` 1장). 대화 기록은 글자만 다시 보내므로 thinking 블록 재전송 규칙에는 걸리지 않아요
- **AI 교체 가능성:** 서버에서 AI 호출부를 인터페이스로 분리하고 주소 · 키 · 모델명은 환경변수로 관리해요

---

## 5. 배포 · 개발 도구

| 기술 | 버전 | 비고 |
|---|---|---|
| Render | 무료 (발표 기간 Starter 권장) | Docker로 Spring Boot 배포. 지역은 싱가포르예요(서울 지역이 없어서 서울 DB와 가장 가까운 곳, 2026.11.11). 서비스를 만든 뒤에는 지역을 바꿀 수 없어요 |
| Docker 베이스 이미지 | eclipse-temurin:21-jre | Java 21 실행 환경 |
| Expo EAS Build | 최신 CLI | 테스트용 앱 빌드 (소셜 로그인 때문에 개발 빌드가 필요해요) |
| Android Studio / Xcode | 최신 안정 버전 | 개발 빌드 실행 |
| GitHub | - | 코드 · 이슈 관리 |
| Postman | - | API 테스트 |

**비밀 정보 관리**

- DB 비밀번호, JWT 키, LLM API 키, 암호화 키는 Render 환경변수로 관리해요. 소셜 로그인 설정(`KAKAO_APP_ID`, `GOOGLE_CLIENT_IDS`)은 비밀은 아니지만 같은 곳에서 관리하고, 비워 두면 그 로그인 방식은 501이에요.
- GitHub에는 절대 올리지 않아요.

---

## 6. 기능별 담당 영역

| 기능 | 앱 | 서버 | DB |
|---|---|---|---|
| 로그인 · 동의 | 소셜 로그인, 약관 · 동의 화면 | 토큰 검증 · JWT 발급, 동의 기록 | 사용자 (동의 시각) |
| 일기 · 캘린더 | 작성 화면, 감정 색 점 캘린더(작성일 표시), 날짜 탭 | 일기 CRUD API | 일기, 감정 태그 |
| 메신저 분석 | 이미지 · txt 고르기, txt는 내 이름 고르기 화면 (폰 안에서 내 말만 추림) | 내 말 글자와 이미지 수신, 이미지에서 내 말 읽기, 원문 1년 보관, AI 요약 | 메신저 원문(`messenger_uploads.original_enc`) · 주간 요약 · 말투 분석 결과 (암호화), 업로드 기간 |
| Time-Slip Chat | 과거의 나 목록, 시점 고르기, 메신저형 채팅 UI | 저장된 시점 분석 재사용 또는 일기 분석 · 페르소나 프롬프트 구성, LLM 스트리밍 | 대화 기록 |
| 회고 리포트 | 리포트 카드 화면 | 대화 종료 시 3항목 자동 생성 | 리포트 |
| 커플 모드 | 초대 코드, 동의 화면, 함께 볼 날 · 상대 페르소나 채팅 · 같은 날 비교 화면 | 양측 동의 확인, 연인의 기록으로 상대 페르소나 구성(같은 엔진), 양측 세션 요약 비교, 해제 시 즉시 삭제 | 커플 연결, 동의 여부, 리포트(기억 요약 · 간극) |

**개발 순서:** 로그인 · 동의 → 일기 → 메신저 분석 → 채팅(목록 · 시점 고르기) → 회고 리포트 → 커플 모드(상대 페르소나 대화 · 같은 날 비교)

---

## 7. 예상 비용 (개발 단계)

| 항목 | 플랜 | 월 비용 |
|---|---|---|
| Supabase | 무료 | 0원 |
| Render | 무료 | 0원 (발표 기간 Starter는 월 7달러) |
| LLM API (Claude Haiku 5.5) | 사용량 과금 (입력 $0.10 · 출력 $0.50 / 100만 토큰, 프롬프트 10만 토큰 이하) | 수업 규모 약 $1~5 (20턴 대화 1세션 약 $0.013 기준 추정, 메신저 스크린샷은 1080×2400 한 장이 약 3,350토큰이라 12장을 올리면 입력 약 4만 토큰, 약 $0.004가 더 듦. 읽어낸 글자와 추론 토큰은 뺀 계산값) |

**발표 전 체크리스트**

- [ ] Render 무료 서버는 15분간 요청이 없으면 잠들어요. 발표 직전에 서버를 깨우거나 Starter로 전환해요.
- [ ] Supabase 무료 DB는 7일간 활동이 없으면 일시 정지돼요. 발표 며칠 전에 상태를 확인해요.
- [ ] 앱의 txt 읽기 규칙이 안드로이드 · 아이폰에서 내보낸 카카오톡 txt를 충분히 읽는지 테스트해요(내 이름의 줄만 남는 비율 100%가 목표). txt로 올릴 때 상대 말이 서버로 나가지 않는지 개발 중에 앱의 네트워크 요청으로 확인하고, 이미지는 읽은 직후 서버에서 지워지는지 확인해요.
- [ ] Anthropic Console에서 API 키를 만들고 크레딧 또는 결제 수단을 등록해요. 키는 서버 환경변수(`ANTHROPIC_API_KEY`)로만 두어요. 동의 화면에 "AI 회사에 최대 30일 남는다"는 안내를 넣은 뒤 실제 참가자 데이터를 써요.

**스토어 출시 시 추가 비용 (수업 시연만 할 경우 불필요)**

- 애플 개발자 계정: 연 99달러
- 구글 플레이 개발자 계정: 최초 1회 25달러

---

## 참고 자료

- Expo SDK 58 릴리스: https://expo.dev/changelog/sdk-58
- Expo SDK 58 업그레이드 안내: https://expo.dev/blog/how-to-upgrade-to-sdk-58
- Spring Boot 버전 정보: https://www.marcobehler.com/guides/spring-and-spring-boot-versions
- Spring Boot 4 · Spring Framework 7 변경점: https://www.baeldung.com/spring-boot-4-spring-framework-7
- Supabase 요금제: https://supabase.com/pricing
- Supabase Edge Functions 제한: https://supabase.com/docs/guides/functions/limits
- Supabase PostgreSQL 17 전환 안내: https://supabase.com/changelog/46080-self-hosted-supabase-upgrading-from-pg-15-to-17-breaking-change
- Render 요금 비교: https://instapods.com/compare/railway-vs-render/
- Claude API 요금: https://platform.claude.com/docs/en/about-claude/pricing
- Claude API 데이터 보관: https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-data
- Claude 모델 지원 중단 안내: https://platform.claude.com/docs/en/about-claude/model-deprecations
