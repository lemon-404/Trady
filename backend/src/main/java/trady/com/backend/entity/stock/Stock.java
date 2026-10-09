package trady.com.backend.entity.stock;

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
    private String market;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Stock(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.createdAt = LocalDateTime.now();
    }
}
