package trady.com.backend.account.entity;

import jakarta.persistence.*;
import lombok.*;
import trady.com.backend.entity.member.Member;

import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @OneToOne
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(name = "cash", nullable = false)
    private Long cash;

    @Column(name = "available_cash", nullable = false)
    private Long availableCash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Account(Member member, Long cash, Long availableCash) {
        this.member = member;
        this.cash = cash;
        this.availableCash = availableCash;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

}
