package ru.avitodeals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class AviApplication {

	@Bean
	Clock applicationClock() {
		return Clock.systemUTC();
	}

	public static void main(String[] args) {
		SpringApplication.run(AviApplication.class, args);
	}
}
