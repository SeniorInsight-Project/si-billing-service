package com.seniorinsight.billing;

import org.springframework.boot.SpringApplication;

/** Sobe o serviço localmente com PostgreSQL e RabbitMQ do Testcontainers: {@code mvn spring-boot:test-run}. */
public class TestBillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(BillingServiceApplication::main).with(ContainersConfig.class).run(args);
    }
}
