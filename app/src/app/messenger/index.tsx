import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { router } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import type { PickedImage } from '@/api/uploads';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { CheckBox } from '@/components/CheckBox';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Icon } from '@/components/Icon';
import { Pill } from '@/components/Pill';
import { useStartUpload } from '@/features/messenger/hooks';
import { parseKakaoTxt } from '@/features/messenger/kakaoTxt';
import { setParsedChat, setPendingUpload } from '@/features/messenger/pending';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';

type Format = 'IMAGE' | 'TXT';

/** 서버 한도(명세 POST /uploads/messenger/images): 한 번에 100장, 한 장 5MB, 전체 50MB, JPEG · PNG · WebP */
const MAX_IMAGES = 100;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_TOTAL_BYTES = 50 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
/** 1년치 대화도 몇 MB라서 넉넉하게 잡은, 폰에서 읽을 txt의 최대 크기 */
const MAX_TXT_BYTES = 30 * 1024 * 1024;

const GRID_GAP = 8;
const GRID_COLUMNS = 3;

/** 파일 이름 · 주소 끝의 확장자로 사진 종류를 짐작해요(폰이 종류를 안 알려줄 때만 써요) */
function guessMimeType(uri: string): string | null {
  const extension = /\.([a-z0-9]+)(?:\?|$)/i.exec(uri)?.[1]?.toLowerCase();
  if (extension === 'png') return 'image/png';
  if (extension === 'jpg' || extension === 'jpeg') return 'image/jpeg';
  if (extension === 'webp') return 'image/webp';
  return null;
}

/**
 * 스토리보드 5번 메신저 대화 올리기(2026.11.8에 바뀐 방식): 카카오톡 대화를 스크린샷이나 내보내기 txt로 올려요.
 *  - 스크린샷: 사진을 고르면 가공 없이 서버로 보내고, 서버가 AI로 내 말을 읽은 뒤 사진을 지워요.
 *  - txt: 앱이 폰에서 읽어 내 이름의 말만 추려요(다음 화면). 상대 말은 폰에서 버려요.
 * 올릴 때마다 안내를 읽고 동의해야 해요(명세 consent).
 */
export default function MessengerUploadScreen() {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const startUpload = useStartUpload();
  const [format, setFormat] = useState<Format>('IMAGE');
  const [images, setImages] = useState<PickedImage[]>([]);
  const [agreed, setAgreed] = useState(false);
  const [reading, setReading] = useState(false);

  const thumb = Math.floor((width - 44 - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS);
  const busy = reading || startUpload.isPending;

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }

  async function pickImages() {
    const room = MAX_IMAGES - images.length;
    if (room <= 0) {
      notify('더 고를 수 없어요', `한 번에 ${MAX_IMAGES}장까지 올릴 수 있어요.`);
      return;
    }
    // 오래된 캡처부터 순서대로 골라야 해서, 고른 순서를 지켜 달라고 해요(지원하는 폰에서)
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      selectionLimit: room,
      orderedSelection: true,
    });
    if (result.canceled) return;

    const next = [...images];
    let total = next.reduce((sum, image) => sum + (image.size ?? 0), 0);
    let wrongType = 0;
    let tooBig = 0;
    let overTotal = 0;
    for (const [index, asset] of result.assets.entries()) {
      if (next.some((image) => image.uri === asset.uri)) continue;
      const mimeType = asset.mimeType ?? guessMimeType(asset.fileName ?? asset.uri);
      if (!mimeType || !ALLOWED_TYPES.includes(mimeType)) {
        wrongType += 1;
        continue;
      }
      if (asset.fileSize !== undefined && asset.fileSize > MAX_IMAGE_BYTES) {
        tooBig += 1;
        continue;
      }
      if (total + (asset.fileSize ?? 0) > MAX_TOTAL_BYTES) {
        overTotal += 1;
        continue;
      }
      total += asset.fileSize ?? 0;
      const extension = mimeType === 'image/png' ? 'png' : mimeType === 'image/webp' ? 'webp' : 'jpg';
      next.push({
        uri: asset.uri,
        name: asset.fileName ?? `capture-${Date.now()}-${index}.${extension}`,
        mimeType,
        size: asset.fileSize,
      });
    }
    setImages(next);

    const problems = [
      wrongType > 0 ? `JPEG · PNG · WebP가 아닌 사진 ${wrongType}장` : null,
      tooBig > 0 ? `5MB가 넘는 사진 ${tooBig}장` : null,
      overTotal > 0 ? `전체 용량(50MB)을 넘는 사진 ${overTotal}장` : null,
    ].filter((line): line is string => line !== null);
    if (problems.length > 0) {
      notify('일부 사진은 빼고 담았어요', `${problems.join(', ')}은 올릴 수 없어요.${overTotal > 0 ? ' 나눠서 올려 주세요.' : ''}`);
    }
  }

  async function uploadImages() {
    const pending = { kind: 'IMAGE', images } as const;
    try {
      const upload = await startUpload.mutateAsync(pending);
      setPendingUpload(pending);
      // 뒤로 가면 올리기 화면이 아니라 일기 화면으로 돌아오게 replace로 열어요
      router.replace({ pathname: '/messenger/[id]', params: { id: upload.id } });
    } catch (error) {
      notify('올리지 못했어요', errorMessage(error));
    }
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
      setParsedChat(parsed);
      router.push('/messenger/review');
    } catch {
      notify('파일을 열지 못했어요', '다른 파일로 다시 시도해 주세요.');
    } finally {
      setReading(false);
    }
  }

  const imageMode = format === 'IMAGE';
  const canSubmit = agreed && !busy && (imageMode ? images.length > 0 : true);

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
        <View style={styles.formats} accessibilityRole="radiogroup">
          <ChoiceChip label="스크린샷 올리기" selected={imageMode} onPress={() => setFormat('IMAGE')} />
          <ChoiceChip label="txt 파일 올리기" selected={!imageMode} onPress={() => setFormat('TXT')} />
        </View>

        <Card style={styles.guide}>
          <AppText weight="bold" size={14.5} lineHeight={22}>
            이렇게 해주세요
          </AppText>
          {imageMode ? (
            <>
              <Step no="①">카카오톡 대화를 캡처해요. 날짜 줄(예: 2026년 9월 28일 일요일)이 보이게 해 주세요</Step>
              <Step no="②">아래에서 캡처를 골라요. 오래된 것부터 순서대로 골라 주세요(최대 100장)</Step>
              <Step no="③">올리면 AI가 내 말(오른쪽 노란 말풍선)만 읽어요</Step>
            </>
          ) : (
            <>
              <Step no="①">카카오톡 대화방 메뉴의 "대화 내보내기"에서 텍스트로 저장해요</Step>
              <Step no="②">아래에서 그 txt 파일을 골라요</Step>
              <Step no="③">내 이름을 고르고 내 말을 확인해요</Step>
            </>
          )}
        </Card>

        <Card tint style={styles.notice}>
          <AppText weight="bold" size={14.5} lineHeight={22}>
            분석 전에 확인해 주세요
          </AppText>
          {imageMode ? (
            <>
              <Bullet>사진은 가공하지 않고 서버로 보내요. AI가 내 말만 읽고, 사진은 읽은 직후 지워요</Bullet>
              <Bullet>사진에 찍힌 상대 말과 이름도 서버와 AI로 가요. 저장하지는 않아요</Bullet>
              <Bullet>읽은 내 말은 암호화해서 1년 보관하고, 요약도 암호화해서 저장해요</Bullet>
              <Bullet>글자를 가리지 않아요. 학습에는 쓰이지 않아요</Bullet>
            </>
          ) : (
            <>
              <Bullet>내 이름의 말만 서버로 보내요. 상대 말은 폰에서 버려요</Bullet>
              <Bullet>내 말은 암호화해서 1년 보관하고, 요약도 암호화해서 저장해요</Bullet>
              <Bullet>글자를 가리지 않고 AI로 보내요. 학습에는 쓰이지 않아요</Bullet>
            </>
          )}
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

        {imageMode ? (
          <View style={styles.pickArea}>
            <View style={styles.pickHead}>
              <AppText weight="bold" size={14} lineHeight={21}>
                {images.length > 0 ? `선택한 사진 ${images.length}장` : '선택한 사진이 없어요'}
              </AppText>
              {images.length > 1 ? (
                <Pill label="순서 뒤집기" tone="line" onPress={() => setImages((current) => [...current].reverse())} />
              ) : null}
            </View>
            {images.length > 0 ? (
              <>
                <AppText size={12} lineHeight={18} color={colors.brown} style={styles.orderHint}>
                  번호 순서대로(1번이 가장 오래된 대화) 올라가요. 거꾸로 골랐다면 "순서 뒤집기"를 눌러 주세요
                </AppText>
                <View style={styles.grid}>
                  {images.map((image, index) => (
                    <View key={image.uri} style={{ width: thumb, height: thumb }}>
                      <Image source={{ uri: image.uri }} style={styles.thumb} accessibilityLabel={`${index + 1}번째 캡처`} />
                      <View style={styles.badge}>
                        <AppText weight="bold" size={11} lineHeight={16} color={colors.white}>
                          {index + 1}
                        </AppText>
                      </View>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel={`${index + 1}번째 사진 빼기`}
                        hitSlop={6}
                        onPress={() => setImages((current) => current.filter((_, i) => i !== index))}
                        style={styles.remove}>
                        <AppText weight="bold" size={12} lineHeight={16} color={colors.white}>
                          ✕
                        </AppText>
                      </Pressable>
                    </View>
                  ))}
                  {images.length < MAX_IMAGES ? (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel="사진 더 고르기"
                      onPress={() => void pickImages()}
                      style={[styles.addTile, { width: thumb, height: thumb }]}>
                      <Icon name="plus" size={24} color={colors.brown} />
                    </Pressable>
                  ) : null}
                </View>
              </>
            ) : (
              <Button variant="line" label="사진 고르기" onPress={() => void pickImages()} />
            )}
          </View>
        ) : null}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        {imageMode ? (
          <Button
            label={images.length > 0 ? `올리기 (${images.length}장)` : '사진을 골라 주세요'}
            disabled={!canSubmit}
            loading={startUpload.isPending}
            onPress={() => void uploadImages()}
          />
        ) : (
          <Button label="txt 파일 고르기" disabled={!canSubmit} loading={reading} onPress={() => void pickTxt()} />
        )}
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
  formats: {
    flexDirection: 'row',
    gap: 8,
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
  pickArea: {
    marginTop: 14,
    gap: 8,
  },
  pickHead: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  orderHint: {
    marginTop: -2,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: GRID_GAP,
  },
  thumb: {
    width: '100%',
    height: '100%',
    borderRadius: 12,
    backgroundColor: colors.panel,
  },
  badge: {
    position: 'absolute',
    left: 6,
    top: 6,
    minWidth: 20,
    height: 20,
    paddingHorizontal: 5,
    borderRadius: 10,
    backgroundColor: 'rgba(58, 42, 31, 0.75)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  remove: {
    position: 'absolute',
    right: 6,
    top: 6,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: 'rgba(58, 42, 31, 0.75)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addTile: {
    borderRadius: 12,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.beige,
    alignItems: 'center',
    justifyContent: 'center',
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
