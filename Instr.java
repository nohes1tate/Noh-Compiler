public class Instr {
    private String str;
    private boolean addrOffset = false;     //是否需要地址offset处理
    private int offset;
    private String preStr;
    private String aftStr;

    private boolean pushOffset = false;
    private boolean activeRegOffset = false;
    private boolean hasRetReg = false;
    public void setPushOffset(boolean pushOffset) {
        this.pushOffset = pushOffset;
    }
    public boolean isPushOffset() {
        return pushOffset;
    }
    public void setActiveRegOffset(boolean activeRegOffset) {
        this.activeRegOffset = activeRegOffset;
    }
    public boolean isActiveRegOffset() {
        return activeRegOffset;
    }
    public void setHasRetReg(boolean hasRetReg) {
        this.hasRetReg = hasRetReg;
    }
    public boolean isHasRetReg() {
        return hasRetReg;
    }
    private int freeRegNum;
    Instr(String str) {
        this.str = str;
    }
    Instr(String preStr, int offset, String aftStr, String type) {  //push 或 actreg 两种状态
        this.preStr = preStr;
        this.offset = offset;
        this.aftStr = aftStr;
        this.addrOffset = true;
        if (type.equals("push")) {
            this.pushOffset = true;
        } else if (type.equals("actreg")) {
            this.activeRegOffset = true;
        }
    }
    public String getStr() {
        return str;
    }
    public int getFreeRegNum() {
        return freeRegNum;
    }

    public void setFreeRegNum(int freeRegNum) {
        this.freeRegNum = freeRegNum;
    }

    public String toString(int activeRegOffset) {       //分为pushoffset与actregoffset两类
        if (addrOffset) {
            offset += activeRegOffset;
            if ((preStr + offset + aftStr).equals("lw $t2, 0x8")) {
                System.out.println("nani1");
            }
            return preStr + offset + aftStr;
        }
        if (str.equals("lw $t2, 0x8")) {
            System.out.println("nani2");
        }
        return str;
    }

    public String toString() {
        return str;
    }
}
