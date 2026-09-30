package com.quotagate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableScheduling
public class QuotagateApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuotagateApplication.class, args);
	}

	@Bean(name = "streamExecutor", destroyMethod = "close")
	ExecutorService streamExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
