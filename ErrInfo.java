public class ErrInfo {
    private int row;
    private String type;

    public ErrInfo(int row, String type) {
        this.row = row;
        this.type = type;
    }

    public String toString() {
        return row + " " + type;
    }

    public int getRow() {
        return row;
    }

    public String getType() {
        return type;
    }
}
