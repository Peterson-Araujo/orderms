package br.petersonaraujo.msorder.controller;

import br.petersonaraujo.msorder.controller.dto.OrderResponse;
import br.petersonaraujo.msorder.exception.domain.EntidadeNaoEncontradaException;
import br.petersonaraujo.msorder.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("deve retornar 200 com summary, data e pagination")
    void deveListarPedidosComSucesso() throws Exception {
        var pageRequest = PageRequest.of(0, 10);
        var content = List.of(
                new OrderResponse(1L, 10L, new BigDecimal("130.50")),
                new OrderResponse(2L, 10L, new BigDecimal("120.25"))
        );

        when(orderService.findAllByCustomerId(10L, pageRequest))
                .thenReturn(new PageImpl<>(content, pageRequest, 25));
        when(orderService.findTotalOnOrdersByCustomerId(10L))
                .thenReturn(new BigDecimal("250.75"));

        mockMvc.perform(get("/customers/{customerId}/orders", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalOnOrders").value(250.75))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderId").value(1))
                .andExpect(jsonPath("$.data[0].customerId").value(10))
                .andExpect(jsonPath("$.data[0].total").value(130.50))
                .andExpect(jsonPath("$.data[1].orderId").value(2))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.pageSize").value(10))
                .andExpect(jsonPath("$.pagination.totalElements").value(25))
                .andExpect(jsonPath("$.pagination.totalPages").value(3));
    }

    @Test
    @DisplayName("deve usar page=0 e pageSize=10 quando os parâmetros não são informados")
    void deveUsarPaginacaoPadrao() throws Exception {
        var pageRequest = PageRequest.of(0, 10);

        when(orderService.findAllByCustomerId(10L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        when(orderService.findTotalOnOrdersByCustomerId(10L))
                .thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/customers/{customerId}/orders", 10L))
                .andExpect(status().isOk());

        verify(orderService).findAllByCustomerId(10L, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("deve repassar page e pageSize informados para o service")
    void deveRepassarPaginacaoCustomizada() throws Exception {
        var pageRequest = PageRequest.of(2, 5);

        when(orderService.findAllByCustomerId(10L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 12));
        when(orderService.findTotalOnOrdersByCustomerId(10L))
                .thenReturn(new BigDecimal("1"));

        mockMvc.perform(get("/customers/{customerId}/orders", 10L)
                        .param("page", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.pageSize").value(5))
                .andExpect(jsonPath("$.pagination.totalPages").value(3));

        verify(orderService).findAllByCustomerId(10L, pageRequest);
    }

    @Test
    @DisplayName("deve retornar 404 quando o cliente não tem pedidos")
    void deveRetornar404QuandoSemPedidos() throws Exception {
        var pageRequest = PageRequest.of(0, 10);

        when(orderService.findAllByCustomerId(77L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        when(orderService.findTotalOnOrdersByCustomerId(77L))
                .thenThrow(new EntidadeNaoEncontradaException("Nenhum pedido encontrado para o cliente 77"));

        mockMvc.perform(get("/customers/{customerId}/orders", 77L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro").value("Recurso Não Encontrado"))
                .andExpect(jsonPath("$.mensagem").value("Nenhum pedido encontrado para o cliente 77"))
                .andExpect(jsonPath("$.campos").doesNotExist());
    }

    @Test
    @DisplayName("deve retornar 400 quando customerId não é numérico")
    void deveRetornar400QuandoCustomerIdInvalido() throws Exception {
        mockMvc.perform(get("/customers/{customerId}/orders", "abc"))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).findAllByCustomerId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("deve retornar 400 quando page não é numérico")
    void deveRetornar400QuandoPageInvalido() throws Exception {
        mockMvc.perform(get("/customers/{customerId}/orders", 10L)
                        .param("page", "x"))
                .andExpect(status().isBadRequest());
    }
}