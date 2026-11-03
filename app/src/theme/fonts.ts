// 굵기별 폴더에서 하나씩 가져와요. 패키지 루트('@expo-google-fonts/noto-serif-kr')에서 가져오면
// 안 쓰는 굵기까지 전부 앱에 들어가서(굵기 하나가 6~14MB) 앱이 수백 MB가 돼요.
import { NotoSansKR_400Regular } from '@expo-google-fonts/noto-sans-kr/400Regular';
import { NotoSansKR_500Medium } from '@expo-google-fonts/noto-sans-kr/500Medium';
import { NotoSansKR_700Bold } from '@expo-google-fonts/noto-sans-kr/700Bold';
import { NotoSerifKR_600SemiBold } from '@expo-google-fonts/noto-serif-kr/600SemiBold';
import { NotoSerifKR_700Bold } from '@expo-google-fonts/noto-serif-kr/700Bold';

/**
 * 앱에서 쓰는 글꼴 파일. useFonts에 그대로 넘겨요.
 * 본문은 Noto Sans KR(400 · 500 · 700), 제목은 Noto Serif KR(600 · 700)이에요(스토리보드 기준).
 */
export const fontAssets = {
  NotoSansKR_400Regular,
  NotoSansKR_500Medium,
  NotoSansKR_700Bold,
  NotoSerifKR_600SemiBold,
  NotoSerifKR_700Bold,
};

export const fontFamily = {
  sans: {
    regular: 'NotoSansKR_400Regular',
    medium: 'NotoSansKR_500Medium',
    bold: 'NotoSansKR_700Bold',
  },
  serif: {
    regular: 'NotoSerifKR_600SemiBold',
    medium: 'NotoSerifKR_600SemiBold',
    bold: 'NotoSerifKR_700Bold',
  },
} as const;
