package com.connexa.api;

import com.connexa.api.config.ConnexaProperties;
import com.connexa.api.config.CheckInProperties;
import com.connexa.api.config.IngestionProperties;
import com.connexa.api.config.MediaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ConnexaProperties.class,
        IngestionProperties.class,
        CheckInProperties.class,
        MediaProperties.class})
public class ConnexaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnexaApiApplication.class, args);
    }
}
