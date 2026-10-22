package trady.com.backend.candle.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.service.CandleQueryServiceV1;


@RestController
@RequestMapping("v1/stock/candle")
@RequiredArgsConstructor
public class CandleController {

    private final CandleQueryServiceV1 service;

    @PostMapping(headers = "cont-yn=N")
    public ResponseEntity<CandleContinuousQueryResponse> getInitialCandles(
            @RequestBody CandleContinuousQueryRequest request
    ) {
        CandleContinuousQueryResponse response = service.getInitialCandles(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(headers = "cont-yn=Y")
    public ResponseEntity<CandleContinuousQueryResponse> getContinuousCandles(
            @RequestBody CandleContinuousQueryRequest request
    ) {
        CandleContinuousQueryResponse response = service.getContinuousCandles(request);
        return ResponseEntity.ok(response);
    }


}
