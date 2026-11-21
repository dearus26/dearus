# Dear Us 소셜 로그인 설정 (카카오 · 구글, Android)

> 2026.11.21에 앱 코드를 연결했어요. 팀원 두 명 모두 안드로이드라서 EAS 개발 빌드(APK)로 시험해요. 이 문서는 각 콘솔에서 사람이 직접 해야 하는 설정을 순서대로 모았어요.

## 어떻게 동작하나요

- **개발 빌드 + live 모드:** 진짜 카카오 · 구글 로그인을 해요. 앱이 토큰을 받아 `POST /auth/{kakao,google}`로 보내고, 서버가 검증해요.
- **Expo Go 또는 mock 모드:** 네이티브 SDK를 쓸 수 없어서 개발용 로그인(`POST /auth/dev`)을 써요. 각자 PC의 서버에서만 켜져 있고, **배포 서버에서는 꺼져 있어요**(이 레포가 공개라서요).
- **애플:** 버튼만 있어요. 누르면 서버가 501로 "준비 중"을 알려줘요(2026.11.18 결정).
- 코드는 `app/src/session/socialLogin.ts`, 빌드 설정은 `app/eas.json` · `app/app.config.js`예요.

## 어떤 값이 어디에 들어가나요

| 값 | 어디서 얻나요 | 어디에 넣나요 |
|---|---|---|
| 카카오 네이티브 앱 키 | 카카오 콘솔 | EAS 환경변수, `app/.env`의 `EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY` |
| 카카오 앱 ID(숫자) | 카카오 콘솔 | Render와 `server/.env`의 `KAKAO_APP_ID` |
| 구글 **웹** 클라이언트 ID | Google Cloud | Render와 `server/.env`의 `GOOGLE_CLIENT_IDS`, `app/.env`의 `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` |
| 구글 Android 클라이언트 | Google Cloud | 만들어 두기만 해요. 앱 코드에는 넣지 않아요 |
| 앱 서명 SHA-1 · 카카오 키 해시 | 첫 EAS 빌드 | 카카오 콘솔, Google Cloud |

이 값들은 git에 올리지 않아요. `app/.env`와 `server/.env`는 `.gitignore`에 막혀 있어요.

## 설정 순서

### 1. EAS 프로젝트 연결

`app/`에서 한 번만 해요. `app.json`에 프로젝트 ID가 생기니 커밋해요.

```powershell
npx eas-cli init
```

### 2. 카카오 앱 만들기

[카카오 개발자 콘솔](https://developers.kakao.com)에서 앱을 만들고 이렇게 설정해요.

1. 앱 키에서 **네이티브 앱 키**와 **앱 ID**를 적어 둬요.
2. 카카오 로그인을 **켜요**.
3. 동의항목에서 **닉네임**을 설정해요. 이메일은 요청하지 않아요.
4. 플랫폼에 **Android**를 더하고 패키지명 `com.dearus`를 넣어요. 키 해시는 5번에서 넣어요.

### 3. EAS에 카카오 키 넣기

```powershell
npx eas-cli env:set development --name EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY --value <네이티브 앱 키> --visibility plaintext
```

### 4. 첫 개발 빌드

처음 빌드할 때 Android 서명 키를 만들지 물으면 **Yes**를 골라요. 이 키를 EAS가 보관해서 두 사람이 같은 APK를 써요.

```powershell
npx eas-cli build -p android --profile development
```

### 5. 서명 값을 콘솔에 넣기

SHA-1을 확인해요(Android → development 프로필의 Keystore).

```powershell
npx eas-cli credentials -p android
```

SHA-1을 카카오 키 해시로 바꿔서 카카오 콘솔의 Android 플랫폼에 넣어요.

```powershell
$sha1 = 'AA:BB:CC:...'; [Convert]::ToBase64String([byte[]]($sha1 -split ':' | ForEach-Object { [Convert]::ToByte($_, 16) }))
```

### 6. 구글 클라이언트 만들기

[Google Cloud 콘솔](https://console.cloud.google.com)에서 프로젝트를 만들고 이렇게 설정해요.

1. OAuth 동의 화면을 **외부(External)** · **테스트** 상태로 만들고, 두 사람의 구글 계정을 **테스트 사용자**로 더해요.
2. OAuth 클라이언트 **웹 애플리케이션**을 만들어요. 이 클라이언트 ID가 서버와 앱에 들어가요.
3. OAuth 클라이언트 **Android**를 만들어요. 패키지명 `com.dearus`와 5번의 SHA-1을 넣어요.

### 7. 서버와 앱에 값 넣기

- Render → `dearus-server` → Environment: `KAKAO_APP_ID`, `GOOGLE_CLIENT_IDS`를 넣어요. 로컬 서버로 시험하면 `server/.env`에도 넣어요.
- `app/.env`:

```
EXPO_PUBLIC_API_MODE=live
EXPO_PUBLIC_API_URL=<배포 서버 주소>
EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY=<네이티브 앱 키>
EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=<웹 클라이언트 ID>
```

### 8. 폰에서 시험

EAS 빌드 페이지의 QR로 APK를 폰에 설치하고, PC에서 개발 서버를 켠 뒤 앱에서 그 서버를 열어요. 폰과 PC는 같은 Wi-Fi여야 해요.

```powershell
npx expo start --dev-client -c
```

## Windows에서 로컬 빌드는 하지 않아요

이 레포 경로에서 `gradlew assembleDebug`를 돌리면 `react-native-reanimated`의 C++ 빌드가 Windows 경로 길이 제한(260자)에 걸려 실패해요(2026.11.21 확인, 폴더 경로가 261자예요). 개발 빌드는 EAS로 만들어요. EAS는 Linux에서 빌드해서 이 제한이 없어요.

## 다시 빌드해야 할 때

- 네이티브 라이브러리를 더하거나 `app.json` · `app.config.js`의 네이티브 설정을 바꿨을 때예요.
- 화면이나 로직(JS · TS)만 바꾸면 다시 빌드하지 않아도 돼요.

## 자주 나는 문제

| 증상 | 확인할 것 |
|---|---|
| 카카오: 키 해시 오류 | 카카오 콘솔의 키 해시가 **EAS 서명 키**의 값인지 봐요. PC의 디버그 키와 달라요 |
| 구글: `DEVELOPER_ERROR` | Android 클라이언트의 패키지명 · SHA-1, 앱의 값이 **웹** 클라이언트 ID인지 봐요 |
| 구글: 접근이 막힘 | 로그인한 계정이 OAuth 동의 화면의 테스트 사용자인지 봐요 |
| 서버 501 | Render의 `KAKAO_APP_ID` · `GOOGLE_CLIENT_IDS`가 비어 있어요 |
| 서버 401 | 토큰의 앱 ID나 대상(aud)이 서버 값과 달라요 |
| 첫 요청이 오래 걸림 | 무료 서버가 잠들어 있어요. 깨어날 때까지 2분쯤 걸려요 |
