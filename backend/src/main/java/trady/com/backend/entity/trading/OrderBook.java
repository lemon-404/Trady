package trady.com.backend.entity.trading;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.entity.trading.Enum.Side;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_book")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_book_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "side", nullable = false, length = 4)
    private Side side;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "remaining_quantity", nullable = false)
    private Long remainingQuantity;

    @Column(name = "priority_at", nullable = false)
    private LocalDateTime priorityAt;

    @Builder
    public OrderBook(
            Order order,
            Stock stock,
            Side side,
            Long price,
            Long remainingQuantity,
            LocalDateTime priorityAt
    ) {
        this.order = order;
        this.stock = stock;
        this.side = side;
        this.price = price;
        this.remainingQuantity = remainingQuantity;
        this.priorityAt = priorityAt;
    }
}
