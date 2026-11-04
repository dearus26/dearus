import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState, type ReactNode } from 'react';
import { Animated, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { ChatSession } from '@/api/chats';
import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useChat, useCreateChat } from '@/features/past/hooks';
import { personaRingLabel, personaTitle, windowText } from '@/features/past/labels';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

/** 시점 분석의 단계 순서(명세 preparingStep) */
const STEPS = ['COLLECT', 'MASK', 'ANALYZE', 'BUILD'] as const;
/** 서버가 단계를 모를 때(null) 앱이 단계를 한 칸씩 넘기는 간격 */
const MIMIC_STEP_MS = 3000;

function leave() {
  if (router.canGoBack()) router.back();
  else router.replace('/past');
}

/**
 * 과거의 나와의 대화 방. 세션 상태에 따라 보여줘요.
 *  - PREPARING: 스토리보드 B-3 "불러오는 중" (1.5초마다 상태를 확인해요)
 *  - FAILED: 이유를 알려주고 새 세션으로 다시 시도해요
 *  - READY: 대화 화면(B-4)은 다음 단계에서 만들어요. 지금은 안내만 보여줘요.
 */
export default function ChatScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const chat = useChat(id);
  const session = chat.data;

  // 조회가 한 번 실패해도 이미 받은 상태가 있으면 그대로 보여줘요(다음 확인 때 다시 이어져요)
  if (!session) {
    return (
      <Shell>
        {chat.isError ? (
          <View style={styles.center}>
            <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
              {errorMessage(chat.error)}
            </AppText>
            <View style={styles.buttons}>
              <Button variant="line" label="다시 불러오기" onPress={() => void chat.refetch()} />
            </View>
          </View>
        ) : (
          <View style={styles.center}>
            <AppText size={14} lineHeight={22} color={colors.muted}>
              불러오는 중이에요…
            </AppText>
          </View>
        )}
      </Shell>
    );
  }

  if (session.status === 'PREPARING') return <PreparingView session={session} />;
  if (session.status === 'FAILED') return <FailedView session={session} />;
  return <ReadyView session={session} />;
}

/** 화면 맨 위 뒤로 가기 줄 + 아래 내용 */
function Shell({ title, children }: { title?: string; children: ReactNode }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        {title ? (
          <AppText weight="bold" size={17}>
            {title}
          </AppText>
        ) : null}
        <View style={styles.navSide} />
      </View>
      {children}
    </View>
  );
}

function PreparingView({ session }: { session: ChatSession }) {
  const insets = useSafeAreaInsets();
  const current = useStepIndex(session.preparingStep);
  const labels = [
    `${windowText(session)} 기록 모으기`,
    '민감정보 가리기',
    '말투와 대화 방식 분석하기',
    '그때의 나 완성하기',
  ];

  return (
    <Shell>
      <ScrollView
        contentContainerStyle={[styles.preparing, { paddingBottom: Math.max(insets.bottom, 14) }]}
        showsVerticalScrollIndicator={false}>
        <Rings label={personaRingLabel(session)} />
        <AppText serif weight="bold" size={24} lineHeight={34} style={styles.preparingTitle}>
          {`${personaTitle(session)}를\n불러오는 중이에요`}
        </AppText>

        <View style={styles.steps}>
          {labels.map((label, index) => (
            <StepRow key={label} label={label} state={index < current ? 'done' : index === current ? 'now' : 'todo'} />
          ))}
        </View>

        <Card tint style={styles.preparingNote}>
          <AppText size={13} lineHeight={20} style={styles.centered}>
            {'처음 한 번만 오래 걸려요.\n같은 시점은 저장해 두었다가 바로 만나요.'}
          </AppText>
        </Card>
      </ScrollView>
    </Shell>
  );
}

/** 지금 진행 중인 단계(0~3). 서버가 알려주면 그대로, 모르면(null) 몇 초마다 한 칸씩 넘겨요. */
function useStepIndex(step: ChatSession['preparingStep']): number {
  const [startedAt] = useState(() => Date.now());
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (step) return;
    const timer = setInterval(() => setElapsed(Date.now() - startedAt), 1000);
    return () => clearInterval(timer);
  }, [step, startedAt]);
  return step ? STEPS.indexOf(step) : Math.min(STEPS.length - 1, Math.floor(elapsed / MIMIC_STEP_MS));
}

/** 스토리보드 .rings — 가운데 동그라미 둘레에 점선 고리 둘이 천천히 숨 쉬어요. */
function Rings({ label }: { label: string }) {
  const [pulse] = useState(() => new Animated.Value(0));
  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1, duration: 1600, useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 0, duration: 1600, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [pulse]);
  const scale = pulse.interpolate({ inputRange: [0, 1], outputRange: [1, 1.06] });

  return (
    <View style={styles.rings} accessible accessibilityLabel={label.replace('\n', ' ')}>
      <Animated.View style={[styles.ring, styles.ringOuter, { transform: [{ scale }] }]} />
      <Animated.View style={[styles.ring, styles.ringMiddle, { transform: [{ scale }] }]} />
      <View style={styles.ringCore} />
      <AppText serif weight="bold" size={19} lineHeight={25} style={styles.centered}>
        {label}
      </AppText>
    </View>
  );
}

function StepRow({ label, state }: { label: string; state: 'done' | 'now' | 'todo' }) {
  const stateText = state === 'done' ? '완료' : state === 'now' ? '진행 중' : '대기';
  return (
    <View
      accessible
      accessibilityLabel={`${label}, ${stateText}`}
      style={[styles.stepRow, state === 'todo' && styles.stepTodo]}>
      <View style={[styles.stepDot, state !== 'todo' && styles.stepDotActive, state === 'done' && styles.stepDotDone]}>
        {state === 'done' ? <Icon name="check" size={12} color={colors.ink} strokeWidth={3} /> : null}
        {state === 'now' ? <View style={styles.stepNow} /> : null}
      </View>
      <AppText size={14.5} lineHeight={22} color={state === 'todo' ? colors.brown : colors.ink}>
        {label}
      </AppText>
    </View>
  );
}

/** FAILED의 이유(명세 failureCode)를 풀어 쓴 문장 */
function failureMessage(code: string | null | undefined): string {
  switch (code) {
    case 'AI_UNAVAILABLE':
      return 'AI에 연결하지 못했어요. 잠시 뒤에 다시 시도해 주세요.';
    case 'AI_REFUSED':
      return 'AI의 안전 장치가 이 기록은 분석하지 못했어요. 다른 시점을 골라 보세요.';
    case 'AI_BAD_RESPONSE':
      return 'AI의 답을 읽지 못했어요. 다시 시도해 주세요.';
    case 'PREPARING_TIMEOUT':
      return '시간이 너무 오래 걸렸어요. 다시 시도해 주세요.';
    default:
      return '문제가 생겼어요. 잠시 뒤에 다시 시도해 주세요.';
  }
}

function FailedView({ session }: { session: ChatSession }) {
  const createChat = useCreateChat();

  // 명세: 실패한 세션은 두고 같은 시점으로 새 세션을 만들어 다시 시도해요
  async function retry() {
    try {
      const next = await createChat.mutateAsync({
        anchorDate: session.anchorDate,
        scope: session.scope,
        shortcutKey: session.shortcutKey ?? undefined,
        forCouple: false,
      });
      router.replace({ pathname: '/chat/[id]', params: { id: next.id } });
    } catch (error) {
      notify('다시 시도하지 못했어요', errorMessage(error));
    }
  }

  return (
    <Shell>
      <View style={styles.center}>
        <AppText serif weight="bold" size={24} lineHeight={34} style={styles.centered}>
          {'그때의 나를\n불러오지 못했어요'}
        </AppText>
        <AppText size={14} lineHeight={22} color={colors.brown} style={styles.failedMessage}>
          {failureMessage(session.failureCode)}
        </AppText>
        <View style={styles.buttons}>
          <Button label="다시 시도" loading={createChat.isPending} onPress={() => void retry()} />
          <Button variant="line" label="목록으로" onPress={() => router.replace('/past')} />
        </View>
      </View>
    </Shell>
  );
}

function ReadyView({ session }: { session: ChatSession }) {
  return (
    <Shell title={personaTitle(session)}>
      <View style={styles.center}>
        <AppText serif weight="bold" size={20} lineHeight={28} style={styles.centered}>
          {`${personaTitle(session)}가 준비됐어요`}
        </AppText>
        <AppText size={13.5} lineHeight={21} color={colors.brown} style={styles.centered}>
          대화 화면은 다음 단계에서 만들어요
        </AppText>
      </View>
    </Shell>
  );
}

const RING_SIZE = 170;

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
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    paddingBottom: 48,
  },
  centered: {
    textAlign: 'center',
  },
  buttons: {
    alignSelf: 'stretch',
    gap: 10,
    marginTop: 20,
  },
  failedMessage: {
    textAlign: 'center',
  },
  preparing: {
    flexGrow: 1,
    alignItems: 'center',
    paddingHorizontal: 22,
    paddingTop: 40,
  },
  preparingTitle: {
    marginTop: 26,
    textAlign: 'center',
  },
  steps: {
    alignSelf: 'stretch',
    marginTop: 26,
    gap: 12,
  },
  preparingNote: {
    alignSelf: 'stretch',
    marginTop: 'auto',
    paddingVertical: 14,
  },
  rings: {
    width: RING_SIZE,
    height: RING_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ring: {
    position: 'absolute',
    borderRadius: 999,
    borderWidth: 2,
    borderStyle: 'dashed',
    borderColor: colors.brown,
  },
  ringOuter: {
    top: -8,
    left: -8,
    width: RING_SIZE + 16,
    height: RING_SIZE + 16,
    opacity: 0.3,
  },
  ringMiddle: {
    top: 14,
    left: 14,
    width: RING_SIZE - 28,
    height: RING_SIZE - 28,
    opacity: 0.6,
  },
  ringCore: {
    position: 'absolute',
    top: 34,
    left: 34,
    width: RING_SIZE - 68,
    height: RING_SIZE - 68,
    borderRadius: 999,
    borderWidth: 2,
    borderColor: colors.gold,
    backgroundColor: colors.panel,
  },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  stepTodo: {
    opacity: 0.6,
  },
  stepDot: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.beige,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepDotActive: {
    borderColor: colors.gold,
  },
  stepDotDone: {
    backgroundColor: colors.gold,
  },
  stepNow: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.gold,
  },
});
