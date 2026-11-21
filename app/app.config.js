/**
 * app.json에 환경변수가 필요한 설정만 더해요(2026.11.21 소셜 로그인).
 * 카카오 플러그인은 네이티브 앱 키가 없으면 오류를 내서, 키가 있을 때만 넣어요.
 * 그래서 키 없이도 Expo Go와 가짜 데이터(mock)로 앱을 계속 켤 수 있어요.
 * EAS 빌드에서는 키가 빠진 APK가 나오지 않게 바로 멈춰요.
 */
module.exports = ({ config }) => {
  const nativeAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY;

  if (!nativeAppKey) {
    if (process.env.EAS_BUILD) {
      throw new Error('EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY가 EAS 환경변수에 없어요. skills/social-login.md를 봐 주세요.');
    }
    return config;
  }

  return {
    ...config,
    plugins: [
      ...(config.plugins ?? []),
      ['@react-native-kakao/core', { nativeAppKey, android: { authCodeHandlerActivity: true } }],
    ],
  };
};
