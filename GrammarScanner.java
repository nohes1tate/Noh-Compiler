import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

public class GrammarScanner {
    private int index;
    private ASTNode astRoot;
    private Token curSym;
    private final ArrayList<Token> tokenList;
    private final ErrorHandler errorHandler = new ErrorHandler();

    private boolean noNeedOpenScope = false;

    private final SymbolNode symbolTable = new SymbolNode();
    private SymbolNode curSymbolTable = symbolTable;

    private int cycleDepth = 0;

    private Symbol curFunc = null;
    private boolean funcReturned = false;
    private boolean lastIsReturn = false;
    private final Stack<Boolean> funcReturnedStack = new Stack<>();

    private ArrayList<Symbol> tempSymbolList = new ArrayList<>();

    private static final String outDir = "output.txt";

    private StringBuffer buffer;
    FileWriter writer;

    public SymbolNode getSymbolTable() {
        return symbolTable;
    }

    public ASTNode getAstRoot() {
        return astRoot;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public Token getCurSym() {
        return tokenList.get(index);
    }

    public Token getLastSym() {
        if (index > 0) {
            return tokenList.get(index - 1);
        }
        System.out.println("Unexpected getLastSym called");
        return null;
    }

    public GrammarScanner(ArrayList<Token> tokenList) {
        index = 0;
        this.tokenList = tokenList;
        curSym = tokenList.get(0);
    }

    public void printRoot(boolean toFile) throws IOException {
        buffer = new StringBuffer();
        printNode(astRoot, toFile);
        if (toFile) {
            System.out.println(buffer);
        }
        writer = new FileWriter(outDir);
        writer.write(buffer.toString());
        writer.flush();
        writer.close();
    }

    private void printNode(ASTNode node, boolean toFile) {
        if (node.getToken() != null) {
            if (toFile) {
                System.out.println(node.getToken());
                buffer.append(node.getToken().toString()).append("\n");
            }
        } else {
            for (ASTNode child : node.getChildren()) {
                printNode(child, toFile);
            }
            if (isOutType(node.getType())) {
                if (toFile) {
                    System.out.println("<" + node.getType() + ">");
                    buffer.append("<").append(node.getType()).append(">").append("\n");
                }
            }
        }
    }

    public ASTNode createASTTree() {
        astRoot = CompUnit();
        return astRoot;
    }

    //<BlockItem>, <Decl>, <BType>不必输出
    //CompUnit → {Decl} {FuncDef} MainFuncDef
    private ASTNode CompUnit() {
        ASTNode node = new ASTNode("CompUnit");
        curSymbolTable.setGlobal(true);
        while ((symCheck("CONSTTK") && preCheck("INTTK", 1) && preCheck("IDENFR", 2))
                || (symCheck("INTTK") && preCheck("IDENFR", 1)) && !preCheck("LPARENT", 2)) {
            node.addChild(Decl());
        }
        while ((symCheck("VOIDTK") && preCheck("IDENFR", 1))
                || (symCheck("INTTK") && preCheck("IDENFR", 1))) {
            node.addChild(FuncDef());
        }
        node.addChild(MainFuncDef());

        return node;
    }

    //MainFuncDef → 'int' 'main' '(' ')' Block
    private ASTNode MainFuncDef() {
        ASTNode node = new ASTNode("MainFuncDef");
        if (symCheck("INTTK") && preCheck("MAINTK", 1) && preCheck("LPARENT", 2)
                && preCheck("RPARENT", 3)) {
            Symbol newSymbol = new Symbol("main", SymbolType.Func, 0, curSymbolTable.isGlobal());
            node.addChild(new ASTNode("INTTK", curSym));
            nextSym();
            node.addChild(new ASTNode("MAINTK", curSym));
            newSymbol.setRow(curSym.getRow());
            nextSym();
            node.addChild(new ASTNode("LPARENT", curSym));
            nextSym();
            if (symCheck("RPARENT"))
                node.addChild(new ASTNode("RPARENT", curSym));
            else {
                missingError("RPARENT", getLastSym().getRow());
            }
            nextSym();

            curFunc = newSymbol;
            curSymbolTable.addSymbol(newSymbol, errorHandler);
            curSymbolTable = new SymbolNode(curSymbolTable);
            node.addChild(Block());
            curSymbolTable = curSymbolTable.getParent();
        } else {
            System.out.println("MainFuncDef error");
        }
        if (!lastIsReturn) {
            errorHandler.handleErrorG(curSym.getRow());
        }
        return node;
    }

    //获取函数返回值类型
    private String getFuncRetType() {
        String retType = "";
        if (symCheck("INTTK")) {
            retType = "INTTK";
        } else if (symCheck("VOIDTK")) {
            retType = "VOIDTK";
        } else {
            System.out.println("getFuncRetType error");
        }
        return retType;
    }

    //获取标识符
    private String getIdentfr() {
        String identfr = "";
        if (symCheck("IDENFR")) {
            identfr = curSym.getTokenValue();
        } else {
            System.out.println("getIdentfr error");
        }
        return identfr;
    }

    //FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
    private ASTNode FuncDef() {
        ASTNode node = new ASTNode("FuncDef");
        ASTNode tmpNode;
        int funcDim;
        String funcName;
        String funcRetType;
        funcRetType = getFuncRetType();
        node.addChild(FuncType());

        funcName = getIdentfr();
        int symbolRow = curSym.getRow();
        ASTNode ident = Ident();

        if (funcRetType.equals("INTTK")) {
            ident.setDataType("Int");
            funcDim = 0;
        } else if (funcRetType.equals("VOIDTK")) {
            ident.setDataType("Func");
            funcDim = -1;
        } else {
            funcDim = -2;
            System.out.println("unexpected funcRetType in " + curSym + " at line " + curSym.getRow());
        }
        node.addChild(ident);

        Symbol funcSymbol = new Symbol(funcName, SymbolType.Func, funcDim, curSymbolTable.isGlobal());
        funcSymbol.setRow(symbolRow);
        curFunc = funcSymbol;
        curSymbolTable.addSymbol(funcSymbol, errorHandler);
        curSymbolTable.setFunc(true);

        curSymbolTable = new SymbolNode(curSymbolTable);

        if (symCheck("LPARENT")) {
            addTK(node, "LPARENT", curSym);
        } else {
            System.out.println("缺少左括号");
        }
        if (symCheck("INTTK")) {
            tempSymbolList = new ArrayList<>();
            ASTNode funcFParams = FuncFParams();
            node.addChild(funcFParams);
            curSymbolTable.getParent().getEndSymbol().setParaList(tempSymbolList);
        }
        if (symCheck("RPARENT")) {
            addTK(node, "RPARENT", curSym);
        } else {
            missingError("RPARENT", getLastSym().getRow());
        }
        ASTNode body = Block();
        node.addChild(body);
        if (funcSymbol.getDimension() == 0 && !funcReturned) {
            errorHandler.handleErrorG(getLastSym().getRow());
        }
        funcReturned = false;
        curSymbolTable = curSymbolTable.getParent();
        return node;
    }

    //Block → '{' { BlockItem } '}' /
    private ASTNode Block() {
        ASTNode node = new ASTNode("Block");
        if (symCheck("LBRACE")) {
            addTK(node, "LBRACE", curSym);
            lastIsReturn = false;
        } else {
            System.out.println("缺少左大括号");
        }
        funcReturnedStack.push(false);
        while (!symCheck("RBRACE")) {
            node.addChild(BlockItem());
        }
        if (symCheck("RBRACE")) {
            addTK(node, "RBRACE", curSym);
        } else {
            System.out.println("缺少右大括号");
        }
        lastIsReturn = funcReturnedStack.pop();
        return node;
    }

    //BlockItem → Decl | Stmt
    private ASTNode BlockItem() {
        ASTNode node = new ASTNode("BlockItem");
        if (symCheck("CONSTTK") || symCheck("INTTK")) {
            node.addChild(Decl());
        } else {
            node.addChild(Stmt());
        }
        return node;
    }

    //Stmt → LVal '=' Exp ';' // 每种类型的语句都要覆盖
    //| [Exp] ';' //有无Exp两种情况
    //| Block
    //| 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // 1.有else 2.无else
    //| 'while' '(' Cond ')' Stmt
    //| 'break' ';' | 'continue' ';'
    //| 'return' [Exp] ';' // 1.有Exp 2.无Exp
    //| LVal '=' 'getint''('')'';'
    //| 'printf''('FormatString{','Exp}')'';' // 1.有Exp 2.无Exp
    private ASTNode Stmt() {
        ASTNode node = new ASTNode("Stmt");
        if (symCheck("IFTK")) {
            addTK(node, "IFTK", curSym);
            if (symCheck("LPARENT")) {
                addTK(node, "LPARENT", curSym);
            } else {
                System.out.println("缺少左括号");
            }
            node.addChild(Cond());
            if (symCheck("RPARENT")) {
                addTK(node, "RPARENT", curSym);
            } else {
                missingError("RPARENT", getLastSym().getRow());
            }
            curSymbolTable = new SymbolNode(curSymbolTable);
            noNeedOpenScope = true;
            node.addChild(Stmt());
            if (symCheck("ELSETK")) {
                curSymbolTable = curSymbolTable.getParent();
                noNeedOpenScope = false;
                addTK(node, "ELSETK", curSym);

                curSymbolTable = new SymbolNode(curSymbolTable);
                noNeedOpenScope = true;
                node.addChild(Stmt());
                curSymbolTable = curSymbolTable.getParent();
                noNeedOpenScope = false;
            } else {
                curSymbolTable = curSymbolTable.getParent();
                noNeedOpenScope = false;
            }
        } else if (symCheck("WHILETK")) {
            curSymbolTable.setWhile(true);
            cycleDepth++;
            addTK(node, "WHILETK", curSym);
            if (symCheck("LPARENT")) {
                addTK(node, "LPARENT", curSym);
            } else {
                System.out.println("缺少左括号");
            }
            ASTNode cond = Cond();
            node.addChild(cond);
            if (symCheck("RPARENT")) {
                addTK(node, "RPARENT", curSym);
            } else {
                missingError("RPARENT", getLastSym().getRow());
            }
            curSymbolTable = new SymbolNode(curSymbolTable);
            noNeedOpenScope = true;
            ASTNode stmt = Stmt();
            node.addChild(stmt);

            curSymbolTable = curSymbolTable.getParent();
            noNeedOpenScope = false;

            cycleDepth--;
        } else if (symCheck("BREAKTK")) {
            if (cycleDepth == 0) {
                errorHandler.handleErrorM(curSym.getRow());
            }
            addTK(node, "BREAKTK", curSym);
            if (symCheck("SEMICN")) {
                addTK(node, "SEMICN", curSym);
            } else {
                missingError("SEMICN", getLastSym().getRow());
            }
        } else if (symCheck("CONTINUETK")) {
            if (cycleDepth == 0) {
                errorHandler.handleErrorM(curSym.getRow());
            }
            addTK(node, "CONTINUETK", curSym);
            if (symCheck("SEMICN")) {
                addTK(node, "SEMICN", curSym);
            } else {
                missingError("SEMICN", getLastSym().getRow());
            }
        } else if (symCheck("RETURNTK")) {
            addTK(node, "RETURNTK", curSym);
            if (symCheck("SEMICN")) {
                addTK(node, "SEMICN", curSym);
            } else {
                if (curFunc.getDimension() == -1) {
                    errorHandler.handleErrorF(curSym.getRow());
                }
                ASTNode ret = Exp();
                node.addChild(ret);
                if (symCheck("SEMICN")) {
                    addTK(node, "SEMICN", curSym);
                } else {
                    missingError("SEMICN", getLastSym().getRow());
                }
                if (symCheck("RBRACE")) {
                    funcReturned = true;
                    funcReturnedStack.pop();
                    funcReturnedStack.push(true);
                }
            }
        } else if (symCheck("PRINTFTK")) {
            addTK(node, "PRINTFTK", curSym);
            if (symCheck("LPARENT")) {
                addTK(node, "LPARENT", curSym);
            } else {
                error("缺少左括号");
            }
            Token tmp = curSym;
            String curFormatString = tmp.getTokenValue();
            int cnt = curFormatString.split("%d").length - 1;
            int paraCnt = 0;
            ASTNode format = FormatString();
            node.addChild(format);
            ASTNode expList = new ASTNode("ExpList");
            while (symCheck("COMMA")) {
                addTK(node, "COMMA", curSym);
                ASTNode exp = Exp();
                node.addChild(exp);
                expList.addChild(exp);
                paraCnt++;
            }
            if (cnt != paraCnt) {
                errorHandler.handleErrorL(tmp.getRow());
            }
            if (symCheck("RPARENT")) {
                addTK(node, "RPARENT", curSym);
            } else {
                missingError("RPARENT", getLastSym().getRow());
            }
            if (symCheck("SEMICN")) {
                addTK(node, "SEMICN", curSym);
            } else {
                missingError("SEMICN", getLastSym().getRow());
            }
        } else if (symCheck("IDENFR")) {
            if (checkAssign()) {
                String curIdent = getIdentfr();
                Symbol leftVar = curSymbolTable.searchSymbol(curIdent, errorHandler, curSym.getRow(), false);
                if (leftVar != null) {
                    if (leftVar.getType() != SymbolType.Var) {
                        errorHandler.handleErrorH(curSym.getRow());
                    }
                }
                ASTNode lVal = LVal();
                node.addChild(lVal);
                if (symCheck("ASSIGN")) {
                    addTK(node, "ASSIGN", curSym);
                } else {
                    error("缺少赋值符号");
                }
                if (symCheck("GETINTTK")) {
                    addTK(node, "GETINTTK", curSym);
                    if (symCheck("LPARENT")) {
                        addTK(node, "LPARENT", curSym);
                    } else {
                        error("缺少左括号");
                    }
                    if (symCheck("RPARENT")) {
                        addTK(node, "RPARENT", curSym);
                    } else {
                        missingError("RPARENT", getLastSym().getRow());
                    }
                    if (symCheck("SEMICN")) {
                        addTK(node, "SEMICN", curSym);
                    } else {
                        missingError("SEMICN", getLastSym().getRow());
                    }
                } else {
                    if (symCheck("SEMICN")) {
                        addTK(node, "SEMICN", curSym);
                    } else {
                        ASTNode exp = Exp();
                        node.addChild(exp);
                        if (symCheck("SEMICN")) {
                            addTK(node, "SEMICN", curSym);
                        } else {
                            missingError("SEMICN", getLastSym().getRow());
                        }
                    }
                }
            } else {
                ASTNode exp = Exp();
                node.addChild(exp);
                if (symCheck("SEMICN")) {
                    addTK(node, "SEMICN", curSym);
                } else {
                    missingError("SEMICN", getLastSym().getRow());
                }
            }
        } else if (symCheck("LBRACE")) {

            boolean localNeedScope = true;
            if (noNeedOpenScope) {
                localNeedScope = false;
                noNeedOpenScope = false;
            }

            if (localNeedScope) {
                curSymbolTable = new SymbolNode(curSymbolTable);
            }

            ASTNode block = Block();
            node.addChild(block);

            if (localNeedScope) {
                curSymbolTable = curSymbolTable.getParent();
            }
        } else if (symCheck("SEMICN")) {
            addTK(node, "SEMICN", curSym);
        } else {
            ASTNode exp = Exp();
            node.addChild(exp);
            if (symCheck("SEMICN")) {
                addTK(node, "SEMICN", curSym);
            } else {
                missingError("SEMICN", getLastSym().getRow());
            }
        }
        return node;
    }

    //FormatString
    private ASTNode FormatString() {
        ASTNode node = new ASTNode("FormatString");
        if (symCheck("STRCON")) {
            errorHandler.handleErrorA(curSym);
            addTK(node, "STRCON", curSym);
        } else {
            error("缺少字符串");
        }
        return node;
    }

    //Cond → LOrExp
    private ASTNode Cond() {
        ASTNode node = new ASTNode("Cond");
        node.addChild(LOrExp());
        return node;
    }

    private ASTNode resumeOriTree(ASTNode node, String rootType, String leafType) {
        ASTNode newNode = new ASTNode(rootType);
        for (int i = 0; i < node.getChildren().size(); i++) {
            if (node.getChildren().get(i).getType().equals(leafType)) {
                newNode.addChild(node.getChildren().get(i));
            } else {
                ASTNode temp = new ASTNode(rootType);
                temp.addChild(newNode);
                temp.addChild(node.getChildren().get(i));
                newNode = temp;
            }
        }
        return newNode;
    }

    //LOrExp → LAndExp | LOrExp '||' LAndExp
    //改写后的LOrExp → LAndExp { '||' LAndExp }
    private ASTNode LOrExp() {
        ASTNode node = new ASTNode("LOrExp");
        node.addChild(LAndExp());
        while (symCheck("OR")) {
            addTK(node, "OR", curSym);
            node.addChild(LAndExp());
        }

        return resumeOriTree(node, "LOrExp", "LAndExp");
    }

    //LAndExp → EqExp | LAndExp '&&' EqExp
    //改写后的LAndExp → EqExp { '&&' EqExp }
    private ASTNode LAndExp() {
        ASTNode node = new ASTNode("LAndExp");
        node.addChild(EqExp());
        while (symCheck("AND")) {
            addTK(node, "AND", curSym);
            node.addChild(EqExp());
        }
        return resumeOriTree(node, "LAndExp", "EqExp");
    }

    // EqExp → RelExp | EqExp ('==' | '!=') RelExp
    //改写后的EqExp → RelExp { ('==' | '!=') RelExp }
    private ASTNode EqExp() {
        ASTNode node = new ASTNode("EqExp");
        node.addChild(RelExp());
        while (symCheck("EQL") || symCheck("NEQ")) {
            if (symCheck("EQL")) {
                addTK(node, "EQL", curSym);
            } else {
                addTK(node, "NEQ", curSym);
            }
            node.addChild(RelExp());
        }
        return resumeOriTree(node, "EqExp", "RelExp");
    }

    // RelExp → AddExp | RelExp ('<' | '<=' | '>' | '>=') AddExp
    //改写后的RelExp → AddExp { ('<' | '<=' | '>' | '>=') AddExp }
    private ASTNode RelExp() {
        ASTNode node = new ASTNode("RelExp");
        node.addChild(AddExp());
        while (symCheck("LSS") || symCheck("LEQ") || symCheck("GRE") || symCheck("GEQ")) {
            if (symCheck("LSS")) {
                addTK(node, "LSS", curSym);
            } else if (symCheck("LEQ")) {
                addTK(node, "LEQ", curSym);
            } else if (symCheck("GRE")) {
                addTK(node, "GRE", curSym);
            } else {
                addTK(node, "GEQ", curSym);
            }
            node.addChild(AddExp());
        }
        return resumeOriTree(node, "RelExp", "AddExp");
    }


    //FuncFParams → FuncFParam {',' FuncFParam}
    private ASTNode FuncFParams() {
        ASTNode node = new ASTNode("FuncFParams");
        node.addChild(FuncFParam());
        while (symCheck("COMMA")) {
            addTK(node, "COMMA", curSym);
            node.addChild(FuncFParam());
        }
        return node;
    }


    //FuncFParam → BType Ident ['[' ']' { '[' ConstExp ']' }]
    private ASTNode FuncFParam() {
        ASTNode node = new ASTNode("FuncFParam");
        node.addChild(BType());
        String paraName = getIdentfr();
        int symbolRow;
        symbolRow = curSym.getRow();
        int paraDim = 0;
        int dim2 = -1;
        node.setDataType("Int");
        node.addChild(Ident());
        if (symCheck("LBRACK")) {
            addTK(node, "LBRACK", curSym);
            node.setDataType("Array");
            if (symCheck("RBRACK")) {
                addTK(node, "RBRACK", curSym);
                paraDim++;
                if (symCheck("LBRACK")) {
                    addTK(node, "LBRACK", curSym);
                    ASTNode constExp = ConstExp();
                    node.addChild(constExp);
                    dim2 = calConstExp(constExp);
                    if (symCheck("RBRACK")) {
                        addTK(node, "RBRACK", curSym);
                        paraDim++;
                    } else {
                        missingError("RBRACK", getLastSym().getRow());
                    }
                }
            } else {
                missingError("RBRACK", getLastSym().getRow());
            }
        }
        Symbol symbol = new Symbol(paraName, SymbolType.Var, paraDim, curSymbolTable.isGlobal());
        symbol.setDimen2(dim2);
        symbol.setRow(symbolRow);
        tempSymbolList.add(symbol);
        curSymbolTable.addSymbol(symbol, errorHandler);
        curSymbolTable.getParent().getEndSymbol().addPara(symbol);
        return node;
    }

    //FuncType → 'void' | 'int' /
    private ASTNode FuncType() {
        ASTNode node = new ASTNode("FuncType");
        if (symCheck("VOIDTK")) {
            addTK(node, "VOIDTK", curSym);
        } else if (symCheck("INTTK")) {
            addTK(node, "INTTK", curSym);
        } else {
            System.out.println("缺少函数类型");
        }
        return node;
    }

    //Decl → ConstDecl | VarDecl // 覆盖两种声明
    private ASTNode Decl() {
        ASTNode node = new ASTNode("Decl");
        if (symCheck("CONSTTK")) {
            node.addChild(ConstDecl());
        } else if (symCheck("INTTK")) {
            node.addChild(VarDecl());
        }
        return node;
    }

    //VarDecl → BType VarDef { ',' VarDef } ';'
    private ASTNode VarDecl() {
        ASTNode node = new ASTNode("VarDecl");
        node.addChild(BType());
        node.addChild(VarDef());
        while (symCheck("COMMA")) {
            addTK(node, "COMMA", curSym);
            node.addChild(VarDef());
        }
        if (symCheck("SEMICN")) {
            addTK(node, "SEMICN", curSym);
        } else {
            missingError("SEMICN", getLastSym().getRow());
        }
        return node;
    }

    //VarDef → Ident { '[' ConstExp ']' } | Ident { '[' ConstExp ']' } '=' InitVal
    private ASTNode VarDef() {
        ASTNode node = new ASTNode("VarDef");
        node.setDataType("Int");
        String symbolName = getIdentfr();
        int symbolRow;
        symbolRow = curSym.getRow();
        int symbolDim = 0;
        int dim1 = -1;
        int dim2 = -1;
        ASTNode tmpNode;
        ASTNode ident = Ident();
        ident.setDataType("Int");
        node.addChild(ident);
        while (symCheck("LBRACK")) {
            node.setDataType("Array");
            addTK(node, "LBRACK", curSym);
            symbolDim++;
            tmpNode = ConstExp();
            node.addChild(tmpNode);
            if (symbolDim == 1) {
                dim1 = calConstExp(tmpNode);
            } else if (symbolDim == 2) {
                dim2 = calConstExp(tmpNode);
            } else {
                System.out.println("Unexpected var array with more than 2 dimensions");
            }
            if (symCheck("RBRACK")) {
                addTK(node, "RBRACK", curSym);
            } else {
                missingError("RBRACK", getLastSym().getRow());
            }
        }
        Symbol tmpSymbol = new Symbol(symbolName, SymbolType.Var, symbolDim, curSymbolTable.isGlobal());
        tmpSymbol.setDimen1(dim1);
        tmpSymbol.setDimen2(dim2);
        tmpSymbol.setRow(symbolRow);
        curSymbolTable.addSymbol(tmpSymbol, errorHandler);
        if (symCheck("ASSIGN")) {
            addTK(node, "ASSIGN", curSym);
            ASTNode initVal = InitVal();
            node.addChild(initVal);
        }
        return node;
    }

    //InitVal → Exp | '{' [ InitVal { ',' InitVal } ] '}'
    private ASTNode InitVal() {
        ASTNode node = new ASTNode("InitVal");
        if (symCheck("LBRACE")) {
            addTK(node, "LBRACE", curSym);
            if (symCheck("RBRACE")) {
                addTK(node, "RBRACE", curSym);
            } else {
                node.addChild(InitVal());
                while (symCheck("COMMA")) {
                    addTK(node, "COMMA", curSym);
                    node.addChild(InitVal());
                }
                if (symCheck("RBRACE")) {
                    addTK(node, "RBRACE", curSym);
                } else {
                    error("缺少右大括号");
                }
            }
        } else {
            node.addChild(Exp());
        }
        return node;
    }

    //ConstDecl → 'const' BType ConstDef {, ConstDef} ;
    private ASTNode ConstDecl() {
        ASTNode node = new ASTNode("ConstDecl");
        if (symCheck("CONSTTK")) {
            addTK(node, "CONSTTK", curSym);
            node.addChild(BType());
            node.addChild(ConstDef());
            while (symCheck("COMMA")) {
                addTK(node, "COMMA", curSym);
                node.addChild(ConstDef());
            }
            if (symCheck("SEMICN")) {
                addTK(node, "SEMICN", curSym);
            } else {
                missingError("SEMICN", getLastSym().getRow());
            }
        } else {
            error("缺少const");
        }
        return node;
    }

    //BType → 'int'
    private ASTNode BType() {
        ASTNode node = new ASTNode("BType");
        if (symCheck("INTTK")) {
            addTK(node, "INTTK", curSym);
        } else {
            error("缺少int");
        }
        return node;
    }

    //ConstDef → Ident { '[' ConstExp ']' } '=' ConstInitVal
    private ASTNode ConstDef() {
        ASTNode node = new ASTNode("ConstDef");
        node.setDataType("ConstInt");
        String symbolName = getIdentfr();
        int symbolRow;
        symbolRow = curSym.getRow();
        int symbolDim = 0;
        int dim1 = -1;
        int dim2 = -1;
        ASTNode tmpNode;
        ASTNode ident = Ident();
        ident.setDataType("ConstInt");
        node.addChild(ident);
        while (symCheck("LBRACK")) {
            node.setDataType("ConstArray");
            addTK(node, "LBRACK", curSym);
            symbolDim++;
            ident.setDataType("ConstArray");
            tmpNode = ConstExp();
            node.addChild(tmpNode);
            if (symbolDim == 1) {
                dim1 = calConstExp(tmpNode);
            } else if (symbolDim == 2) {
                dim2 = calConstExp(tmpNode);
            } else {
                System.out.println("Unexpected const array with more than 2 dimension");
            }
            if (symCheck("RBRACK")) {
                addTK(node, "RBRACK", curSym);
            } else {
                missingError("RBRACK", getLastSym().getRow());
            }
        }
        Symbol constSymbol = new Symbol(symbolName, SymbolType.Const, symbolDim, curSymbolTable.isGlobal());
        constSymbol.setDimen1(dim1);
        constSymbol.setDimen2(dim2);
        constSymbol.setRow(symbolRow);
        curSymbolTable.addSymbol(constSymbol, errorHandler);
        if (symCheck("ASSIGN")) {
            addTK(node, "ASSIGN", curSym);
            ASTNode constInitVal = ConstInitVal();
            node.addChild(constInitVal);
            ArrayList<Integer> constInitValList = getInitValList(constInitVal);
            if (symbolDim == 0) {
                constSymbol.setValue(constInitValList.get(0));
            } else {
                constSymbol.setValueList(constInitValList);
            }
        } else {
            error("缺少赋值符号");
        }
        return node;
    }

    //ConstInitVal → ConstExp | '{' ConstInitVal { ',' ConstInitVal } '}'
    private ArrayList<Integer> getInitValList(ASTNode initList) {
        ArrayList<Integer> res = new ArrayList<>();
        //遍历ConstInitVal的所有children直到找到ConstExp，将所有该ConstInitVal下的ConstExp的值加入res
        if (initList.getChildren().size() == 1) {
            res.add(initList.getChildren().get(0).getNum());
        } else {
            for (ASTNode child : initList.getChildren()) {
                res.addAll(getInitValList(child));
            }
        }
        return res;
    }

    //ConstInitVal → ConstExp | '{' ConstInitVal { ',' ConstInitVal } '}'
    private ASTNode ConstInitVal() {
        ASTNode node = new ASTNode("ConstInitVal");
        if (symCheck("LBRACE")) {
            addTK(node, "LBRACE", curSym);
            if (!symCheck("RBRACE"))
                node.addChild(ConstInitVal());
            while (symCheck("COMMA")) {
                addTK(node, "COMMA", curSym);
                node.addChild(ConstInitVal());
            }
            if (symCheck("RBRACE")) {
                addTK(node, "RBRACE", curSym);
            } else {
                error("缺少右大括号");
            }
        } else {
            node.addChild(ConstExp());
        }
        return node;
    }

    //Ident
    private ASTNode Ident() {
        ASTNode node = new ASTNode("Ident");
        if (symCheck("IDENFR")) {
            addTK(node, "IDENFR", curSym);
        } else {
            error("缺少标识符");
        }
        return node;
    }

    //ConstExp → AddExp
    private ASTNode ConstExp() {
        ASTNode node = new ASTNode("ConstExp");
        node.addChild(AddExp());
        node.setNum(calConstExp(node));
        return node;
    }


    private int calConstExp(ASTNode node) {
        return calAddExp(node.getChildren().get(0));
    }

    private int calAddExp(ASTNode node) {
        int result;
        if (node.getChildren().size() == 1) {
            result = calMulExp(node.getChildren().get(0));
        } else {
            int left = calAddExp(node.getChildren().get(0));
            int right = calMulExp(node.getChildren().get(2));
            if (node.getChildren().get(1).getToken().getTokenCode().equals("PLUS")) {
                result = left + right;
            } else {
                result = left - right;
            }
        }
        return result;
    }


    //AddExp → MulExp | AddExp ('+' | '−') MulExp
    //改写后:AddExp →]\  { ('+' | '−') MulExp
    private ASTNode AddExp() {
        ASTNode node = new ASTNode("AddExp");
        node.addChild(MulExp());
        while (symCheck("PLUS") || symCheck("MINU")) {
            if (symCheck("PLUS")) {
                addTK(node, "PLUS", curSym);
            } else {
                addTK(node, "MINU", curSym);
            }
            node.addChild(MulExp());
        }
        return resumeOriTree(node, "AddExp", "MulExp");
    }


    private int calMulExp(ASTNode node) {
        int result;
        if (node.getChildren().size() == 1) {
            result = calUnaryExp(node.getChildren().get(0));
        } else {
            int left = calMulExp(node.getChildren().get(0));
            int right = calUnaryExp(node.getChildren().get(2));
            if (node.getChildren().get(1).getToken().getTokenCode().equals("MULT")) {
                result = left * right;
            } else if (node.getChildren().get(1).getToken().getTokenCode().equals("DIV")) {
                result = left / right;
            } else {
                result = left % right;
            }
        }

        return result;
    }

    //MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    //改写后:MulExp → UnaryExp { ('*' | '/' | '%') UnaryExp }
    private ASTNode MulExp() {
        ASTNode node = new ASTNode("MulExp");
        node.addChild(UnaryExp());
        while (symCheck("MULT") || symCheck("DIV") || symCheck("MOD")) {
            if (symCheck("MULT")) {
                addTK(node, "MULT", curSym);
            } else if (symCheck("DIV")) {
                addTK(node, "DIV", curSym);
            } else {
                addTK(node, "MOD", curSym);
            }
            node.addChild(UnaryExp());
        }
        return resumeOriTree(node, "MulExp", "UnaryExp");
    }

    private int calUnaryExp(ASTNode node) {
        if (node.getChildren().size() == 1) {
            return calPrimaryExp(node.getChildren().get(0));
        } else {
            if (getUnaryOp(node.getChildren().get(0)).equals("PLUS")) {
                return calUnaryExp(node.getChildren().get(1));
            } else if (getUnaryOp(node.getChildren().get(0)).equals("MINU")) {
                return -calUnaryExp(node.getChildren().get(1));
            } else {
                return ~calUnaryExp(node.getChildren().get(1));
            }
        }
    }

    private String getUnaryOp(ASTNode node) {
        return node.getChildren().get(0).getToken().getTokenCode();
    }

    //UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
    private ASTNode UnaryExp() {
        ASTNode node = new ASTNode("UnaryExp");
        if (symCheck("IDENFR") && preCheck("LPARENT", 1)) {
            String symbolName = getIdentfr();
            node.setDataType("Func");
            Symbol funcSymbol = curSymbolTable.searchSymbol(symbolName, errorHandler, curSym.getRow(), true);
            int curRow = curSym.getRow();
            node.addChild(Ident());
            addTK(node, "LPARENT", curSym);
            if (symCheck("RPARENT")) {
                addTK(node, "RPARENT", curSym);
            } else if (symCheck("SEMICN")) {
                missingError("RPARENT", getLastSym().getRow());
            } else {
                tempSymbolList = new ArrayList<>();
                ASTNode funcRParams = FuncRParams();
                tempSymbolList = analyseFuncRParams(funcRParams);
                if (funcSymbol != null) {
                    if (funcSymbol.getType() != SymbolType.Func) {
                        System.out.println("第" + curRow + "行: " + symbolName + "不是函数");
                    } else if (funcSymbol.getParaList().size() != tempSymbolList.size()) {
                        System.out.println("第" + curRow + "行: " + symbolName + "参数个数不匹配");
                        errorHandler.handleErrorD(curRow);
                    } else {
                        errorHandler.handleErrorE(funcSymbol, tempSymbolList, curRow);
                    }
                }
                node.addChild(funcRParams);
                if (symCheck("RPARENT")) {
                    addTK(node, "RPARENT", curSym);
                } else {
                    missingError("RPARENT", getLastSym().getRow());
                }
            }
        } else if (symCheck("PLUS") || symCheck("MINU") || symCheck("NOT")) {
            node.addChild(UnaryOp());
            ASTNode unaryExp = UnaryExp();
            node.addChild(unaryExp);
        } else {
            node.addChild(PrimaryExp());
        }
        return node;
    }

    private ArrayList<Symbol> analyseFuncRParams(ASTNode node) {
        ArrayList<Symbol> symbolList = new ArrayList<>();
        for (ASTNode child : node.getChildren()) {
            if (child.getType().equals("Exp")) {
                symbolList.add(analyseExp(child));
            }
        }
        return symbolList;
    }

    private Symbol analyseExp(ASTNode node) {
        return analyseAddExp(node.getChildren().get(0));
    }

    private Symbol analyseAddExp(ASTNode node) {
        if (node.getChildren().size() != 1) {
            return new Symbol("addExp", SymbolType.Const, 0, curSymbolTable.isGlobal());
        } else {
            return analyseMulExp(node.getChildren().get(0));
        }
    }

    private Symbol analyseMulExp(ASTNode node) {
        if (node.getChildren().size() != 1) {
            return new Symbol("mulExp", SymbolType.Const, 0, curSymbolTable.isGlobal());
        } else {
            return analyseUnaryExp(node.getChildren().get(0));
        }
    }

    private Symbol analyseUnaryExp(ASTNode node) {
        if (node.getChildren().size() != 1) {
            int i;
            for (i = 0; node.getChildren().get(i).getType().equals("UnaryOp"); i++) ;
            node = node.getChildren().get(i);
        }
        if (node.getType().equals("Ident")) {
            String symbolName = node.getChildren().get(0).getToken().getTokenValue();
            return curSymbolTable.searchSymbol(symbolName, errorHandler, curSym.getRow(), false);
        } else {
            return analysePrimaryExp(node.getChildren().get(0));
        }
    }

    private Symbol analysePrimaryExp(ASTNode node) {
        if (node.getChildren().get(0).getType().equals("LVal")) {
            return analyseLVal(node.getChildren().get(0));
        } else {
            return new Symbol("primaryExp", SymbolType.Const, 0, curSymbolTable.isGlobal());
        }
    }

    private Symbol analyseLVal(ASTNode node) {
        String symbolName = node.getChildren().get(0).getChildren().get(0).getToken().getTokenValue();
        Symbol symbol = curSymbolTable.searchSymbol(symbolName, errorHandler, curSym.getRow(), false);
        if (symbol != null) {
            int dim = symbol.getDimension();
            for (ASTNode child : node.getChildren()) {
                if (child.getType().equals("Exp")) {
                    dim--;
                }
            }
            return new Symbol(symbolName, symbol.getType(), dim, curSymbolTable.isGlobal());
        } else {
            return null;
        }
    }

    //FuncRParams → Exp { ',' Exp }
    private ASTNode FuncRParams() {
        ASTNode node = new ASTNode("FuncRParams");
        node.addChild(Exp());
        while (symCheck("COMMA")) {
            addTK(node, "COMMA", curSym);
            node.addChild(Exp());
        }
        return node;
    }

    //UnaryOp → '+' | '−' | '!'
    private ASTNode UnaryOp() {
        ASTNode node = new ASTNode("UnaryOp");
        if (symCheck("PLUS") || symCheck("MINU") || symCheck("NOT")) {
            addTK(node, curSym.getTokenCode(), curSym);
        } else {
            error("缺少一元运算符");
        }
        return node;
    }

    private int calPrimaryExp(ASTNode node) {
        ASTNode firstChild = node.getChildren().get(0);
        if (firstChild.getType().equals("Number")) {
            return calNumber(firstChild);
        } else if (firstChild.getType().equals("LPARENT")) {
            return calConstExp(node.getChildren().get(1));
        } else if (firstChild.getType().equals("LVal")) {
            return getConstLVal(firstChild);
        }
        System.out.println("Unexpected const cal");
        return -1;
    }
//    LVal → Ident {'[' Exp ']'}
    private int getConstLVal(ASTNode node) {
        ASTNode ident = node.getChildren().get(0);
        String name = ident.getIdentName();
        int length = node.getChildren().size();
        int dim1 = 0;
        int dim2 = 0;
        Symbol identSym = curSymbolTable.searchSymbol(name);
        if (length == 1) {
            return identSym.getValue();
        } else if (length == 4) {
            dim1 = calConstExp(node.getChildren().get(2));
            return identSym.getArrValue(dim1);
        } else {
            dim1 = calConstExp(node.getChildren().get(2));
            dim2 = calConstExp(node.getChildren().get(5));
            return identSym.getArrValue(dim1, dim2);
        }
    }

    private int calNumber(ASTNode node) {
        return calIntconst(node.getChildren().get(0));
    }

    private int calIntconst(ASTNode node) {
        return calINTCON(node.getChildren().get(0));
    }

    private int calINTCON(ASTNode node) {
        return Integer.parseInt(node.getToken().getTokenValue());
    }

    //PrimaryExp → '(' Exp ')' | LVal | Number
    private ASTNode PrimaryExp() {
        ASTNode node = new ASTNode("PrimaryExp");
        if (symCheck("LPARENT")) {
            addTK(node, "LPARENT", curSym);
            node.addChild(Exp());
            if (symCheck("RPARENT")) {
                addTK(node, "RPARENT", curSym);
            } else {
                missingError("RPARENT", getLastSym().getRow());
            }
        } else if (symCheck("IDENFR")) {
            node.addChild(LVal());
        } else if (symCheck("INTCON")) {
            node.addChild(Number());
        } else {
            error("缺少左小括号或标识符或整数");
        }
        return node;
    }

    //LVal → Ident { '[' Exp ']' }
    private ASTNode LVal() {
        ASTNode node = new ASTNode("LVal");
        node.setDataType("Int");
        String symbolName = getIdentfr();
        curSymbolTable.searchSymbol(symbolName, errorHandler, curSym.getRow(), true);
        node.addChild(Ident());
        while (symCheck("LBRACK")) {
            node.setDataType("Array");
            addTK(node, "LBRACK", curSym);
            node.addChild(Exp());
            if (symCheck("RBRACK")) {
                addTK(node, "RBRACK", curSym);
            } else {
                missingError("RBRACK", getLastSym().getRow());
            }
        }
        return node;
    }

    // Exp → AddExp
    private ASTNode Exp() {
        ASTNode node = new ASTNode("Exp");
        node.addChild(AddExp());
        return node;
    }

    //Number → IntConst
    private ASTNode Number() {
        ASTNode node = new ASTNode("Number");
        node.addChild(IntConst());
        return node;
    }

    //IntConst
    private ASTNode IntConst() {
        ASTNode node = new ASTNode("IntConst");
        if (symCheck("INTCON")) {
            addTK(node, "INTCON", curSym);
        } else {
            error("缺少整数");
        }
        return node;
    }

    private void addTK(ASTNode node, String tokenCode, Token TK) {
        node.addChild(new ASTNode(tokenCode, TK));
        nextSym();
    }

    private void nextSym() {
        index++;
        if (index < tokenList.size())
            curSym = tokenList.get(index);
    }

    private void error(String msg) {
        System.out.println("Error: " + msg + " at " + curSym.getTokenValue() + " " + curSym.getRow());
    }

    private void missingError(String tokenName, int row) {
        if (tokenName.equals("SEMICN")) {
            errorHandler.handleErrorI(row);
        }
        if (tokenName.equals("RPARENT")) {
            errorHandler.handleErrorJ(row);
        }
        if (tokenName.equals("RBRACK")) {
            errorHandler.handleErrorK(row);
        }
    }

    private boolean checkAssign() {
        int offset = 1;
        Token tmpSym = curSym;
        while (index + offset < tokenList.size()) {
            Token newSym = tokenList.get(index + offset);
            if (newSym.getTokenCode().equals("SEMICN") || newSym.getRow() > tmpSym.getRow()) {
                break;
            } else if (newSym.getTokenCode().equals("ASSIGN")) {
                return true;
            }
            offset++;
        }
        return false;
    }


    private boolean isOutType(String nodeType) {
        return !nodeType.equals("BlockItem") && !nodeType.equals("Decl") && !nodeType.equals("BType") && !nodeType.equals("IntConst")
                && !nodeType.equals("Ident") && !nodeType.equals("FormatString");
    }

    private boolean symCheck(String tokenCode) {

        return curSym.getTokenCode().equals(tokenCode);
    }

    private boolean preCheck(String tokenCode, int offset) {
        if (index + offset >= tokenList.size()) {
            return false;
        }
        return tokenList.get(index + offset).getTokenCode().equals(tokenCode);
    }

}
