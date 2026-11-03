import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * 로그인 토큰(JWT)을 폰의 보안 저장소에 둬요. 키에는 영문 · 숫자 · . - _ 만 쓸 수 있어요.
 * 웹에는 보안 저장소가 없어서 localStorage를 써요. 앱은 모바일 전용이라 웹은 PC 브라우저로 화면을
 * 확인할 때만 쓰는 개발용 대체 경로예요.
 */
const KEY = 'dearus.accessToken';

const isWeb = Platform.OS === 'web';

export async function readToken(): Promise<string | null> {
  try {
    return isWeb ? globalThis.localStorage?.getItem(KEY) ?? null : await SecureStore.getItemAsync(KEY);
  } catch {
    return null;
  }
}

export async function saveToken(token: string): Promise<void> {
  if (isWeb) {
    globalThis.localStorage?.setItem(KEY, token);
    return;
  }
  await SecureStore.setItemAsync(KEY, token);
}

export async function clearToken(): Promise<void> {
  try {
    if (isWeb) globalThis.localStorage?.removeItem(KEY);
    else await SecureStore.deleteItemAsync(KEY);
  } catch {
    // 지울 게 없거나 저장소를 못 읽는 경우는 로그아웃을 막을 이유가 아니에요
  }
}
