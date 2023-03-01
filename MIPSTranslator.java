import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MIPSTranslator {
    private ArrayList<IRCode> IRCodeList;
    private ArrayList<String> mipsList;
    private HashMap<IRCode, String> printStrMap;

    private boolean toDir = false;

    private int tabCnt = 0;
    private int printCnt = 0;
    private final String tab = "\t";

    private Register register;

    private int spOffset = 0;
    private boolean innerFunc = false;
    private int inFuncOffset = 0;
    private Symbol curFunc;

    private ArrayList<InstrList> pushQueue;

    private boolean inDecl = true;
    private boolean inFuncDef = false;
    private boolean inMain = false;

    private final String outDir = "mips.txt";
    private StringBuilder buffer;
    FileWriter writer;

    public MIPSTranslator(ArrayList<IRCode> IRCodeList) {
        this.IRCodeList = IRCodeList;
        mipsList = new ArrayList<>();
        printStrMap = new HashMap<>();
        register = new Register();
        pushQueue = new ArrayList<>();
    }

    public void IRCodeToMips(boolean out) throws IOException {
        buffer = new StringBuilder();

        toDir = out;
        mipsTranslate();

        if (out) {
            writer = new FileWriter(outDir);
            writer.write(buffer.toString());
            writer.flush();
            writer.close();
        }
    }

    private void addInstr(String mipsStr) {
        if (tabCnt == 1) {
            mipsStr = tab + mipsStr;
        }
        mipsList.add(mipsStr);
//        System.out.println(mipsStr);
        if (toDir) {
            buffer.append(mipsStr + "\n");
        }
    }

    private void analysePrintStr() {
        int printStrCnt = 0;
        for (IRCode ir : IRCodeList) {
            if (ir.getIRType().equals("note") && ir.getIRStr().equals("#Start Print")) {
                printCnt++;
                printStrCnt = 1;
            }

            if (ir.getIRType().equals("print")
                    && ir.getVar().getType().equals("str")) {
                String printStr = ir.getVar().getName();
                String strConstName = "print" + printCnt + "_str" + printStrCnt;
                String strConst = strConstName + ": .asciiz" + tab + "\"" + printStr + "\"";
                printStrMap.put(ir, strConstName);
                printStrCnt++;
                addInstr(strConst);
            }
        }
        tabCnt--;
    }

    private void mipsTranslate() {
        addInstr(".data");
        tabCnt++;

        analysePrintStr();

        for (IRCode ir : IRCodeList) {
            if (inMain) {
                analyseIR(ir);

            } else if (inFuncDef) {
                generateFuncDef(ir);

                if (ir.getIRType().equals("note") && ir.getIRStr().equals("#Main Start")) {
                    inFuncDef = false;
                    addInstr("main:");
                    inMain = true;
                    tabCnt++;
                }
            } else {
                addDecl(ir);

                if (ir.getIRType().equals("note") && ir.getIRStr().equals("#Start FuncDecl")) {
                    inFuncDef = true;
                    addInstr(".text");
//                    todo: 初始化除法优化

                    addInstr(tab + "j main");
                    inDecl = false;
                }
            }
        }
        addProgramEnd();
        return;
    }

    private void generateFuncDef(IRCode ir) {
        String type = ir.getIRType();

        switch (type) {
            case "funcDecl":
                addInstr("Func_" + ir.getName() + ":");
                SymbolNode scope = ir.getScope();

                Symbol symbol = scope.searchSymbol(ir.getName());
                innerFunc = true;
                inFuncOffset = 0;
                curFunc = symbol;
                tabCnt++;
                break;

            case "note":
                if (ir.getIRStr().equals("#End of a Function")) {
                    if (inFuncOffset != 0) {
                        addInstr("addi $sp, $sp, " + inFuncOffset); //注意回复sp指针！处理无return的函数情况
                    }
                    addInstr("jr $ra");
                    addInstr("");

                    innerFunc = false;
                    tabCnt--;
                    register.reset();

                } else {
                    generateNote(ir);
                }
                break;
            default:
                analyseIR(ir);
                break;
        }
    }

    private void addDecl(IRCode ir) {
        analyseIR(ir);
        return;
    }

    private void analyseIR(IRCode ir) {
        String IRType = ir.getIRType();
        switch (IRType) {
            case "intDecl":
                generateIntDecl(ir);
                break;
            case "assign_ret":
                generateAssignRet(ir);
                break;
            case "assign":
                generateAssign(ir);
                break;
            case "assign2":
                generateAssign2(ir);
                break;
            case "push":
                generatePush(ir);
                break;
            case "print":
                generatePrint(ir);
                break;
            case "getint":
                generateGetint(ir);
                break;
            case "return":
                generateReturn(ir);
                break;
            case "call":
                generateCall(ir);
                break;
            case "arrayDecl":
                generateArrDecl(ir);
//                todo: 完成数组声明
                break;
            case "label":
            case "note":
                generateNote(ir);
                break;
            case "jump":
                addInstr("j " + ir.getJumpLabel());
                break;
            case "branch"://todo 完成跳转
                generateBranch(ir);
                break;
            case "setcmp":
                generateCmp(ir);
                break;
            default:
                break;
        }
    }

    private String reverseCmpOp(String cmp) {
        switch (cmp) {
            case "beq":
                return "beq";
            case "bne":
                return "bne";
            case "bge":
                return "blt";
            case "ble":
                return "bgt";
            case "bgt":
                return "ble";
            case "blt":
                return "bge";
            case "sge":
                return "slt";
            case "sgt":
                return "sle";
            case "sle":
                return "sgt";
            case "slt":
                return "sge";
            case "seq":
                return "seq";
            case "sne":
                return "sne";
            default:
                break;
        }
        return null;
    }

    private void generateBranch(IRCode ir) {
        String op = ir.getBranchStr();
        String jumpLabel = ir.getJumpLabel();

        Variable src1 = ir.getSrc1();
        Variable src2 = ir.getSrc2();

        if (src1.getType().equals("var") && src2.getType().equals("var")) {
            String src1Reg;
            String src2Reg;

            boolean src1IsTmp = false;
            boolean src2IsTmp = false;

            int tmpRegForSrc1 = 0;
            int tmpRegForSrc2 = 0;

            if (src1.getSymbolKind()) {
                Symbol src1Symbol = src1.getSymbol();
                if (src1Symbol.isConst()) {
                    src1Reg = searchRegName(src1);
                    addInstr("li $" + src1Reg + ", " + src1Symbol.getValue());
                } else {
                    if (innerFunc && !src1Symbol.isGlobal()) {
                        tmpRegForSrc1 = register.applyTmpRegister();
                        src1Reg = register.getRegName(tmpRegForSrc1);
                        src1IsTmp = true;
                        loadInFunVarFromSP(src1, src1Reg);
                    } else if (src1Symbol.isGlobal() && !src1Symbol.getType().equals(SymbolType.Func)) {
                        String globalVarName = src1Symbol.getName();
                        src1Reg = searchRegName(src1);
                        addInstr("lw $" + src1Reg + ", Global_" + globalVarName);
                    } else {
                        src1Reg = searchRegName(src1);
                        loadMainFuncVarFromSP(src1Reg, src1Symbol);
                    }
                }

            } else {
                src1Reg = searchRegName(src1);
            }

            if (src2.getSymbolKind()) {
                Symbol src2Symbol = src2.getSymbol();
                if (src2Symbol.isConst()) {
                    src2Reg = searchRegName(src2);
                    addInstr("li $" + src2Reg + ", " + src2Symbol.getValue());
                } else {
                    if (innerFunc && !src2Symbol.isGlobal()) {
                        tmpRegForSrc2 = register.applyTmpRegister();
                        src2Reg = register.getRegName(tmpRegForSrc2);
                        src2IsTmp = true;
                        loadInFunVarFromSP(src2, src2Reg);
                    } else if (src2Symbol.isGlobal() && !src2Symbol.getType().equals(SymbolType.Func)) {
                        String globalVarName = src2Symbol.getName();
                        src2Reg = searchRegName(src2);
                        addInstr("lw $" + src2Reg + ", Global_" + globalVarName);
                    } else {
                        src2Reg = searchRegName(src2);
                        loadMainFuncVarFromSP(src2Reg, src2Symbol);
                    }
                }

            } else {
                src2Reg = searchRegName(src2);
            }

            addInstr(op + " $" + src1Reg + ", $" + src2Reg + ", " + jumpLabel);

            if (src1IsTmp) {
                register.freeTmpRegister(tmpRegForSrc1);
            } else {
                register.freeRegister(src1);
            }

            if (src2IsTmp) {
                register.freeTmpRegister(tmpRegForSrc2);
            } else {
                register.freeRegister(src2);
            }

        } else if ((src1.getType().equals("var") && src2.getType().equals("num")) || (src1.getType().equals("num") && src2.getType().equals("var"))) {
            boolean reverse = false;
            if (src2.getType().equals("var")) {
                Variable tmp = src1;
                src1 = src2;
                src2 = tmp;
                reverse = true;
            }

            int num = src2.getNum();
            String src1Reg;
            boolean src1IsTmp = false;
            int tmpRegForSrc1 = 0;

            if (src1.getSymbolKind()) {
                Symbol src1Symbol = src1.getSymbol();
                if (src1Symbol.isConst()) {
                    src1Reg = searchRegName(src1);
                    addInstr("li $" + src1Reg + ", " + src1Symbol.getValue());
                } else {
                    if (innerFunc && !src1Symbol.isGlobal()) {
                        tmpRegForSrc1 = register.applyTmpRegister();
                        src1Reg = register.getRegName(tmpRegForSrc1);
                        src1IsTmp = true;
                        loadInFunVarFromSP(src1, src1Reg);
                    } else if (src1Symbol.isGlobal() && !src1Symbol.getType().equals(SymbolType.Func)) {
                        String globalVarName = src1Symbol.getName();
                        src1Reg = searchRegName(src1);
                        addInstr("lw $" + src1Reg + ", Global_" + globalVarName);
                    } else {
                        src1Reg = searchRegName(src1);
                        loadMainFuncVarFromSP(src1Reg, src1Symbol);
                    }
                }

            } else {
                src1Reg = searchRegName(src1);
            }

            if (reverse) {
                op = reverseCmpOp(op);
            }

           addInstr(op + " $" + src1Reg + ", " + num + ", " + jumpLabel);
            if (src1IsTmp) {
                register.freeTmpRegister(tmpRegForSrc1);
            } else {
                register.freeRegister(src1);
            }

        } else {
            int num1 = src1.getNum();
            int num2 = src2.getNum();

            boolean jump = false;
            switch (op) {
                case "beq":
                    if (num1 == num2) {
                        jump = true;
                    }
                    break;
                case "bne":
                    if (num1 != num2) {
                        jump = true;
                    }
                    break;
                case "bge":
                    if (num1 >= num2) {
                        jump = true;
                    }
                    break;
                case "ble":
                    if (num1 <= num2) {
                        jump = true;
                    }
                    break;
                case "bgt":
                    if (num1 > num2) {
                        jump = true;
                    }
                    break;
                case "blt":
                    if (num1 < num2) {
                        jump = true;
                    }
                    break;
                default:
                    break;
            }

            if (jump) {
                addInstr("# jump branch always true.");
                addInstr("j " + jumpLabel);
            } else {
                addInstr("# jump branch always false.");
            }
        }
    }

    private void generateCmp(IRCode ir) {
        String op = ir.getOp();

        Variable src1 = ir.getSrc1();
        Variable src2 = ir.getSrc2();
        Variable dst = ir.getDst();

        String dstReg = searchRegName(dst);

        if (src1.getType().equals("var") && src2.getType().equals("var")) {
            String src1Reg;
            String src2Reg;

            boolean src1IsTmp = false;
            boolean src2IsTmp = false;

            int tmpRegForSrc1 = 0;
            int tmpRegForSrc2 = 0;

            if (src1.getSymbolKind()) {
                Symbol src1Symbol = src1.getSymbol();
                if (src1Symbol.isConst()) {
                    src1Reg = searchRegName(src1);
                    addInstr("li $" + src1Reg + ", " + src1Symbol.getValue());
                } else {
                    if (innerFunc && !src1Symbol.isGlobal()) {
                        tmpRegForSrc1 = register.applyTmpRegister();
                        src1Reg = register.getRegName(tmpRegForSrc1);
                        src1IsTmp = true;
                        loadInFunVarFromSP(src1, src1Reg);
                    } else if (src1Symbol.isGlobal() && !src1Symbol.getType().equals(SymbolType.Func)) {
                        String globalVarName = src1Symbol.getName();
                        src1Reg = searchRegName(src1);
                        addInstr("lw $" + src1Reg + ", Global_" + globalVarName);
                    } else {
                        src1Reg = searchRegName(src1);
                        loadMainFuncVarFromSP(src1Reg, src1Symbol);
                    }
                }

            } else {
                src1Reg = searchRegName(src1);
            }

            if (src2.getSymbolKind()) {
                Symbol src2Symbol = src2.getSymbol();
                if (src2Symbol.isConst()) {
                    src2Reg = searchRegName(src2);
                    addInstr("li $" + src2Reg + ", " + src2Symbol.getValue());
                } else {
                    if (innerFunc && !src2Symbol.isGlobal()) {
                        tmpRegForSrc2 = register.applyTmpRegister();
                        src2Reg = register.getRegName(tmpRegForSrc2);
                        src2IsTmp = true;
                        loadInFunVarFromSP(src2, src2Reg);
                    } else if (src2Symbol.isGlobal() && !src2Symbol.getType().equals(SymbolType.Func)) {
                        String globalVarName = src2Symbol.getName();
                        src2Reg = searchRegName(src2);
                        addInstr("lw $" + src2Reg + ", Global_" + globalVarName);
                    } else {
                        src2Reg = searchRegName(src2);
                        loadMainFuncVarFromSP(src2Reg, src2Symbol);
                    }
                }

            } else {
                src2Reg = searchRegName(src2);
            }

            addInstr(op + " $" + dstReg + ", $" + src1Reg + ", $" + src2Reg);

            if (src1IsTmp) {
                register.freeTmpRegister(tmpRegForSrc1);
            } else {
                register.freeRegister(src1);
            }

            if (src2IsTmp) {
                register.freeTmpRegister(tmpRegForSrc2);
            } else {
                register.freeRegister(src2);
            }

        } else if ((src1.getType().equals("var") && src2.getType().equals("num")) || (src1.getType().equals("num") && src2.getType().equals("var"))) {
            boolean reverse = false;
            if (src2.getType().equals("var")) {
                Variable tmp = src1;
                src1 = src2;
                src2 = tmp;
                reverse = true;
            }

            int num = src2.getNum();
            String src1Reg;
            boolean src1IsTmp = false;
            int tmpRegForSrc1 = 0;

            if (src1.getSymbolKind()) {
                Symbol src1Symbol = src1.getSymbol();
                if (src1Symbol.isConst()) {
                    src1Reg = searchRegName(src1);
                    addInstr("li $" + src1Reg + ", " + src1Symbol.getValue());
                } else {
                    if (innerFunc && !src1Symbol.isGlobal()) {
                        tmpRegForSrc1 = register.applyTmpRegister();
                        src1Reg = register.getRegName(tmpRegForSrc1);
                        src1IsTmp = true;
                        loadInFunVarFromSP(src1, src1Reg);
                    } else if (src1Symbol.isGlobal() && !src1Symbol.getType().equals(SymbolType.Func)) {
                        String globalVarName = src1Symbol.getName();
                        src1Reg = searchRegName(src1);
                        addInstr("lw $" + src1Reg + ", Global_" + globalVarName);
                    } else {
                        src1Reg = searchRegName(src1);
                        loadMainFuncVarFromSP(src1Reg, src1Symbol);
                    }
                }

            } else {
                src1Reg = searchRegName(src1);
            }

            if (reverse) {
                op = reverseCmpOp(op);
            }

            int tmpRegNo = register.applyTmpRegister();
            String tmp = register.getRegName(tmpRegNo);
            addInstr("li $" + tmp + ", " + num);
            addInstr(op + " $" + dstReg + ", $" + src1Reg + ", $" + tmp);

            if (src1IsTmp) {
                register.freeTmpRegister(tmpRegForSrc1);
            } else {
                register.freeRegister(src1);
            }

        } else {
            int num1 = src1.getNum();
            int num2 = src2.getNum();

            boolean res = false;
            switch (op) {
                case "sge":
                    if (num1 >= num2) {
                        res = true;
                    }
                    break;
                case "sgt":
                    if (num1 > num2) {
                        res = true;
                    }
                    break;
                case "sle":
                    if (num1 <= num2) {
                        res = true;
                    }
                    break;
                case "slt":
                    if (num1 < num2) {
                        res = true;
                    }
                    break;
                case "seq":
                    if (num1 == num2) {
                        res = true;
                    }
                    break;
                case "sne": //暂无用
                    if (num1 != num2) {
                        res = true;
                    }
                    break;
                default:
                    break;
            }

            if (res) {
                addInstr("# RelExp judge always true.");
                addInstr("li $" + dstReg + ", 1");
            } else {
                addInstr("# RelExp judge always false.");
                addInstr("li $" + dstReg + ", 0");
            }
        }
    }

    private void generateArrDecl(IRCode ir) {
        String name = ir.getName();
        int size;
        size = ir.calDim();
        if (ir.isGlobal()) {//全局
            tabCnt++;
            String arrDeclInitStr = "Global_" + name + ": .word ";
            if (ir.isInit()) {
                addInstr(arrDeclInitStr + ir.initArrToString());
            } else {
                addInstr(arrDeclInitStr + "0:" + size);
            }
            tabCnt--;
        } else {
            int addrOffsetSize = size * 4;
            Symbol symbol = ir.getSymbol();
            ir.getScope().increaseBlockOffset(addrOffsetSize);

            if (innerFunc) {
                inFuncOffset += addrOffsetSize;
                symbol.setAddrOffset(-inFuncOffset);
            } else {
                spOffset += addrOffsetSize;
                symbol.setAddrOffset(-spOffset);
            }

            addInstr("# init local array");
            addInstr("addi $sp, $sp, " + (-addrOffsetSize));

            if (ir.isInit()) {//todo 也许可以删去？
                int regNo = register.applyTmpRegister();
                String regName = register.getRegName(regNo);

                ArrayList<Integer> initNumList = ir.getInitList();
                for (int i = 0; i < initNumList.size(); i++) {
                    int offset = i * 4;
                    int num = initNumList.get(i);
                    addInstr("li $" + regName + ", " + num);
                    addInstr("sw $" + regName + ", " + offset + "($sp)");
                }
                register.freeTmpRegister(regNo);
            }
        }
    }

    private void loadInFunVarFromSP(Variable var, String regName) {
        String name = var.getName();
        if (curFunc.varNameInParaList(name)) {
            int paraIndex = calFuncParaOffset(name);
            addInstr("lw $" + regName + ", " + paraIndex + "($sp)");
        } else {
            Symbol symbol = var.getSymbol();
            int localVarSpOffset = calFuncLocalOffset(symbol);
            addInstr("lw $" + regName + ", " + localVarSpOffset + "($sp)");
        }
        var.setCurReg(register.getRegNum(regName));
        var.getSymbol().setCurReg(register.getRegNum(regName));
    }

    private void loadInFunVarFromSP(Variable var, String regName, InstrList pushInstr) {
        String name = var.getName();
        if (curFunc.varNameInParaList(name)) {
            int paraSpOffset = calFuncParaOffset(name);
            Instr lwInstr = new Instr("lw $" + regName + ", ", paraSpOffset, "($sp)", "actreg");
            pushInstr.addInstr(lwInstr);
        } else {
            Symbol symbol = var.getSymbol();
            int localVarSpOffset = calFuncLocalOffset(symbol);

            Instr lwInstr = new Instr("lw $" + regName + ", ", localVarSpOffset, "($sp)", "actreg");
            pushInstr.addInstr(lwInstr);
        }
    }

    private void loadMainFuncVarFromSP(String regName, Symbol symbol) {
        int symAddr = symbol.getSpBaseHex() + symbol.getAddrOffset();
        String hexAddr = intToHexStr(symAddr);
        addInstr("lw $" + regName + ", " + hexAddr);
    }

    private void loadMainFuncVarFromSP(String regName, Symbol symbol, InstrList pushInstr) {
        int symAddr = symbol.getSpBaseHex() + symbol.getAddrOffset();
        String hexAddr = intToHexStr(symAddr);

        Instr hexInstr = new Instr("lw $" + regName + ", " + hexAddr);
        pushInstr.addInstr(hexInstr);
    }

    private void addProgramEnd() {
        addInstr("#End Program");
        addInstr("li $v0, 10");
        addInstr("syscall");
    }

    private void generateCall(IRCode ir) {
        String funName = ir.getLabelString();

        ArrayList<Integer> activeReg = register.getActiveRegList();
        int activeRegCnt = activeReg.size();
        int activeRegIndex = activeRegCnt * 4;
        int cnt = 0;

        for (int i = activeRegCnt - 1; i >= 0; i--) {
            cnt++;
            String regName = register.getRegName(activeReg.get(i));
            addInstr("sw $" + regName + ", -" + cnt * 4 + "($sp)");
//            System.out.println("Push Active Reg: " + regName);
        }
        if (cnt != 0) {
            addInstr("addi $sp, $sp, -" + cnt * 4);
        }

        SymbolNode scope = ir.getScope();
        Symbol symbol = scope.searchSymbol(funName);
        int paraNum = symbol.getParaNum();
        int paraAddrOffset = (paraNum + 1) * 4;

        int pushOffset = 0;
        ArrayList<Integer> freeRegNoList = new ArrayList<>();
        for (int i = pushQueue.size() - paraNum; i < pushQueue.size(); i++) {
            InstrList pushInstrList = pushQueue.get(i);
            pushOffset -= 4;
            for (Instr instr : pushInstrList.getInstrList()) {
                if (instr.isPushOffset()) {
                    addInstr(instr.toString(pushOffset));
                } else if (instr.isActiveRegOffset()) {
                    addInstr(instr.toString(activeRegIndex));
                } else {
                    addInstr(instr.toString(0));
                }
                if (instr.isHasRetReg()) {
                    freeRegNoList.add(instr.getFreeRegNum());
                }
            }
        }

        addInstr("addi $sp, $sp, " + (-paraAddrOffset));
        addInstr("sw $ra, ($sp)");       //保存$ra，为处理递归准备

        addInstr("jal " + "Func_" + funName);       //todo 未处理函数体内局部变量导致的sp移动，需return时+sp

        addInstr("lw $ra, ($sp)");       //加载$ra，为处理递归准备
        addInstr("addi $sp, $sp, " + paraAddrOffset);    //移动push para的sp偏移

        cnt = 0;
        for (int i = 0; i < activeRegCnt; i++) {   //倒着推进去，正着取出来
            String regname = register.getRegName(activeReg.get(i));
            addInstr("lw $" + regname + ", " + cnt * 4 + "($sp)");
//            System.out.println("Load Active Reg :" + regname);
            cnt++;
        }
        if (cnt != 0) {
            addInstr("addi $sp, $sp, " + cnt * 4);

        }

        for (int num : freeRegNoList) {
            register.freeTmpRegister(num);
        }

        int size = pushQueue.size();
        for (int i = size - 1; i >= size - paraNum; i--) {
            pushQueue.remove(i);
        }
    }

    private void generateReturn(IRCode ir) {
        if (inMain) {
//            addProgramEnd();
            return;
        }

        if (ir.isVoidReturn()) {
            if (inFuncOffset == 0) {
                addInstr("#addi $sp, $sp, 0");
            } else {
                addInstr("addi $sp, $sp, " + inFuncOffset);
            }

            addInstr("jr $ra");
            addInstr("");
            return;
        }

        Variable var = ir.getVar();
        String type = var.getType();

        if (type.equals("num")) {
            int num = var.getNum();
            addInstr("li $v0, " + num);
        } else if (type.equals("var")) {
            if (var.getSymbolKind()) {
                Symbol varSymbol = var.getSymbol();
                if (varSymbol.isConst()) {
                    addInstr("li $v0, " + varSymbol.getValue());
                } else {
                    if (innerFunc && !varSymbol.isGlobal()) {
                        loadInFunVarFromSP(var, "v0");
                    } else if (varSymbol.isGlobal() && varSymbol.getType() != SymbolType.Func) {
                        String globalVarName = varSymbol.getName();
                        addInstr("lw $v0, " + "Global_" + globalVarName);
                    } else {
                        loadMainFuncVarFromSP("v0", varSymbol);
                    }
                }
            } else {
                String varRegName = searchRegName(var);
                addInstr("move $v0, $" + varRegName);
                register.freeRegister(var);
            }
        }

        if (inFuncOffset == 0) {
            addInstr("#addi $sp, $sp, 0");
        } else {
            addInstr("addi $sp, $sp, " + inFuncOffset);
        }
        addInstr("jr $ra");
        addInstr("");
    }


    private void generatePush(IRCode ir) {
        InstrList pushInstr = new InstrList();

        Variable var = ir.getVar();
        String type = var.getType();
        if (type.equals("num")) {
            int num = var.getNum();
            int tmpRegNo = register.applyTmpRegister();
            String tmpRegName = register.getRegName(tmpRegNo);

            pushInstr.addInstr(new Instr("li $" + tmpRegName + ", " + num));
            pushInstr.addInstr(new Instr("sw $" + tmpRegName + ", ", 0, "($sp)", "push"));

            register.freeTmpRegister(tmpRegNo);
        } else if (type.equals("var")) {
            if (var.getSymbolKind()) {
                Symbol varSymbol = var.getSymbol();
                int tmpRegNo = register.applyTmpRegister();
                String tmpRegName = register.getRegName(tmpRegNo);

                if (innerFunc && !varSymbol.isGlobal()) {
                    loadInFunVarFromSP(var, tmpRegName, pushInstr);
                } else if (varSymbol.isGlobal() && varSymbol.getType() != SymbolType.Func) {
                    String globalVarName = varSymbol.getName();
                    pushInstr.addInstr(new Instr("lw $" + tmpRegName + ", Global_" + globalVarName));
                } else {
                    int symbolAddr = varSymbol.getSpBaseHex() + varSymbol.getAddrOffset();
                    String hexAddr = intToHexStr(symbolAddr);
                    Instr hexInstr = new Instr("lw $" + tmpRegName + ", " + hexAddr);
                    pushInstr.addInstr(hexInstr);
                }

                pushInstr.addInstr(new Instr("sw $" + tmpRegName + ", ", 0, "($sp)", "push"));
                Instr last = new Instr("#push an symbol kind var end.");
                last.setHasRetReg(true);
                last.setFreeRegNum(tmpRegNo);
                pushInstr.addInstr(last);
            } else {
                String varRegName = searchRegName(var);
                pushInstr.addInstr(new Instr("sw $" + varRegName + ", ", 0, "($sp)", "push"));

                Instr last = new Instr("#push an nonsymbol(tmp)kind var end.");  //用一个#标签包装处理
                last.setHasRetReg(true);            //归还tmpregno
                last.setFreeRegNum(var.getCurReg());  //释放的寄存器编号 //todo 可能参数没在寄存器的情况?
                pushInstr.addInstr(last);
            }
        } else if (type.equals("array")) { //todo: 处理数组
            Symbol arrSymbol = var.getSymbol();
            int tmpRegNo = register.applyTmpRegister();
            String tmpRegName = register.getRegName(tmpRegNo);
            if (innerFunc && !arrSymbol.isGlobal()) { //函数内
                loadInFunArrayVarFromSP(var, tmpRegName, pushInstr);

            } else if (arrSymbol.isGlobal() && arrSymbol.getType() != SymbolType.Func) {
                String globalArrayName = arrSymbol.getName();

                if (var.getVar() != null) {     //处理如b[i]或b[1]等含偏移情况
                    Variable offset = var.getVar();     //此处offset为array 的 index, 命名统一取名offset

                    if (offset.getType().equals("num")) {    //offset = 数字
                        int arrOffset = offset.getNum() * arrSymbol.getDimen2() * 4;
                        pushInstr.addInstr(new Instr("la $" + tmpRegName + ", Global_" + globalArrayName + " + " + arrOffset));
                    } else {    //offset = var变量
                        String offsetRegName = varToRegNameForFunc(offset, pushInstr);
                        pushInstr.addInstr(new Instr("sll $" + offsetRegName + ", $" + offsetRegName + ", 2"));   //！！！需要乘以4
                        pushInstr.addInstr(new Instr("li $" + tmpRegName + ", " + arrSymbol.getDimen2()));
                        pushInstr.addInstr(new Instr("mult $" + offsetRegName + ", $" + tmpRegName));
                        pushInstr.addInstr(new Instr("mflo $" + tmpRegName));

                        pushInstr.addInstr(new Instr("la $" + tmpRegName + ", Global_" + globalArrayName + "($" + tmpRegName + ")"));

                        //以下处理： reg.freeReg(offset);
                        if (offset.getCurReg() != -1) {
                            //reg.freeReg(offset);  //统一释放存数组偏移量的reg.此处不能放

                            Instr last = new Instr("#push/la an hasoffset global array end.");  //用一个#标签包装处理
                            last.setHasRetReg(true);        //最后一个语句，附加一个归还offsetReg操作
                            last.setFreeRegNum(offset.getCurReg());  //todo getCurReg方法存疑
                            pushInstr.addInstr(last);

                        } else {
                            pushInstr.addInstr(new Instr("#push an hasoffset global array end."));  //用一个#标签包装处理);
                        }
                    }

                } else {
                    pushInstr.addInstr(new Instr("la $" + tmpRegName + ", Global_" + globalArrayName));
                }
            } else {//局部数组
                loadMainFuncArraySymbolFromSp(tmpRegName, arrSymbol, pushInstr, var);
            }

            Instr pushStr = new Instr("sw $" + tmpRegName + ", ", 0, "($sp)", "push");


            pushInstr.addInstr(pushStr);
            Instr last = new Instr("#push an global array end.");  //用一个#标签包装处理
            last.setHasRetReg(true);            //归还tmpregno
            last.setFreeRegNum(tmpRegNo);  //释放的寄存器编号
            pushInstr.addInstr(last);
        } else {
            System.err.println("Error: push type error!");
        }
        pushQueue.add(pushInstr);
    }

    private void loadMainFuncArraySymbolFromSp(String regName, Symbol symbol, InstrList pushInstr, Variable var) {
        int symbolAddr = symbol.getSpBaseHex() + symbol.getAddrOffset();

        if (var.getVar() != null) {     //处理如array[1]或array[i]情况
            Variable offset = var.getVar();

            //分类arr[1]或arr[i]处理
            if (offset.getType().equals("num")) {    //offset = 数字
                int arroffset = offset.getNum() * var.getSymbol().getDimen2() * 4;    //偏移量=index * dimen2 * 4
                symbolAddr += arroffset;

                String hexAddr = intToHexStr(symbolAddr);
                Instr hexInstr = new Instr("li $" + regName + ", " + hexAddr);
                pushInstr.addInstr(hexInstr);

            } else {    //offset = var变量
                int tmpregNo = register.applyTmpRegister();
                String tmpregName = register.getRegName(tmpregNo);   //申请临时寄存器

                String offsetRegName = varToRegNameForFunc(offset, pushInstr);
                pushInstr.addInstr(new Instr("sll $" + offsetRegName + ", $" + offsetRegName + ", 2"));   //！！！需要乘以4
                pushInstr.addInstr(new Instr("li $" + tmpregName + ", " + var.getSymbol().getDimen2()));
                pushInstr.addInstr(new Instr("mult $" + offsetRegName + ", $" + tmpregName));
                pushInstr.addInstr(new Instr("mflo $" + tmpregName));

                //tmpregname是此时算出的偏移量
                pushInstr.addInstr(new Instr("addi $" + regName + ", $" + tmpregName + ", " + symbolAddr));

                //以下处理： reg.freeReg(offset);
                if (offset.getCurReg() != -1) {
                    //reg.freeReg(offset);  //统一释放存数组偏移量的reg.此处不能放
                    //todo la有点问题，好像本质就是move
                    Instr last = new Instr("#push an local array end.");  //用一个#标签包装处理
                    last.setHasRetReg(true);        //最后一个语句，附加一个归还offsetReg操作
                    last.setFreeRegNum(offset.getCurReg());  //todo getCurReg方法存疑
                    pushInstr.addInstr(last);

                } else {
                    Instr last = new Instr("#push an local array end.");  //用一个#标签包装处理
                    pushInstr.addInstr(last);
                }
            }

        } else {  //无偏移量
            String hexAddr = intToHexStr(symbolAddr);
            Instr hexMips = new Instr("li $" + regName + ", " + hexAddr);
            pushInstr.addInstr(hexMips);
        }
    }

    //todo 待校验
    private void loadInFunArrayVarFromSP(Variable var, String regName, InstrList pushInstr) {
        String name = var.getName();
        Symbol arraySymbol = var.getSymbol();
        if (curFunc.varNameInParaList(name)) {
            int paraSpOffset = calFuncParaOffset(name);

            if (var.getVar() != null) {
                Variable offset = var.getVar();
                String offsetType = offset.getType();

                if (offsetType.equals("num")) {
                    int arrOffset = offset.getNum() * arraySymbol.getDimen2() * 4;
                    paraSpOffset += arrOffset;
                    Instr lwInstr = new Instr("lw $" + regName + ", ", paraSpOffset, "($sp)", "actreg");
                    pushInstr.addInstr(lwInstr);
                } else { //offset为变量
                    int tmpRegNo = register.applyTmpRegister();
                    String tmpRegName = register.getRegName(tmpRegNo);

                    String offsetRegName = loadAnyVarToReg(offset, pushInstr);
                    pushInstr.addInstr(new Instr("sll $" + offsetRegName + ", $" + offsetRegName + ", 2"));   //！！！需要乘以4
                    pushInstr.addInstr(new Instr("li $" + tmpRegName + ", " + arraySymbol.getDimen2()));
                    pushInstr.addInstr(new Instr("mult $" + offsetRegName + ", $" + tmpRegName));
                    pushInstr.addInstr(new Instr("mflo $" + tmpRegName));

                    //先把函数参数中array首地址加载到regname
                    pushInstr.addInstr(new Instr("lw $" + regName + ", ", paraSpOffset, "($sp)", "actreg"));

                    //之后将regname中的地址增加偏移量(即$tmpregname)
                    pushInstr.addInstr(new Instr("add $" + regName + ", $" + regName + ", $" + tmpRegName));

                    //以下处理： register.freeRegister(offset);
                    if (offset.getCurReg() != -1) {
                        //register.freeRegister(offset);  //统一释放存数组偏移量的reg.此处不能放

                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        last.setHasRetReg(true);       //最后一个语句，附加一个归还offsetReg操作
                        last.setFreeRegNum(offset.getCurReg());  //todo getCurReg方法存疑
                        pushInstr.addInstr(last);

                    } else {
                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        pushInstr.addInstr(last);
                    }

                }
            } else {
                Instr lwInstr = new Instr("lw $" + regName + ", ", paraSpOffset, "($sp)", "actreg");  //特意用一个Instr包装处理
                pushInstr.addInstr(lwInstr);
            }
        } else {    //函数内+局部变量需要la
            int localVarSpOffset = calFuncLocalOffset(arraySymbol);
            if (var.getVar() != null) {
                Variable offset = var.getVar();
                if (offset.getType().equals("num")) {
                    int arrOffset = offset.getNum() * arraySymbol.getDimen2() * 4;    //偏移量=index * dimen2 * 4
                    localVarSpOffset += arrOffset;
                    //local variable，正常la传地址
                    Instr instr = new Instr("la $" + regName + ", ", localVarSpOffset, "($sp)", "actreg");  //特意用一个Mips包装处理
                    pushInstr.addInstr(instr);

                } else { //offset为变量
                    int tmpregno = register.applyTmpRegister();
                    String tmpregname = register.getRegName(tmpregno);   //申请临时寄存器

                    String offsetregname = varToRegNameForFunc(offset, pushInstr);
                    pushInstr.addInstr(new Instr("sll $" + offsetregname + ", $" + offsetregname + ", 2"));   //！！！需要乘以4
                    pushInstr.addInstr(new Instr("li $" + tmpregname + ", " + arraySymbol.getDimen2()));
                    pushInstr.addInstr(new Instr("mult $" + offsetregname + ", $" + tmpregname));
                    pushInstr.addInstr(new Instr("mflo $" + tmpregname));

                    pushInstr.addInstr(new Instr("add $" + tmpregname + ", $" + tmpregname + ", $sp"));
                    pushInstr.addInstr(new Instr("la $" + regName + ", ", localVarSpOffset, "($" + tmpregname + ")", "actreg"));

                    if (offset.getCurReg() != -1) {

                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        last.setHasRetReg(true);        //最后一个语句，附加一个归还offsetReg操作
                        last.setFreeRegNum(offset.getCurReg());
                        pushInstr.addInstr(last);

                    } else {
                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        pushInstr.addInstr(last);
                    }
                }
            } else {
                Instr instr = new Instr("la $" + regName + ", ", localVarSpOffset, "($sp)", "actreg");  //特意用一个Mips包装处理
                pushInstr.addInstr(instr);
            }
        }
    }

    private String loadAnyVarToReg(Variable src1, InstrList pushInStr) {
        String src1Reg;
        if (src1.getSymbolKind()) {
            Symbol src1Symbol = src1.getSymbol();
            if (src1Symbol.isConst()) {
                src1Reg = searchRegName(src1);//todo 存疑？？这里有寄存器吗
                pushInStr.addInstr(new Instr("li $" + src1Reg + ", " + src1Symbol.getValue()));
            } else {
                if (innerFunc && !src1Symbol.isGlobal()) {
                    int tmpReg = register.applyTmpRegister();
                    src1Reg = register.getRegName(tmpReg);

                    loadInFunVarFromSP(src1, src1Reg, pushInStr);
                } else if (src1Symbol.isGlobal() && src1Symbol.getType() != SymbolType.Func) {
                    String globalVarName = src1Symbol.getName();
                    src1Reg = searchRegName(src1);
                    pushInStr.addInstr(new Instr("lw $" + src1Reg + ", Global_" + globalVarName));
                } else {
                    src1Reg = searchRegName(src1);
                    loadMainFuncVarFromSP(src1Reg, src1Symbol, pushInStr);
                }
            }
        } else {
            src1Reg = searchRegName(src1);
        }

        return src1Reg;
    }

    private void generateGetint(IRCode ir) {
        Variable var = ir.getVar();
        String varName = var.getName();
        String type = var.getType();

        addInstr("li $v0, 5");
        addInstr("syscall");

        if (type.equals("var")) {
            if (var.getSymbolKind()) {
                Symbol varSymbol = var.getSymbol();
                if (innerFunc && !varSymbol.isGlobal()) {
                    saveInFuncVar(var, "v0");
                } else if (varSymbol.isGlobal() && varSymbol.getType() != SymbolType.Func) {
                    String globalVarName = varSymbol.getName();
                    addInstr("sw $v0, Global_" + globalVarName);
                } else {
                    saveLocalMainVar(var, "v0");
                }
            } else {
                String saveDst = searchRegName(var);
                addInstr("move $" + saveDst + ", $v0");
            }
        } else if (type.equals("array")) {
//            todo:完成数组解析
            arrGetInt(var, ir);
        } else {
            System.err.println("Error: getint type error!");
        }
    }

    private void arrGetInt(Variable var, IRCode ir) {
        String arrayName = var.getName();
        Symbol arrSymbol = var.getSymbol();

        Variable offset = var.getVar();
        if (arrSymbol.isGlobal()) {    //全局取data段
            if (offset.getType().equals("num")) {    //offset = 数字
                int offsetNum = offset.getNum();
                addInstr("sw $v0, Global_" + arrayName + "+" + offsetNum * 4);   //与oper1主要区别lw变成sw
            } else {    //offset = var变量
                //该函数：将任意variable加载到指定寄存器，oper1、2、dest等均可用
                String offsetRegname = varToRegName(offset);   //todo 不能原来这么简单粗暴;但此方法存疑

                addInstr("sll $" + offsetRegname + ", $" + offsetRegname + ", 2");   //需要乘以4!!!
                addInstr("sw $v0, Global_" + arrayName + "($" + offsetRegname + ")");
            }
        } else {    //局部取 堆栈
            if (innerFunc) {  //array数组在函数内
                if (curFunc.varNameInParaList(arrayName)) {     //数组为 函数参数
                    int tmpregNo1 = register.applyTmpRegister();
                    int tmpregNo2 = register.applyTmpRegister();
                    String tmpRegName1 = register.getRegName(tmpregNo1);       //申请临时寄存器
                    String tmpRegName2 = register.getRegName(tmpregNo2);     //申请临时寄存器2

                    int arraybaseaddroffset = calFuncParaOffset(arrayName);
                    addInstr("lw $" + tmpRegName1 + ", " + intToHexStr(arraybaseaddroffset) + "($sp)");  //取出存放array的基地址,存到tmpregname中

                    if (offset.getType().equals("num")) {    //offset = 数字
                        int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                        addInstr("sw $v0, " + numaddroffset + "($" + tmpRegName1 + ")");
                    } else {    //offset = var变量
                        String offsetregname = varToRegName(offset);   //todo 存疑
                        addInstr("sll $" + tmpRegName2 + ", $" + offsetregname + ", 2");
                        addInstr("add $" + tmpRegName1 + ", $" + tmpRegName1 + ", $" + tmpRegName2);
                        addInstr("sw $v0, ($" + tmpRegName1 + ")");
                    }

                    register.freeTmpRegister(tmpregNo1);
                    register.freeTmpRegister(tmpregNo2);
                } else {    //函数内 + 局部array
                    SymbolNode scope = ir.getScope();
                    Symbol symbol = scope.searchSymbol(arrayName);

                    int localArraySpOffset = calFuncLocalOffset(symbol);   //局部array的首地址
                    if (offset.getType().equals("num")) {    //offset = 数字
                        int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                        localArraySpOffset += numaddroffset;
                        addInstr("sw $v0, " + localArraySpOffset + "($sp)");
                    } else {    //offset = var变量
                        int tmpRegNo = register.applyTmpRegister();
                        String tmpRegName = register.getRegName(tmpRegNo);   //申请临时寄存器

                        String offsetRegName = varToRegName(offset);   //todo 存疑
                        addInstr("sll $" + tmpRegName + ", $" + offsetRegName + ", 2");
                        addInstr("add $" + tmpRegName + ", $" + tmpRegName + ", $sp");
                        addInstr("sw $v0, " + localArraySpOffset + "($" + tmpRegName + ")");

                        register.freeTmpRegister(tmpRegNo);
                    }
                }
            } else {  //array数组在正常结构内
                int arraybaseaddr = arrSymbol.getSpBaseHex() + arrSymbol.getAddrOffset();

                if (offset.getType().equals("num")) {    //offset = 数字
                    int arrayNumAddr = arraybaseaddr + offset.getNum() * 4;
                    String arrOffsetHex = intToHexStr(arrayNumAddr);   //地址格式int转16进制
                    addInstr("sw $v0, " + arrOffsetHex);
                } else {    //offset = var变量
                    int tmpregNo = register.applyTmpRegister();
                    String tmpregname = register.getRegName(tmpregNo);   //申请临时寄存器

                    String offsetregname = varToRegName(offset);   //todo 存疑
                    addInstr("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                    addInstr("sw $v0, " + intToHexStr(arraybaseaddr) + "($" + tmpregname + ")");

                    register.freeTmpRegister(tmpregNo);
                }
            }
        }
        if (offset.getCurReg() != -1) {
            register.freeRegister(offset);  //统一释放存数组偏移量的reg
        }
    }

    private void generatePrint(IRCode ir) { //todo 处理数组
        Variable printVar = ir.getVar();
        String type = printVar.getType();

        if (type.equals("str")) {
            String printStrLoc = printStrMap.get(ir);

            addInstr("li $v0, 4");
            addInstr("la $a0, " + printStrLoc);
            addInstr("syscall");
        } else if (type.equals("num")) {
            int num = printVar.getNum();

            addInstr("li $v0, 1");
            addInstr("li $a0, " + num);
            addInstr("syscall");
        } else if (type.equals("var")) {
            if (printVar.getSymbolKind()) {
                Symbol printVarSymbol = printVar.getSymbol();
                if (printVarSymbol.isConst()) {
                    addInstr("li $v0, 1");
                    addInstr("li $a0, " + printVarSymbol.getValue());
                    addInstr("syscall");
                } else {
                    if (innerFunc && !printVarSymbol.isGlobal()) {    //函数内+symbol需要lw //todo 必须先判定，防止全局变量覆盖局部变量
                        addInstr("li $v0, 1");
                        loadInFunVarFromSP(printVar, "a0");       //包装从函数体sp读取到reg
                        addInstr("syscall");

                    } else if (printVarSymbol.isGlobal() && printVarSymbol.getType() != SymbolType.Func) {  //还要判断不是func返回值
                        String globalvarname = printVarSymbol.getName();

                        addInstr("li $v0, 1");
                        addInstr("lw $a0" + ", Global_" + globalvarname);  //todo 也许不用加$zero
                        addInstr("syscall");

                    } else {
                        addInstr("li $v0, 1");
                        loadMainFuncVarFromSP("a0", printVarSymbol);
                        addInstr("syscall");
                    }
                }
            } else {
                String varRegName = searchRegName(printVar);

                addInstr("li $v0, 1");
                addInstr("move $a0, $" + varRegName);
                addInstr("syscall");

                register.freeRegister(printVar);
            }
        }
    }


    private void generateAssign(IRCode ir) {//todo isConst判断修改

        Variable dst = ir.getDst();
        Variable src1 = ir.getSrc1();
        Variable src2 = ir.getSrc2();
        String op = ir.getOp();
        String type1 = src1.getType();
        String type2 = src2.getType();

        String dstReg = searchRegName(dst);

        if (type1.equals("var") && type2.equals("var")) {
            boolean src1RegIsTemp = false;
            boolean src2RegIsTemp = false;

            String src1Reg = "";
            String src2Reg = "";

            int tmpRegForSrc1 = 0;
            int tmpRegForSrc2 = 0;

            if (src1.getSymbolKind()) {
                Symbol src1Symbol = src1.getSymbol();
                if (src1.getType().equals("num")) {
                    src1Reg = searchRegName(src1);
                    addInstr("li $" + src1Reg + ", " + src1.getNum());
                } else {
                    if (innerFunc && !src1Symbol.isGlobal()) {
                        tmpRegForSrc1 = register.applyTmpRegister();
                        src1Reg = register.getRegName(tmpRegForSrc1);
                        src1RegIsTemp = true;

                        loadInFunVarFromSP(src1, src1Reg);//从sp总读取值
                    } else if (src1Symbol.isGlobal() && src1Symbol.getType() != SymbolType.Func) {
                        String globalVarName = src1Symbol.getName();
                        src1Reg = searchRegName(src1);
                        addInstr("lw $" + src1Reg + ", Global_" + globalVarName);
                    } else {
                        src1Reg = searchRegName(src1);
                        loadMainFuncVarFromSP(src1Reg, src1Symbol);
                    }
                }
            } else {
                src1Reg = searchRegName(src1);
            }

            if (src2.getSymbolKind()) {
                Symbol src2Symbol = src2.getSymbol();
                if (src2.getType().equals("num")) {
                    src2Reg = searchRegName(src2);
                    addInstr("li $" + src2Reg + ", " + src2.getNum());
                } else {
                    if (innerFunc && !src2Symbol.isGlobal()) {
                        tmpRegForSrc2 = register.applyTmpRegister();
                        src2Reg = register.getRegName(tmpRegForSrc2);
                        src2RegIsTemp = true;

                        loadInFunVarFromSP(src2, src2Reg);//从sp总读取值
                    } else if (src2Symbol.isGlobal() && src2Symbol.getType() != SymbolType.Func) {
                        String globalVarName = src2Symbol.getName();
                        src2Reg = searchRegName(src2);
                        addInstr("lw $" + src2Reg + ", Global_" + globalVarName);
                    } else {
                        src2Reg = searchRegName(src2);
                        loadMainFuncVarFromSP(src2Reg, src2Symbol);
                    }
                }
            } else {
                src2Reg = searchRegName(src2);
            }

            switch (op) {
                case "+":
                    addInstr("addu $" + dstReg + ", $" + src1Reg + ", $" + src2Reg);
                    break;
                case "-":
                    addInstr("subu $" + dstReg + ", $" + src1Reg + ", $" + src2Reg);
                    break;
                case "*":
                    addInstr("mult $" + src1Reg + ", $" + src2Reg);
                    addInstr("mflo $" + dstReg);
                    break;
                case "/":
                    addInstr("div $" + dstReg + ", $" + src1Reg + ", $" + src2Reg);
                    addInstr("mflo $" + dstReg);
                    break;
                case "%":
                    addInstr("div $" + dstReg + ", $" + src1Reg + ", $" + src2Reg);
                    addInstr("mfhi $" + dstReg);
                    break;
            }

            if (src1RegIsTemp) {
                register.freeTmpRegister(tmpRegForSrc1);
            } else {
                register.freeRegister(src1);
            }

            if (src2RegIsTemp) {
                register.freeTmpRegister(tmpRegForSrc2);
            } else {
                register.freeRegister(src2);
            }

            if (dst.getSymbolKind()) {
                register.freeRegister(dst);
            }
        } else if ((type1.equals("var") && type2.equals("num")) || (type1.equals("num") && type2.equals("var"))) {
            boolean reverse = false;
            if (type1.equals("num") && type2.equals("var")) {
                Variable tmp = src1;
                src1 = src2;
                src2 = tmp;
                reverse = true;
            }

            int num = src2.getNum();

            String src1Reg = "";
            boolean src1RegIsTemp = false;
            int tmpRegForSrc1 = 0;
            if (src1.getSymbolKind()) {
                Symbol src1Symbol = src1.getSymbol();
                if (src1Symbol.isConst()) {
                    src1Reg = searchRegName(src1);
                    addInstr("li $" + src1Reg + ", " + src1Symbol.getValue());
                } else {
                    if (innerFunc && !src1Symbol.isGlobal()) {
                        tmpRegForSrc1 = register.applyTmpRegister();
                        src1Reg = register.getRegName(tmpRegForSrc1);
                        src1RegIsTemp = true;

                        loadInFunVarFromSP(src1, src1Reg);//从sp总读取值
                    } else if (src1Symbol.isGlobal() && src1Symbol.getType() != SymbolType.Func) {
                        String globalVarName = src1Symbol.getName();
                        src1Reg = searchRegName(src1);
                        addInstr("lw $" + src1Reg + ", Global_" + globalVarName);
                    } else {
                        src1Reg = searchRegName(src1);
                        loadMainFuncVarFromSP(src1Reg, src1Symbol);
                    }
                }
            } else {
                src1Reg = searchRegName(src1);
            }
            switch (op) {
                case "+":
                    addInstr("addi $" + dstReg + ", $" + src1Reg + ", " + num);
                    break;
                case "-":
                    if (reverse) {
                        addInstr("sub $" + src1Reg + ", $zero, $" + src1Reg);
                        addInstr("addi $" + dstReg + ", $" + src1Reg + ", " + num);
                    } else {
                        addInstr("subi $" + dstReg + ", $" + src1Reg + ", " + num);
                    }
                    break;
                case "*":
                    //todo: 乘法可优化
                    addInstr("mul $" + dstReg + ", $" + src1Reg + ", " + num);
                    break;
                case "/":
                    if (reverse) {
                        if (num == 0) {
                            addInstr("li $" + dstReg + ", 0");
                        } else {
                            addInstr("li $v1, " + num);
                            addInstr("div $v1, $" + src1Reg);
                            addInstr("mflo $" + dstReg);
                        }
                    } else {
                        addInstr("div $" + dstReg + ", $" + src1Reg + ", " + num);
                    }
//                    todo: 除法可优化
                    break;
                case "%"://todo 取模优化
                    if (reverse) {
                        if (num == 0) {
                            addInstr("li $" + dstReg + ", 0");
                        } else {
                            addInstr("li $v1, " + num);
                            addInstr("div $v1, $" + src1Reg);
                            addInstr("mfhi $" + dstReg);
                        }
                    } else {
                        if (num == 1) {
                            addInstr("li $" + dstReg + ", 0");
                        } else {
                            addInstr("li $v1, " + num);
                            addInstr("div $" + src1Reg + ", $v1");
                            addInstr("mfhi $" + dstReg);
                        }
                    }
//                    todo: 取模可优化
                    break;
            }
            if (src1RegIsTemp) {
                register.freeTmpRegister(tmpRegForSrc1);
            } else {
                register.freeRegister(src1);
            }

            if (dst.getSymbolKind()) {
                register.freeRegister(dst);
            }
        } else {
            int num1 = src1.getNum();
            int num2 = src2.getNum();
            int res;
            switch (op) {
                case "+":
                    res = num1 + num2;
                    break;
                case "-":
                    res = num1 - num2;
                    break;
                case "*":
                    res = num1 * num2;
                    break;
                case "/":
                    res = num1 / num2;
                    break;
                case "%":
                    res = num1 % num2;
                    break;
                default:
                    res = -5674;
                    System.err.println("unexpteced op");
                    break;
            }
            addInstr("li $" + dstReg + ", " + res);

            if (dst.getSymbolKind()) {
                register.freeRegister(dst);
            }
        }
    }

    private int calFuncParaOffset(String name) {
        int paraIndex = curFunc.getParaIndex(name);
        int paraNum = curFunc.getParaNum();
        int funcParaOffset = inFuncOffset + 4 + 4 * (paraNum - paraIndex);
        return funcParaOffset;
    }

    private int calFuncLocalOffset(Symbol symbol) {
        int localOffset = inFuncOffset + symbol.getAddrOffset();
        return localOffset;
    }

    private void saveInFuncVar(Variable var, String regName) {
        String name = var.getName();
        if (curFunc.varNameInParaList(name)) {
            int paraSpOffset = calFuncParaOffset(name);
            addInstr("sw $" + regName + ", " + paraSpOffset + "($sp)");
        } else {
            Symbol symbol = var.getSymbol();
            int localSpOffset = calFuncLocalOffset(symbol);
            addInstr("sw $" + regName + ", " + localSpOffset + "($sp)");
        }
    }

    private String intToHexStr(int num) {
        String hexStr = Integer.toHexString(num);
        return "0x" + hexStr;
    }

    private void saveLocalMainVar(Variable var, String regName) {
        Symbol symbol = var.getSymbol();
        int localSpOffset = symbol.getSpBaseHex() + symbol.getAddrOffset();
        String hexAddr = intToHexStr(localSpOffset);
        addInstr("sw $" + regName + ", " + hexAddr);
    }

    private String searchRegName(Variable var) {
        String regName;
        if (var.getCurReg() == -1) {
            regName = register.applyRegister(var);
            if (var.getSymbolKind()) {
                int regNo = register.getRegNum(regName);
                var.setCurReg(regNo);
                var.getSymbol().setCurReg(regNo);
            }
        } else {
            int regNo = var.getCurReg();
            regName = register.getRegName(regNo);
        }
        if (regName.equals("v0")) {
            regName = "v1";
        }
        return regName;
    }

    private void generateAssignRet(IRCode ir) {
        Variable dst = ir.getVar();
        String name = dst.getName();
        if (dst.getSymbolKind()) {
            Symbol dstSymbol = dst.getSymbol();
            if (!dstSymbol.isGlobal() && innerFunc) {
                saveInFuncVar(dst, "v0");
            } else if (dstSymbol.isGlobal() && dstSymbol.getType() != SymbolType.Func) {
                String globalVarName = dstSymbol.getName();
                addInstr("sw $v0" + ", Global_" + globalVarName);
            } else {
                saveLocalMainVar(dst, "v0");
            }
        } else {
            String retReg = searchRegName(dst);
            addInstr("move $" + retReg + ", $v0");
        }
    }

    private String varToRegNameForFunc(Variable var, InstrList pushInstr) {//todo 此处isConst存疑
        String varReg;
        if (var.getSymbolKind()) {
            Symbol varSymbol = var.getSymbol();
            if (varSymbol.isConst()) {
                varReg = searchRegName(var);
                pushInstr.addInstr(new Instr("li $" + varReg + ", " + varSymbol.getValue()));
            } else {
                if (innerFunc && !varSymbol.isGlobal()) { //函数内，非global
                    int tmpReg = register.applyTmpRegister();
                    varReg = register.getRegName(tmpReg);
                    loadInFunVarFromSP(var, varReg, pushInstr);
                } else if (varSymbol.isGlobal() && !varSymbol.getType().equals(SymbolType.Func)) {
                    String globalVarName = var.getName();
                    varReg = searchRegName(var);
                    pushInstr.addInstr(new Instr("lw $" + varReg + ", Global_" + globalVarName));
                } else {
                    varReg = searchRegName(var);
                    loadMainFuncVarFromSP(varReg, varSymbol, pushInstr);
                }
            }
        } else {
            varReg = searchRegName(var);
        }
        return varReg;
    }

    private String varToRegName(Variable var) {
        String regName;

        if (var.getSymbolKind()) {       //todo 判定、分类有隐患?
            Symbol varSymbol = var.getSymbol();
            if (var.getType().equals("num")) {
                regName = searchRegName(var);
                addInstr("li $" + regName + ", " + var.getNum());

            } else {
                if (innerFunc && !varSymbol.isGlobal()) {    //函数内+symbol需要lw
                    int tmpReg = register.applyTmpRegister();
                    regName = register.getRegName(tmpReg);

                    loadInFunVarFromSP(var, regName);       //包装从函数体sp读取到reg过程

                } else if (varSymbol.isGlobal() && varSymbol.getType() != SymbolType.Func) {  //还要判断不是func返回值
                    String globalVarName = varSymbol.getName();
                    regName = searchRegName(var);
                    addInstr("lw $" + regName + ", Global_" + globalVarName);

                } else {
                    regName = searchRegName(var);
                    loadMainFuncVarFromSP(regName, varSymbol);
                }
            }

        } else {
            regName = searchRegName(var);
        }

        return regName;
    }

    private void generateAssign2(IRCode ir) {

        Variable dst = ir.getDst();
        Variable src = ir.getSrc1();

        String regForSrc = searchRegName(src);
        if (src.getType().equals("array")) {
            //todo: 处理右侧为数组时的情况
            String arrName = src.getName();

            Symbol arrSymbol = src.getSymbol();

            Variable offset = src.getVar();

            if (arrSymbol.isConst() && offset.getType().equals("num")) {
                int index = offset.getNum();
                int num = arrSymbol.getArrValue(index);
                addInstr("li $" + regForSrc + ", " + num);
            } else {
                if (arrSymbol.isGlobal()) {
                    if (offset.getType().equals("num")) {
                        addInstr("lw $" + regForSrc + ", Global_" + arrName + " + " + offset.getNum() * 4);
                    } else {
                        String offsetRegName = varToRegName(offset);

                        addInstr("sll $" + offsetRegName + ", $" + offsetRegName + ", 2");//乘4
                        addInstr("lw $" + regForSrc + ", Global_" + arrName + "($" + offsetRegName + ")");
                    }
                } else {
                    if (innerFunc) {//在函数内部
                        if (curFunc.varNameInParaList(arrName)) {//为函数参数数组
                            int tmpRegNo1 = register.applyTmpRegister();
                            int tmpRegNo2 = register.applyTmpRegister();
                            String tmpRegName1 = register.getRegName(tmpRegNo1);
                            String tmpRegName2 = register.getRegName(tmpRegNo2);

                            int arrayBaseAddrOffset = calFuncParaOffset(arrName);
                            addInstr("lw $" + tmpRegName1 + ", " + intToHexStr(arrayBaseAddrOffset) + "($sp)");
                            if (offset.getType().equals("num")) {
                                int numAddrOffset = offset.getNum() * 4;
                                addInstr("lw $" + regForSrc + ", " + numAddrOffset + "($" + tmpRegName1 + ")");
                            } else {
                                String offsetRegName = varToRegName(offset);
                                addInstr("sll $" + tmpRegName2 + ", $" + offsetRegName + ", 2");
                                addInstr("add $" + tmpRegName1 + ", $" + tmpRegName1 + ", $" + tmpRegName2);
                                addInstr("lw $" + regForSrc + ", ($" + tmpRegName1 + ")");
                            }

                            register.freeTmpRegister(tmpRegNo1);
                            register.freeTmpRegister(tmpRegNo2);
                        } else { //函数内定义的数组
                            SymbolNode scope = ir.getScope();
                            Symbol symbol = scope.searchSymbol(arrName);
                            int localArraySpOffset = calFuncLocalOffset(symbol);
                            if (offset.getType().equals("num")) {
                                int numAddrOffset = offset.getNum() * 4;
                                localArraySpOffset += numAddrOffset;
                                addInstr("lw $" + regForSrc + ", " + localArraySpOffset + "($sp)");

                            } else {    //offset = var变量
                                int tmpRegno = register.applyTmpRegister();
                                String tmpRegName = register.getRegName(tmpRegno);   //申请临时寄存器

                                String offsetRegName = varToRegName(offset);   //todo 存疑
                                addInstr("sll $" + tmpRegName + ", $" + offsetRegName + ", 2");
                                addInstr("add $" + tmpRegName + ", $" + tmpRegName + ", $sp");
                                addInstr("lw $" + regForSrc + ", " + localArraySpOffset + "($" + tmpRegName + ")");

                                register.freeTmpRegister(tmpRegno);
                            }
                        }
                    } else { //array在main函数中
                        int arrayBaseAddr = arrSymbol.getSpBaseHex() + arrSymbol.getAddrOffset();

                        if (offset.getType().equals("num")) {    //offset = 数字
                            int arrayNumAddr = arrayBaseAddr + offset.getNum() * 4;
                            String arrOffsetHex = intToHexStr(arrayNumAddr);   //地址格式int转16进制

                            addInstr("lw $" + regForSrc + ", " + arrOffsetHex);

                        } else {    //offset = var变量
                            int tmpRegNo = register.applyTmpRegister();
                            String tmpRegName = register.getRegName(tmpRegNo);   //申请临时寄存器

                            String offsetRegname = varToRegName(offset);   //todo 存疑
                            addInstr("sll $" + tmpRegName + ", $" + offsetRegname + ", 2");
                            addInstr("lw $" + regForSrc + ", " + intToHexStr(arrayBaseAddr) + "($" + tmpRegName + ")");

                            register.freeTmpRegister(tmpRegNo);
                        }
                    }
                }
            }
            if (offset.getCurReg() != -1) {
                register.freeRegister(offset);  //统一释放存数组偏移量的reg
            }
        } else if (src.getType().equals("var")) {
            if (src.getSymbolKind()) {
                Symbol srcSymbol = src.getSymbol();
                if (srcSymbol.getType() == SymbolType.Const) {
                    addInstr("li $" + regForSrc +  ", " + srcSymbol.getValue());
//                    System.err.println("why happen this?");
                } else {
                    if (innerFunc && !srcSymbol.isGlobal()) {
                        loadInFunVarFromSP(src, regForSrc);
                    } else if (srcSymbol.isGlobal() && srcSymbol.getType() != SymbolType.Func) {
                        String globalVarName = srcSymbol.getName();
                        addInstr("lw $" + regForSrc + ", Global_" + globalVarName);

                    } else {
                        loadMainFuncVarFromSP(regForSrc, srcSymbol);
                    }
                }
            }
        } else if (src.getType().equals("num")) {
            int num = src.getNum();
            addInstr("li $" + regForSrc + ", " + num);
        } else {
            System.err.println("unexpected src type in assign2");
        }
//处理左侧dst
        String dstType = dst.getType();

        if (dstType.equals("array")) {
            //todo: 处理左侧为数组
            String arrayName = dst.getName();
            Symbol dstSymbol = dst.getSymbol();
            Variable offset = dst.getVar();
            if (dstSymbol.isGlobal()) {
                int tmpRegNo = register.applyTmpRegister();
                if (offset.getType().equals("num")) {
                    addInstr("sw $" + regForSrc + ", Global_" + arrayName + " + " + offset.getNum() * 4);
                } else {    //offset = var变量
                    //该函数：将任意variable加载到指定寄存器，oper1、2、dest等均可用
                    String offsetRegName = varToRegName(offset);   //todo 不能原来这么简单粗暴;但此方法存疑

                    addInstr("sll $" + offsetRegName + ", $" + offsetRegName + ", 2");   //需要乘以4!!!
                    addInstr("sw $" + regForSrc + ", Global_" + arrayName + "($" + offsetRegName + ")");
                }

                register.freeTmpRegister(tmpRegNo);
            } else {
                if (innerFunc) {  //array数组在函数内
                    if (curFunc.varNameInParaList(arrayName)) {     //数组为 函数参数
                        int tmpRegNo1 = register.applyTmpRegister();
                        int tmpRegNo2 = register.applyTmpRegister();
                        String tmpRegName1 = register.getRegName(tmpRegNo1);       //申请临时寄存器
                        String tmpRegName2 = register.getRegName(tmpRegNo2);     //申请临时寄存器2

                        int arrayBaseAddroffset = calFuncParaOffset(arrayName);
                        addInstr("lw $" + tmpRegName1 + ", " + intToHexStr(arrayBaseAddroffset) + "($sp)");  //取出存放array的基地址,存到tmpregname中

                        if (offset.getType().equals("num")) {    //offset = 数字
                            int numAddrOffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            addInstr("sw $" + regForSrc + ", " + numAddrOffset + "($" + tmpRegName1 + ")");

                        } else {    //offset = var变量
                            String offsetRegName = varToRegName(offset);   //todo 存疑
                            addInstr("sll $" + tmpRegName2 + ", $" + offsetRegName + ", 2");
                            addInstr("add $" + tmpRegName1 + ", $" + tmpRegName1 + ", $" + tmpRegName2);
                            addInstr("sw $" + regForSrc + ", ($" + tmpRegName1 + ")");
                        }

                        register.freeTmpRegister(tmpRegNo1);
                        register.freeTmpRegister(tmpRegNo2);
                    } else {    //函数内 + 局部array
                        SymbolNode scope = ir.getScope();
                        Symbol arrSymbol = scope.searchSymbol(arrayName);
                        int localArraySpOffset = calFuncLocalOffset(arrSymbol);   //局部array的首地址
                        if (offset.getType().equals("num")) {    //offset = 数字
                            int numAddrOffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            localArraySpOffset += numAddrOffset;
                            addInstr("sw $" + regForSrc + ", " + localArraySpOffset + "($sp)");

                        } else {    //offset = var变量
                            int tmpregno = register.applyTmpRegister();
                            String tmpRegName = register.getRegName(tmpregno);   //申请临时寄存器

                            String offsetRegName = varToRegName(offset);   //todo 存疑
                            addInstr("sll $" + tmpRegName + ", $" + offsetRegName + ", 2");
                            addInstr("add $" + tmpRegName + ", $" + tmpRegName + ", $sp");
                            addInstr("sw $" + regForSrc + ", " + localArraySpOffset + "($" + tmpRegName + ")");

                            register.freeTmpRegister(tmpregno);
                        }
                    }

                } else {  //array数组在正常结构内
                    int arrayBaseAddr = dstSymbol.getSpBaseHex() + dstSymbol.getAddrOffset();

                    if (offset.getType().equals("num")) {    //offset = 数字
                        int arrayNumAddr = arrayBaseAddr + offset.getNum() * 4;
                        String arroffsetHex = intToHexStr(arrayNumAddr);   //地址格式int转16进制

                        addInstr("sw $" + regForSrc + ", " + arroffsetHex);

                    } else {    //offset = var变量
                        int tmpRegNo = register.applyTmpRegister();
                        String tmpRegName = register.getRegName(tmpRegNo);   //申请临时寄存器

                        String offsetRegName = varToRegName(offset);   //todo 存疑
                        addInstr("sll $" + tmpRegName + ", $" + offsetRegName + ", 2");
                        addInstr("sw $" + regForSrc + ", " + intToHexStr(arrayBaseAddr) + "($" + tmpRegName + ")");

                        register.freeTmpRegister(tmpRegNo);
                    }
                }
            }
        } else if (dstType.equals("var")) {
            if (!dst.getSymbolKind()) {
                String regForDst = searchRegName(dst);
                addInstr("move $" + regForDst + ", $" + regForSrc);
            } else {
                Symbol dstSymbol = dst.getSymbol();
                if (innerFunc && !dstSymbol.isGlobal()) {
                    saveInFuncVar(dst, regForSrc);
                } else if (dstSymbol.isGlobal() && dstSymbol.getType() != SymbolType.Func) {
                    String globalVarName = dstSymbol.getName();
                    addInstr("sw $" + regForSrc + ", Global_" + globalVarName);
                } else {
                    saveLocalMainVar(dst, regForSrc);
                }
            }
        } else {
            System.err.println("unexpected dst type in assign2");
        }

        register.freeRegister(src);

        if (dst.getSymbolKind()) {
            register.freeRegister(dst);
        }

    }

    private void generateNote(IRCode ir) {
            addInstr(ir.getIRStr());
        if (ir.getIRStr().equals("#Out Block")) {
            SymbolNode scope = ir.getScope();
            int inBlockOffset = scope.getInBlockOffset();
            if (inBlockOffset == 0) {
                addInstr("# no need sp+-");
            } else {
                addInstr("addi $sp, $sp, " + inBlockOffset);
                if (inFuncDef) {
                    inFuncOffset -= inBlockOffset;
                } else {
                    spOffset -= inBlockOffset;
                }
            }
        }  else if (ir.getIRStr().equals("#Out Block WhileCut")) {

            addInstr("# no need sp+-");
            SymbolNode scope = ir.getScope();
            int sumOffset = 0;

            while (!scope.isWhile()) {
                sumOffset += scope.getInBlockOffset();
                register.clearScopeReg(scope);
                scope = scope.getParent();
            }
            sumOffset += scope.getInBlockOffset();
            register.clearScopeReg(scope);
            if (sumOffset == 0) {
                addInstr("# no need sp+-");

            } else {
                addInstr("addi $sp, $sp, " + sumOffset);
            }
        }
    }

    private void generateIntDecl(IRCode ir) {
        String name = ir.getName();
        if (ir.isGlobal()) {
            String globalStr = "Global_" + name + ": .word ";
            tabCnt++;
            if (ir.isInit()) {
                addInstr(globalStr + ir.getNum());
            } else {
                addInstr(globalStr + "0:1");
            }
            tabCnt--;
        } else {
            SymbolNode scope = ir.getScope();
            scope.increaseBlockOffset(4);

            if (innerFunc) {
                addInstr("addi $sp, $sp, -4");

                inFuncOffset += 4;
                Symbol symbol = ir.getSymbol();
                symbol.setAddrOffset(-inFuncOffset);


            } else {
                addInstr("addi $sp, $sp, -4");
                spOffset += 4;
                Symbol symbol = ir.getSymbol();
                symbol.setAddrOffset(-spOffset);

            }
            if (ir.isInit()) {
                int regNum = register.applyTmpRegister();
                String regName = register.getRegName(regNum);

                int num = ir.getNum();
                addInstr("li $" + regName + ", " + num);
                addInstr("sw $" + regName + ", 0($sp)");
                register.freeTmpRegister(regNum);
            }
        }
    }
}
