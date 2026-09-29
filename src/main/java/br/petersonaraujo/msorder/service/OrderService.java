package br.petersonaraujo.msorder.service;

import br.petersonaraujo.msorder.entity.OrderEntity;
import br.petersonaraujo.msorder.entity.OrderItem;
import br.petersonaraujo.msorder.listener.dto.OrderCreatedEvent;
import br.petersonaraujo.msorder.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public void save(OrderCreatedEvent orderCreatedEvent) {

        var entity = new OrderEntity();
        entity.setOrderId(orderCreatedEvent.codigoPedido());
        entity.setCustumerId(orderCreatedEvent.codigoCliente());
        entity.setItems(getOrderItems(orderCreatedEvent));
        entity.setTotal(getTotal(orderCreatedEvent));

        orderRepository.save(entity);
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
