import { Alert, Platform } from 'react-native';

/**
 * 안내창 · 확인창이에요. 폰에서는 Alert를 쓰고, PC 브라우저 미리보기(웹)에서는 Alert가 동작하지 않아서
 * 브라우저 기본 창을 써요. 화면 코드는 Alert를 직접 부르지 않고 이 함수들을 써요.
 */

export function notify(title: string, message?: string): void {
  if (Platform.OS === 'web') {
    globalThis.alert?.(message ? `${title}\n\n${message}` : title);
    return;
  }
  Alert.alert(title, message);
}

type ConfirmOptions = {
  title: string;
  message?: string;
  confirmLabel: string;
  cancelLabel?: string;
  /** 지우기처럼 되돌릴 수 없는 일이면 true (iOS에서 빨간 글씨) */
  destructive?: boolean;
};

/** 사용자가 확인을 누르면 true, 취소하거나 창을 닫으면 false예요. */
export function confirm({
  title,
  message,
  confirmLabel,
  cancelLabel = '취소',
  destructive = false,
}: ConfirmOptions): Promise<boolean> {
  if (Platform.OS === 'web') {
    const ok = globalThis.confirm?.(message ? `${title}\n\n${message}` : title) ?? false;
    return Promise.resolve(ok);
  }
  return new Promise((resolve) => {
    Alert.alert(
      title,
      message,
      [
        { text: cancelLabel, style: 'cancel', onPress: () => resolve(false) },
        { text: confirmLabel, style: destructive ? 'destructive' : 'default', onPress: () => resolve(true) },
      ],
      { cancelable: true, onDismiss: () => resolve(false) },
    );
  });
}
