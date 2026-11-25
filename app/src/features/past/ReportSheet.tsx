import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';

import type { MessageReportReason } from '@/api/chats';
import { AppText } from '@/components/AppText';
import { BottomSheet } from '@/components/BottomSheet';
import { Button } from '@/components/Button';
import { useReportMessage } from '@/features/past/hooks';
import { notify } from '@/lib/dialog';
import { notifyApiError } from '@/lib/apiErrors';
import { colors } from '@/theme/colors';
import { fontFamily } from '@/theme/fonts';

/** 적는 내용의 최대 글자 수(명세 MessageReportRequest.detail) */
const MAX_DETAIL = 500;
/** 신고할 답변을 한 줄로 되짚어 줄 때 보여주는 글자 수 */
const QUOTE_CHARS = 60;

/** 신고 사유(명세 2026.11.18 확정). 화면에는 이 순서대로 보여줘요. */
const REASONS: { value: MessageReportReason; label: string }[] = [
  { value: 'HURTFUL', label: '상처 주는 말' },
  { value: 'INACCURATE', label: '내 기록과 다른 내용' },
  { value: 'HARMFUL', label: '위험하거나 해로운 내용' },
  { value: 'PRIVACY', label: '개인정보가 드러남' },
  { value: 'OTHER', label: '기타' },
];

/** 신고할 답변. 화면에 있는 말풍선에서 골라요. */
export type ReportTarget = { chatId: string; seq: number; text: string };

type Props = {
  /** 신고할 답변. 없으면 시트가 닫혀 있어요. */
  target: ReportTarget | null;
  onClose: () => void;
};

/**
 * 스토리보드 B-7 답변 신고. 말풍선을 길게 누르거나 "신고"를 누르면 열려요. 사유 하나를 고르고(기타면 내용이 필요해요)
 * "신고하기"를 누르면 서버로 보내요(POST /chats/{id}/messages/{seq}/report). 관리자가 확인하고 AI가 바르게 답하도록 고쳐요.
 */
export function ReportSheet({ target, onClose }: Props) {
  const report = useReportMessage();
  const [reason, setReason] = useState<MessageReportReason | null>(null);
  const [detail, setDetail] = useState('');
  // 닫히는 동안에도 내용이 사라지지 않게 마지막 답변을 들고 있어요
  const [shown, setShown] = useState(target);
  if (target && target !== shown) setShown(target);

  // 새로 열 때마다 처음 상태로 시작해요
  useEffect(() => {
    if (target) {
      setReason(null);
      setDetail('');
    }
  }, [target]);

  const other = reason === 'OTHER';
  const canSubmit = reason !== null && (!other || detail.trim().length > 0);

  async function submit() {
    if (!shown || reason === null) return;
    try {
      await report.mutateAsync({
        chatId: shown.chatId,
        seq: shown.seq,
        input: { reason, detail: detail.trim().length > 0 ? detail.trim() : undefined },
      });
      onClose();
      notify('신고가 접수됐어요', '관리자가 확인하고 AI가 바르게 답하도록 고쳐요. 신고한 분께 따로 답을 드리지는 않아요.');
    } catch (error) {
      notifyApiError('신고하지 못했어요', error);
    }
  }

  const quote = shown ? (shown.text.length > QUOTE_CHARS ? `${shown.text.slice(0, QUOTE_CHARS)}…` : shown.text) : '';

  return (
    <BottomSheet visible={target !== null} onClose={() => (report.isPending ? undefined : onClose())}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <AppText serif weight="bold" size={22} lineHeight={30} style={styles.title}>
          이 답변을 신고할까요?
        </AppText>
        <View style={styles.quote}>
          <AppText size={13} lineHeight={20} color={colors.brown} numberOfLines={2}>
            {`“${quote}”`}
          </AppText>
        </View>

        <View style={styles.reasons} accessibilityRole="radiogroup">
          {REASONS.map((item) => {
            const selected = reason === item.value;
            return (
              <Pressable
                key={item.value}
                accessibilityRole="radio"
                accessibilityLabel={item.label}
                accessibilityState={{ checked: selected }}
                onPress={() => setReason(item.value)}
                style={[styles.reason, selected && styles.reasonOn]}>
                <View style={[styles.radio, selected && styles.radioOn]}>
                  {selected ? <View style={styles.radioDot} /> : null}
                </View>
                <AppText size={14.5} lineHeight={22} weight={selected ? 'bold' : 'regular'} style={styles.reasonText}>
                  {item.label}
                </AppText>
              </Pressable>
            );
          })}
        </View>

        <TextInput
          accessibilityLabel={other ? '신고 내용(필수)' : '신고 내용(선택)'}
          value={detail}
          onChangeText={setDetail}
          placeholder={other ? '어떤 점이 문제였는지 적어 주세요(필수)' : '더 알려 주실 내용이 있다면 적어 주세요(선택)'}
          placeholderTextColor={colors.muted}
          multiline
          maxLength={MAX_DETAIL}
          textAlignVertical="top"
          style={styles.input}
        />
        <AppText size={11.5} lineHeight={17} color={colors.muted} style={styles.count}>
          {`${detail.length} / ${MAX_DETAIL}`}
        </AppText>

        <View style={styles.buttons}>
          <Button label="신고하기" disabled={!canSubmit} loading={report.isPending} onPress={() => void submit()} />
          <Button variant="line" label="취소" disabled={report.isPending} onPress={onClose} />
        </View>
      </KeyboardAvoidingView>
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  title: {
    textAlign: 'center',
    marginTop: 4,
  },
  quote: {
    marginTop: 10,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 12,
    backgroundColor: colors.panel,
  },
  reasons: {
    marginTop: 12,
    gap: 8,
  },
  reason: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 11,
    paddingHorizontal: 14,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: colors.beige,
    backgroundColor: colors.paper,
  },
  reasonOn: {
    borderColor: colors.gold,
    backgroundColor: colors.panel,
  },
  reasonText: {
    flex: 1,
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: colors.beige,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOn: {
    borderColor: colors.gold,
  },
  radioDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.gold,
  },
  input: {
    marginTop: 10,
    minHeight: 72,
    maxHeight: 120,
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: colors.beige,
    backgroundColor: colors.paper,
    fontFamily: fontFamily.sans.regular,
    fontSize: 14.5,
    lineHeight: 22,
    color: colors.ink,
  },
  count: {
    alignSelf: 'flex-end',
    marginTop: 2,
  },
  buttons: {
    marginTop: 10,
    gap: 10,
  },
});
