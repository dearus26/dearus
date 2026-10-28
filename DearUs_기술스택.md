# 나에게, Dear Us — 기술 스택

> 기준일: 2026년 9월 / 플랫폼: 모바일 앱 전용 (iOS · Android, 웹 제외)
>
> 표기 규칙: 발표 · 문서에서는 'Dear Us', 코드 식별자(패키지 `com.dearus`, 저장소, 환경변수 등)는 `dearus`
>
> 최신화: 2026.10.14 스토리보드 기준으로 메신저 캡처 업로드, 시점 선택, 커플 부분을 고쳤어요. 2026.10.28에 사진은 폰 밖으로 보내지 않기로 해서, 글자 읽기와 가리기를 앱(폰) 안에서 해요. AI 회사도 Gemini로 고정하지 않아요.

## 한 줄 요약

```
앱: Expo SDK 57 · React Native 0.86 · React 19.2 · TypeScript · Node.js 24 LTS
글자 읽기: 폰 안에서 (Android Google ML Kit · iOS Apple Vision), 사진은 서버로 보내지 않음
서버: Spring Boot 4.1 · Spring Framework 7.0 · Java 21 LTS · Gradle
DB: Supabase (PostgreSQL 17)
AI: 기본 후보 Google Gemini 3.5 Flash-Lite (gemini-3.5-flash-lite · 유료 등급), 회사는 확정 아님
배포: Render (Docker) · Expo EAS Build
```

## 전체 구조

```
React Native 앱 (Expo) → Spring Boot 서버 (Render) → Supabase (PostgreSQL)
                                     ↘ Gemini API (3.5 Flash-Lite)
```

- Supabase는 **DB 용도로만** 사용하고, 인증과 비즈니스 로직은 Spring Boot가 담당해요.
- LLM API 키, DB 비밀번호 등 모든 비밀 정보는 서버에만 둬요.

---

## 1. 앱 (React Native)

| 기술 | 버전 | 비고 |
|---|---|---|
| Node.js | 24 LTS | React Native 0.85 이상은 Node.js v20.19.4 이상 필요 |
| Expo SDK | 57 (expo 57.0.9 이상) | 2026년 6월 30일 출시, React Native 0.86 포함 |
| React Native | 0.86 | Expo SDK 57 기본값 |
| React | 19.2 | Expo SDK 56 · 57 공통 |
| TypeScript | Expo 기본 템플릿 버전 | `create-expo-app`이 자동 설정 |
| Expo Router | SDK 57 포함 버전 | 화면 이동 (SDK 56부터 React Navigation 의존성 제거) |
| TanStack Query | v5 | 서버 데이터 조회 · 캐싱 |
| expo-secure-store | SDK 57 호환 버전 | 로그인 토큰 저장 |
| expo-image-picker | SDK 57 호환 버전 | 메신저 캡처 사진 선택 (여러 장) |
| 글자 읽기(OCR) · 가리기 | 개발 빌드 필요 (Expo Go 불가) | 사진은 폰 밖으로 보내지 않아요. 안드로이드는 Google ML Kit 글자 인식 v2(한국어 모델은 Google Play 서비스가 내려받아요), iOS는 Apple Vision(한국어는 iOS 16부터라는 설명이 있고 공식 문서로는 확인 필요)을 써요. Expo용 연결 패키지로 `expo-mlkit-ocr`(줄 단위 좌표), `@zhanziyang/expo-text-extractor`, `rn-mlkit-ocr` 등이 있지만 모두 0.x~1.0이라 Expo SDK 57과 맞는지 확인하고, 안 맞으면 Expo Modules API로 직접 얇게 감싸요. 전화 · 계좌 · 주소 · 이메일 가림 규칙과 내 말풍선 구분(카카오톡은 오른쪽 · 노란색)은 앱의 TypeScript로 구현해요 |
| 직접 가리기 그리기 | 화면을 만들 때 확정 | 사진 위에 손가락으로 사각형 · 브러시 영역 그리기. Expo에 포함된 제스처 · 애니메이션 라이브러리(후보)로 만들어요. 가림은 사진이 아니라 글자에 적용해요(가림 영역과 겹치는 글자를 '[전화번호]' 같은 표시로 바꿔서 보내요) |
| 카카오 로그인 | @react-native-seoul/kakao-login | 개발 빌드 필요 |
| 구글 로그인 | @react-native-google-signin/google-signin | 개발 빌드 필요 |
| 애플 로그인 | expo-apple-authentication | 소셜 로그인 제공 시 iOS 필수 |

**주의**

- Expo 패키지는 `npx expo install`로 설치해야 SDK에 맞는 버전이 들어가요.
- expo 57.0.9 미만은 reanimated 등을 사용할 때 메모리 사용량이 크게 늘어나는 문제가 있어서 57.0.9 이상을 사용해요.
- 다음 SDK가 2026년 9~10월 중 출시될 수 있어요. 프로젝트 시작 시 버전을 정하고 발표가 끝날 때까지 고정해요.

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
| WebClient + SSE | Spring Boot 관리 버전 | AI 응답 스트리밍 전달 |
| Flyway | Spring Boot 관리 버전 | 테이블을 SQL 파일(마이그레이션)로 만들고 버전 관리 |
| Actuator | Spring Boot 관리 버전 | 서버 상태 확인(`/actuator/health`), Render 배포 점검 |
| 사진 · 글자 읽기 | 서버에서 하지 않음 | 서버는 사진을 받지 않아요(수신 API가 없고 이미지를 보내면 415). 앱이 가린 글자(JSON)만 보내고, 서버는 받은 글자를 한 번 더 규칙으로 가려요(안전장치). 그래서 Render 서버에서 이미지 처리 · 글자 읽기의 메모리를 걱정하지 않아도 돼요 |
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

**주의**

- 인터넷 튜토리얼 대부분이 Spring Boot 3 기준이에요. Spring Boot 4에서는 javax 패키지가 사라지고 JUnit 4 지원이 제외돼서, 예제 코드보다 공식 문서를 우선해요.

### 패키지 구조

```
com.dearus
├── auth       # 소셜 로그인, JWT 발급
├── diary      # 일기 CRUD, 감정 태그, 캘린더 조회
├── upload     # 메신저 가린 글자 수신 · 한 번 더 가림 · 요약
├── persona    # 선택 구간(전후 2주 또는 그날 하루) 일기 · 메신저 주간 요약 분석, 분석 결과 재사용, 페르소나 생성
├── chat       # Time-Slip Chat (LLM 호출)
├── report     # 회고 리포트 생성
├── couple     # 초대 코드, 양측 동의, 같은 날 기억 비교
└── common     # 암호화, 민감정보 마스킹, 예외 처리, 설정
```

### 주요 API

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | /auth/{provider} | 소셜 로그인 |
| PUT / DELETE | /me/consent | 서비스 이용 동의 · AI 분석 동의 철회 |
| GET / POST | /diaries | 일기 조회 · 작성 (캘린더용 작성일 · 감정 태그 포함) |
| POST | /uploads/messenger | 메신저 대화 올리기. 앱이 폰 안에서 가린 글자(JSON)만 보내고 사진은 받지 않아요. 요약은 비동기(202 + 상태 조회) |
| GET | /personas/shortcuts · /personas/preview | 시점 고르기 (바로가기는 전후 2주, 캘린더 날짜는 그날 하루) |
| GET | /chats | 과거의 나 목록 |
| POST | /chats | 과거의 나와 대화 세션 생성. 요청에 시점 포함, 최대 1년 전까지. 답변 스트리밍은 `POST /chats/{chatId}/messages` |
| POST | /couples/invite · /couples/invite/accept | 초대 코드 발급 · 입력 |
| POST | /couples/consent | 동의 처리 |
| GET | /couples/days · /couples/comparisons | 함께 볼 날, 같은 날 두 사람의 기억 비교 |

전체 API(요청 · 응답 형태 포함)는 `server/openapi.yaml`에 있어요. 위 표는 요약이에요.

---

## 3. DB

| 기술 | 버전 | 비고 |
|---|---|---|
| Supabase | 무료 플랜 | DB 500MB, 7일간 활동 없으면 일시 정지 |
| PostgreSQL | 17 | Supabase 플랫폼 기본 버전 |

**테이블 설계:** [DearUs_ERD.html](DearUs_ERD.html) 참고

**연결 설정**

- Supabase 직접 연결 주소는 IPv6 전용이라 Render에서 연결되지 않을 수 있어요.
- 대시보드의 Connect 메뉴에서 **Connection pooler (Session mode)** 주소를 JDBC URL로 사용해요.

---

## 4. AI

> AI 회사는 Gemini로 고정하지 않아요(2026.10.28). 글자(텍스트)만 보내므로 이미지 입력이 필요 없고, 학습 미사용 · 보관 기간 · 한국어 말투 재현 · 스트리밍 · 가격을 비교해 정해요. 아래는 지금 기본 후보인 Gemini 기준이고, 다른 회사의 약관은 아직 확인하지 않았어요.

| 기술 | 버전 | 비고 |
|---|---|---|
| AI API (기본 후보: Google Gemini) | gemini-3.5-flash-lite | 서버에서만 호출, **유료 등급 필수** (Google AI Studio에서 프로젝트에 Cloud Billing 연결) |

**선정 근거**

- **데이터 정책:** 유료 등급은 프롬프트 · 응답을 모델 학습에 사용하지 않아요 (무료 등급은 사용해요). 악용 방지용 임시 기록은 남을 수 있어요. 기획서 파기 정책과 바로 이어져요.
- **한국어 말투 재현:** 카카오톡 말투, 반말 · 존댓말 재현 품질을 샘플 대화로 미리 확인해요 (미흡하면 상위 모델을 검토해요)
- **입력 형태:** 글자(텍스트)만 보내요. 캡처 사진은 AI에 보내지 않아서 이미지 입력이 필요 없어요.
- **스트리밍 지원:** 스트리밍 응답을 SSE로 앱에 전달해요. 개발 목표 "첫 응답 5초 이내"를 달성하는 데 필요해요.
- **비용:** 입력 $0.30 · 출력 $2.50 (100만 토큰당, thinking 토큰은 출력에 포함)
- **AI 교체 가능성:** 서버에서 AI 호출부를 인터페이스로 분리하고 주소 · 키 · 모델명은 환경변수로 관리해요

---

## 5. 배포 · 개발 도구

| 기술 | 버전 | 비고 |
|---|---|---|
| Render | 무료 (발표 기간 Starter 권장) | Docker로 Spring Boot 배포 |
| Docker 베이스 이미지 | eclipse-temurin:21-jre | Java 21 실행 환경 |
| Expo EAS Build | 최신 CLI | 테스트용 앱 빌드 (글자 읽기와 소셜 로그인 때문에 개발 빌드가 필요해요) |
| Android Studio / Xcode | 최신 안정 버전 | 개발 빌드 실행 |
| GitHub | - | 코드 · 이슈 관리 |
| Postman | - | API 테스트 |

**비밀 정보 관리**

- DB 비밀번호, JWT 키, LLM API 키, 암호화 키는 Render 환경변수로 관리해요.
- GitHub에는 절대 올리지 않아요.

---

## 6. 기능별 담당 영역

| 기능 | 앱 | 서버 | DB |
|---|---|---|---|
| 로그인 · 동의 | 소셜 로그인, 약관 · 동의 화면 | 토큰 검증 · JWT 발급, 동의 기록 | 사용자 (동의 시각) |
| 일기 · 캘린더 | 작성 화면, 감정 색 점 캘린더(작성일 표시), 날짜 탭 | 일기 CRUD API | 일기, 감정 태그 |
| 메신저 분석 | 사진 선택, 폰 안 글자 읽기 · 자동 가림 · 검수 · 직접 가리기 화면 | 가린 글자 수신, 한 번 더 가림, AI 요약(내 말풍선), 받은 글자는 요약 뒤 삭제 | 메신저 주간 요약 · 말투 분석 결과 (암호화), 업로드별 개수 |
| Time-Slip Chat | 과거의 나 목록, 시점 고르기, 메신저형 채팅 UI | 저장된 시점 분석 재사용 또는 일기 분석 · 페르소나 프롬프트 구성, LLM 스트리밍 | 대화 기록 |
| 회고 리포트 | 리포트 카드 화면 | 대화 종료 시 3항목 자동 생성 | 리포트 |
| 커플 모드 | 초대 코드, 동의 화면, 함께 볼 날 · 같은 날 비교 화면 | 양측 동의 확인, 양측 세션 요약 비교, 해제 시 즉시 삭제 | 커플 연결, 동의 여부, 리포트(기억 요약 · 간극) |

**개발 순서:** 로그인 · 동의 → 일기 → 메신저 분석 → 채팅(목록 · 시점 고르기) → 회고 리포트 → 커플 모드(같은 날 비교)

---

## 7. 예상 비용 (개발 단계)

| 항목 | 플랜 | 월 비용 |
|---|---|---|
| Supabase | 무료 | 0원 |
| Render | 무료 | 0원 (발표 기간 Starter는 월 7달러) |
| LLM API (Gemini 3.5 Flash-Lite) | 사용량 과금 (입력 $0.30 · 출력 $2.50 / 100만 토큰) | 수업 규모 약 $1~5 (20턴 대화 1세션 약 $0.04 기준 추정, 캡처는 글자만 보내서 이미지 입력 비용 없음) |

**발표 전 체크리스트**

- [ ] Render 무료 서버는 15분간 요청이 없으면 잠들어요. 발표 직전에 서버를 깨우거나 Starter로 전환해요.
- [ ] Supabase 무료 DB는 7일간 활동이 없으면 일시 정지돼요. 발표 며칠 전에 상태를 확인해요.
- [ ] 앱의 글자 읽기가 iOS · 안드로이드 두 기기에서 한국어 카카오톡 캡처를 충분히 읽는지 시험해요(가짜 캡처 30~50장). 사진이 서버로 나가지 않는지 개발 중에 앱의 네트워크 요청으로 확인해요.
- [ ] (Gemini를 쓸 경우) Gemini API 프로젝트에 Cloud Billing이 연결되어 유료 등급인지 확인한 뒤 실제 참가자 데이터를 사용해요. (무료 등급은 입력이 모델 개선에 쓰일 수 있어요)

**스토어 출시 시 추가 비용 (수업 시연만 할 경우 불필요)**

- 애플 개발자 계정: 연 99달러
- 구글 플레이 개발자 계정: 최초 1회 25달러

---

## 참고 자료

- Expo SDK 57 릴리스: https://expo.dev/changelog/sdk-57
- Expo SDK 56 릴리스: https://expo.dev/sdk/56
- Spring Boot 버전 정보: https://www.marcobehler.com/guides/spring-and-spring-boot-versions
- Spring Boot 4 · Spring Framework 7 변경점: https://www.baeldung.com/spring-boot-4-spring-framework-7
- Supabase 요금제: https://supabase.com/pricing
- Supabase Edge Functions 제한: https://supabase.com/docs/guides/functions/limits
- Supabase PostgreSQL 17 전환 안내: https://supabase.com/changelog/46080-self-hosted-supabase-upgrading-from-pg-15-to-17-breaking-change
- Render 요금 비교: https://instapods.com/compare/railway-vs-render/
- Gemini API 요금: https://ai.google.dev/gemini-api/docs/pricing
- Gemini API 데이터 사용 약관: https://ai.google.dev/gemini-api/terms
- Gemini API 모델 지원 중단 안내: https://ai.google.dev/gemini-api/docs/deprecations
