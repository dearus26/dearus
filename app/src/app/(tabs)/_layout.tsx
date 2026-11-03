import { Tabs } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Icon, type IconName } from '@/components/Icon';
import { colors } from '@/theme/colors';

const tabs: { name: string; title: string; icon: IconName }[] = [
  { name: 'index', title: '홈', icon: 'home' },
  { name: 'past', title: '과거의 나', icon: 'chat' },
  { name: 'couple', title: '커플', icon: 'heart' },
];

/** 하단 탭 3개. 스토리보드 .tabbar: 종이색 바탕, 위쪽 베이지 선, 선택된 탭은 아이콘 뒤에 금색 알약이에요. */
export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        sceneStyle: { backgroundColor: colors.cream },
        tabBarActiveTintColor: colors.ink,
        tabBarInactiveTintColor: 'rgba(111, 81, 57, 0.65)',
        tabBarStyle: {
          backgroundColor: colors.paper,
          borderTopColor: colors.beige,
          borderTopWidth: 1,
          // 알약 아이콘(30) + 이름(16)이 들어가도록 기본 높이(49)보다 키워요. 아래는 제스처 바 영역만큼 띄워요.
          height: 64 + insets.bottom,
          paddingTop: 8,
          paddingBottom: insets.bottom,
        },
      }}>
      {tabs.map((tab) => (
        <Tabs.Screen
          key={tab.name}
          name={tab.name}
          options={{
            title: tab.title,
            tabBarLabel: ({ focused, color }) => (
              <AppText weight={focused ? 'bold' : 'regular'} size={11} lineHeight={16} color={color}>
                {tab.title}
              </AppText>
            ),
            tabBarIcon: ({ focused, color }) => (
              <View style={[styles.pill, focused && styles.pillOn]}>
                <Icon name={tab.icon} size={24} color={color} />
              </View>
            ),
          }}
        />
      ))}
    </Tabs>
  );
}

const styles = StyleSheet.create({
  pill: {
    paddingVertical: 3,
    paddingHorizontal: 16,
    borderRadius: 14,
  },
  pillOn: {
    backgroundColor: colors.gold,
  },
});
