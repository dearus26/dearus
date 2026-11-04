import { useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';

import type { SafetyNotice } from '@/api/chats';
import { AppText } from '@/components/AppText';
import { BottomSheet } from '@/components/BottomSheet';
import { Button } from '@/components/Button';
import { Icon } from '@/components/Icon';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

type Props = {
  /** 위기 신호가 감지되면 서버가 보내 준 안내(상담전화 번호는 서버 설정이라 앱 배포 없이 바뀔 수 있어요). 없으면 시트가 닫혀 있어요. */
  notice: SafetyNotice | null;
  onClose: () => void;
};

/** 스토리보드 B-5 안전 안내 — 대화에서 힘든 마음이 감지되면 상담 기관을 알려주고, 전화를 걸거나 대화를 이어갈 수 있어요. */
export function SafetySheet({ notice, onClose }: Props) {
  // 닫히는 동안에도 내용이 사라지지 않게 마지막 안내를 들고 있어요
  const [shown, setShown] = useState(notice);
  if (notice && notice !== shown) setShown(notice);

  async function call(phone: string) {
    try {
      await Linking.openURL(`tel:${phone.replace(/[^0-9]/g, '')}`);
    } catch {
      notify('전화를 걸 수 없어요', `${phone}로 직접 걸어 주세요.`);
    }
  }

  const hotlines = shown?.hotlines ?? [];

  return (
    <BottomSheet visible={notice !== null} onClose={onClose}>
      <View style={styles.heart}>
        <Icon name="heart" size={28} color={colors.ink} />
      </View>
      <AppText serif weight="bold" size={23} lineHeight={31} style={styles.title}>
        많이 힘들어 보여요
      </AppText>
      <AppText size={14} lineHeight={23} color={colors.brown} style={styles.body}>
        {'마음이 무겁게 느껴진다면 혼자 견디지 않아도 괜찮아요.\n전문 상담 기관과 이야기해 보세요.'}
      </AppText>

      {hotlines.map((hotline) => (
        <Pressable
          key={hotline.phone}
          accessibilityRole="button"
          accessibilityLabel={`${hotline.name} ${hotline.phone}로 전화 걸기`}
          onPress={() => void call(hotline.phone)}
          style={({ pressed }) => [styles.hotline, pressed && styles.pressed]}>
          <Icon name="phone" size={18} color={colors.brown} />
          <View style={styles.hotlineText}>
            <AppText weight="bold" size={14.5} lineHeight={21}>
              {hotline.name}
            </AppText>
            <AppText size={12.5} lineHeight={19} color={colors.brown}>
              {hotline.hours}
            </AppText>
          </View>
          <AppText serif weight="bold" size={20} lineHeight={26}>
            {hotline.phone}
          </AppText>
        </Pressable>
      ))}

      <View style={styles.buttons}>
        {hotlines[0] ? <Button label="전화 연결하기" onPress={() => void call(hotlines[0].phone)} /> : null}
        <Button variant="line" label="대화 계속하기" onPress={onClose} />
      </View>
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  heart: {
    alignSelf: 'center',
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    textAlign: 'center',
    marginTop: 10,
  },
  body: {
    textAlign: 'center',
    marginTop: 8,
  },
  hotline: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 16,
    backgroundColor: colors.paper,
    borderWidth: 1,
    borderColor: colors.beige,
  },
  pressed: {
    opacity: 0.7,
  },
  hotlineText: {
    flex: 1,
  },
  buttons: {
    gap: 10,
    marginTop: 14,
  },
});
