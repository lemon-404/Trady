package trady.com.backend.entity.member;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.stock.entity.Stock;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "position",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_POSITION",
                        columnNames = {"account_id", "stock_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "average_price", nullable = false)
    private int averagePrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Position(Account account, Stock stock, int quantity, int averagePrice) {
        this.account = account;
        this.stock = stock;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

}