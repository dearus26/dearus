import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Icon } from '@/components/Icon';
import { confirm } from '@/lib/dialog';
import { useSession } from '@/session/SessionProvider';
import { colors } from '@/theme/colors';

const providerLabels = { kakao: '카카오', google: '구글', apple: '애플', dev: '개발용' } as const;

/**
 * 스토리보드 C-5 설정 (자리만 있어요).
 * TODO: 내 데이터 보기 · 삭제, 알림, 커플 모드, 동의 철회 · 연결 해제, 회원 탈퇴는 해당 단계에서 만들어요.
 * 지금은 로그인 흐름을 다시 확인할 수 있게 계정 정보와 로그아웃만 있어요.
 */
export default function SettingsScreen() {
  const insets = useSafeAreaInsets();
  const { user, signOut } = useSession();

  async function handleSignOut() {
    const ok = await confirm({ title: '로그아웃할까요?', confirmLabel: '로그아웃' });
    if (ok) await signOut();
  }

  function goBack() {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={goBack}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          설정
        </AppText>
        <View style={styles.navSpacer} />
      </View>

      <View style={styles.list}>
        <View style={styles.item}>
          <AppText size={15}>계정</AppText>
          <AppText size={14} color={colors.muted}>
            {user ? `${user.nickname} · ${providerLabels[user.provider]}` : ''}
          </AppText>
        </View>
        <Pressable accessibilityRole="button" onPress={() => void handleSignOut()} style={[styles.item, styles.lastItem]}>
          <AppText size={15}>로그아웃</AppText>
          <Icon name="chevronRight" size={16} color={colors.muted} />
        </Pressable>
      </View>
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
    paddingHorizontal: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navSpacer: {
    width: 22,
  },
  list: {
    marginTop: 12,
    marginHorizontal: 22,
    backgroundColor: colors.paper,
    borderWidth: 1,
    borderColor: colors.beige,
    borderRadius: 20,
    overflow: 'hidden',
  },
  item: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(205, 182, 156, 0.6)',
  },
  lastItem: {
    borderBottomWidth: 0,
  },
});
