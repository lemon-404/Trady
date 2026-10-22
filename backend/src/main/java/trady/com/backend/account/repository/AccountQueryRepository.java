package trady.com.backend.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import trady.com.backend.account.entity.Account;

import java.util.Optional;

@Repository
public interface AccountQueryRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByMemberId(Long memberId);
}
