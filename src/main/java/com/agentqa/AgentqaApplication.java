package com.agentqa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AgentqaApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentqaApplication.class, args);
	}

}