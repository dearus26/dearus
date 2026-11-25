import type { ReactNode } from 'react';
import { RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

type Props = {
  children: ReactNode;
  /** 화면 아래에 붙는 버튼 줄(스크롤되지 않아요) */
  footer?: ReactNode;
  refreshing?: boolean;
  /** 있으면 아래로 당겨 새로고침할 수 있어요 */
  onRefresh?: () => void;
};

/** 커플 탭 화면들이 함께 쓰는 틀: 스크롤되는 내용 + (있으면) 맨 아래 고정 버튼 */
export function CouplePage({ children, footer, refreshing = false, onRefresh }: Props) {
  return (
    <View style={styles.page}>
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        refreshControl={onRefresh ? <RefreshControl refreshing={refreshing} onRefresh={onRefresh} /> : undefined}>
        {children}
      </ScrollView>
      {footer ? <View style={styles.footer}>{footer}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    paddingHorizontal: 22,
    paddingTop: 8,
    paddingBottom: 20,
  },
  footer: {
    paddingHorizontal: 22,
    paddingTop: 8,
    paddingBottom: 12,
  },
});
