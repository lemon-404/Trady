-- PostgreSQL 전용 수동 변환. 수집·조회 애플리케이션 중지 후 전체 파일을 한 번에 실행한다.
BEGIN;
LOCK TABLE stock_candle IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE candle_interval_mapping ON COMMIT DROP AS
SELECT * FROM (VALUES ('1', '1m'), ('5', '5m'), ('15', '15m'), ('30', '30m'), ('60', '60m'))
    AS mapping(legacy_code, standard_code);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM stock_candle
        WHERE "interval" IS NULL
           OR "interval" NOT IN ('1', '5', '15', '30', '60', '1m', '5m', '15m', '30m', '60m', '1d', '1w', '1M', '1y')
    ) THEN
        RAISE EXCEPTION '미지원 봉 단위가 있습니다. 01-preflight.sql 결과를 확인하세요.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stock_candle legacy
        JOIN candle_interval_mapping m ON legacy."interval" = m.legacy_code
        JOIN stock_candle canonical
          ON canonical.stock_id = legacy.stock_id
         AND canonical.candle_time = legacy.candle_time
         AND canonical."interval" = m.standard_code
        WHERE ROW(legacy.open_price, legacy.high_price, legacy.low_price, legacy.close_price, legacy.volume)
                  IS DISTINCT FROM
              ROW(canonical.open_price, canonical.high_price, canonical.low_price, canonical.close_price, canonical.volume)
    ) THEN
        RAISE EXCEPTION '기존/표준 코드의 OHLCV가 충돌합니다. 자동 병합하지 않습니다. 01-preflight.sql 결과를 확인하세요.';
    END IF;
END $$;

-- 동일 OHLCV 중복은 표준 코드 행을 유지한다. 충돌 검사는 삭제 전에 완료된다.
DELETE FROM stock_candle legacy
USING stock_candle canonical, candle_interval_mapping m
WHERE legacy."interval" = m.legacy_code
  AND canonical."interval" = m.standard_code
  AND canonical.stock_id = legacy.stock_id
  AND canonical.candle_time = legacy.candle_time;

UPDATE stock_candle c
SET "interval" = m.standard_code
FROM candle_interval_mapping m
WHERE c."interval" = m.legacy_code;

-- 이전 코드나 수동 입력으로 숫자 표기가 재유입되는 것을 방지한다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'stock_candle'::regclass
          AND conname = 'ck_stock_candle_interval_standard'
    ) THEN
        ALTER TABLE stock_candle ADD CONSTRAINT ck_stock_candle_interval_standard
            CHECK ("interval" IN ('1m', '5m', '15m', '30m', '60m', '1d', '1w', '1M', '1y'));
    END IF;
END $$;

COMMIT;
