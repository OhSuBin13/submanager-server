package com.subin.submanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SubManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubManagerApplication.class, args);
    }
}
