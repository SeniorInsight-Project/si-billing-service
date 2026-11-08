package com.seniorinsight.billing.application.porta.saida;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio.ReferenciasStripe;

public interface ExcluirCobrancaExterna {

    void excluir(ReferenciasStripe referencias);
}
