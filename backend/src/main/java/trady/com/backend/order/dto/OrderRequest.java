package trady.com.backend.order.dto;

import jakarta.validation.constraints.*;


public record OrderRequest(
        @NotBlank(message = "종목 코드는 필수입니다.")
        String stockCode,

        @NotNull(message = "주문 유형은 필수입니다.")
        @Min(value = 0, message = "주문 유형은 0 또는 1이어야 합니다.")
        @Max(value = 1, message = "주문 유형은 0 또는 1이어야 합니다.")
        Integer type,

        @NotNull(message = "주문 가격은 필수입니다.")
        @Positive(message = "주문 가격은 0보다 커야 합니다.")
        Long price,

        @NotNull(message = "주문 수량은 필수입니다.")
        @Positive(message = "주문 수량은 1 이상이어야 합니다.")
        Long quantity
) {
}