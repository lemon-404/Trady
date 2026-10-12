package trady.com.backend.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;

    @Transactional
    public void savePage(List<Stock> incomingStocks) {
        if (incomingStocks.isEmpty()) return;

        List<String> codes = incomingStocks.stream()
                .map(Stock::getCode)
                .toList();
        List<Stock> existingStocks = stockRepository.findAllByCodeIn(codes);

        // 교집합: 기존 엔티티의 이름·시장만 변경한다. ID와 생성 시각은 유지한다.
        for (Stock existing : existingStocks) {
            incomingStocks.stream()
                    .filter(incoming -> incoming.getCode().equals(existing.getCode()))
                    .findFirst()
                    .filter(incoming -> !existing.hasSameInfo(incoming))
                    .ifPresent(existing::updateInfo);
        }

        // 차집합: DB에 같은 종목 코드가 없는 종목만 INSERT한다.
        List<Stock> newStocks = incomingStocks.stream()
                .filter(incoming -> existingStocks.stream()
                        .noneMatch(existing -> existing.getCode().equals(incoming.getCode())))
                .toList();
        if (!newStocks.isEmpty()) {
            stockRepository.saveAll(newStocks);
        }
    }
}
