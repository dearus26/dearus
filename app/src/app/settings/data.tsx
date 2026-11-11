import { router } from 'expo-router';
import { Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import type { MessengerUpload } from '@/api/uploads';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { Pill } from '@/components/Pill';
import { useDeleteUpload, useMessengerUploads } from '@/features/messenger/hooks';
import { dateStringOf, formatYearMonthDay } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';
import { colors, emotionColors } from '@/theme/colors';

const statusLabels = { PROCESSING: '분석 중', DONE: '분석 완료', FAILED: '분석 실패' } as const;

function dayOf(iso: string): string {
  return formatYearMonthDay(dateStringOf(new Date(iso)));
}

/**
 * 설정 → 내 데이터: 올린 메신저 대화를 보고 지워요(개인정보 처리방침의 "올린 메신저 대화 보기 · 지우기").
 * 지우면 그 업로드의 내 말 원문과 주간 요약이 함께 지워져요. 일기는 캘린더에서, 대화 · 리포트는 과거의 나 목록에서 봐요.
 */
export default function MyDataScreen() {
  const insets = useSafeAreaInsets();
  const uploads = useMessengerUploads();
  const deleteUpload = useDeleteUpload();
  const items = uploads.data;

  function goBack() {
    if (router.canGoBack()) router.back();
    else router.replace('/settings');
  }

  async function remove(upload: MessengerUpload) {
    const ok = await confirm({
      title: '이 대화를 지울까요?',
      message:
        '내 말 원문과 이 대화로 만든 주간 요약이 모두 지워져요. 이 기간의 "과거의 나"는 다음 대화 때 다시 만들어요. 되돌릴 수 없어요.',
      confirmLabel: '지우기',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteUpload.mutateAsync(upload.id);
    } catch (error) {
      notify('지우지 못했어요', errorMessage(error));
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={goBack}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          내 데이터
        </AppText>
        <View style={styles.navSpacer} />
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: Math.max(insets.bottom, 12) + 24 }]}
        refreshControl={<RefreshControl refreshing={uploads.isRefetching} onRefresh={() => void uploads.refetch()} />}>
        <AppText serif weight="bold" size={20} lineHeight={28}>
          올린 메신저 대화
        </AppText>
        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.sub}>
          올린 날부터 1년 뒤에 내 말 원문과 요약이 자동으로 지워져요
        </AppText>

        {items ? (
          items.length > 0 ? (
            <View style={styles.list}>
              {items.map((upload) => (
                <Card key={upload.id} style={styles.item}>
                  <View style={styles.itemHead}>
                    <AppText weight="bold" size={15} lineHeight={22} style={styles.grow}>
                      txt 파일
                    </AppText>
                    <AppText
                      weight="bold"
                      size={12.5}
                      lineHeight={19}
                      color={upload.status === 'FAILED' ? emotionColors.ANGRY : colors.brown}>
                      {statusLabels[upload.status]}
                    </AppText>
                  </View>
                  {upload.status === 'DONE' && upload.periodStart && upload.periodEnd ? (
                    <AppText size={13} lineHeight={20}>
                      {`${formatYearMonthDay(upload.periodStart)} ~ ${formatYearMonthDay(upload.periodEnd)} · ${upload.weekCount ?? 0}주 요약`}
                    </AppText>
                  ) : null}
                  <AppText size={12} lineHeight={18} color={colors.brown}>
                    {upload.status === 'DONE'
                      ? `${dayOf(upload.uploadedAt)}에 올렸고, ${dayOf(upload.expiresAt)}에 자동으로 지워져요`
                      : `${dayOf(upload.uploadedAt)}에 올렸어요`}
                  </AppText>
                  {upload.status === 'PROCESSING' ? null : (
                    <View style={styles.itemAction}>
                      <Pill
                        label="지우기"
                        tone="line"
                        loading={deleteUpload.isPending && deleteUpload.variables === upload.id}
                        onPress={() => void remove(upload)}
                      />
                    </View>
                  )}
                </Card>
              ))}
            </View>
          ) : (
            <View style={styles.empty}>
              <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
                올린 메신저 대화가 없어요
              </AppText>
              <View style={styles.emptyButton}>
                <Button variant="line" label="메신저 올리기" onPress={() => router.push('/messenger')} />
              </View>
            </View>
          )
        ) : uploads.isError ? (
          <View style={styles.empty}>
            <AppText size={14} lineHeight={22} color={colors.muted} style={styles.centered}>
              {errorMessage(uploads.error)}
            </AppText>
            <View style={styles.emptyButton}>
              <Button variant="line" label="다시 불러오기" onPress={() => void uploads.refetch()} />
            </View>
          </View>
        ) : (
          <AppText size={14} lineHeight={22} color={colors.muted} style={styles.loading}>
            불러오는 중이에요…
          </AppText>
        )}

        <Card tint style={styles.other}>
          <AppText weight="bold" size={14} lineHeight={21}>
            그 밖의 내 기록
          </AppText>
          <AppText size={13} lineHeight={20} color={colors.brown}>
            {'일기는 홈 캘린더에서 날짜를 눌러 보고 고치고 지울 수 있어요.\n대화와 리포트는 "과거의 나" 탭에서 볼 수 있어요(하나씩 지우는 기능은 준비 중이에요).'}
          </AppText>
        </Card>
      </ScrollView>
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
  navSpacer: {
    width: 22,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
  grow: {
    flex: 1,
  },
  sub: {
    marginTop: 2,
  },
  list: {
    marginTop: 14,
    gap: 10,
  },
  item: {
    paddingVertical: 14,
    paddingHorizontal: 16,
    gap: 2,
  },
  itemHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  itemAction: {
    alignSelf: 'flex-end',
    marginTop: 6,
  },
  empty: {
    marginTop: 40,
    alignItems: 'center',
    gap: 8,
  },
  centered: {
    textAlign: 'center',
  },
  emptyButton: {
    alignSelf: 'stretch',
    marginTop: 12,
  },
  loading: {
    marginTop: 24,
    textAlign: 'center',
  },
  other: {
    marginTop: 24,
    paddingVertical: 14,
    paddingHorizontal: 16,
    gap: 4,
  },
});
