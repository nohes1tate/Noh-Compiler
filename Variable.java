public class Variable {
    private String type; //包含 var,num,str,array,func五种
    private String name;
    private int num;
    private Variable var = null;

    private int curReg = -1;

    private boolean symbolKind = false;
    private Symbol symbol; //当Variable在符号表中时对symbol进行保存

    public SymbolNode scope; //Symbol所在的符号表

    public Variable(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public Variable (String type, int num) {//类型为num
        this.type = type;
        this.num = num;
    }

    //访问数组时，var为数组下标
    public Variable(String type, String name, Variable var) {
        this.type = type;
        this.name = name;
        this.var = var;
    }

    public void setScope(SymbolNode scope) {
        this.scope = scope;
    }

    public SymbolNode getScope() {
        return scope;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getNum() {
        return num;
    }

    public int getCurReg() {
        return curReg;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    //访问数组使用
    public Variable getVar() {
        return var;
    }

    public void setVar(Variable var) {
        this.var = var;
    }

    public void setCurReg(int curReg) {
        this.curReg = curReg;
    }

    public boolean getSymbolKind() {
        return symbolKind;
    }

    public void setSymbolKind(boolean symbolKind) {
        this.symbolKind = symbolKind;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        if (type.equals("var") || type.equals("str") || type.equals("null")) {
            return name;

        } else if (type.equals("array")) {
            if (var != null) {
                return name + "[" + var.toString() + "]";

            } else {
                return name;
            }
        }
        return String.valueOf(num);
    }
}
