package com.seniorinsight.billing.application.evento;

import java.util.UUID;

/** Evento recebido de outro serviço pela fila {@code billing.q}: id (= {@code message_id}), tipo e corpo JSON. */
public record EventoRecebido(UUID id, String tipo, String payload) {
}
