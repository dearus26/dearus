import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useStartUpload } from '@/features/messenger/hooks';
import { getParsedChat, setParsedChat, setPendingUpload } from '@/features/messenger/pending';
import { planTxtUpload } from '@/features/messenger/uploadPlan';
import { formatMonthDay, formatYearMonthDay } from '@/lib/date';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

/** 내 말 확인 화면에 보여주는 최근 말의 개수(전부 보여주면 너무 길어요) */
const PREVIEW_COUNT = 30;

/**
 * 스토리보드 6번(바뀐 방식) txt: 내 이름 고르기 · 내 말 확인.
 * 앱이 txt에서 읽은 말한 사람들 중 내 이름을 고르면, 내 말만 추려서 보여줘요. 이 내용만 서버로 가고 상대 말은 폰에서 버려요.
 */
export default function MessengerReviewScreen() {
  const insets = useSafeAreaInsets();
  const startUpload = useStartUpload();
  const parsed = getParsedChat();
  const [me, setMe] = useState<string | null>(null);

  // 앱을 다시 켜는 등으로 읽은 글이 사라졌으면 처음 화면으로 돌아가요
  if (!parsed) return <Redirect href="/messenger" />;

  const mine = me === null ? [] : parsed.messages.filter((message) => message.speaker === me);
  const plan = me === null ? null : planTxtUpload(mine);
  const preview = plan?.messages.slice(-PREVIEW_COUNT) ?? [];
  const hiddenCount = plan ? plan.messages.length - preview.length : 0;

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace('/messenger');
  }

  async function upload() {
    if (!plan) return;
    const pending = { messages: plan.messages };
    try {
      const created = await startUpload.mutateAsync(pending);
      setPendingUpload(pending);
      setParsedChat(null);
      router.replace({ pathname: '/messenger/[id]', params: { id: created.id } });
    } catch (error) {
      notify('올리지 못했어요', errorMessage(error));
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          내 말 확인
        </AppText>
        <View style={styles.navSide} />
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <AppText serif weight="bold" size={22} lineHeight={31}>
          어느 이름이 나인가요?
        </AppText>
        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.sub}>
          고른 이름의 말만 서버로 보내고, 나머지 사람의 말은 폰에서 버려요
        </AppText>

        <View style={styles.speakers} accessibilityRole="radiogroup">
          {parsed.speakers.map((speaker) => {
            const selected = me === speaker.name;
            const sample = parsed.messages.find((message) => message.speaker === speaker.name)?.text ?? '';
            return (
              <Pressable
                key={speaker.name}
                accessibilityRole="radio"
                accessibilityLabel={`${speaker.name}, 말 ${speaker.count}개`}
                accessibilityState={{ checked: selected }}
                onPress={() => setMe(speaker.name)}
                style={[styles.speaker, selected && styles.speakerOn]}>
                <View style={[styles.radio, selected && styles.radioOn]}>
                  {selected ? <View style={styles.radioDot} /> : null}
                </View>
                <View style={styles.speakerText}>
                  <AppText weight="bold" size={15} lineHeight={22} numberOfLines={1}>
                    {speaker.name}
                  </AppText>
                  <AppText size={12.5} lineHeight={19} color={colors.brown} numberOfLines={1}>
                    {sample.replace(/\s+/g, ' ')}
                  </AppText>
                </View>
                <AppText size={12.5} lineHeight={19} color={colors.brown}>
                  {`${speaker.count}개`}
                </AppText>
              </Pressable>
            );
          })}
        </View>

        {me !== null ? (
          plan ? (
            <>
              <Card tint style={styles.summary}>
                <AppText weight="bold" size={14.5} lineHeight={22}>
                  {`내 말 ${plan.messages.length.toLocaleString('ko-KR')}개 · ${plan.weekCount}주`}
                </AppText>
                <AppText size={13} lineHeight={20} color={colors.brown}>
                  {`${formatYearMonthDay(plan.periodStart)} ~ ${formatYearMonthDay(plan.periodEnd)}`}
                </AppText>
                {plan.notes.map((note) => (
                  <AppText key={note} size={12.5} lineHeight={19} color={colors.brown}>
                    {`· ${note}`}
                  </AppText>
                ))}
                {parsed.skipped > 0 ? (
                  <AppText size={12.5} lineHeight={19} color={colors.brown}>
                    {`· 입장 · 퇴장 안내, 사진 · 이모티콘만 있는 줄 ${parsed.skipped}개는 건너뛰었어요`}
                  </AppText>
                ) : null}
                {parsed.unreadable > 0 ? (
                  <AppText size={12.5} lineHeight={19} color={colors.brown}>
                    {`· 날짜를 읽지 못한 줄 ${parsed.unreadable}개는 빼고 보내요`}
                  </AppText>
                ) : null}
              </Card>

              <AppText weight="bold" size={14} lineHeight={21} style={styles.previewTitle}>
                서버로 가는 내 말
              </AppText>
              <View style={styles.preview}>
                {hiddenCount > 0 ? (
                  <AppText size={12} lineHeight={18} color={colors.brown} style={styles.hidden}>
                    {`앞의 ${hiddenCount.toLocaleString('ko-KR')}개는 줄여서 보여드려요(모두 올라가요)`}
                  </AppText>
                ) : null}
                {preview.map((message, index) => (
                  <View key={`${index}-${message.date}`} style={styles.line}>
                    <AppText size={11.5} lineHeight={17} color={colors.muted}>
                      {formatMonthDay(message.date)}
                    </AppText>
                    <AppText size={14} lineHeight={21}>
                      {message.text}
                    </AppText>
                  </View>
                ))}
              </View>
            </>
          ) : (
            <Card tint style={styles.summary}>
              <AppText size={13.5} lineHeight={21}>
                올릴 수 있는 말이 없어요. 다른 이름을 골라 보세요.
              </AppText>
            </Card>
          )
        ) : null}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        <Button
          label={plan ? `내 말 ${plan.messages.length.toLocaleString('ko-KR')}개 올리기` : '내 이름을 골라 주세요'}
          disabled={!plan}
          loading={startUpload.isPending}
          onPress={() => void upload()}
        />
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
    paddingBottom: 16,
  },
  sub: {
    marginTop: 4,
  },
  speakers: {
    marginTop: 14,
    gap: 8,
  },
  speaker: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: colors.beige,
    backgroundColor: colors.paper,
  },
  speakerOn: {
    borderColor: colors.gold,
    backgroundColor: colors.panel,
  },
  radio: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.beige,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOn: {
    borderColor: colors.gold,
  },
  radioDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.gold,
  },
  speakerText: {
    flex: 1,
  },
  summary: {
    marginTop: 14,
    paddingVertical: 14,
    paddingHorizontal: 16,
    gap: 2,
  },
  previewTitle: {
    marginTop: 16,
  },
  preview: {
    marginTop: 8,
    gap: 10,
  },
  hidden: {
    textAlign: 'center',
  },
  line: {
    gap: 1,
    paddingBottom: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  footer: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
});
