package com.seniorinsight.billing.adapters.in.mensageria;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.rabbitmq.client.Channel;
import com.seniorinsight.billing.application.evento.EventoRecebido;
import com.seniorinsight.billing.application.evento.ReceberEventoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila {@code billing.q}, com ack manual.
 *
 * <p>O ack só sai depois que a transação do processamento foi confirmada. Reentregas (mesmo {@code message_id}) são
 * confirmadas sem efeito. Mensagem sem {@code message_id} válido ou com falha no processamento recebe nack sem
 * requeue e vai para a dead-letter {@code billing.dlq}.
 */
@Component
class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    private final ReceberEventoService receberEvento;

    ConsumidorEventos(ReceberEventoService receberEvento) {
        this.receberEvento = receberEvento;
    }

    @RabbitListener(queues = MensageriaConfig.FILA)
    void consumir(Message mensagem, Channel canal) throws IOException {
        MessageProperties propriedades = mensagem.getMessageProperties();
        long tag = propriedades.getDeliveryTag();
        try {
            receberEvento.receber(new EventoRecebido(
                    idDoEvento(propriedades),
                    tipoDoEvento(propriedades),
                    new String(mensagem.getBody(), StandardCharsets.UTF_8)));
            canal.basicAck(tag, false);
        } catch (RuntimeException e) {
            log.error("Evento {} ({}) enviado para a dead-letter: {}",
                    propriedades.getMessageId(), propriedades.getReceivedRoutingKey(), e.getMessage(), e);
            canal.basicNack(tag, false, false);
        }
    }

    private static UUID idDoEvento(MessageProperties propriedades) {
        String messageId = propriedades.getMessageId();
        if (messageId == null) {
            throw new IllegalArgumentException("mensagem sem message_id");
        }
        return UUID.fromString(messageId);
    }

    private static String tipoDoEvento(MessageProperties propriedades) {
        return propriedades.getType() != null ? propriedades.getType() : propriedades.getReceivedRoutingKey();
    }
}
