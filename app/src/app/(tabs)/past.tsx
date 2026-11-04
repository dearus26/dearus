import { router } from 'expo-router';
import { useEffect, useRef } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon } from '@/components/Icon';
import { ChatRow } from '@/features/past/ChatRow';
import { useChats } from '@/features/past/hooks';
import { colors } from '@/theme/colors';

/**
 * 스토리보드 B-1 과거의 나 목록. 지금까지 만난 시점의 대화를 최근 순으로 보여주고, 누르면 다시 들어가요.
 * 오른쪽 위 +로 새 시점을 열어요. 대화가 한 번도 없는 첫 방문이면 목록 대신 바로 시점 고르기로 가요.
 */
export default function PastScreen() {
  const insets = useSafeAreaInsets();
  const chats = useChats();
  const items = chats.data;
  const autoOpened = useRef(false);

  // 한 번만 열어요. 시점 고르기에서 그냥 돌아오면 아래의 빈 목록 안내에 머물러요(다시 튕겨 나가지 않게요).
  useEffect(() => {
    if (items?.length === 0 && !autoOpened.current) {
      autoOpened.current = true;
      router.push('/persona/pick');
    }
  }, [items]);

  function pickNew() {
    router.push('/persona/pick');
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <AppText serif weight="bold" size={22} lineHeight={30}>
          과거의 나
        </AppText>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="새 시점 만나기"
          hitSlop={8}
          onPress={pickNew}
          style={({ pressed }) => [styles.add, pressed && styles.pressed]}>
          <Icon name="plus" size={18} color={colors.ink} />
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={chats.isRefetching} onRefresh={() => void chats.refetch()} />}>
        <AppText size={12.5} lineHeight={19} color={colors.brown}>
          다시 만나고 싶은 나를 골라보세요. 오른쪽 위 + 로 새 시점도 열 수 있어요
        </AppText>

        {items ? (
          items.length > 0 ? (
            <View style={styles.list}>
              {items.map((chat) => (
                <ChatRow
                  key={chat.id}
                  chat={chat}
                  onPress={() => router.push({ pathname: '/chat/[id]', params: { id: chat.id } })}
                />
              ))}
            </View>
          ) : (
            <View style={styles.empty}>
              <AppText serif weight="bold" size={18} lineHeight={26} style={styles.centered}>
                아직 만난 과거의 나가 없어요
              </AppText>
              <AppText size={13.5} lineHeight={21} color={colors.brown} style={styles.centered}>
                일기와 기록으로 그때의 나를 만나 볼 수 있어요
              </AppText>
              <View style={styles.emptyButton}>
                <Button label="시점 고르기" onPress={pickNew} />
              </View>
            </View>
          )
        ) : chats.isError ? (
          <View style={styles.empty}>
            <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
              {errorMessage(chats.error)}
            </AppText>
            <View style={styles.emptyButton}>
              <Button variant="line" label="다시 불러오기" onPress={() => void chats.refetch()} />
            </View>
          </View>
        ) : (
          <AppText size={14} lineHeight={22} color={colors.muted} style={styles.loading}>
            불러오는 중이에요…
          </AppText>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.cream,
  },
  header: {
    height: 50,
    paddingHorizontal: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  add: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.85,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 4,
    paddingBottom: 24,
  },
  list: {
    marginTop: 8,
  },
  loading: {
    marginTop: 24,
    textAlign: 'center',
  },
  empty: {
    marginTop: 56,
    alignItems: 'center',
    gap: 8,
  },
  centered: {
    textAlign: 'center',
  },
  emptyButton: {
    alignSelf: 'stretch',
    marginTop: 16,
  },
});
