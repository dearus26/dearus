import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * 메신저 올리기의 "위 내용을 이해했고 대화 분석에 동의해요"를 처음 한 번만 받으려고, 동의했다는 사실을 폰에 기억해 둬요(2026.11.28).
 * 계정마다 따로 기억하고(사용자 ID가 키에 들어가요), AI 분석 동의를 철회하면 지워서 다시 받아요.
 * 서버로 보내는 요청은 그대로 consent: true예요(서버가 매번 확인해요). 앱을 지우고 다시 깔면 한 번 더 물어요.
 * 토큰과 같은 보안 저장소를 쓰고, 웹(개발용)은 localStorage예요.
 */
const isWeb = Platform.OS === 'web';

function keyOf(userId: string): string {
  // 보안 저장소 키에는 영문 · 숫자 · . - _ 만 쓸 수 있어요(사용자 ID는 UUID라 괜찮아요)
  return `dearus.messengerConsent.${userId.replace(/[^A-Za-z0-9._-]/g, '_')}`;
}

export async function hasMessengerConsent(userId: string): Promise<boolean> {
  try {
    const value = isWeb ? globalThis.localStorage?.getItem(keyOf(userId)) : await SecureStore.getItemAsync(keyOf(userId));
    return value === 'yes';
  } catch {
    return false; // 못 읽으면 한 번 더 물어요
  }
}

export async function rememberMessengerConsent(userId: string): Promise<void> {
  try {
    if (isWeb) globalThis.localStorage?.setItem(keyOf(userId), 'yes');
    else await SecureStore.setItemAsync(keyOf(userId), 'yes');
  } catch {
    // 기억하지 못해도 올리기는 막지 않아요(다음에 한 번 더 물을 뿐이에요)
  }
}

export async function forgetMessengerConsent(userId: string): Promise<void> {
  try {
    if (isWeb) globalThis.localStorage?.removeItem(keyOf(userId));
    else await SecureStore.deleteItemAsync(keyOf(userId));
  } catch {
    // 지울 게 없으면 그만이에요
  }
}
