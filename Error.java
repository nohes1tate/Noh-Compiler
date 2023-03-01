public class Error {
    private ErrType errType;
    private String errCode;
    private String errInfo;
    public Error(ErrType errType, String errCode, String errInfo) {
        this.errType = errType;
        this.errCode = errCode;
        this.errInfo = errInfo;
    }

    public String getErrCode() {
        return errCode;
    }

}
