import { StyleSheet, View } from 'react-native';

import type { CoupleState } from '@/api/couples';
import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { BottomSheet } from '@/components/BottomSheet';
import { Button } from '@/components/Button';
import { SheetLine } from '@/components/SheetLine';
import { useRevokeCouple } from '@/features/couple/hooks';
import { notify } from '@/lib/dialog';

type Props = {
  visible: boolean;
  onClose: () => void;
  state: CoupleState | undefined;
};

/**
 * 스토리보드 C-5 동의 철회 시트. 연결돼 있으면 "동의 철회 · 연결 해제"(상대의 접근을 바로 끊고, 이 커플의 대화와 리포트를 두 사람 것 모두 지워요),
 * 아직 연결 전이면 만든 초대 코드를 취소해요(POST /couples/revoke). 되돌릴 수 없어서 한 번 더 확인을 받아요.
 * AI 분석 동의 철회(30일 뒤 삭제)와는 다른 일이라서, 스토리보드 시트의 "O월 O일에 모두 지워져요" 줄은 여기에 넣지 않아요.
 */
export function RevokeSheet({ visible, onClose, state }: Props) {
  const revoke = useRevokeCouple();
  const connected = state?.status === 'CONNECTED';
  const partner = state?.partner?.nickname ?? '상대';

  async function run() {
    try {
      await revoke.mutateAsync();
      onClose();
    } catch (error) {
      notify(connected ? '철회하지 못했어요' : '코드를 취소하지 못했어요', errorMessage(error));
    }
  }

  return (
    <BottomSheet visible={visible} onClose={() => (revoke.isPending ? undefined : onClose())}>
      <AppText serif weight="bold" size={22} lineHeight={30} style={styles.title}>
        {connected ? '동의를 철회할까요?' : '초대 코드를 취소할까요?'}
      </AppText>
      <View style={styles.list}>
        {connected ? (
          <>
            <SheetLine>
              {`${partner}님의 접근이 `}
              <AppText weight="bold" size={14} lineHeight={22}>
                즉시 차단
              </AppText>
              돼요
            </SheetLine>
            <SheetLine>이 커플의 대화와 리포트가 두 사람 것 모두 삭제돼요</SheetLine>
            <SheetLine>내 일기와 개인 모드 기록은 그대로 남아요</SheetLine>
            <SheetLine>되돌릴 수 없어요. 다시 쓰려면 새 초대 코드로 처음부터 연결해요</SheetLine>
          </>
        ) : (
          <>
            <SheetLine>만든 초대 코드로는 더 이상 연결할 수 없어요</SheetLine>
            <SheetLine>필요하면 새 코드를 다시 만들 수 있어요</SheetLine>
          </>
        )}
      </View>
      <View style={styles.buttons}>
        <Button
          variant="danger"
          label={connected ? '철회하기' : '코드 취소하기'}
          loading={revoke.isPending}
          onPress={() => void run()}
        />
        <Button variant="line" label="취소" disabled={revoke.isPending} onPress={onClose} />
      </View>
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  title: {
    textAlign: 'center',
    marginTop: 4,
  },
  list: {
    marginTop: 14,
    gap: 8,
  },
  buttons: {
    marginTop: 18,
    gap: 10,
  },
});
