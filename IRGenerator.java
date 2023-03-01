import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class IRGenerator {
    private SymbolNode curSymbolTable;
    private ASTNode root;
    private ASTNode curNode;
    private ArrayList<IRCode> IRCodeList = new ArrayList<>();
    private boolean printIR = true;

    private StringBuilder buffer;
    private String outDir = "ircode.txt";
    FileWriter writer;

    public boolean noNeedOpenScope = false;
    String curName = null;

    private int tempCnt = 0;
    private int ifCnt = 0;
    private int whileCnt = 0;
    private int logicOrCnt = 0;



    private int startIndex = IRCodeList.size();


    public IRGenerator(ASTNode root, SymbolNode symbolTable) {
        this.root = root;
        this.curSymbolTable = symbolTable;
    }

    public ArrayList<IRCode> generateIRCode(boolean out) throws IOException {
        curNode = root;
        printIR = out;
        buffer = new StringBuilder();

        parseCompUnit(curNode);

        if (out) {
            writer = new FileWriter(outDir);
            writer.write(buffer.toString());
            writer.flush();
            writer.close();
        }
        return IRCodeList;
    }

    private void parseCompUnit(ASTNode node) {
        boolean funcDeclStarted = false;
        for (ASTNode tempNode : node.getChildren()) {
            if (tempNode.getType().equals("Decl")) {
                parseDecl(tempNode);
            } else if (tempNode.getType().equals("FuncDef")) {
                if (!funcDeclStarted) {
                    funcDeclStarted = true;
                    createIRCode("note", "#Start FuncDecl");
                }
                parseFuncDef(tempNode);
            } else {
                if (!funcDeclStarted) {
                    funcDeclStarted = true;
                    createIRCode("note", "#Start FuncDecl");
                }
                createIRCode("note", "#Main Start");
                parseMainFuncDef(tempNode);
            }


        }
    }

    private void parseDecl(ASTNode node) {
        for (ASTNode tempNode : node.getChildren()) {
            if (tempNode.getType().equals("ConstDecl")) {
                parseConstDecl(tempNode);
            } else {
                parseVarDecl(tempNode);
            }
        }
    }


    //    ConstDecl → 'const' BType ConstDef { ',' ConstDef }
    private void parseConstDecl(ASTNode node) {
        for (ASTNode tempNode : node.getChildren()) {
            if (tempNode.getType().equals("ConstDef")) {
                parseConstDef(tempNode);
            }
        }
    }

    //    ConstDef → Ident { '[' ConstExp ']' } '=' ConstInitVal
    private void parseConstDef(ASTNode node) {
        curSymbolTable.increaseCurSymIndex();
        String name = "";
        int dim1 = 0;
        int dim2 = 0;
        Symbol symbol = null;
        boolean isArr = false;
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Ident")) {
                name = tmpNode.getIdentName();
                symbol = curSymbolTable.searchSymbolInIr(name);
            } else if (tmpNode.getType().equals("ConstExp")) {
                if (dim1 == 0) {
                    isArr = true;
                    dim1 = tmpNode.getNum();
                } else {
                    dim2 = tmpNode.getNum();
                }
            } else if (tmpNode.getType().equals("ConstInitVal")) {
                if (isArr) {
                    IRCode ir = new IRCode("arrayDecl", name, dim1, dim2);
                    ArrayList<Integer> initList = parseConstInitVal(tmpNode);
                    symbol.setValueList(initList);
                    ir.setSymbol(symbol);
                    ir.setInitList(initList);
                    ir.setInit(true);
                    IRInit(ir);
                } else {
                    symbol.setIRIndex(IRCodeList.size());
                    IRCode ir = new IRCode("intDecl", "const", name, symbol.getValue());
                    ir.setInit(true);
                    ir.setSymbol(symbol);
                    IRInit(ir);
                    return;
                }
            }
        }
    }

    //    ConstInitVal → ConstExp | '{' [ ConstInitVal { ',' ConstInitVal } ] '}'
    private ArrayList<Integer> parseConstInitVal(ASTNode node) {
        ArrayList<Integer> initList = new ArrayList<>();
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("ConstInitVal")) {
                initList.addAll(parseConstInitVal(tmpNode));
            } else if (tmpNode.getType().equals("ConstExp")) {
                initList.add(tmpNode.getNum());
            }
        }
        return initList;
    }

    //    VarDecl → BType VarDef { ',' VarDef } ';'
    private void parseVarDecl(ASTNode node) {
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("VarDef")) {
                parseVarDef(tmpNode);
            }
        }
    }

    private ArrayList<ASTNode> parseInitVal(ASTNode node) {
        ArrayList<ASTNode> initList = new ArrayList<>();
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("InitVal")) {
                initList.addAll(parseInitVal(tmpNode));
            } else if (tmpNode.getType().equals("Exp")) {
                initList.add(tmpNode);
            }
        }
        return initList;
    }

    //    VarDef → Ident { '[' ConstExp ']' }  | Ident { '[' ConstExp ']' } '=' InitVal
//      InitVal → Exp | '{' [ InitVal { ',' InitVal } ] '}'// 1.表达式初值 2.一
    private void parseVarDef(ASTNode node) {
        curSymbolTable.increaseCurSymIndex();
        String name = "";
        int dim1 = 0;
        int dim2 = 0;
        Symbol symbol = null;
        boolean isArr = false;
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Ident")) {
                name = tmpNode.getIdentName();
                symbol = curSymbolTable.searchSymbolInIr(name);
            } else if (tmpNode.getType().equals("ConstExp")) {
                if (dim1 == 0) {
                    isArr = true;
                    dim1 = tmpNode.getNum();
                } else {
                    dim2 = tmpNode.getNum();
                }
            } else if (tmpNode.getType().equals("InitVal")) {
                if (isArr) {
                    // 解析数组初始化
                    startIndex = IRCodeList.size();

                    if (curSymbolTable.isGlobal()) {
                        ArrayList<ASTNode> InitVarList = parseInitVal(tmpNode);

                        ArrayList<Integer> initList = new ArrayList<>();
                        IRCode irAssign = new IRCode("arrayDecl", name, dim1, dim2);
                        irAssign.setInit(true);

                        int index = 0;
                        irAssign.setSymbol(symbol);
                        for (ASTNode initValNode : InitVarList) {
                            int initNum = parseExp(initValNode).getNum();
                            initList.add(initNum);
                        }
                        irAssign.setInitList(initList);
                        IRInit(irAssign);
                    } else {
                        IRCode ir = new IRCode("arrayDecl", name, dim1, dim2);
                        ir.setSymbol(symbol);
                        startIndex = IRCodeList.size();
                        ArrayList<ASTNode> InitVarList = parseInitVal(tmpNode);
                        IRInit(ir);
                        if (dim2 == 0) {
                            int index = 0;
                            for (ASTNode initValNode : InitVarList) {
                                if (initValNode.getType().equals("Exp")) {
                                    int arrIndex = index;
                                    Variable arrIndexVar = new Variable("num", arrIndex);
                                    Variable initVar = parseExp(initValNode);
                                    Variable LValVar = new Variable("array", name, arrIndexVar);

                                    LValVar.setSymbol(symbol);
                                    LValVar.setSymbolKind(true);

                                    IRCode irAssign = new IRCode("assign2", LValVar, initVar);
                                    irAssign.releaseDest();
                                    IRInit(irAssign);
                                    index++;
                                }
                            }
                        } else {
                            int indexI = 0;
                            int indexJ = 0;
                            for (ASTNode initValNode : InitVarList) {
                                if (initValNode.getType().equals("Exp")) {
                                    int arrIndex = indexI * dim2 + indexJ;
                                    Variable arrIndexVar = new Variable("num", arrIndex);
                                    Variable initVar = parseExp(initValNode);
                                    Variable LValVar = new Variable("array", name, arrIndexVar);

                                    LValVar.setSymbol(symbol);
                                    LValVar.setSymbolKind(true);

                                    IRCode irAssign = new IRCode("assign2", LValVar, initVar);
                                    irAssign.releaseDest();
                                    IRInit(irAssign);
                                    indexJ++;
                                    if (indexJ == dim2) {
                                        indexJ = 0;
                                        indexI++;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (curSymbolTable.isGlobal()) {
                        IRCode numInitIr = new IRCode("intDecl", "var", name);
                        numInitIr.setSymbol(symbol);
                        numInitIr.setInit(true);
                        ASTNode initVar = parseInitVal(tmpNode).get(0);
                        int initNum = parseExp(initVar).getNum();
                        numInitIr.setNum(initNum);
                        IRInit(numInitIr);
                    } else {
                        IRCode numInitIr = new IRCode("intDecl", "var", name);
                        numInitIr.setSymbol(symbol);
                        IRInit(numInitIr);

                        startIndex = IRCodeList.size();

                        curName = name;

                        Variable intInitVar = parseExp(tmpNode.getChildren().get(0));

                        curName = null;
                        Variable intInitLval = new Variable("var", name);

                        intInitLval.setSymbol(symbol);
                        intInitLval.setSymbolKind(true);

                        IRCode ir = new IRCode("assign2", intInitLval, intInitVar);
                        IRInit(ir);
                    }
                }
                return;
            }
        }
        if (!isArr) {
            IRCode ir = new IRCode("intDecl", "var", name);
            ir.setSymbol(symbol);
            IRInit(ir);
        }
        else {
            IRCode ir = new IRCode("arrayDecl",name,dim1, dim2);
            ir.setSymbol(symbol);
            IRInit(ir);
        }
    }

    //    Exp → AddExp 注：SysY 表达式是int 型表达式 // 存在即可
    private Variable parseExp(ASTNode node) {
        return parseAddExp(node.getChildren().get(0));
    }

    private Variable generateOpIR(ASTNode n, String op) {
        ASTNode leftNode = n.getChildren().get(0);
        ASTNode rightNode = n.getChildren().get(2);
        String leftType = leftNode.getType();
        String rightType = rightNode.getType();
        Variable lExp = null;
        Variable rExp = null;
        if (leftType.equals("Exp")) {
            lExp = parseExp(leftNode);
        } else if (leftType.equals("AddExp")) {
            lExp = parseAddExp(leftNode);
        } else if (leftType.equals("MulExp")) {
            lExp = parseMulExp(leftNode);
        } else if (leftType.equals("UnaryExp")) {
            lExp = parseUnaryExp(leftNode);
        } else if (leftType.equals("PrimaryExp")) {
            lExp = parsePrimaryExp(leftNode);
        } else {
            System.out.println("Error: generateOpIR unExpected leftType");
        }
        if (rightType.equals("Exp")) {
            rExp = parseExp(rightNode);
        } else if (rightType.equals("AddExp")) {
            rExp = parseAddExp(rightNode);
        } else if (rightType.equals("MulExp")) {
            rExp = parseMulExp(rightNode);
        } else if (rightType.equals("UnaryExp")) {
            rExp = parseUnaryExp(rightNode);
        } else if (rightType.equals("PrimaryExp")) {
            rExp = parsePrimaryExp(rightNode);
        } else {
            System.out.println("Error: generateOpIR unExpected rightType");
        }
        if (lExp.getType().equals("num") && rExp.getType().equals("num")) {
            int lNum = lExp.getNum();
            int rNum = rExp.getNum();

            switch (op) {
                case "+":
                    return new Variable("num", lNum + rNum);
                case "-":
                    return new Variable("num", lNum - rNum);
                case "*":
                    return new Variable("num", lNum * rNum);
                case "/":
                    return new Variable("num", lNum / rNum);
                case "%":
                    return new Variable("num", lNum % rNum);
                default:
                    System.err.println("unexpected op " + op);
                    return null;
            }
        } else {
            Variable tmpVar = createTemp();
            createIRCode("assign", op, tmpVar, lExp, rExp);
            return tmpVar;
        }
    }

    //    AddExp → MulExp | AddExp ('+' | '−') MulExp
    private Variable parseAddExp(ASTNode node) {
        if (node.getChildren().size() == 1) {
            return parseMulExp(node.getChildren().get(0));
        } else {
            return generateOpIR(node, node.getChildren().get(1).getToken().getTokenValue());
        }
    }

    //    MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    private Variable parseMulExp(ASTNode node) {
        if (node.getChildren().size() == 1) {
            return parseUnaryExp(node.getChildren().get(0));
        } else {
            return generateOpIR(node, node.getChildren().get(1).getToken().getTokenValue());
        }
    }

    //    UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')'| UnaryOp UnaryExp
//    FuncRParams → Exp { ',' Exp }
//    UnaryOp → '+' | '−' | '!'
    private Variable parseUnaryExp(ASTNode node) {
        if (node.getChildren().size() == 1) {
            return parsePrimaryExp(node.getChildren().get(0));
        } else if (node.getChildren().get(0).getType().equals("Ident")) {//函数调用
            ASTNode identNode = node.getChildren().get(0);
            String funcName = identNode.getIdentName();
            Symbol funcSymbol = curSymbolTable.searchSymbolInIr(funcName);
            if (node.getChildren().get(2).getType().equals("FuncRParams")) {
                ASTNode funcRParamsNode = node.getChildren().get(2);
                int index = 0;
                for (ASTNode tmpNode : funcRParamsNode.getChildren()) {
                    if (tmpNode.getType().equals("Exp")) {
                        Symbol paramSymbol = funcSymbol.getParaList().get(index);
                        int paramDimension = paramSymbol.getDimension();

                        if (paramDimension == 0) {
                            Variable paramVar = parseExp(tmpNode);
                            createIRCode("push", paramVar);
                        } else {
                            Variable paramVar = parseArrParam(tmpNode, paramDimension);
                            createIRCode("push", paramVar);
                        }
                        index++;
                    }
                }
            }

            IRCode ir = new IRCode("call", funcName);
            ir.setSymbol(funcSymbol);
            IRInit(ir);

            if (funcSymbol.getDimension() == 0) {
                Variable tmpVar = createTemp();
                createIRCode("assign_ret", tmpVar);
                return tmpVar;
            }
        } else if (node.getChildren().get(0).getType().equals("UnaryOp")) {
            ASTNode unaryOpNode = node.getChildren().get(0);
            String unaryOp = unaryOpNode.getChildren().get(0).getToken().getTokenValue();
            switch (unaryOp) {
                case "+":
                    return parseUnaryExp(node.getChildren().get(1));
                case "-":
                    Variable zeroExp = new Variable("num", 0);
                    Variable unaryExp = parseUnaryExp(node.getChildren().get(1));

                    if (unaryExp.getType().equals("num")) {
                        return new Variable("num", -unaryExp.getNum());
                    } else {
                        Variable tmpVar = createTemp();
                        createIRCode("assign", "-", tmpVar, zeroExp, unaryExp);
                        return tmpVar;
                    }
                case "!":
                    Variable unaryExp2 = parseUnaryExp(node.getChildren().get(1));
                    Variable tmpVar = createTemp();
                    createIRCode("setcmp", "seq", tmpVar, unaryExp2, new Variable("num", 0));
                    return tmpVar;
            }
        }
        return null;
    }

    //解析传参时的数组参数
    //    FuncRParams → Exp { ',' Exp },此处解析为Exp
//    Exp → AddExp
//    AddExp → MulExp | AddExp ('+' | '−') MulExp
//    MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
//    UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')'| UnaryOp UnaryExp
//    PrimaryExp → '(' Exp ')' | LVal | Number // 三种情况均需覆盖
//    LVal → Ident {'[' Exp ']'} //1.普通变量 2.一维数组 3.二维数组

    private Variable parseArrParam(ASTNode node, int paraDim) {
        ASTNode LValNode = node.getChildren().get(0);
        while (!LValNode.getType().equals("LVal")) {
            LValNode = LValNode.getChildren().get(0);
        }
        ASTNode identNode = LValNode.getChildren().get(0);
        String name = identNode.getIdentName();
        Symbol symbol = curSymbolTable.searchSymbolInIr(name);

        Variable arrVar = new Variable("array", name);

        if (symbol.getDimension() == 2) {
            Variable numVar = null;
            if (paraDim == 1) {
                for (int i = 0; i < LValNode.getChildren().size(); i++) {
                    ASTNode tmpNode = LValNode.getChildren().get(i);
                    if (tmpNode.getType().equals("Exp")) {
                        numVar = parseExp(tmpNode);
                        break;
                    }
                }
                arrVar.setVar(numVar);
            }
        }
        arrVar.setSymbol(symbol);
        arrVar.setSymbolKind(true);
        return arrVar;
    }

    //    PrimaryExp → '(' Exp ')' | LVal | Number
    private Variable parsePrimaryExp(ASTNode node) {
        if (node.getChildren().size() == 1) {
            ASTNode firstChild = node.getChildren().get(0);
            if (firstChild.getType().equals("LVal")) {
                return parseLVal(firstChild);
            } else if (firstChild.getType().equals("Number")) {
                return new Variable("num", firstChild.calValue());
            }
        } else {
            return parseExp(node.getChildren().get(1));
        }
        return null;
    }

    //    LVal → Ident { '[' Exp ']' }
    private Variable parseLVal(ASTNode node) {
        Variable Exp1 = null;
        Variable Exp2 = null;
        String name = "";
        Symbol symbol = null;
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Ident")) {
                name = tmpNode.getIdentName();
                symbol = curSymbolTable.searchSymbolInIr(name);
            } else if (tmpNode.getType().equals("Exp")) {
                if (Exp1 == null) {
                    Exp1 = parseExp(tmpNode);
                } else {
                    Exp2 = parseExp(tmpNode);
                }
            }
        }
        Variable parseRes = parseIdent(node.getChildren().get(0), Exp1, Exp2, node.getDataType());
        if (symbol != null && !symbol.getType().equals(SymbolType.Func)) {
            parseRes.setSymbol(symbol);
        }
        return parseRes;
    }

    //  FuncFParams → FuncFParam { ',' FuncFParam }
    private void parseFuncFParams(ASTNode node) {
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("FuncFParam")) {
                parseFuncFParam(tmpNode);
            }
        }
    }

    //    FuncFParam → BType Ident ['[' ']' { '[' ConstExp ']' }]
    private void parseFuncFParam(ASTNode node) {
        curSymbolTable.increaseCurSymIndex();
        String name = "";
        Symbol symbol = null;
        String IRStr = "";
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Ident")) {
                name = tmpNode.getIdentName();
                symbol = curSymbolTable.searchSymbolInIr(name);
                int dim = symbol.getDimension();
                if (dim == 0) {
                    IRStr = "para int " + name;
                } else if (dim == 1) {
                    IRStr = "para int " + name + "[]";
                } else if (dim == 2) {
                    IRStr = "para int " + name + "[][]";
                }
                createIRCode("funcPara", IRStr);
                break;
            }
        }
    }

    //    FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
    private void parseFuncDef(ASTNode node) {
        String funcName = "";
        Symbol funcSym = null;
        String retType = "";
        curSymbolTable.increaseCurSymIndex();
//        函数优化

//        函数定义处理
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Ident")) {
                funcName = tmpNode.getIdentName();
                funcSym = curSymbolTable.searchSymbolInIr(funcName);
                if (funcSym.getDimension() == 0) {
                    retType = "int";
                } else {
                    retType = "void";
                }
                createIRCode("funcDecl", retType, funcName);
                curSymbolTable = curSymbolTable.getNextChild();
            } else if (tmpNode.getType().equals("FuncFParams")) {
                parseFuncFParams(tmpNode);
            } else if (tmpNode.getType().equals("Block")) {
                parseBlock(tmpNode, -1);
                createIRCode("note", "#End of a Function");
            }
        }
        curSymbolTable = curSymbolTable.getParent();
        return;
    }

    // Block → '{' { BlockItem } '}'
    private void parseBlock(ASTNode node, int whileCnt) {
        SymbolNode symbolSave = curSymbolTable;
        if (curSymbolTable == null) {
            System.out.println("Error: Block");
            return;
        }
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("BlockItem")) {
                parseBlockItem(tmpNode, whileCnt);
            }
        }
        return;
    }

    //BlockItem → Decl | Stmt // 覆盖两种语句块项
    private void parseBlockItem(ASTNode node, int whileCnt) {
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Decl")) {
                parseDecl(tmpNode);
            } else if (tmpNode.getType().equals("Stmt")) {
                parseStmt(tmpNode, whileCnt);
            }
        }
        return;
    }

    //Stmt → LVal '=' Exp ';' // 每种类型的语句都要覆盖
//| [Exp] ';' //有无Exp两种情况
//| Block
//| 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // 1.有else 2.无else
//| 'while' '(' Cond ')' Stmt
//| 'break' ';'
//| 'continue' ';'
//| 'return' [Exp] ';' // 1.有Exp 2.无Exp
//| LVal '=' 'getint''('')'';'
//| 'printf''('FormatString{','Exp}')'';' // 1.有Exp 2.无Exp
    private void parseStmt(ASTNode node, int whileCnt) {
        ASTNode firstChild = node.getChildren().get(0);
        String firstChildType = firstChild.getType();
        switch (firstChildType) {
            case "IFTK":
                parseIfStmt(node, whileCnt);
                break;
            case "WHILETK":
                parseWhile(node);
                break;
            case "BREAKTK":
                String breakstr = "end_loop" + whileCnt;
                createIRCode("note", "#Out Block WhileCut");
                createIRCode("jump", breakstr);
                break;
            case "CONTINUETK":
                String ctnstr = "begin_loop" + whileCnt;
                createIRCode("note", "#Out Block WhileCut");
                createIRCode("jump", ctnstr);
                break;
            case "RETURNTK":
                if (node.getChildren().size() == 2) {
                    IRCode ir = new IRCode("return", "void return");
                    ir.setVoidReturn(true);
                    IRInit(ir);
                } else {
                    ASTNode expNode = node.getChildren().get(1);

                    Variable ret = parseExp(expNode);

                    IRCode ir = new IRCode("return", ret);
                    IRInit(ir);
                }
                break;
            case "SEMICN":
                break;
            case "PRINTFTK":
                parsePrintfStmt(node);
                break;
            case "LVal":
                ASTNode AssignVal = node.getChildren().get(2);
                if (AssignVal.getType().equals("GETINTTK")) {
                    Variable getIntExp = parseAssignLVal(firstChild);
                    createIRCode("getint", getIntExp);
                } else {
                    startIndex = IRCodeList.size();
                    Variable LVal = parseAssignLVal(firstChild);
                    Variable Exp = parseExp(AssignVal);

                    createIRCode("assign2", LVal, Exp);
                }
                break;
            case "Block":
                boolean localOpenBlock = true;
               if (noNeedOpenScope) {
                   localOpenBlock = false;
                   noNeedOpenScope = false;
               }

               if (localOpenBlock) {
                   curSymbolTable = curSymbolTable.getNextChild();
               }

                parseBlock(firstChild, whileCnt);

                if (localOpenBlock) {
                    createIRCode("note", "#Out Block");
                    curSymbolTable = curSymbolTable.getParent();
                }
                break;
            case "Exp":
                parseExp(firstChild);
                break;
            default:
                System.out.println("Unknown Stmt");
                break;
        }
    }


    private Variable parseAssignLVal(ASTNode node) {
        Variable Exp1 = null;
        Variable Exp2 = null;
        String name = "";
        Symbol symbol = null;
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Ident")) {
                name = tmpNode.getIdentName();
                symbol = curSymbolTable.searchSymbolInIr(name);
            } else if (tmpNode.getType().equals("Exp")) {
                if (Exp1 == null) {
                    Exp1 = parseExp(tmpNode);
                } else {
                    Exp2 = parseExp(tmpNode);
                }
            }
        }
        if (symbol.getType().equals(SymbolType.Const)) {
            if (symbol.getDimension() == 0) {
                return new Variable("num", symbol.getValue());
            } else {
                return parseConstArrVisit(symbol, Exp1, Exp2);
            }
        } else if (symbol.getType().equals(SymbolType.Var)) {
            if (symbol.getDimension() == 0) {
                Variable intVar = new Variable("var", symbol.getName());
                intVar.setSymbolKind(true);
                intVar.setSymbol(symbol);
                return intVar;
            } else {
                startIndex = IRCodeList.size();
                Variable nameAndIndex = parseArrayVisit(symbol, Exp1, Exp2);

                return nameAndIndex;
            }
        }

        return null;
    }

    //| 'printf''('FormatString{','Exp}')'';' // 1.有Exp 2.无Exp
    private void parsePrintfStmt(ASTNode node) {
        String formatString = "";
        String[] splits = null;
        ArrayList<Variable> expList = new ArrayList<>();
        int splitIndex = 0;
        createIRCode("note", "#Start Print");
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("FormatString")) {
                formatString = tmpNode.getStrcon();
                formatString = formatString.substring(1, formatString.length() - 1);
                splits = formatString.split("%d", -1);
                if (!splits[splitIndex].equals("")) {
                    Variable splitStrVar = new Variable("str", splits[splitIndex]);
                    expList.add(splitStrVar);
//                    createIRCode("print", splitStrVar);
                }
                splitIndex++;
                if (splits.length == 1) {
                    break;
                }
            } else if (tmpNode.getType().equals("Exp")) {
                Variable exp = parseExp(tmpNode);
                expList.add(exp);
//                createIRCode("print", exp);
                String splitStr = splits[splitIndex];
                if (!splitStr.equals("")) {
                    Variable splitStrVar = new Variable("str", splitStr);
                    expList.add(splitStrVar);
//                    createIRCode("print", splitStrVar);
                }
                splitIndex++;
            }

        }
        for (Variable tmpVar : expList) {
            createIRCode("print", tmpVar);
        }
        return;
    }

    private void parseIfStmt(ASTNode node, int whileCnt) {
        int localIfCnt = ifCnt;
        ifCnt++;

        String endIfLabel = "end_if" + localIfCnt;
        String endIfElseLabel = "end_if_else" + localIfCnt;
        String intoIfLabel = "into_if" + localIfCnt;

        int stmtCnt = 0;

        int length = node.getChildren().size();

        ASTNode cond = node.getChildren().get(2);
        parseCond(cond, endIfLabel, intoIfLabel);

        curSymbolTable = curSymbolTable.getNextChild();
        noNeedOpenScope = true;
        createIRCode("label", intoIfLabel + ":");
        ASTNode stmt1 = node.getChildren().get(4);
        parseStmt(stmt1, whileCnt);

        if (length == 5) {
            createIRCode("note", "#Out Block");     //出基本块sp移动,必须保证此条code在scope内
            curSymbolTable = curSymbolTable.getParent();
            noNeedOpenScope = false;

            createIRCode("label", endIfLabel + ":");
        } else {
            createIRCode("note", "#Out Block");     //出基本块sp移动,必须保证此条code在scope内
            createIRCode("jump", endIfElseLabel);     //出基本块sp移动,必须保证此条code在scope内
            curSymbolTable = curSymbolTable.getParent();
            noNeedOpenScope = false;

            createIRCode("label", endIfLabel + ":");

            curSymbolTable = curSymbolTable.getNextChild();
            noNeedOpenScope = true;
            ASTNode stmt2 = node.getChildren().get(6);
            parseStmt(stmt2, whileCnt);
            createIRCode("note", "#Out Block");     //todo 是否必要存疑

            curSymbolTable = curSymbolTable.getParent();
            noNeedOpenScope = false;

            createIRCode("label", endIfElseLabel + ":");
        }
        return;
    }

    private void parseCond(ASTNode node, String jumpOutLabel, String jumpInLabel)

    {
        String condType = node.getCondType();
        if (condType.equals("AND") || condType.equals("OR")) {
            ASTNode cond1 = node.getLeftCond();
            ASTNode cond2 = node.getRightCond();
            if (condType.equals("AND")) {
                parseCond(cond1, jumpOutLabel, jumpInLabel);
            } else {
                String logicORLabel = jumpOutLabel + "_logicOR" + logicOrCnt;
                logicOrCnt += 1;

                parseCond(cond1, logicORLabel, jumpInLabel);
                createIRCode("jump", jumpInLabel);

                createIRCode("label", logicORLabel + ":");

            }
            parseCond(cond2, jumpOutLabel, jumpInLabel);
        } else if (condType.equals("EQL") || condType.equals("NEQ")) {
            ASTNode eq1 = node.getLeftEq();
            ASTNode eq2 = node.getRightEq();
            Variable leftEq = parseEqExp(eq1);
            Variable rightEq = parseEqExp(eq2);
            if (condType.equals("EQL")) {
                createIRCode("branch", "bne", jumpOutLabel, leftEq, rightEq);
            } else {
                createIRCode("branch", "beq", jumpOutLabel, leftEq, rightEq);
            }
        } else if (condType.equals("GEQ") || condType.equals("LEQ") || condType.equals("GRE") || condType.equals("LSS")) {
            ASTNode rel1 = node.getLeftRel();
            ASTNode rel2 = node.getRightRel();
            Variable leftVar = parseRelExp(rel1);
            Variable rightVar = parseRelExp(rel2);

            switch (condType) {
                case "GEQ":
                    createIRCode("branch", "blt", jumpOutLabel, leftVar, rightVar);
                    break;
                case "LEQ":
                    createIRCode("branch", "bgt", jumpOutLabel, leftVar, rightVar);
                    break;
                case "GRE":
                    createIRCode("branch", "ble", jumpOutLabel, leftVar, rightVar);
                    break;
                case "LSS":
                    createIRCode("branch", "bge", jumpOutLabel, leftVar, rightVar);
                    break;
            }
        } else {
            ASTNode addExp = node.relToAddExp();
            Variable addVar = parseAddExp(addExp);
            createIRCode("branch", "beq", jumpOutLabel, addVar, new Variable("num", 0));
        }
    }

    private Variable parseEqExp(ASTNode node)
    {
        String condType = node.getCondType();
        if (condType.equals("EQL") || condType.equals("NEQ")) {
            ASTNode eq1 = node.getLeftEq();
            ASTNode eq2 = node.getRightEq();
            Variable leftVar = parseEqExp(eq1);
            Variable rightVar = parseEqExp(eq2);
            Variable tmpVar = createTemp();

            if (condType.equals("EQL")) {
                createIRCode("setcmp", "seq", tmpVar, leftVar, rightVar);
            } else {
                createIRCode("setcmp", "sne", tmpVar, leftVar, rightVar);
            }
            return tmpVar;
        } else {
            return parseRelExp(node);
        }
    }

    Variable parseRelExp(ASTNode node)
    {
        String condType = node.getCondType();
        if (condType.equals("GEQ") || condType.equals("LEQ") || condType.equals("GRE") || condType.equals("LSS")) {
            ASTNode rel1 = node.getLeftRel();
            ASTNode rel2 = node.getRightRel();
            Variable leftVar = parseRelExp(rel1);
            Variable rightVar = parseRelExp(rel2);
            Variable tmpVar = createTemp();

            switch (condType) {
                case "GEQ":
                    createIRCode("setcmp", "sge", tmpVar, leftVar, rightVar);
                    break;
                case "LEQ":
                    createIRCode("setcmp", "sle", tmpVar, leftVar, rightVar);
                    break;
                case "GRE":
                    createIRCode("setcmp", "sgt", tmpVar, leftVar, rightVar);
                    break;
                case "LSS":
                    createIRCode("setcmp", "slt", tmpVar, leftVar, rightVar);
                    break;
            }
            return tmpVar;
        } else {
            ASTNode addExp = node.relToAddExp();
            return parseAddExp(addExp);
        }
    }

    private void parseWhile(ASTNode node) {
        int localWhileCnt = whileCnt;
        whileCnt++;

        String beginLabel = "begin_loop" + localWhileCnt;
        String endLabel = "end_loop" + localWhileCnt;
        String intoBlockLabel = "into_loop" + localWhileCnt;

        int whileStartIndex = IRCodeList.size();

        createIRCode("label", beginLabel + ":");

        ASTNode whileCond = node.getChildren().get(2);

        parseCond(whileCond, endLabel, intoBlockLabel);

        createIRCode("label", intoBlockLabel + ":");

        curSymbolTable = curSymbolTable.getNextChild();
        curSymbolTable.setWhile(true);
        noNeedOpenScope = true;
        ASTNode whileStmt = node.getChildren().get(4);
        parseStmt(whileStmt, localWhileCnt);
        createIRCode("note", "#Out Block");
        createIRCode("jump", beginLabel);
        curSymbolTable = curSymbolTable.getParent();
        noNeedOpenScope = false;

        createIRCode("label", endLabel + ":");
        return;
    }

    //    MainFuncDef → 'int' 'main' '(' ')' Block // 存在main函数
    private void parseMainFuncDef(ASTNode node) {
        for (ASTNode tmpNode : node.getChildren()) {
            if (tmpNode.getType().equals("Block")) {
                curSymbolTable.increaseCurSymIndex();
                curSymbolTable = curSymbolTable.getNextChild();
                parseBlock(tmpNode, -1);
            }
        }
        curSymbolTable = curSymbolTable.getParent();
        return;
    }
//仅在LVal中被使用
    private Variable parseIdent(ASTNode node, Variable Exp1, Variable Exp2, String dataType) {
        Symbol symbol = curSymbolTable.searchSymbolInIr(node.getIdentName());
        if (symbol == null) {
            System.err.println("undefined symbol " + node.getIdentName());
            return null;
        }
        if (dataType.equals("ConstInt")) {
            return new Variable("num", symbol.getValue());
        } else if (dataType.equals("Int")) {
            Variable intVar = new Variable("var", symbol.getName());
                intVar.setSymbolKind(true);
                return intVar;
        } else if (dataType.equals("ConstArray")) {
            return parseConstArrVisit(symbol, Exp1, Exp2);
        } else if (dataType.equals("Array")) {
            startIndex = IRCodeList.size();
            Variable nameAndIndex = parseArrayVisit(symbol,Exp1,Exp2);
            Variable tmpVar = createTemp();
                createIRCode("assign2", tmpVar, nameAndIndex);
               return tmpVar;
        }
        return null;
    }

    private Variable parseArrayVisit(Symbol symbol, Variable Exp1, Variable Exp2) {
        if (symbol.getDimension() == 2) {
            int arrDim2 = symbol.getDimen2();
            Variable varDim2 = new Variable("num", arrDim2);

            if (Exp1.getType().equals("num")) {
                if (Exp2.getType().equals("num")) {
                    int offsetnum = Exp1.getNum() * arrDim2 + Exp2.getNum();
                    Variable offset = new Variable("num", offsetnum);
                    Variable retVar = new Variable("array", symbol.getName(), offset);
                    retVar.setSymbol(symbol);
                    return retVar;
                } else {
                    int t1 = Exp1.getNum() * arrDim2;
                    Variable t1Var = new Variable("num", t1);
                    Variable offset = createTemp();
                    createIRCode("assign", "+", offset, t1Var, Exp2);
                    Variable retVar = new Variable("array", symbol.getName(), offset);
                    retVar.setSymbol(symbol);
                    return retVar;
                }
            } else {
                Variable t1Var = createTemp();
                createIRCode("assign", "*", t1Var, Exp1, varDim2);

                Variable offset = createTemp();

                createIRCode("assign", "+", offset, t1Var, Exp2);
                Variable retVar = new Variable("array", symbol.getName(), offset);
                retVar.setSymbol(symbol);
                return retVar;
            }
        } else {
            Variable retVar = new Variable("array", symbol.getName(), Exp1);
            retVar.setSymbol(symbol);
            return retVar;
        }
    }

    private Variable parseConstArrVisit(Symbol symbol, Variable Exp1, Variable Exp2) {
        if (symbol.getDimension() == 1) {
            Variable retVar = new Variable("array", symbol.getName(), Exp1);

            retVar.setSymbol(symbol);
            return retVar;
        } else {
            int dim2 = symbol.getDimen2();
            Variable var_dim2 = new Variable("num", dim2);
            if (Exp1.getType().equals("num")) {
                if (Exp2.getType().equals("num")) {
                    int arrValue = symbol.getArrValue(Exp1.getNum(), Exp2.getNum());
                    return new Variable("num", arrValue);
                } else {
                    int t1Num = Exp1.getNum() * dim2;
                    Variable t1 = new Variable("num", t1Num);
                    Variable offset = createTemp();
                    createIRCode("assign", "+", offset, Exp2, t1);
                    Variable retVar = new Variable("array", symbol.getName(), offset);
                    retVar.setSymbol(symbol);
                    return retVar;
                }
            } else {
                Variable t1 = createTemp();
                createIRCode("assign", "*", t1, Exp1, var_dim2);

                Variable offset = createTemp();

                createIRCode("assign", "+", offset, t1, Exp2);

                Variable retVar = new Variable("array", symbol.getName(), offset);
                retVar.setSymbol(symbol);
                return retVar;
            }
        }
    }

    private void IRInit(IRCode ir) {
        ir.setGlobal(curSymbolTable.isGlobal());
        ir.setScope(curSymbolTable);
        ir.generateIRStr();

        IRCodeList.add(ir);
//        System.out.println(ir.getIRStr());
        if (printIR) {
            buffer.append(ir.getIRStr() + "\n");
        }
    }

    private void createIRCode(String type, Variable variable) {
        IRCode ir = new IRCode(type, variable);
        IRInit(ir);
    }

    private void createIRCode(String type, String labelString) {
        IRCode ir = new IRCode(type, labelString);
        IRInit(ir);
    }

    private void createIRCode(String type, String op, Variable dst, Variable src1, Variable src2) {
        IRCode ir = new IRCode(type, op, dst, src1, src2);
        IRInit(ir);
    }

    //    cond用
    private void createIRCode(String type, String instr, String jumploc, Variable oper1, Variable oper2) {
        IRCode ir = new IRCode(type, instr, jumploc, oper1, oper2);
        IRInit(ir);
    }

    private void createIRCode(String type, String kind, String name) {
        IRCode ir = new IRCode(type, kind, name);
        IRInit(ir);
    }

    private void createIRCode(String type, Variable dst, Variable src1) {
        IRCode ir = new IRCode(type, dst, src1);
        IRInit(ir);
    }

    private Variable createTemp() {
        Variable temp = new Variable("var", "t" + tempCnt);
        tempCnt++;
        temp.setScope(curSymbolTable);
        return temp;
    }
}
