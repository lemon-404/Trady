package trady.com.backend.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
//@AuthenticationPrincipal(expression = "memberId")  //security 적용 후 해제
public @interface CurrentMemberId {
}
