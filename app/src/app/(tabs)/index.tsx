import { View } from 'react-native';

import { Button } from '@/components/Button';
import { ScreenPlaceholder } from '@/components/ScreenPlaceholder';
import { useSession } from '@/session/SessionProvider';

/** 스토리보드 A-3 홈 (다음 단계: 감정 색 점 캘린더 + "오늘의 일기 쓰기") */
export default function HomeScreen() {
  const { user, signOut } = useSession();
  return (
    <ScreenPlaceholder
      title={`${user?.nickname ?? ''}님, 오늘도 한 줄 남겨요`}
      description="감정 색 점이 찍히는 캘린더와 '오늘의 일기 쓰기'가 여기에 들어와요.">
      {/* TODO(개발용): 설정 화면(로그아웃 · 탈퇴)이 생기면 지워요. 지금은 로그인 흐름을 다시 확인하는 용도예요. */}
      <View style={{ marginTop: 24 }}>
        <Button label="(개발용) 로그아웃" variant="line" onPress={() => void signOut()} />
      </View>
    </ScreenPlaceholder>
  );
}
