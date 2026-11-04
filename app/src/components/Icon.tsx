import type { ColorValue } from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { colors } from '@/theme/colors';

/** 스토리보드의 SVG 아이콘 도형을 그대로 옮겼어요(24×24 기준, 선 아이콘). */
const icons = {
  home: { paths: ['M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z'], strokeWidth: 2 },
  chat: { paths: ['M4 5h16v11H9l-5 4z', 'M8 10h8'], strokeWidth: 2 },
  heart: {
    paths: ['M12 20s-7-4.6-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.4-7 10-7 10z'],
    strokeWidth: 2,
  },
  check: { paths: ['M5 12.5l4.5 4.5L19 7.5'], strokeWidth: 3 },
  chevronRight: { paths: ['M9 5l7 7-7 7'], strokeWidth: 2.2 },
  chevronLeft: { paths: ['M15 5l-7 7 7 7'], strokeWidth: 2.4 },
  plus: { paths: ['M12 5v14', 'M5 12h14'], strokeWidth: 2.4 },
  send: { paths: ['M5 12h13', 'M13 6l6 6-6 6'], strokeWidth: 2.6 },
  phone: { paths: ['M6 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L16 13l5 2v4a2 2 0 0 1-2 2A15 15 0 0 1 4 6a2 2 0 0 1 2-2z'], strokeWidth: 2 },
} as const;

export type IconName = keyof typeof icons;

type Props = {
  name: IconName;
  size?: number;
  color?: ColorValue;
  strokeWidth?: number;
};

export function Icon({ name, size = 24, color = colors.ink, strokeWidth }: Props) {
  const def = icons[name];
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      {def.paths.map((d) => (
        <Path
          key={d}
          d={d}
          stroke={color}
          strokeWidth={strokeWidth ?? def.strokeWidth}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ))}
    </Svg>
  );
}
