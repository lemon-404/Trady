package trady.com.backend.stock.Enum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum StockMarket {

    KOSPI("0", "코스피"),
    KOSDAQ("10", "코스닥"),
    K_OTC("30", "K-OTC"),
    KONEX("50", "코넥스"),
    ETN("60", "ETN"),
    LOSS_LIMIT_ETN("70", "손실제한 ETN"),
    GOLD_SPOT("80", "금현물"),
    VOLATILITY_ETN("90", "변동성 ETN"),
    INFRASTRUCTURE_FUND("2", "인프라투융자"),
    ELW("3", "ELW"),
    MUTUAL_FUND("4", "뮤추얼펀드"),
    SUBSCRIPTION_WARRANT("5", "신주인수권"),
    REIT("6", "리츠종목"),
    SUBSCRIPTION_RIGHT_CERTIFICATE("7", "신주인수권증서"),
    ETF("8", "ETF"),
    HIGH_YIELD_FUND("9", "하이일드펀드");

    private final String code;
    private final String name;

    public static StockMarket fromCode(String code) {
        return Arrays.stream(values())
                .filter(market -> market.getCode().equals(code))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("알 수 없는 시장 코드: " + code));
    }
}
