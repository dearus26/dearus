import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState, type ReactNode } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { CoupleState } from '@/api/couples';
import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ConnectView } from '@/features/couple/ConnectView';
import { CouplePage } from '@/features/couple/CouplePage';
import { DayPickView } from '@/features/couple/DayPickView';
import { PairHeader } from '@/features/couple/PairHeader';
import { RevokeLink } from '@/features/couple/RevokeLink';
import { RevokeSheet } from '@/features/couple/RevokeSheet';
import { useCouple } from '@/features/couple/hooks';
import { colors } from '@/theme/colors';

/**
 * 스토리보드 C 커플 모드의 첫 화면(하단 탭). 내 커플 상태(GET /couples/me)에 따라 다음 중 하나를 보여줘요.
 *  - 연결 전(NONE · PENDING · EXPIRED): C-1 초대 코드 연결
 *  - 연결됨 + 내가 아직 동의 전: 동의 안내(C-2)로 가는 화면
 *  - 연결됨 + 내가 동의, 상대는 아직: 상대를 기다리는 화면
 *  - 연결됨 + 두 사람 모두 동의: C-3 함께 볼 날 고르기 → 두 시점 비교(C-4)
 * 철회 · 연결 해제(C-5)는 이 화면 아래의 작은 글씨와 설정에서 열어요.
 */
export default function CoupleScreen() {
  const insets = useSafeAreaInsets();
  const couple = useCouple();
  const [revokeOpen, setRevokeOpen] = useState(false);
  const state = couple.data;

  // 다른 화면(동의 · 비교 · 설정)에서 돌아오면 바뀐 상태를 바로 보여줘요
  const { refetch } = couple;
  useFocusEffect(
    useCallback(() => {
      void refetch({ cancelRefetch: false });
    }, [refetch]),
  );

  const refresh = () => void refetch();
  const openRevoke = () => setRevokeOpen(true);

  let body: ReactNode;
  if (!state) {
    body = (
      <CouplePage>
        {couple.isError ? (
          <View style={styles.center}>
            <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
              {errorMessage(couple.error)}
            </AppText>
            <Button variant="line" label="다시 불러오기" onPress={refresh} />
          </View>
        ) : (
          <View style={styles.center}>
            <ActivityIndicator color={colors.gold} />
          </View>
        )}
      </CouplePage>
    );
  } else if (state.status !== 'CONNECTED') {
    body = (
      <CouplePage refreshing={couple.isRefetching} onRefresh={refresh}>
        <ConnectView state={state} onCancelInvite={openRevoke} />
      </CouplePage>
    );
  } else if (!state.me?.consented) {
    body = (
      <CouplePage refreshing={couple.isRefetching} onRefresh={refresh}>
        <NeedConsent state={state} onRevoke={openRevoke} />
      </CouplePage>
    );
  } else if (!state.compareOpen) {
    body = (
      <CouplePage refreshing={couple.isRefetching} onRefresh={refresh}>
        <WaitingPartner state={state} onRevoke={openRevoke} />
      </CouplePage>
    );
  } else {
    body = <DayPickView state={state} refreshing={couple.isRefetching} onRefresh={refresh} onRevoke={openRevoke} />;
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <AppText weight="bold" size={17}>
          커플 모드
        </AppText>
      </View>
      {body}
      <RevokeSheet visible={revokeOpen} onClose={() => setRevokeOpen(false)} state={state} />
    </View>
  );
}

/** 연결은 됐고 내가 아직 동의하지 않았어요. 동의 안내(C-2)를 읽고 동의하게 해요. */
function NeedConsent({ state, onRevoke }: { state: CoupleState; onRevoke: () => void }) {
  const partner = state.partner?.nickname ?? '상대';
  return (
    <View>
      <PairHeader
        me={{ name: state.me?.nickname ?? '나', tag: '동의 전', tone: 'low' }}
        partner={{ name: partner, tag: state.partner?.consented ? '동의 완료' : '대기 중', tone: state.partner?.consented ? 'ok' : 'plain' }}
        linked={false}
      />
      <AppText serif weight="bold" size={26} lineHeight={36} style={styles.title}>
        {`${partner}님과\n연결됐어요`}
      </AppText>
      <Card tint style={styles.card}>
        <AppText size={14} lineHeight={22}>
          서로의 과거와 만나기 전에, 꼭 읽어야 할 안내가 있어요. 안내를 읽고 동의하면 다음으로 넘어가요.
        </AppText>
      </Card>
      <View style={styles.button}>
        <Button label="동의 안내 보기" onPress={() => router.push('/couples/consent')} />
      </View>
      <RevokeLink onPress={onRevoke} />
    </View>
  );
}

/** 내 동의는 끝났고 상대가 동의하길 기다려요(useCouple이 4초마다 확인해서, 동의하면 저절로 다음 화면이 돼요). */
function WaitingPartner({ state, onRevoke }: { state: CoupleState; onRevoke: () => void }) {
  const partner = state.partner?.nickname ?? '상대';
  return (
    <View>
      <PairHeader
        me={{ name: state.me?.nickname ?? '나', tag: '동의 완료', tone: 'ok' }}
        partner={{ name: partner, tag: '대기 중', tone: 'plain' }}
        linked={false}
      />
      <AppText serif weight="bold" size={26} lineHeight={36} style={styles.title}>
        {`${partner}님의 동의를\n기다리고 있어요`}
      </AppText>
      <View style={styles.waiting}>
        <ActivityIndicator size="small" color={colors.gold} />
        <AppText size={13.5} lineHeight={21} color={colors.brown}>
          두 사람이 모두 동의하면 함께 볼 날을 고를 수 있어요
        </AppText>
      </View>
      <Card tint style={styles.card}>
        <AppText size={13} lineHeight={20} color={colors.brown}>
          동의는 언제든 철회할 수 있어요. 철회하면 상대의 접근이 바로 차단돼요.
        </AppText>
      </Card>
      <RevokeLink onPress={onRevoke} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.cream,
  },
  navbar: {
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingTop: 60,
  },
  centered: {
    textAlign: 'center',
  },
  title: {
    marginTop: 20,
    textAlign: 'center',
  },
  card: {
    marginTop: 16,
  },
  button: {
    marginTop: 18,
  },
  waiting: {
    marginTop: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
});
