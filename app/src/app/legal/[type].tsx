import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { isLegalDocType, LEGAL_NOTICE, legalDocuments } from '@/legal/documents';
import { colors } from '@/theme/colors';

/** 약관 · 동의 화면의 `>`에서 열리는 세부 내용 (이용약관 · 개인정보 · AI 분석 · 알림) */
export default function LegalDocumentScreen() {
  const insets = useSafeAreaInsets();
  const { type } = useLocalSearchParams<{ type: string }>();
  const document = isLegalDocType(type) ? legalDocuments[type] : null;

  function goBack() {
    // 링크로 바로 열린 경우처럼 돌아갈 곳이 없으면 처음 화면으로 가요
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={goBack}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17} numberOfLines={1} style={styles.title}>
          {document?.title ?? '약관'}
        </AppText>
        <View style={styles.navSpacer} />
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: Math.max(insets.bottom, 12) + 24 }]}
        showsVerticalScrollIndicator={false}>
        {document ? (
          <>
            <Card tint>
              <AppText size={12.5} lineHeight={19} color={colors.muted}>
                {LEGAL_NOTICE}
              </AppText>
            </Card>

            {document.sections.map((section) => (
              <View key={section.heading} style={styles.section}>
                <AppText serif weight="bold" size={17} lineHeight={24}>
                  {section.heading}
                </AppText>
                {section.paragraphs?.map((text) => (
                  <AppText key={text} size={14} lineHeight={22} style={styles.paragraph}>
                    {text}
                  </AppText>
                ))}
                {section.bullets?.map((text) => (
                  <View key={text} style={styles.bulletRow}>
                    <AppText size={14} lineHeight={22} color={colors.gold}>
                      ·
                    </AppText>
                    <AppText size={14} lineHeight={22} style={styles.bulletText}>
                      {text}
                    </AppText>
                  </View>
                ))}
              </View>
            ))}
          </>
        ) : (
          <AppText size={14} lineHeight={22} color={colors.muted}>
            찾을 수 없는 문서예요.
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
  navbar: {
    height: 44,
    paddingHorizontal: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 12,
  },
  navSpacer: {
    width: 22,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 16,
  },
  section: {
    marginTop: 24,
  },
  paragraph: {
    marginTop: 8,
  },
  bulletRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 8,
    paddingRight: 8,
  },
  bulletText: {
    flex: 1,
  },
});
