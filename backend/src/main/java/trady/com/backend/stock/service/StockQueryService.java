package trady.com.backend.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.global.exception.ResourceNotFoundException;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class StockQueryService {
    private final StockQueryRepository stockQueryRepository;

    public Stock findByStockCode(String stockCode) {
        return stockQueryRepository.findByCode(stockCode)
                .orElseThrow(() ->
                        new ResourceNotFoundException("종목을 찾을 수 없습니다. stockCode = " + stockCode)
                );
    }
}
