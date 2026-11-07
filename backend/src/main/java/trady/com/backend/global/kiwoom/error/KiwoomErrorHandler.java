package trady.com.backend.global.kiwoom.error;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * 키움 API 호출 실패 정보를 보관하는 예외.
 *
 * <p>returnCode는 응답의 return_code 값으로, "0"은 성공이고 그 외는 실패다.
 * errorCode는 return_msg의 "[숫자:" 패턴에서 추출한 상세 오류코드이며,
 * 해당 패턴이 없으면 null이다.
 */
@Component
@RequiredArgsConstructor
public class KiwoomErrorHandler {

    private final ObjectMapper objectMapper;

    public KiwoomApiException convert(WebClientResponseException cause) {
        KiwoomErrorResponse body;

        try {
            body = objectMapper.readValue(
                    cause.getResponseBodyAsString(),
                    KiwoomErrorResponse.class
            );
        } catch (Exception parsingFailure) {
            // HTML, 빈 본문 등 파싱할 수 없는 응답도 원래 HTTP 오류는 유지
            return new KiwoomApiException(
                    null,
                    null,
                    "키움 HTTP 오류: " + cause.getStatusCode(),
                    cause
            );
        }

        if (body == null) {
            return new KiwoomApiException(
                    null,
                    null,
                    "키움 HTTP 오류: " + cause.getStatusCode(),
                    cause
            );
        }

        return createException(String.valueOf(body.returnCode()), body.returnMsg(), cause);
    }

    // HTTP 200으로 전달된 업무 오류 검사
    public void validate(String returnCode, String returnMsg) {
        if (returnCode == null) {
            throw new KiwoomApiException(
                    null, null, "키움 응답에 return_code가 없습니다.", null
            );
        }

        if (!returnCode.equals("0")) {
            throw createException(returnCode, returnMsg, null);
        }
    }

    private KiwoomApiException createException(
            String returnCode,
            String returnMsg,
            Throwable cause
    ) {
        String errorCode = KiwoomErrorCodeExtractor.extract(returnMsg);
        String message = returnMsg == null || returnMsg.isBlank()
                ? "키움 API 오류"
                : returnMsg;

        return new KiwoomApiException(
                returnCode, errorCode, message, cause
        );
    }
}
