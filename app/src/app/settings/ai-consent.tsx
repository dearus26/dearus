import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { SheetLine } from '@/components/SheetLine';
import { Tag } from '@/components/Tag';
import { daysBetween, dateStringOf, formatYearMonthDay, todayString } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';
import { useSession } from '@/session/SessionProvider';
import { colors } from '@/theme/colors';

/** 동의 철회 뒤 분석 데이터와 대화 기록을 보관하는 기간(일). 서버 규칙(2026.10.29 확정)과 같아요. */
const RETENTION_DAYS = 30;

/**
 * AI 분석 동의 철회 · 다시 동의 화면(2026.11.18 결정: 커플 철회와 따로 둬요). 설정의 "AI 분석"에서 열리고,
 * AI를 쓰는 기능이 403 AI_CONSENT_REQUIRED로 막혔을 때도 여기로 안내해요.
 *  - 동의 중: 철회하면 무엇이 멈추고 언제 지워지는지 알려 주고, "철회하기"로 철회해요(DELETE /me/consent).
 *  - 철회한 뒤: 서버가 정한 삭제 날짜(GET /me의 aiDataDeleteAt)를 "○월 ○일에 모두 지워져요"로 보여주고, 그 전에 "다시 동의하기"로 이어서 쓸 수 있어요.
 */
export default function AiConsentScreen() {
  const insets = useSafeAreaInsets();
  const { user, giveConsent, revokeAiConsent } = useSession();
  const [busy, setBusy] = useState(false);

  if (!user) return null;

  const active = user.aiConsentActive;
  const consentedAt = user.aiConsentedAt ? formatYearMonthDay(dateStringOf(new Date(user.aiConsentedAt))) : null;
  const deleteDate = user.aiDataDeleteAt ? dateStringOf(new Date(user.aiDataDeleteAt)) : null;
  const daysLeft = deleteDate ? daysBetween(todayString(), deleteDate) : null;

  function goBack() {
    if (router.canGoBack()) router.back();
    else router.replace('/settings');
  }

  async function revoke() {
    const ok = await confirm({
      title: 'AI 분석 동의를 철회할까요?',
      message: 'AI를 쓰는 기능이 바로 멈춰요. 30일 안에 다시 동의하면 이어서 쓸 수 있어요.',
      confirmLabel: '철회하기',
      destructive: true,
    });
    if (!ok) return;
    setBusy(true);
    try {
      await revokeAiConsent();
    } catch (error) {
      notify('동의를 철회하지 못했어요', errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function reconsent() {
    setBusy(true);
    try {
      await giveConsent({ notifications: user?.notificationsOptIn ?? false });
    } catch (error) {
      notify('다시 동의하지 못했어요', errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={goBack}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          AI 분석 동의
        </AppText>
        <View style={styles.navSide} />
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: Math.max(insets.bottom, 12) + 20 }]}
        showsVerticalScrollIndicator={false}>
        <View style={styles.statusRow}>
          <Tag label={active ? '동의함' : '철회함'} ok={active} low={!active} />
          {active && consentedAt ? (
            <AppText size={12.5} lineHeight={19} color={colors.brown}>
              {`${consentedAt}에 동의했어요`}
            </AppText>
          ) : null}
        </View>

        {active ? (
          <>
            <AppText serif weight="bold" size={26} lineHeight={36} style={styles.title}>
              {'AI 분석 동의를\n철회할까요?'}
            </AppText>
            <Card tint style={styles.card}>
              <AppText size={13.5} lineHeight={22}>
                일기와 올린 메신저 대화(내 말)를 AI로 보내서 과거의 나를 만들고, 대화와 리포트를 만들어요. 글자는 가리지 않고 보내고, 학습에는 쓰이지 않아요.
              </AppText>
            </Card>

            <AppText weight="bold" size={12.5} lineHeight={19} color={colors.brown} style={styles.sectionTitle}>
              철회하면
            </AppText>
            <View style={styles.list}>
              <SheetLine>메신저 올리기 · 과거의 나와 대화 · 리포트는 바로 쓸 수 없어요</SheetLine>
              <SheetLine>연인이 나의 과거의 나와 하는 대화도 멈춰요. 연인과 나눈 그날의 대화는 30일 뒤 함께 지워져요</SheetLine>
              <SheetLine>일기는 그대로 쓰고 볼 수 있어요</SheetLine>
              <SheetLine>올린 메신저 대화 · 분석 · 대화 기록은 철회한 날부터 30일 뒤에 모두 지워져요</SheetLine>
              <SheetLine>그 전에 다시 동의하면 이어서 쓸 수 있어요</SheetLine>
              <SheetLine>이미 AI 회사로 보낸 글자는 그 회사에 최대 30일 남고, 우리가 지워도 이 기간은 줄지 않아요</SheetLine>
            </View>

            <View style={styles.buttons}>
              <Button variant="danger" label="철회하기" loading={busy} onPress={() => void revoke()} />
              <Button variant="line" label="취소" disabled={busy} onPress={goBack} />
            </View>
          </>
        ) : (
          <>
            <AppText serif weight="bold" size={26} lineHeight={36} style={styles.title}>
              {'AI 분석 동의를\n철회했어요'}
            </AppText>
            <Card style={[styles.card, styles.deleteCard]}>
              <AppText weight="bold" size={12} lineHeight={18} color={colors.brown}>
                모두 지워지는 날
              </AppText>
              {deleteDate ? (
                <>
                  <AppText serif weight="bold" size={22} lineHeight={31} style={styles.deleteText}>
                    {`${formatYearMonthDay(deleteDate)}에\n모두 지워져요`}
                  </AppText>
                  <AppText size={12.5} lineHeight={19} color={colors.brown}>
                    {daysLeft !== null && daysLeft > 0 ? `${daysLeft}일 남았어요` : '오늘 지워져요'}
                  </AppText>
                </>
              ) : (
                <AppText serif weight="bold" size={20} lineHeight={29} style={styles.deleteText}>
                  {`철회한 날부터 ${RETENTION_DAYS}일 뒤에\n모두 지워져요`}
                </AppText>
              )}
              <AppText size={13} lineHeight={20} style={styles.deleteNote}>
                올린 메신저 대화 · 분석 · 대화 기록이에요. 연인과 나눈 그날의 대화도 함께 지워져요.
              </AppText>
            </Card>

            <View style={styles.list}>
              <SheetLine>AI를 쓰는 기능(메신저 올리기 · 대화 · 리포트)은 멈춰 있어요</SheetLine>
              <SheetLine>일기는 그대로 쓰고 볼 수 있어요</SheetLine>
              <SheetLine>그 전에 다시 동의하면 보관 중인 것을 이어서 쓸 수 있어요</SheetLine>
            </View>

            <View style={styles.buttons}>
              <Button label="다시 동의하기" loading={busy} onPress={() => void reconsent()} />
              <Button variant="line" label="설정으로" disabled={busy} onPress={goBack} />
            </View>
          </>
        )}
      </ScrollView>
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
  navSide: {
    width: 22,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  title: {
    marginTop: 10,
  },
  card: {
    marginTop: 16,
  },
  deleteCard: {
    borderColor: colors.gold,
    borderWidth: 2,
  },
  deleteText: {
    marginTop: 4,
  },
  deleteNote: {
    marginTop: 10,
  },
  sectionTitle: {
    marginTop: 18,
    marginLeft: 4,
  },
  list: {
    marginTop: 12,
    gap: 10,
  },
  buttons: {
    marginTop: 22,
    gap: 10,
  },
});
