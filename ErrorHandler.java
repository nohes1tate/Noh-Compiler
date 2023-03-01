import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class ErrorHandler {
    private static final String outDir = "error.txt";
    public ArrayList<ErrInfo> errInfos;
    private static final HashMap<ErrType, Error> ERROR_DICT;

    private StringBuffer buffer = new StringBuffer();

    public ErrorHandler() {this.errInfos = new ArrayList<>();}

    static {
        ERROR_DICT = new HashMap<>();
        ERROR_DICT.put(ErrType.ERR_A, new Error(ErrType.ERR_A, "a", "Illegal symbol"));
        ERROR_DICT.put(ErrType.ERR_B, new Error(ErrType.ERR_B, "b", "Name redefined"));
        ERROR_DICT.put(ErrType.ERR_C, new Error(ErrType.ERR_C, "c", "Undefined name"));
        ERROR_DICT.put(ErrType.ERR_D, new Error(ErrType.ERR_D, "d", "Function parameter number mismatch"));
        ERROR_DICT.put(ErrType.ERR_E, new Error(ErrType.ERR_E, "e", "Function parameter type mismatch"));
        ERROR_DICT.put(ErrType.ERR_F, new Error(ErrType.ERR_F, "f", "Function return type mismatch"));
        ERROR_DICT.put(ErrType.ERR_G, new Error(ErrType.ERR_G, "g", "Missing function return"));
        ERROR_DICT.put(ErrType.ERR_H, new Error(ErrType.ERR_H, "h", "Reassign to constant"));
        ERROR_DICT.put(ErrType.ERR_I, new Error(ErrType.ERR_I, "i", "Missing semicolon"));
        ERROR_DICT.put(ErrType.ERR_J, new Error(ErrType.ERR_J, "j", "Missing right parenthesis"));
        ERROR_DICT.put(ErrType.ERR_K, new Error(ErrType.ERR_K, "k", "Missing right bracket"));
        ERROR_DICT.put(ErrType.ERR_L, new Error(ErrType.ERR_L, "l", "Print number mismatch"));
        ERROR_DICT.put(ErrType.ERR_M, new Error(ErrType.ERR_M, "m", "Unexpected break or continue"));
    }

    public void addError(ErrType errType, int row) {
        Error error = ERROR_DICT.get(errType);
        errInfos.add(new ErrInfo(row, ERROR_DICT.get(errType).getErrCode()));
        System.out.println("Error at " + row + ": " + errType);
    }

    public void printError(boolean toFile) throws Exception {
        for (ErrInfo errInfo : errInfos) {
            System.out.println(errInfo);
            buffer.append(errInfo + "\n");
        }
        if (toFile) {
            FileWriter fileWriter = new FileWriter(outDir);
            fileWriter.write(buffer.toString());
            fileWriter.flush();
            fileWriter.close();
        }
    }


    public void handleErrorA(Token tk) {
        String formatString = tk.getTokenValue();
        for (int i=0; i<formatString.length(); i++) {
            char c = formatString.charAt(i);
            int ascii = (int) c;
            if (c == '%') {
                if (i == formatString.length() - 1) {
                    addError(ErrType.ERR_A, tk.getRow());
                    return;
                }
                if (formatString.charAt(i+1) != 'd') {
                    addError(ErrType.ERR_A, tk.getRow());
                    return;
                }
                i++;
                continue;
            } else if (c == '\\') {
                if (i == formatString.length() - 1) {
                    addError(ErrType.ERR_A, tk.getRow());
                    return;
                }
                if (formatString.charAt(i+1) != 'n') {
                    addError(ErrType.ERR_A, tk.getRow());
                    return;
                }
                i++;
                continue;
            }else if (ascii == 34 && (i==0 || i==formatString.length()-1)) {
                continue;
            }else if (ascii == 32 || ascii == 33 || (ascii >= 40 && ascii <= 126)){
                continue;
            } else {
                addError(ErrType.ERR_A, tk.getRow());
                return;
            }
        }
    }
    public void handleErrorB(SymbolNode symbolTable, Symbol symbol) {
        for (Symbol tmpSym : symbolTable.getSymbols()) {
            if (tmpSym.getName().equals(symbol.getName())) {
                addError(ErrType.ERR_B, symbol.getRow());
                return;
            }
        }
        if (symbolTable.getParent()!=null && symbolTable.getParent().isFunc()) {
            for (Symbol tmpSym : symbolTable.getParent().getSymbols()) {
                if (tmpSym.getName().equals(symbol.getName())) {
                    return;
                }
            }
        }
    }

    public void handleErrorC(int row) {
        addError(ErrType.ERR_C, row);
    }

    public void handleErrorD(int row) {
        addError(ErrType.ERR_D, row);
    }

    public void handleErrorE(Symbol funcSymbol, ArrayList<Symbol> paramSymbols, int row) {
        for (int i=0; i<funcSymbol.getParaList().size(); i++) {
            if (funcSymbol.getParaList().get(i).getDimension() != paramSymbols.get(i).getDimension()) {
                //System.out.println("param dimension mismatch at fun " + funcSymbol.getName() + " at row " + row);
                addError(ErrType.ERR_E, row);
                return;
            }

            }
        }

        public void handleErrorF(int row) {
            addError(ErrType.ERR_F, row);
        }

        public void handleErrorG(int row) {
            addError(ErrType.ERR_G, row);
        }

        public void handleErrorH(int row) {
            addError(ErrType.ERR_H, row);
        }

        public void handleErrorI(int row) {
            addError(ErrType.ERR_I, row);
        }

        public void handleErrorJ(int row) {
            addError(ErrType.ERR_J, row);
        }

        public void handleErrorK(int row) {
            addError(ErrType.ERR_K, row);
        }

        public void handleErrorL(int row) {
            addError(ErrType.ERR_L, row);
        }

        public void handleErrorM(int row) {
            addError(ErrType.ERR_M, row);
        }
}
