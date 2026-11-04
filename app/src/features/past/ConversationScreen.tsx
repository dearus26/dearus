import { router } from 'expo-router';
import { FlatList, KeyboardAvoidingView, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { ChatMessage, ChatSession } from '@/api/chats';
import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { Pill } from '@/components/Pill';
import { ChatBubble } from '@/features/past/ChatBubble';
import { ChatInputBar } from '@/features/past/ChatInputBar';
import { useEndChat, useTodayUsage } from '@/features/past/hooks';
import { accuracyLabel, personaDateTitle, personaPeriod, personaTitle } from '@/features/past/labels';
import { SafetySheet } from '@/features/past/SafetySheet';
import { useConversation, type Draft } from '@/features/past/useConversation';
import { confirm, notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

type Row = { key: string; role: ChatMessage['role']; text: string | undefined };

/** 화면에 그릴 줄들. 목록은 아래부터 쌓이는 거꾸로 목록(inverted)이라 최신 것이 맨 앞이에요. */
function buildRows(messages: ChatMessage[], draft: Draft | null): Row[] {
  const rows: Row[] = messages.map((message) => ({
    key: `message-${message.seq}`,
    role: message.role,
    text: message.content,
  }));
  if (draft) {
    rows.push({ key: 'draft-user', role: 'USER', text: draft.user });
    // 답변이 한 글자도 안 왔으면 text가 없어서 점 세 개(입력 중)로 보여요
    rows.push({ key: 'draft-reply', role: 'PERSONA', text: draft.reply === '' ? undefined : draft.reply });
  }
  return rows.reverse();
}

function leave() {
  if (router.canGoBack()) router.back();
  else router.replace('/past');
}

/**
 * 스토리보드 B-4 Time-Slip Chat. 위에는 시점 · 정확도 · 오늘 대화 시간, 가운데는 대화, 아래는 말 거는 칸이에요.
 * 오른쪽 위 "종료"로 대화를 끝내면 회고 리포트(B-6)로 가요. 끝난 대화는 읽기만 되고 "리포트 보기"가 나와요.
 */
export function ConversationScreen({ session }: { session: ChatSession }) {
  const insets = useSafeAreaInsets();
  const conversation = useConversation(session.id);
  const usage = useTodayUsage();
  const endChat = useEndChat();
  const ended = session.endedAt != null;
  const rows = buildRows(conversation.messages, conversation.draft);
  const lowRecords = session.level === 'NONE' || session.level === 'LOW';

  function openReport() {
    router.push({ pathname: '/report/[id]', params: { id: session.id } });
  }

  async function end() {
    if (conversation.draft) {
      notify('답변이 오는 중이에요', '답변이 끝난 뒤에 대화를 끝낼 수 있어요.');
      return;
    }
    const myMessages = conversation.messages.filter((message) => message.role === 'USER').length;
    const ok = await confirm({
      title: '대화를 끝낼까요?',
      message:
        myMessages < 2
          ? '이야기가 짧아서 리포트는 만들어지지 않아요. 끝내면 이어서 대화할 수 없어요.'
          : '끝내면 이어서 대화할 수 없어요. 이 대화로 회고 리포트를 만들어 드릴게요.',
      confirmLabel: '끝내기',
    });
    if (!ok) return;
    try {
      const result = await endChat.mutateAsync(session.id);
      if (result.reportStatus === 'NONE') {
        notify('대화를 끝냈어요', '이야기가 짧아서 리포트는 만들지 않았어요.');
        leave();
        return;
      }
      // 뒤로 가면 끝난 대화가 아니라 목록으로 가게 replace로 열어요
      router.replace({ pathname: '/report/[id]', params: { id: session.id } });
    } catch (error) {
      notify('대화를 끝내지 못했어요', errorMessage(error));
    }
  }

  return (
    <KeyboardAvoidingView style={styles.screen} behavior="padding">
      <View style={[styles.top, { paddingTop: insets.top }]}>
        <View style={styles.navbar}>
          <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave} style={styles.navSide}>
            <Icon name="chevronLeft" size={22} color={colors.brown} />
          </Pressable>
          <View style={styles.titleBox}>
            <AppText weight="bold" size={17} lineHeight={22} numberOfLines={1}>
              {personaTitle(session)}
            </AppText>
            <AppText size={12} lineHeight={17} color={colors.brown} numberOfLines={1}>
              {`${personaPeriod(session)} · 정확도 ${accuracyLabel(session.level)}`}
            </AppText>
          </View>
          <View style={[styles.navSide, styles.navEnd]}>
            {ended ? null : (
              <Pill label="종료" tone="line" onPress={() => void end()} loading={endChat.isPending} />
            )}
          </View>
        </View>
        {usage.data ? (
          <View style={styles.usage}>
            <AppText size={11.5} lineHeight={17} color={colors.brown}>
              {`오늘 대화 ${usage.data.chatMinutes}분 · 하루 ${usage.data.recommendedMinutes}분 정도를 권장해요`}
            </AppText>
          </View>
        ) : null}
      </View>

      {conversation.loading ? (
        <View style={styles.center}>
          <AppText size={14} lineHeight={22} color={colors.muted}>
            대화를 불러오는 중이에요…
          </AppText>
        </View>
      ) : conversation.failed ? (
        <View style={styles.center}>
          <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
            {errorMessage(conversation.error)}
          </AppText>
          <View style={styles.retry}>
            <Button variant="line" label="다시 불러오기" onPress={conversation.refetch} />
          </View>
        </View>
      ) : (
        <FlatList
          inverted
          data={rows}
          keyExtractor={(row) => row.key}
          renderItem={({ item }) => <ChatBubble role={item.role} text={item.text} />}
          ItemSeparatorComponent={Separator}
          // 거꾸로 목록의 Footer는 화면 맨 위(대화의 시작)에 보여요
          ListFooterComponent={
            <View style={styles.listHead}>
              <View style={styles.sep}>
                <AppText size={11.5} lineHeight={17} color={colors.brown}>
                  {personaDateTitle(session)}
                </AppText>
              </View>
              {lowRecords ? (
                <Card tint style={styles.lowNote}>
                  <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.centered}>
                    기록이 부족해 정확도가 낮을 수 있어요
                  </AppText>
                </Card>
              ) : null}
            </View>
          }
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          style={styles.listBox}
        />
      )}

      {conversation.draft?.recovering ? (
        <View style={styles.notice}>
          <AppText size={12} lineHeight={18} color={colors.brown}>
            연결이 끊겼어요. 답변이 도착했는지 확인하고 있어요…
          </AppText>
        </View>
      ) : null}

      {ended ? (
        <View style={[styles.endedBar, { paddingBottom: Math.max(insets.bottom, 12) }]}>
          <View style={styles.endedText}>
            <AppText weight="bold" size={14} lineHeight={21}>
              끝난 대화예요
            </AppText>
            <AppText size={12} lineHeight={18} color={colors.brown}>
              이어서 대화할 수 없고, 리포트를 볼 수 있어요
            </AppText>
          </View>
          <Pill label="리포트 보기" onPress={openReport} />
        </View>
      ) : (
        <ChatInputBar
          onSend={conversation.send}
          disabled={conversation.loading || conversation.failed || conversation.draft !== null}
        />
      )}

      <SafetySheet notice={conversation.safety} onClose={conversation.dismissSafety} />
    </KeyboardAvoidingView>
  );
}

function Separator() {
  return <View style={styles.separator} />;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.panel,
  },
  top: {
    paddingHorizontal: 18,
    paddingBottom: 8,
    backgroundColor: colors.paper,
    borderBottomWidth: 1,
    borderBottomColor: colors.beige,
  },
  navbar: {
    height: 46,
    flexDirection: 'row',
    alignItems: 'center',
  },
  // 가운데 제목이 정확히 가운데 오도록 양쪽 칸을 같은 폭으로 둬요
  navSide: {
    width: 64,
  },
  navEnd: {
    alignItems: 'flex-end',
  },
  titleBox: {
    flex: 1,
    alignItems: 'center',
  },
  usage: {
    alignSelf: 'stretch',
    alignItems: 'center',
    marginTop: 2,
    paddingVertical: 3,
    paddingHorizontal: 8,
    borderRadius: 10,
    backgroundColor: colors.panel,
  },
  listBox: {
    flex: 1,
  },
  list: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  separator: {
    height: 10,
  },
  listHead: {
    alignItems: 'center',
    gap: 10,
    paddingBottom: 12,
  },
  sep: {
    paddingVertical: 3,
    paddingHorizontal: 12,
    borderRadius: 999,
    backgroundColor: 'rgba(205, 182, 156, 0.4)',
  },
  lowNote: {
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 22,
    gap: 12,
  },
  centered: {
    textAlign: 'center',
  },
  retry: {
    alignSelf: 'stretch',
  },
  notice: {
    alignItems: 'center',
    paddingVertical: 6,
  },
  endedBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingTop: 12,
    paddingHorizontal: 18,
    backgroundColor: colors.paper,
    borderTopWidth: 1,
    borderTopColor: colors.beige,
  },
  endedText: {
    flex: 1,
  },
});
