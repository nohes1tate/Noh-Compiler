import java.util.ArrayList;
import java.util.HashMap;

public class Register {
    private HashMap<Integer, String> regMap;
    private HashMap<String, Integer> regNameMap;

    private ArrayList<Integer> freeRegList;
    private HashMap<Integer, Variable> varUseMap;
    private ArrayList<Integer> activeRegList; //活跃寄存器列表

    public Register() {
        this.regMap = new HashMap<>();
        this.regNameMap = new HashMap<>();

        this.freeRegList = new ArrayList<>();
        this.varUseMap = new HashMap<>();
        this.activeRegList = new ArrayList<>();

        initRegMap();
        initRegnameMap();
        initFreeRegList();
    }

    private void initRegMap() {
        regMap.put(0, "zero");
        regMap.put(1, "at");
        regMap.put(2, "v0");
        regMap.put(3, "v1");
        regMap.put(4, "a0");
        regMap.put(5, "a1");
        regMap.put(6, "a2");
        regMap.put(7, "a3");
        regMap.put(8, "t0");
        regMap.put(9, "t1");
        regMap.put(10, "t2");
        regMap.put(11, "t3");
        regMap.put(12, "t4");
        regMap.put(13, "t5");
        regMap.put(14, "t6");
        regMap.put(15, "t7");
        regMap.put(16, "s0");
        regMap.put(17, "s1");
        regMap.put(18, "s2");
        regMap.put(19, "s3");
        regMap.put(20, "s4");
        regMap.put(21, "s5");
        regMap.put(22, "s6");
        regMap.put(23, "s7");
        regMap.put(24, "t8");
        regMap.put(25, "t9");
        regMap.put(26, "k0");
        regMap.put(27, "k1");
        regMap.put(28, "gp");
        regMap.put(29, "sp");
        regMap.put(30, "fp");
        regMap.put(31, "ra");
    }

    private void initRegnameMap() {
        regNameMap.put("zero", 0);
        regNameMap.put("at", 1);
        regNameMap.put("v0", 2);
        regNameMap.put("v1", 3);
        regNameMap.put("a0", 4);
        regNameMap.put("a1", 5);
        regNameMap.put("a2", 6);
        regNameMap.put("a3", 7);
        regNameMap.put("t0", 8);
        regNameMap.put("t1", 9);
        regNameMap.put("t2", 10);
        regNameMap.put("t3", 11);
        regNameMap.put("t4", 12);
        regNameMap.put("t5", 13);
        regNameMap.put("t6", 14);
        regNameMap.put("t7", 15);
        regNameMap.put("s0", 16);
        regNameMap.put("s1", 17);
        regNameMap.put("s2", 18);
        regNameMap.put("s3", 19);
        regNameMap.put("s4", 20);
        regNameMap.put("s5", 21);
        regNameMap.put("s6", 22);
        regNameMap.put("s7", 23);
        regNameMap.put("t8", 24);
        regNameMap.put("t9", 25);
        regNameMap.put("k0", 26);
        regNameMap.put("k1", 27);
        regNameMap.put("gp", 28);
        regNameMap.put("sp", 29);
        regNameMap.put("fp", 30);
        regNameMap.put("ra", 31);
    }

    private void initFreeRegList() {
        for (int i = 8; i < 32; i++) {
            if (i != 29 && i != 31) {
                freeRegList.add(i);
            }
        }
    }

//    根据编号查询寄存器名
    public String getRegName(int regNum) {
        return regMap.get(regNum);
    }

//    根据寄存器名查询寄存器编号
    public int getRegNum(String regName) {
        return regNameMap.get(regName);
    }

//    添加活跃寄存器
    public void activeReg(int regNum) {
        if (!activeRegList.contains(regNum)) {
            activeRegList.add(regNum);
        }
    }

//    申请临时寄存器
    public int applyTmpRegister() {
        int regNum;
        if (freeRegList.isEmpty()) {
            System.err.println("No free reg, alloc $v1");
            return 3;
        }else {
            regNum = freeRegList.remove(0);
            activeReg(regNum);
            return regNum;
        }
    }

//    符号申请寄存器

//    变量申请寄存器
    public String applyRegister(Variable var) {
        int num;

        if (!freeRegList.isEmpty()) {
            num = freeRegList.remove(0);
            varUseMap.put(num, var);
            var.setCurReg(num);
            activeReg(num);

        } else {
            System.err.println("No free reg, alloc $v1");
            return regMap.get(3);
        }
//        System.out.println("Alloc Reg $"+regMap.get(num));
        return regMap.get(num);
    }

//    释放临时寄存器
    public void freeTmpRegister(int num) {
        if (num == -1) {
            return;
        }
        if (num<8||num==29||num==31) {
            System.err.println("Can't free reg $"+regMap.get(num));
            return;
        } else if (!freeRegList.contains(num)) {
            freeRegList.add(num);
            removeActiveReg(num);
//            varUseMap.remove(num);
//            System.out.println("Free Reg $"+regMap.get(num));
        }
    }

//    释放变量使用的寄存器
    public void freeRegister(Variable var) {
        if (var.getSymbolKind()) {
            Symbol s = var.getSymbol();

            int regNum = s.getCurReg();
            freeTmpRegister(regNum);
            var.setCurReg(-1);
            s.setCurReg(-1);
            removeActiveReg(regNum);
//            System.out.println("Free Reg $"+regMap.get(regNum) + " for " + s.getName());
        }else {
            int regNum = var.getCurReg();
            freeTmpRegister(regNum);
//            var.setCurReg(-1);
            removeActiveReg(regNum);
//            System.out.println("Free Reg $"+regMap.get(regNum) + " for " + var.getName());
        }
    }

    private void removeActiveReg(int num) {
        if (activeRegList.contains(num)) {
            activeRegList.remove((Integer) num);
        }
    }

    public ArrayList<Integer> getActiveRegList() {
        return activeRegList;
    }

    public void reset() {
        freeRegList.clear();
        activeRegList.clear();
        varUseMap.clear();
        initFreeRegList();
    }

    public void clearScopeReg(SymbolNode scope) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int no:activeRegList) {
            list.add(no);
        }
        for (int no : list) {
            if (varUseMap.containsKey(no)) {
                Variable var = varUseMap.get(no);
                if (var.getScope() == scope) {
                    freeRegister(var);
                }
            }
        }
    }
}
