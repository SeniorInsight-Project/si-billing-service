package com.seniorinsight.billing.adapters.in.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.seniorinsight.billing.TesteIntegracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Evento publicado em si.events chega na billing.q e é processado uma vez só; mensagem inválida vai para a DLQ. */
@TesteIntegracao
class ConsumidorEventosIntegracaoTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void esvaziaDeadLetter() {
        amqpAdmin.purgeQueue(MensageriaConfig.FILA_DEAD_LETTER, false);
    }

    @Test
    void processaOEventoEConfirma() {
        UUID eventoId = UUID.randomUUID();

        publicar(mensagem(eventoId.toString()));

        await().atMost(Duration.ofSeconds(10)).until(() -> processados(eventoId) == 1);
    }

    @Test
    void reentregaComOMesmoIdEhIgnorada() {
        UUID eventoId = UUID.randomUUID();
        UUID sentinela = UUID.randomUUID();

        publicar(mensagem(eventoId.toString()));
        publicar(mensagem(eventoId.toString()));
        publicar(mensagem(sentinela.toString()));

        // Um consumidor só, em ordem: quando a sentinela é processada, as duas cópias já foram.
        await().atMost(Duration.ofSeconds(10)).until(() -> processados(sentinela) == 1);
        assertThat(processados(eventoId)).isEqualTo(1);
        assertThat(rabbitTemplate.receive(MensageriaConfig.FILA_DEAD_LETTER, 500)).isNull();
    }

    @Test
    void mensagemSemIdValidoVaiParaADeadLetter() {
        publicar(mensagem(null));
        publicar(mensagem("nao-eh-uuid"));

        Message primeira = rabbitTemplate.receive(MensageriaConfig.FILA_DEAD_LETTER, 10_000);
        Message segunda = rabbitTemplate.receive(MensageriaConfig.FILA_DEAD_LETTER, 10_000);

        assertThat(primeira).isNotNull();
        assertThat(segunda).isNotNull();
        assertThat(segunda.getMessageProperties().getMessageId()).isEqualTo("nao-eh-uuid");
    }

    private void publicar(Message mensagem) {
        rabbitTemplate.send(MensageriaConfig.EXCHANGE_EVENTOS, "user.created", mensagem);
    }

    private static Message mensagem(String messageId) {
        MessageBuilderSupport<Message> builder = MessageBuilder.withBody("{\"perfilId\":\"x\"}".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setType("user.created");
        if (messageId != null) {
            builder.setMessageId(messageId);
        }
        return builder.build();
    }

    private int processados(UUID eventoId) {
        return jdbc.queryForObject("SELECT count(*) FROM evento_processado WHERE id = ?", Integer.class, eventoId);
    }
}
