package com.seniorinsight.billing.domain.pagamento;

import com.seniorinsight.billing.domain.assinatura.Plano;

/** Dados para abrir um checkout no provedor de pagamento. */
public record SolicitacaoCheckout(String titularSub, String email, Plano plano, String urlSucesso,
                                  String urlCancelamento) {
}
