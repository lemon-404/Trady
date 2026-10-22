package trady.com.backend.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import trady.com.backend.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
