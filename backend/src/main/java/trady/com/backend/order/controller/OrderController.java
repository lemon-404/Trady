package trady.com.backend.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trady.com.backend.global.auth.CurrentMemberId;
import trady.com.backend.order.dto.OrderRequest;
import trady.com.backend.order.service.OrderService;

/*
    우선 지정가로만 구현
   TODO 주문 조회get도 만들기
 */

@RestController
@RequestMapping("/v1/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/buy")
    public ResponseEntity<?> buy(
            @Validated @RequestBody OrderRequest request,
            @CurrentMemberId Long memberId
            ) {
        orderService.buy(request, memberId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(
            @Validated @RequestBody OrderRequest request,
            @CurrentMemberId Long memberId
    ) {
        orderService.sell(request, memberId);
        return ResponseEntity.ok().build();
    }
}
