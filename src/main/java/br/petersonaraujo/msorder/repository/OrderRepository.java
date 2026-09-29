package br.petersonaraujo.msorder.repository;

import br.petersonaraujo.msorder.entity.OrderEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<OrderEntity, String> {
}
