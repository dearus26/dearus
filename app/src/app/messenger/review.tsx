import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useStartUpload } from '@/features/messenger/hooks';
import { getParsedChat, getTargetDate, setParsedChat, setPendingUpload, setTargetDate } from '@/features/messenger/pending';
import { planTxtUpload } from '@/features/messenger/uploadPlan';
import { formatKoreanDate } from '@/lib/date';
import { notifyApiError } from '@/lib/apiErrors';
import { colors } from '@/theme/colors';

/** 내 말 확인 화면에 보여주는 말의 개수(하루치라 대개 다 보이지만, 아주 많은 날은 줄여요) */
const PREVIEW_COUNT = 30;

/**
 * 스토리보드 6번(바뀐 방식) txt: 내 이름 고르기 · 내 말 확인.
 * 앱이 txt에서 읽은 대화 중 **고른 날(홈 캘린더에서 넘어온 날)** 에 말한 사람들을 보여주고, 내 이름을 고르면 그날의 내 말만 추려서 보여줘요.
 * 이 내용만 서버로 가고 다른 날과 상대 말은 폰에서 버려요(2026.11.25).
 */
export default function MessengerReviewScreen() {
  const insets = useSafeAreaInsets();
  const startUpload = useStartUpload();
  const parsed = getParsedChat();
  const targetDate = getTargetDate();
  const [me, setMe] = useState<string | null>(null);

  // 앱을 다시 켜는 등으로 읽은 글이나 고른 날이 사라졌으면 처음 화면으로 돌아가요
  if (!parsed || !targetDate) return <Redirect href="/messenger" />;

  // 고른 날에 나눈 말만 봐요. 말한 사람과 개수도 그날 기준이에요(그날 말한 사람만 목록에 나와요).
  const dayMessages = parsed.messages.filter((message) => message.date === targetDate);
  const counts = new Map<string, number>();
  for (const message of dayMessages) counts.set(message.speaker, (counts.get(message.speaker) ?? 0) + 1);
  const speakers = [...counts.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count);

  const mine = me === null ? [] : dayMessages.filter((message) => message.speaker === me);
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
      setTargetDate(null);
      router.replace({ pathname: '/messenger/[id]', params: { id: created.id } });
    } catch (error) {
      notifyApiError('올리지 못했어요', error);
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
          {`${formatKoreanDate(targetDate)}의 대화예요. 고른 이름의 말만 서버로 보내고, 나머지 사람의 말은 보내지 않아요`}
        </AppText>

        {speakers.length === 0 ? (
          <Card tint style={styles.summary}>
            <AppText size={13.5} lineHeight={21}>
              이 txt에는 이 날 나눈 대화가 없어요. 다른 파일을 고르거나 홈 캘린더에서 다른 날을 골라 주세요.
            </AppText>
          </Card>
        ) : null}

        <View style={styles.speakers} accessibilityRole="radiogroup">
          {speakers.map((speaker) => {
            const selected = me === speaker.name;
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
                  {`내 말 ${plan.messages.length.toLocaleString('ko-KR')}개`}
                </AppText>
                <AppText size={13} lineHeight={20} color={colors.brown}>
                  {formatKoreanDate(targetDate)}
                </AppText>
                {plan.notes.map((note) => (
                  <AppText key={note} size={12.5} lineHeight={19} color={colors.brown}>
                    {`· ${note}`}
                  </AppText>
                ))}
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
