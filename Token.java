public class Token {
    private int row;
    private String tokenCode;
    private String tokenValue;

    public Token(int row, String code, String value) {
        this.row = row;
        this.tokenCode = code;
        this.tokenValue = value;
    }

    public int getRow() {
        return row;
    }

    public String getTokenCode() {
        return tokenCode;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    @Override
    public String toString() {
        return tokenCode + " " + tokenValue;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setTokenCode(String tokenCode) {
        this.tokenCode = tokenCode;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }
}
