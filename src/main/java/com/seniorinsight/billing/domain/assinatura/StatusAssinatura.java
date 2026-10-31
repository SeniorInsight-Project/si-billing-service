package com.seniorinsight.billing.domain.assinatura;

/** Situação de uma assinatura. Mesmos valores da restrição {@code ck_assinatura_status}. */
public enum StatusAssinatura {
    ATIVA,
    INADIMPLENTE,
    CANCELADA,
    EXPIRADA
}
