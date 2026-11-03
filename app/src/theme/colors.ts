import type { components } from '@/api/schema';

/** 스토리보드(DearUs_스토리보드.html)의 :root 색 그대로예요. */
export const colors = {
  cream: '#FEF7EE',
  beige: '#CDB69C',
  gold: '#D9A869',
  brown: '#6F5139',
  ink: '#3A2A1F',
  paper: '#FFFCF7',
  panel: '#F7EDE0',
  /** 스토리보드에서 var(--muted, #857260)으로 쓰는 흐린 글자색 */
  muted: '#857260',
  /** 스토리보드에서 var(--line, #E5D8C6)으로 쓰는 구분선 */
  line: '#E5D8C6',
  white: '#FFFFFF',
  black: '#000000',
  kakao: '#FEE500',
  kakaoText: '#191919',
  googleText: '#3C4043',
} as const;

export type Emotion = components['schemas']['Emotion'];

/** API의 감정 값(JOY 등)별 색. 설렘(EXCITED)은 스토리보드 CSS에서 --e-flutter예요. */
export const emotionColors: Record<Emotion, string> = {
  JOY: '#E3A33D',
  EXCITED: '#D98BA0',
  CALM: '#8FA87D',
  TIRED: '#9B8FA0',
  SAD: '#6E8FB4',
  ANXIOUS: '#8B6BA8',
  ANGRY: '#C14B3F',
};

export const emotionLabels: Record<Emotion, string> = {
  JOY: '기쁨',
  EXCITED: '설렘',
  CALM: '평온',
  TIRED: '피곤',
  SAD: '슬픔',
  ANXIOUS: '불안',
  ANGRY: '화남',
};
