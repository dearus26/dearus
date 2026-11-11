import Constants from 'expo-constants';
import { router } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { BottomSheet } from '@/components/BottomSheet';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { addDays, dateStringOf, formatMonthDay, formatYearMonthDay, todayString } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';
import { useSession } from '@/session/SessionProvider';
import { colors, emotionColors } from '@/theme/colors';

const providerLabels = { kakao: '카카오', google: '구글', apple: '애플', dev: '개발용' } as const;

/** 동의 철회 뒤 분석 데이터와 대화 기록을 보관하는 기간(일). 서버 규칙(2026.10.29 확정)과 같아요. */
const RETENTION_DAYS = 30;

type SheetName = 'revoke' | 'withdraw';

/**
 * 설정: 계정 · AI 분석 동의 철회와 다시 동의 · 알림 · 내 데이터 · 약관 · 로그아웃 · 회원 탈퇴.
 * 스토리보드에는 설정 화면이 따로 없어서(커플 화면의 동의 철회만 있어요) 개인정보 처리방침의 약속
 * ("설정 → 내 데이터", "AI 분석 동의 철회 · 다시 동의", "회원 탈퇴")을 그대로 옮겼어요.
 */
export default function SettingsScreen() {
  const insets = useSafeAreaInsets();
  const { user, giveConsent, revokeAiConsent, deleteAccount, signOut } = useSession();
  const [sheet, setSheet] = useState<SheetName | null>(null);
  const [busy, setBusy] = useState(false);

  if (!user) return null;

  const active = user.aiConsentActive;
  const consentedAt = user.aiConsentedAt ? formatYearMonthDay(dateStringOf(new Date(user.aiConsentedAt))) : null;
  const deleteAt = user.aiDataDeleteAt ? formatYearMonthDay(dateStringOf(new Date(user.aiDataDeleteAt))) : null;
  const joined = formatYearMonthDay(dateStringOf(new Date(user.createdAt)));
  const version = Constants.expoConfig?.version;

  function goBack() {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }

  async function run(task: () => Promise<void>, failTitle: string): Promise<boolean> {
    setBusy(true);
    try {
      await task();
      return true;
    } catch (error) {
      notify(failTitle, errorMessage(error));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function revoke() {
    if (await run(revokeAiConsent, '동의를 철회하지 못했어요')) setSheet(null);
  }

  async function reconsent() {
    const ok = await confirm({
      title: 'AI 분석에 다시 동의할까요?',
      message: '보관 중인 분석 데이터와 대화 기록을 이어서 쓸 수 있어요.',
      confirmLabel: '다시 동의',
    });
    if (ok) await run(() => giveConsent(user?.notificationsOptIn ?? false), '다시 동의하지 못했어요');
  }

  async function withdraw() {
    // 성공하면 로그인 화면으로 넘어가면서 이 화면이 사라져요
    await run(deleteAccount, '탈퇴하지 못했어요');
  }

  async function handleSignOut() {
    const ok = await confirm({ title: '로그아웃할까요?', confirmLabel: '로그아웃' });
    if (ok) await signOut();
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

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: Math.max(insets.bottom, 12) + 24 }]}
        showsVerticalScrollIndicator={false}>
        <Card style={styles.account}>
          <AppText serif weight="bold" size={20} lineHeight={28}>
            {user.nickname}
          </AppText>
          <AppText size={12.5} lineHeight={19} color={colors.brown}>
            {`${providerLabels[user.provider]} 로그인 · ${joined}부터 함께해요`}
          </AppText>
        </Card>

        <Section title="AI 분석">
          <View style={styles.consentHead}>
            <AppText size={15} lineHeight={22} style={styles.grow}>
              일기 · 대화 내용 AI 분석
            </AppText>
            <AppText weight="bold" size={13} lineHeight={20} color={active ? colors.brown : emotionColors.ANGRY}>
              {active ? '동의함' : '철회함'}
            </AppText>
          </View>
          <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.hint}>
            {active
              ? `${consentedAt ?? '이전'}에 동의했어요. 철회하면 AI를 쓰는 기능은 바로 멈추고, 일기는 그대로 써요.`
              : deleteAt
                ? `AI를 쓰는 기능이 멈춰 있어요. ${deleteAt}에 올린 메신저 대화 · 분석 · 대화 기록이 모두 지워져요. 그 전에 다시 동의하면 이어서 쓸 수 있어요.`
                : 'AI를 쓰는 기능이 멈춰 있어요. 다시 동의하면 쓸 수 있어요.'}
          </AppText>
          <View style={styles.rowButton}>
            {active ? (
              <Button size="small" variant="line" label="동의 철회" onPress={() => setSheet('revoke')} />
            ) : (
              <Button size="small" label="다시 동의하기" loading={busy} onPress={() => void reconsent()} />
            )}
          </View>
        </Section>

        <Section title="알림">
          <View style={styles.consentHead}>
            <AppText size={15} lineHeight={22} style={styles.grow}>
              일기 작성 알림 받기
            </AppText>
            <Switch
              accessibilityLabel="일기 작성 알림 받기"
              value={user.notificationsOptIn}
              disabled={busy || !active}
              onValueChange={(next) => void run(() => giveConsent(next), '알림 설정을 바꾸지 못했어요')}
              trackColor={{ false: colors.beige, true: colors.gold }}
              thumbColor={colors.white}
            />
          </View>
          <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.hint}>
            {active
              ? '알림 기능은 준비 중이에요. 지금은 받겠다는 선택만 저장해 둬요.'
              : 'AI 분석 동의를 철회한 동안에는 바꿀 수 없어요. 다시 동의하면 바꿀 수 있어요.'}
          </AppText>
        </Section>

        <Section title="내 정보" flush>
          <LinkRow label="내 데이터 · 올린 메신저 대화" onPress={() => router.push('/settings/data')} />
          <LinkRow label="이용약관" onPress={() => router.push({ pathname: '/legal/[type]', params: { type: 'terms' } })} />
          <LinkRow
            label="개인정보 수집 · 이용"
            onPress={() => router.push({ pathname: '/legal/[type]', params: { type: 'privacy' } })}
          />
          <LinkRow
            label="일기 · 대화 내용 AI 분석 안내"
            onPress={() => router.push({ pathname: '/legal/[type]', params: { type: 'ai' } })}
            last
          />
        </Section>

        <Section title="계정" flush>
          <LinkRow label="로그아웃" onPress={() => void handleSignOut()} />
          <LinkRow label="회원 탈퇴" danger onPress={() => setSheet('withdraw')} last />
        </Section>

        {version ? (
          <AppText size={12} lineHeight={18} color={colors.muted} style={styles.version}>
            {`Dear Us ${version}`}
          </AppText>
        ) : null}
      </ScrollView>

      <BottomSheet visible={sheet === 'revoke'} onClose={() => (busy ? undefined : setSheet(null))}>
        <AppText serif weight="bold" size={22} lineHeight={30} style={styles.sheetTitle}>
          AI 분석 동의를 철회할까요?
        </AppText>
        <View style={styles.sheetList}>
          <SheetLine>메신저 올리기 · 과거의 나와 대화 · 리포트는 바로 쓸 수 없어요</SheetLine>
          <SheetLine>일기는 그대로 쓰고 볼 수 있어요</SheetLine>
          <SheetLine>
            {`올린 메신저 대화 · 분석 · 대화 기록은 ${formatMonthDay(addDays(todayString(), RETENTION_DAYS))}(${RETENTION_DAYS}일 뒤)에 모두 지워져요`}
          </SheetLine>
          <SheetLine>그 전에 다시 동의하면 이어서 쓸 수 있어요</SheetLine>
        </View>
        <View style={styles.sheetButtons}>
          <Button variant="danger" label="철회하기" loading={busy} onPress={() => void revoke()} />
          <Button variant="line" label="취소" disabled={busy} onPress={() => setSheet(null)} />
        </View>
      </BottomSheet>

      <BottomSheet visible={sheet === 'withdraw'} onClose={() => (busy ? undefined : setSheet(null))}>
        <AppText serif weight="bold" size={22} lineHeight={30} style={styles.sheetTitle}>
          정말 탈퇴할까요?
        </AppText>
        <View style={styles.sheetList}>
          <SheetLine>일기 · 대화 · 리포트 · 올린 메신저 대화와 분석까지 모든 정보가 바로 지워지고, 되돌릴 수 없어요</SheetLine>
          <SheetLine>이미 AI 회사(Anthropic)로 보낸 글자는 그곳 정책에 따라 30일 안에 지워져요</SheetLine>
        </View>
        <View style={styles.sheetButtons}>
          <Button variant="danger" label="탈퇴하기" loading={busy} onPress={() => void withdraw()} />
          <Button variant="line" label="취소" disabled={busy} onPress={() => setSheet(null)} />
        </View>
      </BottomSheet>
    </View>
  );
}

function Section({ title, flush = false, children }: { title: string; flush?: boolean; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <AppText weight="bold" size={12.5} lineHeight={19} color={colors.brown} style={styles.sectionTitle}>
        {title}
      </AppText>
      <Card style={flush ? styles.flushCard : styles.sectionCard}>{children}</Card>
    </View>
  );
}

function LinkRow({
  label,
  onPress,
  danger = false,
  last = false,
}: {
  label: string;
  onPress: () => void;
  danger?: boolean;
  last?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => [styles.linkRow, !last && styles.linkRowLine, pressed && styles.pressed]}>
      <AppText size={15} lineHeight={22} color={danger ? emotionColors.ANGRY : colors.ink} style={styles.grow}>
        {label}
      </AppText>
      <Icon name="chevronRight" size={16} color={colors.muted} />
    </Pressable>
  );
}

function SheetLine({ children }: { children: ReactNode }) {
  return (
    <View style={styles.sheetLine}>
      <AppText size={14} lineHeight={22} color={colors.gold} weight="bold">
        ✓
      </AppText>
      <AppText size={14} lineHeight={22} style={styles.grow}>
        {children}
      </AppText>
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
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
  grow: {
    flex: 1,
  },
  account: {
    paddingVertical: 14,
    gap: 2,
  },
  section: {
    marginTop: 18,
  },
  sectionTitle: {
    marginBottom: 6,
    marginLeft: 4,
  },
  sectionCard: {
    paddingVertical: 14,
    paddingHorizontal: 16,
  },
  flushCard: {
    padding: 0,
    overflow: 'hidden',
  },
  consentHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  hint: {
    marginTop: 4,
  },
  rowButton: {
    marginTop: 12,
  },
  linkRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 15,
    paddingHorizontal: 16,
  },
  linkRowLine: {
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(205, 182, 156, 0.6)',
  },
  pressed: {
    opacity: 0.6,
  },
  version: {
    marginTop: 22,
    textAlign: 'center',
  },
  sheetTitle: {
    textAlign: 'center',
    marginTop: 4,
  },
  sheetList: {
    marginTop: 14,
    gap: 8,
  },
  sheetLine: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'flex-start',
  },
  sheetButtons: {
    marginTop: 18,
    gap: 10,
  },
});
