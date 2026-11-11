import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import type { MessengerUpload } from '@/api/uploads';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useMessengerUpload, useStartUpload } from '@/features/messenger/hooks';
import { getPendingUpload, setPendingUpload } from '@/features/messenger/pending';
import { personaKeys } from '@/features/past/hooks';
import { dateStringOf, formatYearMonthDay } from '@/lib/date';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

function goHome() {
  router.dismissTo('/');
}

function goBackToUpload() {
  router.replace('/messenger');
}

/** FAILED의 이유(명세 failureCode)를 풀어 쓴 문장 */
function failureMessage(code: string | null | undefined): string {
  switch (code) {
    case 'AI_UNAVAILABLE':
      return 'AI에 연결하지 못했어요. 잠시 뒤에 다시 시도해 주세요.';
    case 'AI_REFUSED':
      return 'AI의 안전 장치가 이 대화는 분석하지 못했어요. 다른 대화로 시도해 보세요.';
    case 'AI_BAD_RESPONSE':
      return 'AI의 답을 읽지 못했어요. 다시 시도해 주세요.';
    case 'PROCESSING_TIMEOUT':
      return '분석이 너무 오래 걸렸어요. 다시 시도해 주세요.';
    default:
      return '문제가 생겼어요. 잠시 뒤에 다시 시도해 주세요.';
  }
}

/**
 * 스토리보드 8번 분석 완료(바뀐 방식)와 그 앞뒤: 올린 대화를 서버가 분석하는 동안(PROCESSING, 1.5초마다 확인),
 * 끝나면(DONE) 무엇이 저장됐는지 보여주고, 실패하면(FAILED) 이유와 다시 시도를 알려줘요.
 */
export default function MessengerUploadStatusScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const upload = useMessengerUpload(id);
  const data = upload.data;
  const done = data?.status === 'DONE';

  // 끝나면 폰에 들고 있던 올린 내용은 필요 없고, 시점 고르기의 메신저 기록량은 달라졌어요
  useEffect(() => {
    if (!done) return;
    setPendingUpload(null);
    void queryClient.invalidateQueries({ queryKey: personaKeys.all });
  }, [done, queryClient]);

  let body;
  if (!data) {
    body = (
      <Center>
        {upload.isError ? (
          <>
            <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
              {errorMessage(upload.error)}
            </AppText>
            <View style={styles.actions}>
              <Button variant="line" label="다시 불러오기" onPress={() => void upload.refetch()} />
            </View>
          </>
        ) : (
          <AppText size={14} lineHeight={22} color={colors.muted}>
            불러오는 중이에요…
          </AppText>
        )}
      </Center>
    );
  } else if (data.status === 'PROCESSING') {
    body = <ProcessingView />;
  } else if (data.status === 'FAILED') {
    body = <FailedView upload={data} />;
  } else {
    body = <DoneView upload={data} bottomInset={insets.bottom} />;
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="닫기" hitSlop={12} onPress={goHome}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <View style={styles.navSide} />
      </View>
      {body}
    </View>
  );
}

function Center({ children }: { children: ReactNode }) {
  return <View style={styles.center}>{children}</View>;
}

function ProcessingView() {
  return (
    <Center>
      <ActivityIndicator color={colors.gold} size="large" />
      <AppText serif weight="bold" size={24} lineHeight={34} style={styles.centered}>
        대화를 분석하고 있어요
      </AppText>
      <AppText size={14} lineHeight={22} color={colors.brown} style={styles.centered}>
        내 말을 주 단위로 요약하고 있어요.
      </AppText>
      <Card tint style={styles.note}>
        <AppText size={13} lineHeight={20} style={styles.centered}>
          {'1~2분쯤 걸릴 수 있어요.\n화면을 나가도 서버에서 계속 분석해요.'}
        </AppText>
      </Card>
    </Center>
  );
}

function FailedView({ upload }: { upload: MessengerUpload }) {
  const startUpload = useStartUpload();
  const pending = getPendingUpload();
  // 앱을 다시 켜서 폰에 들고 있던 내 말이 사라졌으면 "다시 시도"를 못 해요(처음부터 다시 골라요)
  const canRetry = pending !== null;

  async function retry() {
    if (!pending) return;
    try {
      const created = await startUpload.mutateAsync(pending);
      router.replace({ pathname: '/messenger/[id]', params: { id: created.id } });
    } catch (error) {
      notify('다시 올리지 못했어요', errorMessage(error));
    }
  }

  return (
    <Center>
      <AppText serif weight="bold" size={24} lineHeight={34} style={styles.centered}>
        {'분석하지 못했어요'}
      </AppText>
      <AppText size={14} lineHeight={22} color={colors.brown} style={styles.centered}>
        {failureMessage(upload.failureCode)}
      </AppText>
      <View style={styles.actions}>
        {canRetry ? <Button label="다시 시도" loading={startUpload.isPending} onPress={() => void retry()} /> : null}
        <Button variant="line" label={canRetry ? '처음부터 다시 고르기' : '다시 고르기'} onPress={goBackToUpload} />
      </View>
    </Center>
  );
}

function DoneView({ upload, bottomInset }: { upload: MessengerUpload; bottomInset: number }) {
  const expires = formatYearMonthDay(dateStringOf(new Date(upload.expiresAt)));
  const period =
    upload.periodStart && upload.periodEnd
      ? `${formatYearMonthDay(upload.periodStart)} ~ ${formatYearMonthDay(upload.periodEnd)}`
      : null;

  return (
    <ScrollView
      contentContainerStyle={[styles.done, { paddingBottom: Math.max(bottomInset, 12) + 12 }]}
      showsVerticalScrollIndicator={false}>
      <View style={styles.okmark}>
        <Icon name="check" size={36} color={colors.ink} />
      </View>
      <AppText serif weight="bold" size={27} lineHeight={36} style={styles.centered}>
        분석이 끝났어요
      </AppText>
      <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.centered}>
        {period && upload.weekCount ? `${period} · ${upload.weekCount}주 요약을 저장했어요` : '요약을 저장했어요'}
      </AppText>

      <Card style={styles.summary}>
        <Row label="올린 방식" value="txt (내 말만)" />
        <Row label="상대 말" value="폰에서 버림" />
        <Row label="내 말 원문" value={`${expires}까지 보관`} last />
      </Card>

      <AppText size={12.5} lineHeight={20} color={colors.brown} style={styles.centered}>
        {`요약과 내 말 원문은 암호화해서 보관하고,\n1년 뒤(${expires})에 자동으로 지워져요.\n캘린더에는 표시되지 않아요.`}
      </AppText>

      <View style={styles.doneButton}>
        <Button variant="brown" label="홈으로" onPress={goHome} />
      </View>
    </ScrollView>
  );
}

function Row({ label, value, last = false }: { label: string; value: string; last?: boolean }) {
  return (
    <View style={[styles.summaryRow, !last && styles.summaryRowLine]}>
      <AppText size={14} lineHeight={21} color={colors.brown}>
        {label}
      </AppText>
      <AppText weight="bold" size={14} lineHeight={21} style={styles.value}>
        {value}
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
  navSide: {
    width: 22,
  },
  center: {
    flex: 1,
    paddingHorizontal: 22,
    paddingBottom: 48,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  centered: {
    textAlign: 'center',
  },
  note: {
    alignSelf: 'stretch',
    marginTop: 8,
    paddingVertical: 14,
  },
  actions: {
    alignSelf: 'stretch',
    gap: 10,
    marginTop: 16,
  },
  done: {
    flexGrow: 1,
    paddingHorizontal: 22,
    paddingTop: 34,
    alignItems: 'center',
    gap: 10,
  },
  okmark: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  summary: {
    alignSelf: 'stretch',
    marginTop: 18,
    marginBottom: 6,
    paddingVertical: 4,
    paddingHorizontal: 16,
  },
  summaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 12,
  },
  summaryRowLine: {
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  value: {
    flexShrink: 1,
    textAlign: 'right',
  },
  doneButton: {
    alignSelf: 'stretch',
    marginTop: 'auto',
    paddingTop: 24,
  },
});
