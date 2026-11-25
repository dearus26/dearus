import { router, useLocalSearchParams } from 'expo-router';
import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { isReportPending, type Report } from '@/api/chats';
import { ApiError, errorMessage } from '@/api/errors';
import type { ShortcutKey } from '@/api/personas';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useCouple } from '@/features/couple/hooks';
import { useChat, useEndChat, useReport } from '@/features/past/hooks';
import { josa, personaTitle } from '@/features/past/labels';
import { dateStringOf, formatYearMonthDay } from '@/lib/date';
import { notifyApiError } from '@/lib/apiErrors';
import { colors } from '@/theme/colors';

/**
 * 스토리보드 B-6 회고 리포트: 당시 고민 · 현재 변화 · 한 줄 요약.
 * 끝내기를 누르면 서버가 뒤에서 만들어서, 만드는 중(202)이면 1.5초마다 다시 확인해요.
 * 커플 모드의 리포트는 당시 고민 · 현재 변화 대신 "그날의 연인에게서 들은 이야기"와 한 줄 요약이에요(명세).
 * 커플 대화는 끝내면 두 시점 비교로 돌아가고, 이 화면은 비교 화면의 "내 대화 리포트 보기"로 열려요.
 */
export default function ReportScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const insets = useSafeAreaInsets();
  const report = useReport(id);
  const session = useChat(id).data;
  const endChat = useEndChat();
  const data = report.data;
  const isCouple = session?.mode === 'COUPLE' || (data !== undefined && !isReportPending(data) && data.mode === 'COUPLE');
  const couple = useCouple(isCouple);
  const partner = isCouple ? (couple.data?.partner?.nickname ?? '연인') : undefined;

  function leave() {
    // 끝난 대화로 돌아가는 게 아니라 목록으로 가요(리포트는 끝내기에서 replace로 열렸어요)
    if (router.canGoBack()) router.back();
    else router.replace(isCouple ? '/couple' : '/past');
  }

  // 만들기에 실패한 리포트는 끝내기를 다시 부르면 다시 만들어요(명세). 끝나면 리포트 조회가 자동으로 다시 시작돼요.
  async function retry() {
    try {
      await endChat.mutateAsync(id);
    } catch (error) {
      notifyApiError('다시 만들지 못했어요', error);
    }
  }

  let body;
  if (data && !isReportPending(data)) {
    body = (
      <ReportView
        report={data}
        shortcutKey={session?.shortcutKey ?? null}
        partner={partner}
        bottomInset={insets.bottom}
      />
    );
  } else if (data || report.isPending) {
    body = (
      <Notice
        loading
        title={data ? '리포트를 만들고 있어요' : '리포트를 불러오는 중이에요'}
        text={data ? '이 대화에서 내가 한 말을 바탕으로 정리해요. 잠시만 기다려 주세요.' : undefined}
      />
    );
  } else {
    const code = report.error instanceof ApiError ? report.error.code : null;
    if (code === 'REPORT_NOT_AVAILABLE') {
      body = (
        <Notice
          title="이야기가 짧아 리포트를 만들지 못했어요"
          text="내 말이 두 번 이상 있어야 리포트를 만들 수 있어요."
          actions={
            isCouple ? (
              <Button label="커플 화면으로" onPress={() => router.replace('/couple')} />
            ) : (
              <>
                <Button label="목록으로" onPress={() => router.replace('/past')} />
                <Button variant="line" label="다른 시점 만나보기" onPress={() => router.replace('/persona/pick')} />
              </>
            )
          }
        />
      );
    } else if (code === 'CHAT_NOT_ENDED') {
      body = (
        <Notice
          title="아직 끝나지 않은 대화예요"
          text="대화를 끝내면 리포트를 만들어 드려요."
          actions={
            <Button label="대화로 돌아가기" onPress={() => router.replace({ pathname: '/chat/[id]', params: { id } })} />
          }
        />
      );
    } else if (code === 'REPORT_FAILED') {
      body = (
        <Notice
          title="리포트를 만들지 못했어요"
          text="대화는 그대로 저장돼 있어요. 다시 만들어 볼까요?"
          actions={
            <>
              <Button label="다시 만들기" loading={endChat.isPending} onPress={() => void retry()} />
              <Button
                variant="line"
                label={isCouple ? '커플 화면으로' : '목록으로'}
                onPress={() => router.replace(isCouple ? '/couple' : '/past')}
              />
            </>
          }
        />
      );
    } else {
      body = (
        <Notice
          title="리포트를 불러오지 못했어요"
          text={errorMessage(report.error)}
          actions={<Button variant="line" label="다시 불러오기" onPress={() => void report.refetch()} />}
        />
      );
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          회고 리포트
        </AppText>
        <View style={styles.navSide} />
      </View>
      {body}
    </View>
  );
}

type ReportViewProps = {
  report: Report;
  /** 목록 항목처럼 "3개월 전의 나"로 부르려면 바로가기 정보가 필요해요(리포트에는 없고 세션에 있어요) */
  shortcutKey: ShortcutKey | null;
  /** 커플 모드 리포트일 때 연인의 이름 */
  partner: string | undefined;
  bottomInset: number;
};

function ReportView({ report, shortcutKey, partner, bottomInset }: ReportViewProps) {
  const couple = report.mode === 'COUPLE';
  const title = personaTitle(
    { scope: report.scope, shortcutKey, anchorDate: report.anchorDate },
    couple ? (partner ?? '연인') : undefined,
  );
  const minutes = report.durationMinutes < 1 ? '1분이 안 되게' : `${report.durationMinutes}분`;

  return (
    <ScrollView
      contentContainerStyle={[styles.content, { paddingBottom: Math.max(bottomInset, 12) + 12 }]}
      showsVerticalScrollIndicator={false}>
      <AppText size={12.5} lineHeight={19} color={colors.brown}>
        {`${title}${josa(title, '과', '와')} ${minutes} 대화했어요`}
      </AppText>
      <AppText serif weight="bold" size={27} lineHeight={36} style={styles.date}>
        {formatYearMonthDay(dateStringOf(new Date(report.createdAt)))}
      </AppText>

      {report.pastConcern ? <ReportCard label="당시 고민" text={report.pastConcern} /> : null}
      {report.presentChange ? <ReportCard label="현재 변화" text={report.presentChange} /> : null}
      {report.memorySummary ? (
        <ReportCard
          label={couple ? `그날의 ${partner ?? '연인'}에게서 들은 이야기` : '내가 기억하는 그날'}
          text={report.memorySummary}
        />
      ) : null}
      <ReportCard label="한 줄 요약" text={report.oneLineSummary} highlight />

      <View style={styles.actions}>
        {couple ? (
          <>
            <Button
              label="두 시점 비교 보기"
              variant="brown"
              onPress={() =>
                router.replace({
                  pathname: '/couples/compare',
                  params: { date: report.anchorDate, scope: report.scope },
                })
              }
            />
            <Button variant="line" label="커플 화면으로" onPress={() => router.replace('/couple')} />
            <AppText size={12} lineHeight={18} color={colors.brown} style={styles.saved}>
              두 사람의 대화와 리포트는 철회하면 두 사람 것 모두 지워져요
            </AppText>
          </>
        ) : (
          <>
            <Button label="목록으로" variant="brown" onPress={() => router.replace('/past')} />
            <Button variant="line" label="다른 시점 만나보기" onPress={() => router.replace('/persona/pick')} />
            <AppText size={12} lineHeight={18} color={colors.brown} style={styles.saved}>
              리포트는 자동으로 저장돼요. 과거의 나 목록에서 끝난 대화를 눌러 다시 볼 수 있어요
            </AppText>
          </>
        )}
      </View>
    </ScrollView>
  );
}

function ReportCard({ label, text, highlight = false }: { label: string; text: string; highlight?: boolean }) {
  return (
    <Card style={[styles.card, highlight && styles.highlight]}>
      <AppText weight="bold" size={12} lineHeight={18} color={highlight ? colors.ink : colors.brown} style={styles.label}>
        {label}
      </AppText>
      {highlight ? (
        <AppText serif weight="bold" size={18} lineHeight={27} style={styles.cardText}>
          {text}
        </AppText>
      ) : (
        <AppText size={14.5} lineHeight={23} style={styles.cardText}>
          {text}
        </AppText>
      )}
    </Card>
  );
}

type NoticeProps = {
  title: string;
  text?: string;
  loading?: boolean;
  actions?: ReactNode;
};

/** 리포트를 만드는 중이거나 만들지 못했을 때의 안내 화면 */
function Notice({ title, text, loading = false, actions }: NoticeProps) {
  return (
    <View style={styles.notice}>
      {loading ? <ActivityIndicator color={colors.gold} size="large" /> : null}
      <AppText serif weight="bold" size={22} lineHeight={31} style={styles.noticeText}>
        {title}
      </AppText>
      {text ? (
        <AppText size={14} lineHeight={22} color={colors.brown} style={styles.noticeText}>
          {text}
        </AppText>
      ) : null}
      {actions ? <View style={styles.noticeActions}>{actions}</View> : null}
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
    flexGrow: 1,
    paddingHorizontal: 22,
    paddingTop: 8,
  },
  date: {
    marginTop: 4,
  },
  card: {
    marginTop: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
  },
  highlight: {
    backgroundColor: colors.gold,
    borderColor: colors.gold,
  },
  label: {
    letterSpacing: 0.3,
  },
  cardText: {
    marginTop: 6,
  },
  actions: {
    marginTop: 'auto',
    paddingTop: 24,
    gap: 10,
  },
  saved: {
    textAlign: 'center',
  },
  notice: {
    flex: 1,
    paddingHorizontal: 22,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingBottom: 48,
  },
  noticeText: {
    textAlign: 'center',
  },
  noticeActions: {
    alignSelf: 'stretch',
    gap: 10,
    marginTop: 12,
  },
});
