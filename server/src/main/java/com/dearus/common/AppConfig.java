package com.dearus.common;

import com.dearus.common.ai.AiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 여러 기능이 같이 쓰는 설정값(app.crypto, app.ai)을 등록해요. */
@Configuration
@EnableConfigurationProperties({CryptoProperties.class, AiProperties.class})
public class AppConfig {
}
