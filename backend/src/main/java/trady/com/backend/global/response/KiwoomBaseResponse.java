package trady.com.backend.global.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public abstract class KiwoomBaseResponse {

    @JsonProperty("return_code")
    private Integer returnCode;

    @JsonProperty("return_msg")
    private String returnMsg;
}
