package com.seniorinsight.billing.adapters.in.mensageria;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de entrada: fila {@code billing.q} ligada à exchange {@code si.events} pelas routing keys de
 * {@code billing.mensageria.routing-keys}. Mensagem rejeitada vai para a exchange {@code billing.dlx} e cai na fila
 * {@code billing.q.dlq}. Tudo é declarado (de forma idempotente) na conexão.
 */
@Configuration(proxyBeanMethods = false)
public class MensageriaConfig {

    public static final String EXCHANGE_EVENTOS = "si.events";
    public static final String FILA = "billing.q";
    public static final String EXCHANGE_DEAD_LETTER = "billing.dlx";
    public static final String FILA_DEAD_LETTER = "billing.q.dlq";
    public static final String EXCHANGE_ESPERA = "billing.retry";
    public static final String FILA_ESPERA = "billing.q.wait";
    public static final String ROUTING_KEY_EXCLUSAO = "lgpd.erasure.requested";

    @Bean
    Declarables topologiaBilling(@Value("${billing.mensageria.routing-keys}") List<String> routingKeys,
                                 @Value("${billing.exclusao.intervalo}") Duration intervalo) {
        TopicExchange eventos = ExchangeBuilder.topicExchange(EXCHANGE_EVENTOS).durable(true).build();
        DirectExchange deadLetter = ExchangeBuilder.directExchange(EXCHANGE_DEAD_LETTER).durable(true).build();
        DirectExchange espera = ExchangeBuilder.directExchange(EXCHANGE_ESPERA).durable(true).build();
        Queue fila = QueueBuilder.durable(FILA)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(FILA_DEAD_LETTER)
                .build();
        Queue filaDeadLetter = QueueBuilder.durable(FILA_DEAD_LETTER).build();
        Queue filaEspera = QueueBuilder.durable(FILA_ESPERA)
                .ttl(Math.toIntExact(intervalo.toMillis()))
                .deadLetterExchange(EXCHANGE_EVENTOS)
                .deadLetterRoutingKey(ROUTING_KEY_EXCLUSAO)
                .build();

        List<Declarable> declaraveis = new ArrayList<>(List.of(eventos, deadLetter, fila, filaDeadLetter,
                espera, filaEspera, BindingBuilder.bind(filaDeadLetter).to(deadLetter).with(FILA_DEAD_LETTER),
                BindingBuilder.bind(filaEspera).to(espera).with(FILA_ESPERA)));
        routingKeys.forEach(routingKey -> declaraveis.add(BindingBuilder.bind(fila).to(eventos).with(routingKey)));
        if (!routingKeys.contains(ROUTING_KEY_EXCLUSAO)) {
            declaraveis.add(BindingBuilder.bind(fila).to(eventos).with(ROUTING_KEY_EXCLUSAO));
        }
        return new Declarables(declaraveis);
    }
}
