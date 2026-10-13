package com.subin.submanager.global.config;

import java.time.ZoneId;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** 결제일·알림 시각 계산과 @Scheduled cron의 기준 시간대 */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
}
