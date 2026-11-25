import { useState } from 'react';
import { ActivityIndicator, Platform, Pressable, Share, StyleSheet, TextInput, View } from 'react-native';

import type { CoupleState } from '@/api/couples';
import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { useAcceptInvite, useCreateInvite } from '@/features/couple/hooks';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

type Tab = 'make' | 'enter';

const CODE_PATTERN = /^DU-[A-Z0-9]{4}-[A-Z0-9]{4}$/;
const MONO = Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' });

/**
 * 입력한 글자를 초대 코드 모양(DU-XXXX-XXXX)으로 맞춰요. 소문자는 대문자로, 공백 · 하이픈은 없애고 다시 붙여요.
 * 붙여넣기("DU-7K4Q-2M9X")도, 앞의 DU- 없이 치는 것도 같은 결과예요.
 */
export function formatInviteCode(text: string): string {
  const raw = text.toUpperCase().replace(/[^A-Z0-9]/g, '');
  const body = (raw.startsWith('DU') ? raw.slice(2) : raw).slice(0, 8);
  if (body.length === 0) return text.length > 0 ? 'DU-' : '';
  return body.length > 4 ? `DU-${body.slice(0, 4)}-${body.slice(4)}` : `DU-${body}`;
}

/** 만료까지 남은 시간을 "약 23시간 뒤"처럼 말해요(24시간 유효하니 시간 단위면 충분해요). */
function expiryText(expiresAt: string, now: number = Date.now()): string {
  const minutes = Math.round((new Date(expiresAt).getTime() - now) / 60_000);
  if (minutes <= 0) return '곧 만료돼요';
  if (minutes < 60) return `${minutes}분 뒤에 만료돼요`;
  return `약 ${Math.round(minutes / 60)}시간 동안 유효해요`;
}

/**
 * 스토리보드 C-1 초대 코드 연결(커플 탭의 첫 화면). 한쪽은 "초대 코드 만들기"로 코드를 만들어 보내고,
 * 다른 쪽은 "코드 입력하기"로 받은 코드를 넣어요. 연결만 되고, 서로 동의해야 대화할 수 있어요.
 * 기다리는 동안(PENDING) 상태는 useCouple이 4초마다 확인해서, 상대가 입력하면 화면이 저절로 바뀌어요.
 */
export function ConnectView({ state, onCancelInvite }: { state: CoupleState; onCancelInvite: () => void }) {
  const [tab, setTab] = useState<Tab>('make');

  return (
    <View>
      <View style={styles.orb}>
        <Icon name="heart" size={30} color={colors.ink} />
      </View>
      <AppText serif weight="bold" size={26} lineHeight={36} style={styles.title}>
        {'그때의 우리를\n함께 만나봐요'}
      </AppText>

      <View style={styles.seg} accessibilityRole="tablist">
        <SegButton label="초대 코드 만들기" on={tab === 'make'} onPress={() => setTab('make')} />
        <SegButton label="코드 입력하기" on={tab === 'enter'} onPress={() => setTab('enter')} />
      </View>

      {tab === 'make' ? <MakeCard state={state} onCancelInvite={onCancelInvite} /> : <EnterCard />}

      <Card tint style={styles.note}>
        <AppText size={13.5} lineHeight={22} color={colors.brown}>
          {tab === 'enter'
            ? '코드는 24시간 동안 유효해요. 연결만 되고, 서로 동의해야 대화할 수 있어요.'
            : state.status === 'PENDING'
              ? '상대가 코드를 입력하면 자동으로 연결돼요. 연결만 되고, 서로 동의해야 대화할 수 있어요.'
              : '버튼을 누르면 코드가 생성돼요. 코드는 24시간 동안 유효하고, 연결만 되고 서로 동의해야 대화할 수 있어요.'}
        </AppText>
      </Card>
    </View>
  );
}

function SegButton({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="tab"
      accessibilityLabel={label}
      accessibilityState={{ selected: on }}
      onPress={onPress}
      style={[styles.segButton, on && styles.segOn]}>
      <AppText size={14} lineHeight={20} weight={on ? 'bold' : 'regular'} color={on ? colors.ink : colors.brown}>
        {label}
      </AppText>
    </Pressable>
  );
}

/** "초대 코드 만들기" 탭: 코드 만들기 → 만든 코드 보여주기 · 보내기 → (만료되면) 새로 만들기 */
function MakeCard({ state, onCancelInvite }: { state: CoupleState; onCancelInvite: () => void }) {
  const createInvite = useCreateInvite();
  const invite = state.status === 'PENDING' ? state.invite : null;
  const expired = state.status === 'EXPIRED';

  async function create() {
    try {
      await createInvite.mutateAsync();
    } catch (error) {
      notify('코드를 만들지 못했어요', errorMessage(error));
    }
  }

  async function send() {
    if (!invite) return;
    try {
      await Share.share({
        message: `Dear Us에서 그때의 우리를 함께 만나요. 앱의 커플 탭 > 코드 입력하기에 이 초대 코드를 넣어 주세요. (24시간 동안 유효해요)\n\n${invite.code}`,
      });
    } catch {
      // 공유 창을 닫았거나 공유할 수 없는 환경이에요. 코드는 화면에 그대로 있어서 따로 알리지 않아요.
    }
  }

  if (invite) {
    return (
      <Card style={styles.card}>
        <AppText size={13} lineHeight={19} color={colors.brown} style={styles.centered}>
          상대에게 이 코드를 보내 주세요
        </AppText>
        <AppText
          selectable
          accessibilityLabel={`초대 코드 ${invite.code}`}
          style={[styles.code, styles.centered]}>
          {invite.code}
        </AppText>
        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.centered}>
          {expiryText(invite.expiresAt)}
        </AppText>
        <View style={styles.waiting}>
          <ActivityIndicator size="small" color={colors.gold} />
          <AppText size={13} lineHeight={19} color={colors.brown}>
            상대가 코드를 입력하길 기다리는 중이에요
          </AppText>
        </View>
        <View style={styles.cardButtons}>
          <Button label="코드 보내기" onPress={() => void send()} />
          <Button variant="line" label="코드 취소" onPress={onCancelInvite} />
        </View>
      </Card>
    );
  }

  return (
    <Card style={styles.card}>
      <AppText size={13} lineHeight={19} color={colors.brown} style={styles.centered}>
        {expired ? '코드가 만료됐어요' : '아직 만든 코드가 없어요'}
      </AppText>
      <AppText style={[styles.code, styles.centered, styles.codeEmpty]}>∙∙∙∙ ∙∙∙∙ ∙∙∙∙</AppText>
      <View style={styles.cardButtons}>
        <Button
          label={expired ? '새 코드 만들기' : '초대 코드 만들기'}
          loading={createInvite.isPending}
          onPress={() => void create()}
        />
      </View>
    </Card>
  );
}

/** "코드 입력하기" 탭: 받은 코드를 넣고 연결해요. iOS와 Android 어느 쪽에서도 같아요. */
function EnterCard() {
  const accept = useAcceptInvite();
  const [text, setText] = useState('');
  const valid = CODE_PATTERN.test(text);

  async function connect() {
    try {
      await accept.mutateAsync(text);
    } catch (error) {
      notify('연결하지 못했어요', errorMessage(error));
    }
  }

  return (
    <Card style={styles.card}>
      <AppText size={13} lineHeight={19} color={colors.brown} style={styles.centered}>
        받은 초대 코드 입력
      </AppText>
      <TextInput
        accessibilityLabel="초대 코드"
        value={text}
        onChangeText={(next) => setText(formatInviteCode(next))}
        placeholder="DU-XXXX-XXXX"
        placeholderTextColor={colors.beige}
        autoCapitalize="characters"
        autoCorrect={false}
        autoComplete="off"
        spellCheck={false}
        maxLength={12}
        returnKeyType="done"
        onSubmitEditing={() => (valid ? void connect() : undefined)}
        style={styles.input}
      />
      <View style={styles.cardButtons}>
        <Button label="연결하기" disabled={!valid} loading={accept.isPending} onPress={() => void connect()} />
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  orb: {
    alignSelf: 'center',
    width: 64,
    height: 64,
    borderRadius: 32,
    marginTop: 6,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    marginTop: 8,
    textAlign: 'center',
  },
  seg: {
    flexDirection: 'row',
    marginTop: 14,
    padding: 4,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.beige,
    backgroundColor: colors.panel,
  },
  segButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 9,
    borderRadius: 10,
  },
  segOn: {
    backgroundColor: colors.paper,
    shadowColor: colors.ink,
    shadowOpacity: 0.15,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  card: {
    marginTop: 14,
    alignItems: 'stretch',
  },
  centered: {
    textAlign: 'center',
  },
  code: {
    marginTop: 6,
    marginBottom: 2,
    fontFamily: MONO,
    fontSize: 26,
    lineHeight: 36,
    fontWeight: '700',
    letterSpacing: 1.5,
    color: colors.ink,
  },
  codeEmpty: {
    opacity: 0.3,
  },
  waiting: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  cardButtons: {
    marginTop: 12,
    gap: 10,
  },
  input: {
    marginTop: 8,
    paddingVertical: 8,
    textAlign: 'center',
    fontFamily: MONO,
    fontSize: 26,
    lineHeight: 36,
    fontWeight: '700',
    letterSpacing: 1.5,
    color: colors.ink,
    borderBottomWidth: 2,
    borderBottomColor: colors.beige,
  },
  note: {
    marginTop: 12,
  },
});
