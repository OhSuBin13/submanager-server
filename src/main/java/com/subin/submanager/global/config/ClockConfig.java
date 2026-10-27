package com.subin.submanager.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각이 필요한 서비스는 Instant.now() 대신 이 Clock을 주입받는다. 테스트에서 고정 시각으로 바꿔 끼우기 위해서다.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
