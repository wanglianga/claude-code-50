package cn.nightpharmacy.web;

/** 业务异常：消息直接返回给前端。 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(String message) {
        this(400, message);
    }

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
