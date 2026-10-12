package trady.com.backend.entity.trading;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.entity.member.Account;
import trady.com.backend.entity.stock.Stock;
import trady.com.backend.entity.trading.Enum.OrderType;
import trady.com.backend.entity.trading.Enum.Side;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "side", nullable = false, length = 4)
    private Side side;

    @Column(name = "type", nullable = false, length = 6)
    private OrderType orderType;

    @Column(name = "price")
    private Long price;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Order(
            Account account,
            Stock stock,
            Side side,
            OrderType type,
            Long price,
            Long quantity,
            String status
    ) {
        this.account = account;
        this.stock = stock;
        this.side = side;
        this.orderType = type;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

}
