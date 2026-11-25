-- 읽기 전용 점검. 실제 사용 중인 DB·스키마에서 실행한다.
SELECT "interval", count(*) AS candle_count
FROM stock_candle
GROUP BY "interval"
ORDER BY "interval";

-- 아래 결과는 없어야 한다. 분봉 숫자 표기는 다음 단계에서 변환한다.
SELECT "interval", count(*) AS unsupported_count
FROM stock_candle
WHERE "interval" IS NULL
   OR "interval" NOT IN ('1', '5', '15', '30', '60', '1m', '5m', '15m', '30m', '60m', '1d', '1w', '1M', '1y')
GROUP BY "interval";

-- 같은 종목·시각의 기존/표준 코드 중복과 OHLCV 충돌을 확인한다.
WITH mapping(legacy_code, standard_code) AS (
    VALUES ('1', '1m'), ('5', '5m'), ('15', '15m'), ('30', '30m'), ('60', '60m')
)
SELECT legacy.stock_id, legacy.candle_time, m.legacy_code, m.standard_code,
       legacy.stock_candle_id AS legacy_id, canonical.stock_candle_id AS standard_id,
       legacy.open_price AS legacy_open, canonical.open_price AS standard_open,
       legacy.high_price AS legacy_high, canonical.high_price AS standard_high,
       legacy.low_price AS legacy_low, canonical.low_price AS standard_low,
       legacy.close_price AS legacy_close, canonical.close_price AS standard_close,
       legacy.volume AS legacy_volume, canonical.volume AS standard_volume,
       ROW(legacy.open_price, legacy.high_price, legacy.low_price, legacy.close_price, legacy.volume)
           IS DISTINCT FROM
       ROW(canonical.open_price, canonical.high_price, canonical.low_price, canonical.close_price, canonical.volume)
           AS ohlcv_conflict
FROM stock_candle legacy
JOIN mapping m ON legacy."interval" = m.legacy_code
JOIN stock_candle canonical
  ON canonical.stock_id = legacy.stock_id
 AND canonical.candle_time = legacy.candle_time
 AND canonical."interval" = m.standard_code
ORDER BY legacy.stock_id, m.standard_code, legacy.candle_time;
