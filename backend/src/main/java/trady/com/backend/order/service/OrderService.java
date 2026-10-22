package trady.com.backend.order.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.account.entity.Account;
import trady.com.backend.account.service.AccountQueryService;
import trady.com.backend.order.Enum.OrderType;
import trady.com.backend.order.Enum.Side;
import trady.com.backend.order.dto.OrderRequest;
import trady.com.backend.order.entity.Order;
import trady.com.backend.order.repository.OrderRepository;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.service.StockQueryService;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final AccountQueryService accountQueryService;
    private final StockQueryService stockQueryService;

    @Transactional
    public void buy(OrderRequest request, Long memberId) {
        Account findAccount = accountQueryService.findByMemberId(memberId);
        Stock findStock = stockQueryService.findByStockCode(request.stockCode());
        orderRepository.save(Order.builder()
                        .account(findAccount)
                        .stock(findStock)
                        .side(Side.BUY)
                        .type(OrderType.values()[request.type()])
                        .price(request.price())
                        .quantity(request.quantity())
                .build());
    }

    @Transactional
    public void sell(OrderRequest request, Long memberId) {
        Account findAccount = accountQueryService.findByMemberId(memberId);
        Stock findStock = stockQueryService.findByStockCode(request.stockCode());
        orderRepository.save(Order.builder()
                .account(findAccount)
                .stock(findStock)
                .side(Side.SELL)
                .type(OrderType.values()[request.type()])
                .price(request.price())
                .quantity(request.quantity())
                .build());
    }

}
