package com.seniorinsight.billing.adapters.out.pagamento;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seniorinsight.billing.application.porta.saida.ExcluirCobrancaExterna;
import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio.ReferenciasStripe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class ExcluirClienteStripe implements ExcluirCobrancaExterna {

    private static final Set<String> ENCERRADAS = Set.of("canceled", "incomplete_expired");
    private final RestClient stripe;
    private final boolean habilitado;

    @Autowired
    ExcluirClienteStripe(RestClient.Builder builder,
                         @Value("${billing.stripe.exclusao-habilitada}") boolean habilitado,
                         @Value("${billing.stripe.chave}") String chave) {
        this(criarCliente(builder, habilitado, chave), habilitado);
    }

    ExcluirClienteStripe(RestClient stripe, boolean habilitado) {
        this.stripe = stripe;
        this.habilitado = habilitado;
    }

    private static RestClient criarCliente(RestClient.Builder builder, boolean habilitado, String chave) {
        if (habilitado && chave.isBlank()) {
            throw new IllegalArgumentException("A exclusão no Stripe exige a configuração da chave");
        }
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(2));
        http.setReadTimeout(Duration.ofSeconds(5));
        return builder.baseUrl("https://api.stripe.com").requestFactory(http)
                .defaultHeaders(headers -> {
                    if (!chave.isBlank()) {
                        headers.setBearerAuth(chave);
                    }
                }).build();
    }

    @Override
    public void excluir(ReferenciasStripe referencias) {
        if (!habilitado) {
            throw new IllegalStateException("Exclusão no Stripe ainda não habilitada");
        }

        try {
            String clienteId = identificarCliente(referencias);
            Cliente cliente = stripe.get().uri("/v1/customers/{id}", clienteId).retrieve().body(Cliente.class);
            exigirCliente(cliente, clienteId);
            if (Boolean.TRUE.equals(cliente.deleted())) {
                return;
            }
            verificarAssinaturas(clienteId);
            Cliente apagado = stripe.delete().uri("/v1/customers/{id}", clienteId).retrieve().body(Cliente.class);
            exigirCliente(apagado, clienteId);
            if (!Boolean.TRUE.equals(apagado.deleted())) {
                throw falha();
            }
        } catch (RestClientException e) {
            // A exceção do provedor pode conter IDs de cobrança ou partes da resposta.
            throw falha();
        }
    }

    private String identificarCliente(ReferenciasStripe referencias) {
        String cliente = referencias.clienteId();
        if (referencias.assinaturaId() != null) {
            String id = referencias.assinaturaId();
            if (!id.matches("sub_[A-Za-z0-9_]+")) {
                throw falha();
            }
            Assinatura assinatura = stripe.get().uri("/v1/subscriptions/{id}", id)
                    .retrieve().body(Assinatura.class);
            if (assinatura == null || !id.equals(assinatura.id()) || assinatura.customer() == null
                    || (cliente != null && !cliente.equals(assinatura.customer()))) {
                throw falha();
            }
            cliente = assinatura.customer();
        }
        if (cliente == null || !cliente.matches("cus_[A-Za-z0-9_]+")) {
            throw falha();
        }
        return cliente;
    }

    private void verificarAssinaturas(String clienteId) {
        String cursor = null;
        do {
            String anterior = cursor;
            PaginaAssinaturas pagina = stripe.get().uri(uri -> {
                uri.path("/v1/subscriptions").queryParam("customer", clienteId)
                        .queryParam("status", "all").queryParam("limit", 100);
                if (anterior != null) {
                    uri.queryParam("starting_after", anterior);
                }
                return uri.build();
            }).retrieve().body(PaginaAssinaturas.class);

            if (pagina == null || pagina.data() == null || pagina.hasMore() == null) {
                throw falha();
            }
            for (Assinatura assinatura : pagina.data()) {
                if (assinatura == null || !clienteId.equals(assinatura.customer())
                        || assinatura.status() == null || assinatura.id() == null) {
                    throw falha();
                }
                if (!ENCERRADAS.contains(assinatura.status())) {
                    throw new IllegalStateException("Exclusão aguardando encerramento das assinaturas no Stripe");
                }
            }
            cursor = pagina.hasMore() && !pagina.data().isEmpty() ? pagina.data().getLast().id() : null;
            if (pagina.hasMore() && (cursor == null || cursor.equals(anterior))) {
                throw falha();
            }
        } while (cursor != null);
    }

    private static void exigirCliente(Cliente cliente, String id) {
        if (cliente == null || !id.equals(cliente.id()) || !"customer".equals(cliente.object())) {
            throw falha();
        }
    }

    private static IllegalStateException falha() {
        return new IllegalStateException("Exclusão do cliente ainda não confirmada pelo Stripe");
    }

    private record Cliente(String id, String object, Boolean deleted) {
    }

    private record Assinatura(String id, String customer, String status) {
    }

    private record PaginaAssinaturas(List<Assinatura> data, @JsonProperty("has_more") Boolean hasMore) {
    }
}
