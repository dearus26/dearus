import * as DocumentPicker from 'expo-document-picker';
import { router, useLocalSearchParams } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { CheckBox } from '@/components/CheckBox';
import { Icon } from '@/components/Icon';
import { parseKakaoTxt } from '@/features/messenger/kakaoTxt';
import { setParsedChat, setTargetDate } from '@/features/messenger/pending';
import { formatKoreanDate, isValidDateString, todayString } from '@/lib/date';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

/** 1년치 대화도 몇 MB라서 넉넉하게 잡은, 폰에서 읽을 txt의 최대 크기 */
const MAX_TXT_BYTES = 30 * 1024 * 1024;

/**
 * 스토리보드 5번 메신저 대화 올리기: 카카오톡 "대화 내보내기" txt를 올려요(이미지는 받지 않기로 했어요, 2026.11.11).
 * **올릴 날은 홈 캘린더에서 고른 날이에요**(일기 화면의 "메신저 올리기"가 `date`로 넘겨줘요, 2026.11.25).
 * 앱이 폰에서 txt를 읽어 그날에 나눈 대화 중 내 이름의 말만 추려요(다음 화면). 다른 날의 말과 상대 말은 폰에서 버려요.
 * 한 날만 올려서 서버가 한 주만 분석하니 빨라요. 날짜 없이 들어오면(설정 등) 오늘이에요.
 * 올릴 때마다 안내를 읽고 동의해야 해요(명세 consent).
 */
export default function MessengerUploadScreen() {
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ date?: string }>();
  const today = todayString();
  const date = isValidDateString(params.date) && params.date <= today ? params.date : today;
  const [agreed, setAgreed] = useState(false);
  const [reading, setReading] = useState(false);

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }

  async function pickTxt() {
    setReading(true);
    try {
      const result = await DocumentPicker.getDocumentAsync({ type: '*/*', copyToCacheDirectory: true, multiple: false });
      if (result.canceled) return;
      const asset = result.assets[0];
      if (asset.size !== undefined && asset.size > MAX_TXT_BYTES) {
        notify('파일이 너무 커요', '30MB가 넘는 파일은 열 수 없어요. 대화를 기간별로 나눠서 내보내 주세요.');
        return;
      }
      // 웹은 File 객체로, 폰은 파일 주소로 글자를 읽어요
      const text = asset.file ? await asset.file.text() : await (await fetch(asset.uri)).text();
      const parsed = parseKakaoTxt(text);
      if (parsed.messages.length === 0) {
        notify('대화를 읽지 못했어요', '카카오톡 대화방의 "대화 내보내기"로 만든 txt 파일이 맞는지 확인해 주세요.');
        return;
      }
      // 고른 날의 대화가 이 파일에 없으면 다음 화면으로 가지 않고 바로 알려요
      if (!parsed.messages.some((message) => message.date === date)) {
        notify(
          `${formatKoreanDate(date)}의 대화가 없어요`,
          '이 txt에는 그날 나눈 대화가 없어요. 그날이 들어 있는 파일을 고르거나, 홈 캘린더에서 다른 날을 골라 주세요.',
        );
        return;
      }
      setTargetDate(date);
      setParsedChat(parsed);
      router.push('/messenger/review');
    } catch {
      notify('파일을 열지 못했어요', '다른 파일로 다시 시도해 주세요.');
    } finally {
      setReading(false);
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          메신저 대화 올리기
        </AppText>
        <View style={styles.navSide} />
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Card style={styles.dateCard}>
          <AppText size={12.5} lineHeight={19} color={colors.brown}>
            올릴 날
          </AppText>
          <AppText serif weight="bold" size={20} lineHeight={28}>
            {formatKoreanDate(date)}
          </AppText>
          <AppText size={12.5} lineHeight={19} color={colors.brown}>
            이 날에 나눈 내 말만 올려요. 다른 날은 홈 캘린더에서 날짜를 골라 주세요
          </AppText>
        </Card>

        <Card style={styles.guide}>
          <AppText weight="bold" size={14.5} lineHeight={22}>
            이렇게 해주세요
          </AppText>
          <Step no="①">카카오톡 대화방 메뉴의 "대화 내보내기"에서 텍스트로 저장해요</Step>
          <Step no="②">아래에서 그 txt 파일을 골라요</Step>
          <Step no="③">내 이름을 고르고 그날의 내 말을 확인해요</Step>
        </Card>

        <Card tint style={styles.notice}>
          <AppText weight="bold" size={14.5} lineHeight={22}>
            분석 전에 확인해 주세요
          </AppText>
          <Bullet>고른 날의 내 말만 서버로 보내요. 다른 날과 상대 말은 폰에서 버려요</Bullet>
          <Bullet>내 말은 암호화해서 1년 보관하고, 요약도 암호화해서 저장해요</Bullet>
          <Bullet>글자를 가리지 않고 AI로 보내요. 학습에는 쓰이지 않아요</Bullet>
          <Pressable
            accessibilityRole="checkbox"
            accessibilityState={{ checked: agreed }}
            accessibilityLabel="위 내용을 이해했고 대화 분석에 동의해요"
            onPress={() => setAgreed((value) => !value)}
            style={styles.agree}>
            <CheckBox checked={agreed} />
            <AppText size={13.5} lineHeight={20} style={styles.agreeText}>
              위 내용을 이해했고 대화 분석에 동의해요 (필수)
            </AppText>
          </Pressable>
        </Card>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        <Button label="txt 파일 고르기" disabled={!agreed || reading} loading={reading} onPress={() => void pickTxt()} />
        {!agreed ? (
          <AppText size={12} lineHeight={18} color={colors.brown} style={styles.agreeHint}>
            위 내용에 동의하면 올릴 수 있어요
          </AppText>
        ) : null}
      </View>
    </View>
  );
}

function Step({ no, children }: { no: string; children: ReactNode }) {
  return (
    <View style={styles.row}>
      <AppText size={14} lineHeight={22} color={colors.gold} weight="bold">
        {no}
      </AppText>
      <AppText size={14} lineHeight={22} style={styles.rowText}>
        {children}
      </AppText>
    </View>
  );
}

function Bullet({ children }: { children: ReactNode }) {
  return (
    <View style={styles.row}>
      <AppText size={13.5} lineHeight={21} color={colors.gold} weight="bold">
        ✓
      </AppText>
      <AppText size={13.5} lineHeight={21} style={styles.rowText}>
        {children}
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
  content: {
    paddingHorizontal: 22,
    paddingTop: 6,
    paddingBottom: 16,
  },
  dateCard: {
    paddingVertical: 14,
    paddingHorizontal: 16,
    gap: 2,
  },
  guide: {
    marginTop: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    gap: 4,
  },
  notice: {
    marginTop: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    gap: 4,
  },
  row: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'flex-start',
  },
  rowText: {
    flex: 1,
  },
  agree: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 8,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  agreeText: {
    flex: 1,
  },
  footer: {
    paddingHorizontal: 22,
    paddingTop: 8,
    gap: 6,
  },
  agreeHint: {
    textAlign: 'center',
  },
});
