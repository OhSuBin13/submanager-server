package com.subin.submanager.global.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 카카오·네이버 API 호출용 RestClient. 로그인 응답을 오래 기다리지 않도록 연결·응답 모두 3초에서 끊는다. */
@Configuration
public class RestClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Bean
    RestClient socialRestClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }
}
