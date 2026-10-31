package com.seniorinsight.billing.application.evento;

import com.seniorinsight.billing.application.porta.saida.RegistroEventosProcessados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processa um evento recebido uma única vez por id. O registro do id e os efeitos do evento ficam na mesma
 * transação: se o processamento falhar, o id não fica registrado e a mensagem pode ser reprocessada da dead-letter.
 */
@Service
public class ReceberEventoService {

    private static final Logger log = LoggerFactory.getLogger(ReceberEventoService.class);

    private final RegistroEventosProcessados registro;

    public ReceberEventoService(RegistroEventosProcessados registro) {
        this.registro = registro;
    }

    /** @return {@code false} se o evento já tinha sido processado (reentrega) e foi ignorado. */
    @Transactional
    public boolean receber(EventoRecebido evento) {
        if (!registro.registrarSeNovo(evento.id(), evento.tipo())) {
            log.info("Evento {} ({}) já processado, ignorado", evento.id(), evento.tipo());
            return false;
        }
        // Os casos de uso de cada tipo de evento (ex.: iniciar o trial em user.created) entram nas próximas HUs.
        log.debug("Evento {} ({}) processado", evento.id(), evento.tipo());
        return true;
    }
}
