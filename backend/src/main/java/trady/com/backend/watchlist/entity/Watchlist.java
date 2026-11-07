package trady.com.backend.watchlist.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import trady.com.backend.stock.entity.Stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Watchlist {

    @Id
    @Column(name = "stock_code", length = 25)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "stock_code",
            referencedColumnName = "code",
            insertable = false,
            updatable = false,
            nullable = false
    )
    private Stock stock;

    @Column(name = "base_price", nullable = false)
    private Long basePrice;

    @Column(name = "rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    public Watchlist(
            String code,
            Long basePrice,
            BigDecimal rate,
            LocalDateTime collectedAt
    ) {
        this.code = code;
        this.basePrice = basePrice;
        this.rate = rate;
        this.collectedAt = collectedAt;
    }

    public Watchlist(
            Stock stock,
            Long basePrice,
            BigDecimal rate,
            LocalDateTime collectedAt
    ) {
        this.stock = stock;
        this.code = stock.getCode();
        this.basePrice = basePrice;
        this.rate = rate;
        this.collectedAt = collectedAt;
    }





}
