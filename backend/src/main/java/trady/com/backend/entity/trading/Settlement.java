package trady.com.backend.entity.trading;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.entity.member.Account;
import trady.com.backend.entity.trading.Enum.ExecutionStatus;
import trady.com.backend.entity.trading.Enum.Side;

import java.time.LocalDateTime;

@Entity
@Table(name = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "side", nullable = false, length = 4)
    private Side side;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "trade_date", nullable = false)
    private LocalDateTime tradeDate;

    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "settlement_date", nullable = false)
    private LocalDateTime settlementDate;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    public Settlement(
            Execution execution,
            Account account,
            Side side,
            Long amount,
            LocalDateTime tradeDate,
            ExecutionStatus status,
            LocalDateTime settlementDate
    ) {
        this.execution = execution;
        this.account = account;
        this.side = side;
        this.amount = amount;
        this.tradeDate = tradeDate;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.settlementDate = settlementDate;
    }
}
