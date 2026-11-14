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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
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

    private final PedidoExclusaoJson pedidos;
    private final RabbitTemplate rabbitTemplate;
    private final int tentativas;

    ConsumidorEventos(ReceberEventoService receberEvento, PedidoExclusaoJson pedidos, RabbitTemplate rabbitTemplate,
                      @Value("${billing.exclusao.tentativas}") int tentativas) {
        this.pedidos = pedidos;
        this.receberEvento = receberEvento;
        this.rabbitTemplate = rabbitTemplate;
        this.tentativas = tentativas;
    }

    @RabbitListener(queues = MensageriaConfig.FILA)
    void consumir(Message mensagem, Channel canal) throws IOException {
        MessageProperties propriedades = mensagem.getMessageProperties();
        long tag = propriedades.getDeliveryTag();
        try {
            var evento = new EventoRecebido(
                    idDoEvento(propriedades),
                    tipoDoEvento(propriedades),
                    new String(mensagem.getBody(), StandardCharsets.UTF_8));
            if (PedidoExclusaoJson.TIPO.equals(evento.tipo())) {
                try {
                    pedidos.tratar(evento.id(), evento.payload());
                } catch (PedidoExclusaoJson.PedidoInvalido e) {
                    log.error("Evento {} enviado para a dead-letter: {}", evento.id(), e.getMessage());
                    canal.basicNack(tag, false, false);
                    return;
                } catch (RuntimeException e) {
                    if (!reagendar(evento, mensagem, e)) {
                        canal.basicNack(tag, false, false);
                    } else {
                        canal.basicAck(tag, false);
                    }
                    return;
                }
            } else {
                receberEvento.receber(evento);
            }

            canal.basicAck(tag, false);
        } catch (RuntimeException e) {
            log.error("Evento {} ({}) enviado para a dead-letter: {}",
                    propriedades.getMessageId(), propriedades.getReceivedRoutingKey(), e.getMessage(), e);
            canal.basicNack(tag, false, false);
        }
    }

    private boolean reagendar(EventoRecebido evento, Message mensagem, RuntimeException erro) {
        int tentativa = tentativas(mensagem);
        if (tentativa >= tentativas) {
            log.error("Exclusão {} falhou após {} tentativas; enviada para a dead-letter", evento.id(), tentativa, erro);
            return false;
        }
        MessageProperties propriedades = new MessageProperties();
        propriedades.setMessageId(evento.id().toString());
        propriedades.setType(PedidoExclusaoJson.TIPO);
        propriedades.setHeader("x-billing-retry-count", tentativa + 1);
        Message novaTentativa = new Message(mensagem.getBody(), propriedades);
        rabbitTemplate.send(MensageriaConfig.EXCHANGE_ESPERA, MensageriaConfig.FILA_ESPERA, novaTentativa);
        log.warn("Exclusão {} falhou na tentativa {}; reagendada: {}", evento.id(), tentativa, erro.getMessage());
        return true;
    }

    private static int tentativas(Message mensagem) {
        Object valor = mensagem.getMessageProperties().getHeaders().get("x-billing-retry-count");
        return valor instanceof Number ? ((Number) valor).intValue() + 1 : 1;
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
