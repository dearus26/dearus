package com.dearus.common;

import com.dearus.common.ai.AiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 여러 기능이 같이 쓰는 설정값(app.crypto, app.ai)을 등록하고, 정해진 시각에 도는 일(@Scheduled)을 켜요. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({CryptoProperties.class, AiProperties.class})
public class AppConfig {
}
