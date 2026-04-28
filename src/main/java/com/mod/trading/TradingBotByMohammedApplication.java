package com.mod.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TradingBotByMohammedApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradingBotByMohammedApplication.class, args);
	}

}
