package trady.com.backend.stock.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 25)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "market", nullable = false, length = 25)
    private String market; // 거래소 명 ex) "코스피", "코스닥"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Stock(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.createdAt = LocalDateTime.now();
    }


    public boolean hasSameInfo(Stock other) {
        return name.equals(other.name) && market.equals(other.market);
    }

    /** 기존 종목의 ID·코드·생성 시각은 유지하고 이름·시장만 변경한다. */
    public void updateInfo(Stock other) {
        this.name = other.name;
        this.market = other.market;
    }
}
