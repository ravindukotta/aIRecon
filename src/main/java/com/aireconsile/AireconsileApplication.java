package com.aireconsile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AireconsileApplication {

	public static void main(String[] args) {
		SpringApplication.run(AireconsileApplication.class, args);
	}

}
