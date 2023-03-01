import java.util.ArrayList;

public class IRCode {
    private String IRType; //中间代码类型
    private String IRStr; //用于输出的ircode字符串

    private String labelString; //label的字符串
    private String kind; //变量类型
    private String name; //变量名
    private int num; //常量值

    private String op;
    private Variable src1;
    private Variable src2;
    private Variable dst;

    private SymbolNode scope; //作用域
    private boolean isGlobal; //是否是全局变量
    private boolean isInit = false; //是否已经初始化
    private ArrayList<Integer> initList = new ArrayList<>(); //常量数组的初始化值List
    private boolean isVoidReturn = false; //是否是void类型的函数

    private Variable var; //使用var的表达式

    //    private Symbol symbol; //使用的symbol
    private int dim1; //数组第一维的大小
    private int dim2; //数组第二维的大小

    private Symbol symbol; //保存symbol

    private String branchStr; //跳转类型
    private String jumpLabel; //跳转位置

    //优化接口
    public boolean releaseDest = false;

    public IRCode(String type, Variable dst, Variable src1) {
        this.IRType = type;
        this.dst = dst;
        this.src1 = src1;
    }

    public void releaseDest() {
        releaseDest = true;
    }

    public void occupyDest() {
        releaseDest = false;
    }

    public boolean isVoidReturn() {
        return isVoidReturn;
    }

    public void setVoidReturn(boolean voidReturn) {
        isVoidReturn = voidReturn;
    }

    public SymbolNode getScope() {
        return scope;
    }

    public void setScope(SymbolNode scope) {
        this.scope = scope;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public void setGlobal(boolean global) {
        isGlobal = global;
    }

    public boolean isInit() {
        return isInit;
    }

    public boolean setInit(boolean init) {
        isInit = init;
        return isInit;
    }

    //跳转处理部分
    public IRCode(String IRType, String branchStr, String JumpLabel, Variable src1, Variable src2) {
        this.IRType = IRType;
        this.branchStr = branchStr;
        this.jumpLabel = JumpLabel;
        this.src1 = src1;
        this.src2 = src2;
    }

    //包含两个操作数的运算
    public IRCode(String IRType, String op, Variable dst, Variable src1, Variable src2) {
        this.IRType = IRType;
        this.op = op;
        this.src1 = src1;
        this.src2 = src2;
        this.dst = dst;
    }

    //包含一个操作数的运算
    public IRCode(String IRType, String op, Variable dst, Variable src1) {
        this.IRType = IRType;
        this.op = op;
        this.src1 = src1;
        this.dst = dst;
    }

    //定义运算
    public IRCode(String IRType, String op, Variable dst) {
        this.IRType = IRType;
        this.op = op;
        this.dst = dst;
    }

    //包含注释或生成标签的中间代码
    public IRCode(String type, String labelString) {
        this.IRType = type;
        this.labelString = labelString;
        this.jumpLabel = labelString;
    }

    //生成print
    public IRCode(String type, Variable var) {
        this.IRType = type;
        this.var = var;
    }

    //生成变量声明和生成函数声明
    public IRCode(String type, String kind, String name) {
        this.IRType = type;
        this.kind = kind;
        this.name = name;
    }

    //const变量初始化
    public IRCode(String type, String kind, String name, int num) {
        this.IRType = type;
        this.kind = kind;
        this.name = name;
        this.num = num;
    }

    //变量初始化，包含数组大小，dim2为0时表示一维数组，dim1和dim2均为0时为变量
    public IRCode(String type, String name, int dim1, int dim2) {
        this.IRType = type;
        this.name = name;
        this.dim1 = dim1;
        this.dim2 = dim2;
    }

    public String getIRType() {
        return IRType;
    }

    public String getIRStr() {
        return IRStr;
    }

    public String getLabelString() {
        return labelString;
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public String getOp() {
        return op;
    }

    public Variable getSrc1() {
        return src1;
    }

    public Variable getSrc2() {
        return src2;
    }

    public Variable getDst() {
        return dst;
    }

    public Variable getVar() {
        return var;
    }

    public void setVar(Variable var) {
        this.var = var;
    }

    public int getDim1() {
        return dim1;
    }

    public void setDim1(int dim1) {
        this.dim1 = dim1;
    }

    public int getDim2() {
        return dim2;
    }

    public void setDim2(int dim2) {
        this.dim2 = dim2;
    }

    public String getBranchStr() {
        return branchStr;
    }

    public String getJumpLabel() {
        return jumpLabel;
    }

    public ArrayList<Integer> getInitList() {
        return initList;
    }

    public void setInitList(ArrayList<Integer> initList) {
        this.initList = initList;
    }

    public String initArrToString() {
        String res = "";
        for (int i = 0; i < initList.size(); i++) {
            res += initList.get(i);
            if (i != initList.size() - 1) {
                res += ",";
            }
        }
        return res;
    }

    public int calDim() {
        if (dim2 == 0) {
            return dim1;
        }
        return dim1 * dim2;
    }

    public void generateIRStr() {
        switch (IRType) {
            case "intDecl":
                if (isInit) {
                    IRStr = "int " + name + " = " + num;
                } else {
                    IRStr = "int " + name;
                }
                break;
            case "assign_ret":
                IRStr = var.toString() + " = RET";
                break;
            case "assign":
                IRStr = dst.toString() + " = " + src1.toString() + " " + op + " " + src2.toString();
                break;
            case "assign2":
                IRStr = dst.toString() + " = " + src1.toString();
                break;
            case "push":
            case "print":
            case "getint":
                IRStr = getIRType() + " " + var.toString();
                break;
            case "return":
                if (isVoidReturn) {
                    IRStr = "ret";
                } else {
                    IRStr = "ret " + var.toString();
                }
                break;
            case "call":
                IRStr = IRType + " " + labelString;
                break;
            case "arrayDecl":
                IRStr = "arr int " + name + "[" + dim1 + "]";
                if (dim2 != 0) {
                    IRStr += "[" + dim2 + "]";
                }
                if (isInit) {
                    IRStr += " = {" + initArrToString() + "}";
                }
                break;
            case "funcDecl":
                IRStr = kind + " " + name + "()";
                break;
            case "note":
            case "label":
            case "funcPara":
                IRStr = labelString;
                break;
            case "jump":
                IRStr = "jump " + jumpLabel;
                break;
            case "branch":
                IRStr = branchStr + " " + src1.toString() + ", " + src2.toString() + ", " + jumpLabel;
                break;
            case "setcmp":
                IRStr = op + " " + dst.toString() + ", " + src1.toString() + ", " + src2.toString();
            default:
                break;
        }
    }
}
