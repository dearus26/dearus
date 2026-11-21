import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiError, errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { CheckBox } from '@/components/CheckBox';
import { Icon } from '@/components/Icon';
import { Tag } from '@/components/Tag';
import { formatBirthDateInput, isValidBirthDate } from '@/lib/date';
import { notify } from '@/lib/dialog';
import type { LegalDocType } from '@/legal/documents';
import { useSession } from '@/session/SessionProvider';
import { colors } from '@/theme/colors';
import { fontFamily } from '@/theme/fonts';

/** 스토리보드 A-2 약관 · 동의 (최초 로그인 때 한 번) */
export default function ConsentScreen() {
  const insets = useSafeAreaInsets();
  const { user, giveConsent, signOut } = useSession();

  const [terms, setTerms] = useState(false);
  const [privacy, setPrivacy] = useState(false);
  const [aiAnalysis, setAiAnalysis] = useState(false);
  const [sensitive, setSensitive] = useState(false);
  const [birthDate, setBirthDate] = useState('');
  const [notifications, setNotifications] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 생년월일은 처음 한 번만 받아요. 이미 저장돼 있으면 입력 칸을 보여주지 않아요
  const needsBirthDate = user?.birthDate == null;
  const birthDateComplete = birthDate.length === 10;
  const birthDateOk = isValidBirthDate(birthDate);
  const requiredDone = terms && privacy && aiAnalysis && sensitive;
  const canSubmit = requiredDone && (!needsBirthDate || birthDateOk);
  const allChecked = requiredDone && notifications;

  function toggleAll() {
    const next = !allChecked;
    setTerms(next);
    setPrivacy(next);
    setAiAnalysis(next);
    setSensitive(next);
    setNotifications(next);
  }

  async function submit() {
    setSubmitting(true);
    try {
      // 성공하면 needsConsent가 false가 되어 루트 레이아웃이 알아서 홈으로 넘겨요
      await giveConsent({ notifications, birthDate: needsBirthDate ? birthDate : undefined });
    } catch (error) {
      if (error instanceof ApiError && error.code === 'GUARDIAN_CONSENT_REQUIRED') {
        notify('지금은 가입을 마칠 수 없어요', '만 14세 미만은 보호자(법정대리인)의 동의가 필요해요. 보호자 동의 절차는 준비 중이에요.');
      } else {
        notify('동의를 저장하지 못했어요', errorMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  const items: {
    key: LegalDocType;
    label: string;
    required: boolean;
    checked: boolean;
    set: (value: boolean) => void;
  }[] = [
    { key: 'terms', label: '이용약관', required: true, checked: terms, set: setTerms },
    { key: 'privacy', label: '개인정보 수집 · 이용', required: true, checked: privacy, set: setPrivacy },
    { key: 'ai', label: '일기 · 대화 내용 AI 분석', required: true, checked: aiAnalysis, set: setAiAnalysis },
    { key: 'sensitive', label: '민감정보(건강 등) 처리', required: true, checked: sensitive, set: setSensitive },
    { key: 'notifications', label: '일기 작성 알림 받기', required: false, checked: notifications, set: setNotifications },
  ];

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="처음으로 돌아가기"
          hitSlop={12}
          onPress={() => void signOut()}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          서비스 이용 동의
        </AppText>
        <View style={styles.navSpacer} />
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}>
        <AppText serif size={24} lineHeight={32} style={styles.title}>
          {'Dear Us를 시작하기 전에\n확인해 주세요'}
        </AppText>
        <AppText size={13} lineHeight={20} color={colors.muted} style={styles.subtitle}>
          처음 한 번만 동의하면 돼요
        </AppText>

        {needsBirthDate ? (
          <Card style={styles.birthCard}>
            <View style={styles.itemLabel}>
              <Tag label="필수" />
              <AppText weight="bold" size={14} lineHeight={20}>
                생년월일
              </AppText>
            </View>
            <TextInput
              accessibilityLabel="생년월일 8자리"
              value={birthDate}
              onChangeText={(text) => setBirthDate(formatBirthDateInput(text))}
              keyboardType="number-pad"
              maxLength={10}
              placeholder="예) 20030514"
              placeholderTextColor={colors.muted}
              style={styles.birthInput}
            />
            <AppText
              size={12.5}
              lineHeight={19}
              color={birthDateComplete && !birthDateOk ? colors.ink : colors.muted}>
              {birthDateComplete && !birthDateOk
                ? '생년월일을 다시 확인해 주세요'
                : '나이 확인에만 써요. 한 번 저장하면 바꿀 수 없어요'}
            </AppText>
          </Card>
        ) : null}

        <Card style={styles.agreeCard}>
          <Pressable
            accessibilityRole="checkbox"
            accessibilityState={{ checked: allChecked }}
            onPress={toggleAll}
            style={styles.allRow}>
            <CheckBox checked={allChecked} />
            <AppText weight="bold" size={15}>
              전체 동의
            </AppText>
          </Pressable>

          {items.map((item) => (
            <View key={item.key} style={styles.itemRow}>
              <Pressable
                accessibilityRole="checkbox"
                accessibilityState={{ checked: item.checked }}
                onPress={() => item.set(!item.checked)}
                style={styles.itemMain}>
                <CheckBox checked={item.checked} />
                <View style={styles.itemLabel}>
                  <Tag label={item.required ? '필수' : '선택'} low={!item.required} />
                  <AppText size={14} lineHeight={20} style={styles.itemText}>
                    {item.label}
                  </AppText>
                </View>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`${item.label} 자세히 보기`}
                hitSlop={10}
                onPress={() => router.push({ pathname: '/legal/[type]', params: { type: item.key } })}>
                <Icon name="chevronRight" size={16} color={colors.muted} />
              </Pressable>
            </View>
          ))}
        </Card>

        <Card tint style={styles.notice}>
          <AppText size={12.5} lineHeight={20}>
            {'글자를 가리지 않고 AI로 보내요. 학습에는 쓰이지 않아요\nAI 분석 동의는 설정에서 언제든 철회할 수 있어요'}
          </AppText>
        </Card>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) + 12 }]}>
        <Button label="동의하고 시작하기" onPress={submit} disabled={!canSubmit} loading={submitting} />
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
    height: 44,
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
    paddingTop: 18,
    paddingBottom: 16,
  },
  title: {},
  subtitle: {
    marginTop: 8,
  },
  birthCard: {
    marginTop: 18,
    gap: 8,
  },
  birthInput: {
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 10,
    backgroundColor: colors.white,
    fontFamily: fontFamily.sans.regular,
    fontSize: 16,
    color: colors.ink,
  },
  agreeCard: {
    marginTop: 18,
    paddingTop: 6,
    paddingBottom: 4,
  },
  allRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 12,
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 13,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  itemMain: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  itemLabel: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    columnGap: 8,
  },
  itemText: {
    flexShrink: 1,
  },
  notice: {
    marginTop: 12,
  },
  footer: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
});
