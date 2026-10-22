package trady.com.backend.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.account.entity.Account;
import trady.com.backend.account.repository.AccountQueryRepository;
import trady.com.backend.global.exception.ResourceNotFoundException;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AccountQueryService {

    private final AccountQueryRepository accountQueryRepository;

    public Account findByMemberId(Long memberId) {
        return accountQueryRepository.findByMemberId(memberId)
                .orElseThrow(() ->
                        new ResourceNotFoundException( "회원에 연결된 계좌가 없습니다. memberId=" + memberId)
                );
    }
}
