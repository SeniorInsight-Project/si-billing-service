package com.seniorinsight.billing.application.porta.saida;

import java.util.UUID;

/** Guarda os ids dos eventos já processados pelo consumidor, para descartar reentregas. */
public interface RegistroEventosProcessados {

    /**
     * Registra o evento na transação corrente.
     *
     * @return {@code true} se é a primeira vez que o id aparece; {@code false} se já tinha sido processado
     */
    boolean registrarSeNovo(UUID eventoId, String tipoEvento);
}
