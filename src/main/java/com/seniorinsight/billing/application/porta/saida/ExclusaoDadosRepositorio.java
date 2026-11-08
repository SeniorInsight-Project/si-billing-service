package com.seniorinsight.billing.application.porta.saida;

import java.util.UUID;

public interface ExclusaoDadosRepositorio {

    boolean reservar(UUID protocolo);

    boolean assinaturaAtiva(String titularSub);

    void excluir(String titularSub);
}
