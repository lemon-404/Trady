package trady.com.backend.entity.trading;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.stock.entity.Stock;

import java.time.LocalDateTime;

@Entity
@Table(name = "execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_order_id", nullable = false)
    private Order buyOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_order_id", nullable = false)
    private Order sellOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;

    @Builder
    public Execution(
            Order buyOrder,
            Order sellOrder,
            Stock stock,
            Long price,
            Long quantity
    ) {
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.stock = stock;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = LocalDateTime.now();
    }
}
