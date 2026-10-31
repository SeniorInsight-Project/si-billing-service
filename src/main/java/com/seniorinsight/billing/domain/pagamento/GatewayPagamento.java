package com.seniorinsight.billing.domain.pagamento;

/**
 * Provedor de pagamento externo (Stripe). Ainda sem implementação: a integração é a HU-043.
 *
 * <p>O domínio só conhece esta porta; o adaptador do Stripe vai morar em {@code adapters.out}.
 */
public interface GatewayPagamento {

    /** Cria a sessão de pagamento hospedada pelo provedor e devolve a URL para onde o titular é redirecionado. */
    SessaoCheckout iniciarCheckout(SolicitacaoCheckout solicitacao);

    /** Abre o portal do cliente no provedor (troca de cartão, faturas, cancelamento) e devolve a URL. */
    String abrirPortalCliente(String clienteId, String urlRetorno);
}
