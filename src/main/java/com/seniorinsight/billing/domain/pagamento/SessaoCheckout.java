package com.seniorinsight.billing.domain.pagamento;

/** Sessão de checkout criada no provedor: {@code id} do provedor e a URL de pagamento. */
public record SessaoCheckout(String id, String url) {
}
