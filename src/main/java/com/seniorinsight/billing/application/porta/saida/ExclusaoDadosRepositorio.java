package com.seniorinsight.billing.application.porta.saida;

import java.util.Optional;
import java.util.UUID;

public interface ExclusaoDadosRepositorio {

    boolean reservar(UUID protocolo);

    boolean assinaturaAtiva(String titularSub);

    Optional<ReferenciasStripe> bloquearCobranca(String titularSub);

    void excluir(String titularSub);

    record ReferenciasStripe(String clienteId, String assinaturaId) {
    }
}
