package com.dearus;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 앱 전체를 띄워 보는 테스트예요. Supabase(DB)에 연결해야 해서 기본으로는 꺼 둬요.
 * server/.env를 채운 뒤 직접 확인하고 싶을 때만 @Disabled를 지우고 실행해요.
 */
@Disabled("Supabase 연결(server/.env)이 필요해요")
@SpringBootTest
class DearusApplicationTests {

	@Test
	void contextLoads() {
	}

}
