package com.build4all;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Build4AllApplication {

	public static void main(String[] args) {
		SpringApplication.run(Build4AllApplication.class, args);
	}

}