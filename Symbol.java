import java.util.ArrayList;

public class Symbol {
    private String name; //符号名
    private SymbolType type; //符号类型
    private int dimension; //维数,-1表示void(void函数返回值),0表示int,1表示一维数组,2表示二维数组
    private int row = -1;
    private int dimen1 = -1; //一维数组长度
    private int dimen2 = -1; //二维数组长度
    private int addr; //符号地址
    private int value; //符号值
    private ArrayList<Integer> valueList = new ArrayList<>(); //符号值列表
    private boolean isTemp = false; //是否是临时变量
    private boolean isGlobal = false; //是否是全局量

    private int IRIndex = -1; //IR的index

    private ArrayList<Symbol> paraList; //参数列表

    private SymbolNode scope; //Symbol所在的符号表

//    mips翻译用
    private final int spBaseHex = 0x7fffeffc;
    private int addrOffset;
    private int curReg = -1;

    public boolean isConst() {
        return type.equals(SymbolType.Const) && dimension==0;//todo 存疑
    }

    public Symbol(String name, SymbolType type, int dimension, boolean isGlobal) {
        this.name = name;
        this.type = type;
        this.dimension = dimension;
        this.paraList = new ArrayList<>();
        this.isGlobal = isGlobal;
    }

    public int getArrValue(int index) {
        return valueList.get(index);
    }

    public int getSpBaseHex() {
        return spBaseHex;
    }

    public void setAddrOffset(int addrOffset) {
        this.addrOffset = addrOffset;
    }

    public int getAddrOffset() {
        return addrOffset;
    }

    public void increaseAddrOffset(int num) {
        addrOffset+=num;
    }

    public void decreaseAddrOffset(int num) {
        addrOffset-=num;
    }

    public void setCurReg(int curReg) {
        this.curReg = curReg;
    }

    public int getCurReg() {
        return curReg;
    }

    public int getArrValue(int index1, int index2) {
        return valueList.get(index1 * dimen2 + index2);
    }

    public void setValueList(ArrayList<Integer> valueList) {
        this.valueList = valueList;
    }

    public ArrayList<Integer> getValueList() {
        return valueList;
    }

    public void setIRIndex(int IRIndex) {
        this.IRIndex = IRIndex;
    }

    public int getIRIndex() {
        return IRIndex;
    }

    public String getName() {
        return name;
    }

    public ArrayList<Symbol> getParaList() {
        return paraList;
    }

    public void setParaList(ArrayList<Symbol> paraList) {
        this.paraList = paraList;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getRow() {
        return row;
    }

    public void setAddr(int addr) {
        this.addr = addr;
    }

    public int getAddr() {
        return addr;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setDimen1(int dimen1) {
        this.dimen1 = dimen1;
    }

    public int getDimen1() {
        return dimen1;
    }

    public void setDimen2(int dimen2) {
        this.dimen2 = dimen2;
    }

    public int getDimen2() {
        return dimen2;
    }

    public int getDimension() {
        return dimension;
    }

    public void addPara(Symbol para) {
        paraList.add(para);
    }

    public SymbolType getType() {
        return type;
    }

    public void setTemp(boolean temp) {
        isTemp = temp;
    }

    public boolean isTemp() {
        return isTemp;
    }

    public void setGlobal(boolean global) {
        isGlobal = global;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public boolean varNameInParaList(String varName) {
        if (paraList == null) {
            return false;
        }
        for (Symbol para : paraList) {
            if (para.getName().equals(varName)) {
                return true;
            }
        }
        return false;
    }

    public int getParaIndex(String varName) {
        if (paraList == null) {
            return -1;
        }
        for (int i = 0; i < paraList.size(); i++) {
            if (paraList.get(i).getName().equals(varName)) {
                return i + 1;
            }
        }
        return -1;
    }

    public int getParaNum() {
        return paraList.size();
    }
}
