import { Text, type ColorValue, type TextProps } from 'react-native';

import { colors } from '@/theme/colors';
import { fontFamily } from '@/theme/fonts';

type Props = TextProps & {
  /** 제목용 명조(Noto Serif KR). 기본은 본문용 고딕(Noto Sans KR)이에요. */
  serif?: boolean;
  weight?: 'regular' | 'medium' | 'bold';
  size?: number;
  color?: ColorValue;
  /** 줄 높이(px). 안 주면 글자 크기의 1.5배예요. */
  lineHeight?: number;
};

/** 앱의 모든 글자는 이 컴포넌트로 써요. 폰트 이름과 기본 색을 한곳에서 맞춰요. */
export function AppText({
  serif = false,
  weight = 'regular',
  size = 15,
  color = colors.ink,
  lineHeight,
  style,
  ...rest
}: Props) {
  const family = (serif ? fontFamily.serif : fontFamily.sans)[weight];
  return (
    <Text
      {...rest}
      style={[{ fontFamily: family, fontSize: size, color, lineHeight: lineHeight ?? Math.round(size * 1.5) }, style]}
    />
  );
}
