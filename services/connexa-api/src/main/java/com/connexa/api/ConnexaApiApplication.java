package com.connexa.api;

import com.connexa.api.config.ConnexaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ConnexaProperties.class)
public class ConnexaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnexaApiApplication.class, args);
    }
}
