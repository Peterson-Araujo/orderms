package br.petersonaraujo.msorder.service;

import br.petersonaraujo.msorder.controller.dto.OrderResponse;
import br.petersonaraujo.msorder.entity.OrderEntity;
import br.petersonaraujo.msorder.entity.OrderItem;
import br.petersonaraujo.msorder.listener.dto.OrderCreatedEvent;
import br.petersonaraujo.msorder.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Salva um {@link OrderCreatedEvent} na base de dados
     */
    public void save(OrderCreatedEvent orderCreatedEvent) {

        var entity = new OrderEntity();
        entity.setOrderId(orderCreatedEvent.codigoPedido());
        entity.setCustomerId(orderCreatedEvent.codigoCliente());
        entity.setItems(getOrderItems(orderCreatedEvent));
        entity.setTotal(getTotal(orderCreatedEvent));

        orderRepository.save(entity);
    }

    /**
     * Realiza a busca de Customers através do ID, retornando os itens paginados
     * @param customerId - ID do customer para busca
     * @param pageRequest - paginação
     */
    public Page<OrderResponse> findAllByCustomerId(Long customerId, PageRequest pageRequest) {
        var orders = orderRepository.findAllByCustomerId(customerId, pageRequest);

        return orders.map(OrderResponse::fromResponse);
    }

    /**
     * Realiza o cálculo do valor total dos itens
     * @param orderCreatedEvent
     */
    private BigDecimal getTotal(OrderCreatedEvent orderCreatedEvent) {
        return orderCreatedEvent.itens().stream()
                .map(i -> i.preco().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Realiza a conversão de {@link OrderCreatedEvent} para {@link OrderItem}
     * @param orderCreatedEvent
     * @return Lista de {@link OrderItem}
     */
    private static List<OrderItem> getOrderItems(OrderCreatedEvent orderCreatedEvent) {
        return orderCreatedEvent.itens().stream()
                .map(i -> new OrderItem(i.produto(), i.quantidade(), i.preco()))
                .toList();
    }
}
