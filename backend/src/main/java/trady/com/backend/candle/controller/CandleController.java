package trady.com.backend.candle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.global.response.TradyResponse;


@RestController
@RequestMapping("/trady/v1/stock/candle")
@RequiredArgsConstructor
public class CandleController {

    private final CandleQueryService service;

    @PostMapping(headers = "cont-yn=N")
    public ResponseEntity<TradyResponse<CandleContinuousQueryResponse>> getInitialCandles(
            @Valid @RequestBody CandleContinuousQueryRequest request
    ) {
        CandleContinuousQueryResponse response = service.getCandles(request);
        return ResponseEntity.ok(TradyResponse.success("차트 조회 성공", response));
    }

    @PostMapping(headers = "cont-yn=Y")
    public ResponseEntity<TradyResponse<CandleContinuousQueryResponse>> getContinuousCandles(
            @Valid @RequestBody CandleContinuousQueryRequest request
    ) {
        CandleContinuousQueryResponse response = service.getContinuousCandles(request);
        return ResponseEntity.ok(TradyResponse.success("차트 조회 성공", response));
    }


}
