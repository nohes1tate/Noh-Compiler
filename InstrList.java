import java.util.ArrayList;

public class InstrList {
    private ArrayList<Instr> instrList;

    public InstrList() {
        instrList = new ArrayList<>();
    }

    public ArrayList<Instr> getInstrList() {
        return instrList;
    }

    public void addInstr(Instr instr) {
        instrList.add(instr);
    }
}
