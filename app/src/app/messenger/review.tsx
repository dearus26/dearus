import { Redirect, router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Icon } from '@/components/Icon';
import { useStartUpload } from '@/features/messenger/hooks';
import { getParsedChat, getTargetDate, setParsedChat, setPendingUpload, setTargetDate } from '@/features/messenger/pending';
import { planTxtUpload } from '@/features/messenger/uploadPlan';
import { formatKoreanDate, formatMonthDay, isPickablePastDate } from '@/lib/date';
import { notifyApiError } from '@/lib/apiErrors';
import { colors } from '@/theme/colors';

/** 내 말 확인 화면에 보여주는 말의 개수(고른 날이 적으면 대개 다 보이지만, 많으면 줄여요) */
const PREVIEW_COUNT = 30;
/** 한 번에 올릴 수 있는 날 수예요. 많이 고를수록 분석이 오래 걸려서 2주로 묶었어요(2026.11.28) */
export const MAX_UPLOAD_DAYS = 14;
/** 날 고르기에서 처음에 보여주는 날 수(최근 것부터). 더 보기를 누르면 모두 보여요 */
const INITIAL_DAY_CHIPS = 21;

/**
 * 스토리보드 6번(바뀐 방식) txt: 내 이름 고르기 · 내 말 확인.
 * txt에 있는 날 중 올릴 날을 고르고(홈 캘린더에서 넘어온 날을 먼저 골라 둬요, 최대 14일, 2026.11.28),
 * 그날들에 말한 사람 중 내 이름을 고르면 고른 날의 내 말만 추려서 보여줘요.
 * 이 내용만 서버로 가고 다른 날과 상대 말은 보내지 않아요. 과거의 나로 만날 수 있는 날(최근 1년)만 고를 수 있어요.
 */
export default function MessengerReviewScreen() {
  const insets = useSafeAreaInsets();
  const startUpload = useStartUpload();
  const parsed = getParsedChat();
  const targetDate = getTargetDate();
  const [me, setMe] = useState<string | null>(null);
  // 이 파일에서 고를 수 있는 날(최근 1년, 최근 것부터)
  const availableDates = useMemo(
    () => [...new Set((parsed?.messages ?? []).map((message) => message.date))].filter((date) => isPickablePastDate(date)).sort().reverse(),
    [parsed],
  );
  const [selectedDates, setSelectedDates] = useState<string[]>(() =>
    targetDate && availableDates.includes(targetDate) ? [targetDate] : [],
  );
  const [showAllDates, setShowAllDates] = useState(false);

  // 앱을 다시 켜는 등으로 읽은 글이 사라졌으면 처음 화면으로 돌아가요
  if (!parsed) return <Redirect href="/messenger" />;

  function toggleDate(date: string) {
    setSelectedDates((current) =>
      current.includes(date) ? current.filter((d) => d !== date) : current.length >= MAX_UPLOAD_DAYS ? current : [...current, date],
    );
  }

  // 고른 날에 나눈 말만 봐요. 말한 사람과 개수도 고른 날 기준이에요(그날들에 말한 사람만 목록에 나와요).
  const selectedSet = new Set(selectedDates);
  const dayMessages = parsed.messages.filter((message) => selectedSet.has(message.date));
  const visibleDates = showAllDates ? availableDates : availableDates.slice(0, INITIAL_DAY_CHIPS);
  const sortedSelected = [...selectedDates].sort();
  const selectedText =
    sortedSelected.length === 0
      ? ''
      : sortedSelected.length === 1
        ? formatKoreanDate(sortedSelected[0])
        : `${formatMonthDay(sortedSelected[0])} ~ ${formatMonthDay(sortedSelected[sortedSelected.length - 1])} 중 ${sortedSelected.length}일`;
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
          올릴 날과 내 이름을 골라 주세요
        </AppText>
        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.sub}>
          {`고른 날에 나눈 내 말만 서버로 보내고, 다른 날과 나머지 사람의 말은 보내지 않아요. 한 번에 ${MAX_UPLOAD_DAYS}일까지 고를 수 있어요`}
        </AppText>

        <AppText weight="bold" size={14} lineHeight={21} style={styles.previewTitle}>
          {`올릴 날 ${selectedDates.length}/${MAX_UPLOAD_DAYS}`}
        </AppText>
        {targetDate && !availableDates.includes(targetDate) ? (
          <AppText size={12.5} lineHeight={19} color={colors.brown}>
            {`${formatKoreanDate(targetDate)}의 대화는 이 파일에 없어요. 아래에서 올릴 날을 골라 주세요`}
          </AppText>
        ) : null}
        {availableDates.length === 0 ? (
          <Card tint style={styles.summary}>
            <AppText size={13.5} lineHeight={21}>
              이 txt에는 최근 1년 안에 나눈 대화가 없어요. 다른 파일을 골라 주세요.
            </AppText>
          </Card>
        ) : (
          <View style={styles.dates}>
            {visibleDates.map((date) => (
              <ChoiceChip
                key={date}
                label={formatMonthDay(date)}
                selected={selectedSet.has(date)}
                disabled={!selectedSet.has(date) && selectedDates.length >= MAX_UPLOAD_DAYS}
                onPress={() => toggleDate(date)}
              />
            ))}
            {!showAllDates && availableDates.length > INITIAL_DAY_CHIPS ? (
              <ChoiceChip label={`더 보기 (${availableDates.length - INITIAL_DAY_CHIPS}일)`} selected={false} onPress={() => setShowAllDates(true)} />
            ) : null}
          </View>
        )}

        {selectedDates.length > 0 ? (
          <AppText weight="bold" size={14} lineHeight={21} style={styles.previewTitle}>
            어느 이름이 나인가요?
          </AppText>
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
                  {selectedText}
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
          label={
            selectedDates.length === 0
              ? '올릴 날을 골라 주세요'
              : plan
                ? `내 말 ${plan.messages.length.toLocaleString('ko-KR')}개 올리기`
                : '내 이름을 골라 주세요'
          }
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
  dates: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 8,
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
