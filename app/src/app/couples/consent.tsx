import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { CheckBox } from '@/components/CheckBox';
import { Icon } from '@/components/Icon';
import { PairHeader } from '@/features/couple/PairHeader';
import { useCouple, useGiveCoupleConsent } from '@/features/couple/hooks';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

/** 연인에게 내 기록이 어떻게 보이는지(제3자 제공 고지 5가지). 개인정보처리방침 6장의 "커플 모드의 연인"과 같은 내용이에요. */
const NOTICES: { label: string; text: string }[] = [
  { label: '받는 사람', text: '커플로 연결된 상대방' },
  { label: '목적', text: '상대가 나의 과거 페르소나와 대화하고, 같은 날 두 사람의 대화를 비교해요' },
  {
    label: '보이는 것',
    text: '내 기록으로 만든 과거 페르소나의 답변, 그날 대화의 요약, 두 사람의 간극, 두 사람의 기록(일기 · 메신저)이 모두 있는 날의 날짜와 일기의 감정 태그, 닉네임 · 동의 여부예요. 일기 본문과 카톡 원문은 그대로 보여 주지 않아요',
  },
  { label: '보관 기간', text: '연결을 끊거나 동의를 철회할 때까지예요. 철회하면 이 커플의 대화와 리포트는 두 사람 것 모두 바로 지워요' },
  { label: '거부할 권리', text: '동의하지 않아도 돼요. 커플 모드만 쓸 수 없고, 다른 기능은 그대로 쓸 수 있어요' },
];

/**
 * 스토리보드 C-2 동의 안내("시작하기 전에"). 상대가 내 기록으로 만든 '과거의 나'와 대화한다는 것, 속마음이 드러날 수 있다는 것,
 * 언제든 철회할 수 있다는 것을 읽고 체크한 뒤 "동의하고 계속하기"를 눌러요. 두 사람이 모두 동의해야 함께 볼 날을 고를 수 있어요.
 */
export default function CoupleConsentScreen() {
  const insets = useSafeAreaInsets();
  const couple = useCouple();
  const giveConsent = useGiveCoupleConsent();
  const [agreed, setAgreed] = useState(false);
  const state = couple.data;
  const partner = state?.partner?.nickname ?? '상대';

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace('/couple');
  }

  async function submit() {
    try {
      await giveConsent.mutateAsync();
      leave();
    } catch (error) {
      notify('동의하지 못했어요', errorMessage(error));
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          시작하기 전에
        </AppText>
        <View style={styles.navSide} />
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <PairHeader
          me={{ name: state?.me?.nickname ?? '나', tag: state?.me?.consented ? '동의 완료' : '동의 전', tone: state?.me?.consented ? 'ok' : 'low' }}
          partner={{ name: partner, tag: state?.partner?.consented ? '동의 완료' : '대기 중', tone: state?.partner?.consented ? 'ok' : 'plain' }}
          linked={false}
        />

        <Card style={styles.card}>
          <AppText size={14} lineHeight={24}>
            {'상대방이 회원님의 일기와 대화 기록으로 만들어진 '}
            <AppText weight="bold" size={14} lineHeight={24}>
              ‘과거의 회원님’
            </AppText>
            {'과 대화하게 됩니다.\n\n대화나 리포트 중에 상대방이 몰랐던 생각이나 고민이 드러날 수 있어요.\n\n동의는 언제든 철회할 수 있으며, 철회하면 상대방의 접근은 즉시 차단됩니다.'}
          </AppText>
        </Card>
        <Card tint style={styles.example}>
          <AppText size={13} lineHeight={20}>
            <AppText weight="bold" size={13} lineHeight={20}>
              예를 들면
            </AppText>
            {'\n3개월 전 일기에 적은 불안이 대화에서 그대로 나올 수 있어요.'}
          </AppText>
        </Card>

        <Card tint style={styles.notice}>
          <AppText weight="bold" size={13} lineHeight={20}>
            내 기록이 연인에게 이렇게 보여요
          </AppText>
          {NOTICES.map((item) => (
            <View key={item.label} style={styles.noticeRow}>
              <AppText weight="bold" size={12.5} lineHeight={19} color={colors.brown} style={styles.noticeLabel}>
                {item.label}
              </AppText>
              <AppText size={12.5} lineHeight={19} style={styles.noticeText}>
                {item.text}
              </AppText>
            </View>
          ))}
        </Card>

        <Pressable
          accessibilityRole="checkbox"
          accessibilityState={{ checked: agreed }}
          accessibilityLabel="위 내용을 이해했으며 커플 모드 데이터 활용에 동의합니다"
          onPress={() => setAgreed((on) => !on)}
          style={styles.agree}>
          <CheckBox checked={agreed} />
          <AppText size={14} lineHeight={21} style={styles.agreeText}>
            위 내용을 이해했으며 커플 모드 데이터 활용에 동의합니다
          </AppText>
        </Pressable>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        <Button label="동의하고 계속하기" disabled={!agreed} loading={giveConsent.isPending} onPress={() => void submit()} />
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
  navSide: {
    width: 22,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
    paddingBottom: 20,
  },
  card: {
    marginTop: 16,
  },
  example: {
    marginTop: 10,
    paddingVertical: 12,
  },
  notice: {
    marginTop: 10,
    gap: 8,
  },
  noticeRow: {
    flexDirection: 'row',
    gap: 10,
  },
  noticeLabel: {
    width: 64,
  },
  noticeText: {
    flex: 1,
  },
  agree: {
    marginTop: 16,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
  },
  agreeText: {
    flex: 1,
  },
  footer: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
});
