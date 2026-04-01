package com.build4all;

import com.build4all.notifications.config.FirebaseConfigStorageProperties;
import com.build4all.notifications.config.FirebaseEnvProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        FirebaseEnvProperties.class,
        FirebaseConfigStorageProperties.class
})
public class Build4AllApplication {

    public static void main(String[] args) {
        SpringApplication.run(Build4AllApplication.class, args);
    }
}