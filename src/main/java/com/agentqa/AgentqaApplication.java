package com.agentqa;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AgentqaApplication {

	public static void main(String[] args) {
		// Deliberate and load-bearing. Run and step timestamps are read in IST, and JDBC
		// converts timestamps using the JVM default zone, so removing this silently
		// shifts every time the dashboard and the database show. Leave it here.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(AgentqaApplication.class, args);
	}

}