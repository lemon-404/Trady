package trady.com.backend.order.Enum;

public enum OrderStatus {
    ACCEPTED,         // 접수: 미체결
    PARTIALLY_FILLED, // 부분 체결
    FILLED,           // 전량 체결
    CANCELLED         // 취소
}
