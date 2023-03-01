import java.util.ArrayList;

public class SymbolNode {
    private SymbolNode parent;
    private ArrayList<Symbol> symbols = new ArrayList<>();
    private ArrayList<SymbolNode> children = new ArrayList<>();
    private boolean isFunc = false;
    private boolean isGlobal = false;
    private int index = 0;
    private boolean isWhile = false;

    private int curSymIndex = 0;//中间代码翻译时使用，寻找符号表时防止提前找到后面定义的符号

//    mips翻译用
    private int inBlockOffset = 0;
    ScopeType type;

    public void increaseCurSymIndex() {
        curSymIndex++;
    }

    public void setInBlockOffset (int blockOffset) {
        inBlockOffset = blockOffset;
    }

    public boolean isWhile() {
        return isWhile;
    }

    public void setWhile(boolean aWhile) {
        isWhile = aWhile;
    }

    public int getInBlockOffset() {
        return inBlockOffset;
    }

    public void increaseBlockOffset(int num) {
        inBlockOffset+=num;
    }

    public void setType (ScopeType type) {
        this.type = type;
    }

    public ScopeType getType() {
        return type;
    }

    public SymbolNode(SymbolNode parent) {
        this.parent = parent;
        parent.addChild(this);
    }

    public SymbolNode() {
        this.parent = null;
    }

    public void addSymbol(Symbol symbol, ErrorHandler errorHandler) {
        errorHandler.handleErrorB(this, symbol);
        symbols.add(symbol);
    }

    public Symbol searchSymbolInIr(String name) {
        SymbolNode tree = this;
        int i;
        while (tree != null) {
            for (i=0; i<tree.getSymbols().size(); i++) {
                if (i >= tree.curSymIndex) {
                    break;
                }
                if (tree.getSymbols().get(i).getName().equals(name)) {
                    return tree.getSymbols().get(i);
                }
            }
            i = 0;
            tree = tree.parent;
        }
        return null;
    }

    public Symbol searchSymbol(String name) {
        SymbolNode tree = this;
        while (tree != null) {
            for (Symbol symbol : tree.getSymbols()) {
                if (symbol.getName().equals(name)) {
                    return symbol;
                }
            }
            tree = tree.parent;
        }
        return null;
    }


    public Symbol searchSymbol(String name, ErrorHandler errorHandler, int row, boolean handleError) {
        SymbolNode tree = this;
        for (Symbol tmpSym : tree.getSymbols()) {
            if (tmpSym.getName().equals(name)) {
                return tmpSym;
            }
        }
        while (tree.parent!= null) {
            tree = tree.parent;
            for (Symbol tmpSym : tree.getSymbols()) {
                if (tmpSym.getName().equals(name)) {
                    return tmpSym;
                }
            }
        }
        if (handleError) {
            //System.out.println("cant find symbol " + name + " at row " + row);
            errorHandler.handleErrorC(row);
        }
        return null;
    }

    public void addChild(SymbolNode child) {
        children.add(child);
    }

    public ArrayList<SymbolNode> getChildren() {
        return children;
    }

    public ArrayList<Symbol> getSymbols() {
        return symbols;
    }

    public Symbol getEndSymbol() {
        return getSymbols().get(getSymbols().size()-1);
    }

    public SymbolNode getParent() {
        return parent;
    }

    public boolean isFunc() {
        return isFunc;
    }

    public void setFunc(boolean isFunc) {
        this.isFunc = isFunc;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public void setGlobal  (boolean isGlobal) {
        this.isGlobal = isGlobal;
    }

    public SymbolNode getNextChild() {
        if (index < children.size()) {
            SymbolNode ret = children.get(index);
            index++;
            return ret;
        }
        return null;
    }

}
