package trady.com.backend.candle.entity;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.stock.entity.Stock;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "stock_candle",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_STOCK_CANDLE",
                        columnNames = {"stock_id", "interval", "candle_time"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_candle_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "interval", nullable = false, length = 10)
    private String interval;

    @Column(name = "candle_time", nullable = false)
    private LocalDateTime time;

    @Column(name = "open_price", nullable = false)
    private int openPrice;

    @Column(name = "high_price", nullable = false)
    private int highPrice;

    @Column(name = "low_price", nullable = false)
    private int lowPrice;

    @Column(name = "close_price", nullable = false)
    private int closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Builder
    public Candle(Stock stock, String interval, LocalDateTime time, int openPrice, int highPrice, int lowPrice, int closePrice, Long volume) {
        this.stock = stock;
        this.interval = interval;
        this.time = time;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public boolean hasSameOhlcv(Candle other) {
        return openPrice == other.openPrice
                && highPrice == other.highPrice
                && lowPrice == other.lowPrice
                && closePrice == other.closePrice
                && volume.equals(other.volume);
    }

    /** 기존 캔들의 식별값은 유지하고 OHLCV만 변경한다. */
    public void updateOhlcv(Candle other) {
        this.openPrice = other.openPrice;
        this.highPrice = other.highPrice;
        this.lowPrice = other.lowPrice;
        this.closePrice = other.closePrice;
        this.volume = other.volume;
    }
}
