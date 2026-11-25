import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { CoupleComparison, CoupleScope } from '@/api/couples';
import { ApiError, errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useCouple, useCoupleComparison } from '@/features/couple/hooks';
import { useCreateChat } from '@/features/past/hooks';
import { josa, personaDateTitle } from '@/features/past/labels';
import { notifyApiError } from '@/lib/apiErrors';
import { colors, emotionColors } from '@/theme/colors';

function leave() {
  if (router.canGoBack()) router.back();
  else router.replace('/couple');
}

/**
 * 스토리보드 C-4 두 시점 비교. 같은 날(또는 같은 무렵)을 두 사람이 각자 그날의 연인과 이야기한 뒤, 두 요약을 나란히 보고
 * 서로 다르게 기억한 지점(간극)을 한 줄로 봐요. 내가 먼저 대화하지 않으면 볼 수 없어서(먼저 보면 대화가 달라질 수 있어요),
 * 대화 전에는 "그날의 연인 만나기"로, 대화 중에는 "이어서 대화하기"로, 상대가 끝내길 기다리는 동안에는 기다리는 안내로 바뀌어요.
 * 상태(GET /couples/comparisons)는 상대를 기다리거나 간극을 만드는 동안 4초마다 다시 확인해요.
 */
export default function CompareScreen() {
  const params = useLocalSearchParams<{ date: string; scope: string }>();
  const date = params.date;
  const scope: CoupleScope = params.scope === 'AROUND' ? 'AROUND' : 'DAY';
  const insets = useSafeAreaInsets();
  const couple = useCouple();
  const comparison = useCoupleComparison(date, scope);
  const createChat = useCreateChat();
  const data = comparison.data;

  const myName = couple.data?.me?.nickname ?? '나';
  const partnerName = data?.partnerNickname ?? couple.data?.partner?.nickname ?? '상대';

  // 대화를 하고 돌아오면 바뀐 상태를 바로 보여줘요(처음 열 때의 요청과 겹치면 기다리던 요청을 그대로 써요)
  const { refetch } = comparison;
  useFocusEffect(
    useCallback(() => {
      void refetch({ cancelRefetch: false });
    }, [refetch]),
  );

  async function start() {
    try {
      const session = await createChat.mutateAsync({ anchorDate: date, scope, forCouple: true });
      router.push({ pathname: '/chat/[id]', params: { id: session.id } });
    } catch (error) {
      notifyApiError(`${partnerName}${josa(partnerName, '을', '를')} 만나지 못했어요`, error);
    }
  }

  const when = personaDateTitle({ scope, anchorDate: date }, partnerName);
  let body: ReactNode;

  if (!data) {
    const accessDenied = comparison.error instanceof ApiError && comparison.error.code === 'COUPLE_ACCESS_DENIED';
    body = comparison.isError ? (
      <Notice
        title={accessDenied ? '지금은 볼 수 없어요' : '불러오지 못했어요'}
        text={errorMessage(comparison.error)}
        actions={
          accessDenied ? (
            <Button label="커플 화면으로" onPress={() => router.replace('/couple')} />
          ) : (
            <Button variant="line" label="다시 불러오기" onPress={() => void comparison.refetch()} />
          )
        }
      />
    ) : (
      <Notice loading title="불러오는 중이에요" />
    );
  } else if (data.status === 'NOT_STARTED') {
    body = (
      <Notice
        title={`${when}${josa(when, '을', '를')}\n먼저 만나 보세요`}
        text={`각자 그날의 연인과 이야기한 뒤에 두 사람의 기억을 나란히 볼 수 있어요. ${partnerName}님이 하는 이야기는 내가 끝내기 전에는 보이지 않아요.`}
        actions={<Button label={`${partnerName}${josa(partnerName, '과', '와')} 대화하기`} loading={createChat.isPending} onPress={() => void start()} />}
      />
    );
  } else if (data.status === 'IN_PROGRESS') {
    body = (
      <Notice
        title="대화를 이어가고 있어요"
        text="대화를 끝내야 두 사람의 기억을 볼 수 있어요."
        actions={
          <Button
            label="이어서 대화하기"
            onPress={() => (data.chatId ? router.push({ pathname: '/chat/[id]', params: { id: data.chatId } }) : void start())}
          />
        }
      />
    );
  } else {
    body = (
      <ComparisonView
        data={data}
        date={date}
        myName={myName}
        partnerName={partnerName}
        bottomInset={insets.bottom}
      />
    );
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          두 시점 비교
        </AppText>
        <View style={styles.navSide} />
      </View>
      {body}
    </View>
  );
}

type ViewProps = {
  data: CoupleComparison;
  date: string;
  myName: string;
  partnerName: string;
  bottomInset: number;
};

/** 내가 끝낸 뒤의 화면: 내가 들은 이야기, (상대가 끝내면) 상대가 들은 이야기, (간극이 완성되면) 두 사람의 간극 */
function ComparisonView({ data, date, myName, partnerName, bottomInset }: ViewProps) {
  const { partnerPastSelf, myPastSelf, gapSummary, status } = data;
  const title = personaDateTitle({ scope: data.scope, anchorDate: date }, '우리');

  return (
    <ScrollView
      contentContainerStyle={[styles.content, { paddingBottom: Math.max(bottomInset, 12) + 12 }]}
      showsVerticalScrollIndicator={false}>
      <AppText size={12.5} lineHeight={19} color={colors.brown}>
        {`${title} · 각자의 기억을 나란히 봤어요`}
      </AppText>
      <AppText serif weight="bold" size={27} lineHeight={36} style={styles.title}>
        {'같은 하루,\n다른 기억'}
      </AppText>

      {partnerPastSelf ? (
        <MemoryCard
          initial={partnerName}
          alt
          label={`내가 들은 그날의 ${partnerName}`}
          text={partnerPastSelf.memorySummary}
        />
      ) : (
        // 내가 끝낸 직후라 내 대화의 요약이 아직 만들어지는 중이에요
        <WaitingCard text="내 대화를 정리하고 있어요" />
      )}
      {partnerPastSelf ? (
        myPastSelf ? (
          <MemoryCard initial={myName} label={`${partnerName}님이 들은 그날의 나`} text={myPastSelf.memorySummary} />
        ) : (
          <WaitingCard text={`${partnerName}님이 대화를 끝내면 ${partnerName}님이 들은 이야기가 여기에 나란히 보여요`} />
        )
      ) : null}

      {status === 'READY' && gapSummary ? (
        <Card style={[styles.card, styles.gap]}>
          <AppText weight="bold" size={12} lineHeight={18} color={colors.ink} style={styles.label}>
            두 사람의 간극
          </AppText>
          <AppText serif weight="bold" size={17} lineHeight={26} style={styles.cardText}>
            {gapSummary}
          </AppText>
        </Card>
      ) : myPastSelf ? (
        <WaitingCard text="두 사람의 간극을 정리하고 있어요" />
      ) : null}

      {status === 'READY' ? (
        <Card tint style={styles.card}>
          <AppText size={13.5} lineHeight={22}>
            <AppText weight="bold" size={13.5} lineHeight={22}>
              이제 직접 이야기해 보세요
            </AppText>
            {'\n그날 못했던 말을, 오늘의 두 사람에게 들려주세요.'}
          </AppText>
        </Card>
      ) : null}

      <View style={styles.actions}>
        {data.chatId ? (
          <Button
            variant="brown"
            label="내 대화 리포트 보기"
            onPress={() => router.push({ pathname: '/report/[id]', params: { id: data.chatId as string } })}
          />
        ) : null}
        <Button variant="line" label="다른 날 고르기" onPress={() => router.replace('/couple')} />
        <AppText size={12} lineHeight={18} color={colors.brown} style={styles.saved}>
          두 사람의 대화와 리포트는 설정이나 커플 화면에서 철회하면 모두 지워져요
        </AppText>
      </View>
    </ScrollView>
  );
}

function MemoryCard({ initial, label, text, alt = false }: { initial: string; label: string; text: string; alt?: boolean }) {
  return (
    <Card style={[styles.card, { borderColor: emotionColors.ANGRY }]}>
      <View style={styles.cardHead}>
        <View style={[styles.avatar, alt && styles.avatarAlt]}>
          <AppText serif weight="bold" size={11} lineHeight={16} color={alt ? colors.cream : colors.ink}>
            {initial.slice(0, 1)}
          </AppText>
        </View>
        <AppText weight="bold" size={12} lineHeight={18} color={colors.brown} style={styles.label}>
          {label}
        </AppText>
      </View>
      <AppText size={14.5} lineHeight={23} style={styles.cardText}>
        {text}
      </AppText>
    </Card>
  );
}

function WaitingCard({ text }: { text: string }) {
  return (
    <Card tint style={[styles.card, styles.waiting]}>
      <ActivityIndicator size="small" color={colors.gold} />
      <AppText size={13.5} lineHeight={21} color={colors.brown} style={styles.waitingText}>
        {text}
      </AppText>
    </Card>
  );
}

type NoticeProps = {
  title: string;
  text?: string;
  loading?: boolean;
  actions?: ReactNode;
};

/** 비교를 아직 볼 수 없을 때(대화 전 · 대화 중 · 불러오는 중 · 오류)의 안내 화면 */
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
  title: {
    marginTop: 4,
  },
  card: {
    marginTop: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
  },
  cardHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  avatar: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarAlt: {
    backgroundColor: colors.brown,
  },
  gap: {
    backgroundColor: colors.gold,
    borderColor: colors.gold,
  },
  label: {
    letterSpacing: 0.3,
  },
  cardText: {
    marginTop: 6,
  },
  waiting: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  waitingText: {
    flex: 1,
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
