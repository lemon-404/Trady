package trady.com.backend.global.kiwoom.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KiwoomErrorCodeExtractor {

    private static final Pattern ERROR_CODE_PATTERN =
            Pattern.compile("\\[(\\d+):");

    private KiwoomErrorCodeExtractor() {
    }

    public static String extract(String message) {
        if (message == null) {
            return null;
        }

        Matcher matcher = ERROR_CODE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}

