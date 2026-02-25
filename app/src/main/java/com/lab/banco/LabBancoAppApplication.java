package com.lab.banco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LabBancoAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(LabBancoAppApplication.class, args);
	}

}
