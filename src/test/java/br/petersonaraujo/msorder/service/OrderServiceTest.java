package br.petersonaraujo.msorder.service;

import br.petersonaraujo.msorder.controller.dto.OrderResponse;
import br.petersonaraujo.msorder.entity.OrderEntity;
import br.petersonaraujo.msorder.entity.OrderItem;
import br.petersonaraujo.msorder.exception.domain.EntidadeNaoEncontradaException;
import br.petersonaraujo.msorder.listener.dto.OrderCreatedEvent;
import br.petersonaraujo.msorder.listener.dto.OrderItemEvent;
import br.petersonaraujo.msorder.repository.OrderRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OrderService orderService;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("deve mapear o evento para a entidade, calcular o total e salvar")
        void deveSalvarPedidoComTotalCalculado() {
            var event = new OrderCreatedEvent(
                    1L,
                    10L,
                    List.of(
                            new OrderItemEvent("Teclado", 2, new BigDecimal("50.00")),
                            new OrderItemEvent("Mouse", 1, new BigDecimal("30.50"))
                    )
            );

            orderService.save(event);

            var captor = ArgumentCaptor.forClass(OrderEntity.class);
            verify(orderRepository).save(captor.capture());
            var saved = captor.getValue();

            assertThat(saved.getOrderId()).isEqualTo(1L);
            assertThat(saved.getCustomerId()).isEqualTo(10L);
            // 2 * 50.00 + 1 * 30.50 = 130.50
            assertThat(saved.getTotal()).isEqualByComparingTo("130.50");
            assertThat(saved.getItems())
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(
                            new OrderItem("Teclado", 2, new BigDecimal("50.00")),
                            new OrderItem("Mouse", 1, new BigDecimal("30.50"))
                    ));
        }

        @Test
        @DisplayName("deve salvar total zero quando não há itens")
        void deveSalvarTotalZeroQuandoSemItens() {
            var event = new OrderCreatedEvent(2L, 20L, List.of());

            orderService.save(event);

            var captor = ArgumentCaptor.forClass(OrderEntity.class);
            verify(orderRepository).save(captor.capture());
            var saved = captor.getValue();

            assertThat(saved.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getItems()).isEmpty();
        }

        @Test
        @DisplayName("deve calcular corretamente com um único item")
        void deveCalcularTotalComUmItem() {
            var event = new OrderCreatedEvent(
                    3L, 30L,
                    List.of(new OrderItemEvent("Monitor", 3, new BigDecimal("100.00")))
            );

            orderService.save(event);

            var captor = ArgumentCaptor.forClass(OrderEntity.class);
            verify(orderRepository).save(captor.capture());

            assertThat(captor.getValue().getTotal()).isEqualByComparingTo("300.00");
        }
    }

    @Nested
    @DisplayName("findAllByCustomerId")
    class FindAllByCustomerId {

        @Test
        @DisplayName("deve retornar a página de pedidos convertida para OrderResponse")
        void deveRetornarPaginaConvertida() {
            var pageRequest = PageRequest.of(0, 10);

            var order = new OrderEntity();
            order.setOrderId(1L);
            order.setCustomerId(10L);
            order.setTotal(new BigDecimal("130.50"));
            order.setItems(List.of(new OrderItem("Teclado", 2, new BigDecimal("50.00"))));

            Page<OrderEntity> page = new PageImpl<>(List.of(order), pageRequest, 1);
            when(orderRepository.findAllByCustomerId(10L, pageRequest)).thenReturn(page);

            var result = orderService.findAllByCustomerId(10L, pageRequest);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent())
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(OrderResponse.fromResponse(order)));
            verify(orderRepository).findAllByCustomerId(10L, pageRequest);
        }

        @Test
        @DisplayName("deve retornar página vazia quando o cliente não tem pedidos")
        void deveRetornarPaginaVazia() {
            var pageRequest = PageRequest.of(0, 10);
            when(orderRepository.findAllByCustomerId(99L, pageRequest))
                    .thenReturn(Page.empty(pageRequest));

            var result = orderService.findAllByCustomerId(99L, pageRequest);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findTotalOnOrdersByCustomerId")
    class FindTotalOnOrdersByCustomerId {

        private void mockAggregation(Document... documents) {
            var results = new AggregationResults<>(List.of(documents), new Document());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq("tb_orders"), eq(Document.class)))
                    .thenReturn(results);
        }

        @Test
        @DisplayName("deve retornar o total agregado quando o Mongo devolve Decimal128")
        void deveRetornarTotalDecimal128() {
            mockAggregation(new Document("total", new Decimal128(new BigDecimal("250.75"))));

            var total = orderService.findTotalOnOrdersByCustomerId(10L);

            assertThat(total).isEqualByComparingTo("250.75");
            verify(mongoTemplate).aggregate(any(Aggregation.class), eq("tb_orders"), eq(Document.class));
        }

        @Test
        @DisplayName("deve converter total em String para BigDecimal")
        void deveConverterTotalString() {
            mockAggregation(new Document("total", "99.90"));

            var total = orderService.findTotalOnOrdersByCustomerId(10L);

            assertThat(total).isEqualByComparingTo("99.90");
        }

        @Test
        @DisplayName("deve lançar EntidadeNaoEncontradaException quando não há resultado")
        void deveLancarExcecaoQuandoSemResultado() {
            mockAggregation(); // lista vazia -> getUniqueMappedResult() == null

            assertThatThrownBy(() -> orderService.findTotalOnOrdersByCustomerId(77L))
                    .isInstanceOf(EntidadeNaoEncontradaException.class)
                    .hasMessage("Nenhum pedido encontrado para o cliente 77");
        }

        @Test
        @DisplayName("não deve acionar o repository, apenas o MongoTemplate")
        void deveUsarApenasMongoTemplate() {
            mockAggregation(new Document("total", "10"));

            orderService.findTotalOnOrdersByCustomerId(1L);

            verifyNoInteractions(orderRepository);
        }
    }
}