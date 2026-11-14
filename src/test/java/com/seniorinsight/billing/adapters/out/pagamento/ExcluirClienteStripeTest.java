package com.seniorinsight.billing.adapters.out.pagamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio.ReferenciasStripe;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExcluirClienteStripeTest {
    private static final String BASE = "https://api.stripe.com";
    private static final String CLIENTE = BASE + "/v1/customers/cus_teste";
    private static final String ASSINATURAS = BASE + "/v1/subscriptions?customer=cus_teste&status=all&limit=100";
    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ExcluirClienteStripe stripe = new ExcluirClienteStripe(builder.build(), true);

    @Test
    void bloqueiaExclusaoQuandoUmaPaginaPosteriorContemAssinaturaAtiva() {
        cliente(false);
        assinaturas(ASSINATURAS, "sub_encerrada", "canceled", true);
        assinaturas(ASSINATURAS + "&starting_after=sub_encerrada", "sub_ativa", "active", false);

        assertThatThrownBy(() -> stripe.excluir(new ReferenciasStripe("cus_teste", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("encerramento");
        servidor.verify();
    }

    @Test
    void confirmaExclusaoComAssinaturaEncerradaEAceitaReentrega() {
        cliente(false);
        assinaturas(ASSINATURAS, "sub_encerrada", "canceled", false);
        servidor.expect(requestTo(CLIENTE)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"id\":\"cus_teste\",\"object\":\"customer\",\"deleted\":true}",
                        MediaType.APPLICATION_JSON));
        cliente(true);

        stripe.excluir(new ReferenciasStripe("cus_teste", null));
        stripe.excluir(new ReferenciasStripe("cus_teste", null));
        servidor.verify();
    }

    private void cliente(boolean excluido) {
        servidor.expect(requestTo(CLIENTE)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"cus_teste","object":"customer","deleted":%b}
                        """.formatted(excluido), MediaType.APPLICATION_JSON));
    }

    private void assinaturas(String url, String id, String status, boolean mais) {
        servidor.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{"id":"%s","customer":"cus_teste","status":"%s"}],"has_more":%b}
                        """.formatted(id, status, mais), MediaType.APPLICATION_JSON));
    }
}
