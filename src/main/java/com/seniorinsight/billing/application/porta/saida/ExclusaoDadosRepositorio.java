package com.seniorinsight.billing.application.porta.saida;

import java.util.Optional;
import java.util.UUID;

public interface ExclusaoDadosRepositorio {

    EstadoReserva reservar(UUID protocolo, String titularSub, boolean idoso);

    boolean assinaturaAtiva(String titularSub);

    Optional<ReferenciasStripe> bloquearCobranca(String titularSub);

    void excluir(String titularSub);

    void concluir(UUID protocolo);

    enum EstadoReserva {
        PENDENTE,
        CONCLUIDA
    }

    record ReferenciasStripe(String clienteId, String assinaturaId) {
    }
}
