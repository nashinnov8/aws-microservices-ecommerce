package response;

import java.time.Instant;

public class ApiResponse<T> {
    private final String code;
    private final String message;
    private final T data;
    private final Instant timestamp;
    private final String traceId;

    public ApiResponse(boolean success, String code, String message, T data, Instant timestamp, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> success(String code, String message, T data, String traceId) {
        return new ApiResponse<>(true, code, message, data, Instant.now(), traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(false, code, message, null, Instant.now(), traceId);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCode() {
        return code;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }
}
