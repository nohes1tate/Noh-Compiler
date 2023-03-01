import java.util.ArrayList;
import java.util.HashMap;


public class TokenDic {
    private static final ArrayList<String> ReservedWordList;
    private static final HashMap<String, String> ReservedWordDic;
    private static final HashMap<String, String> SymbolDic;
    private static final HashMap<String, TokenType> TypeDic;

    static {
        ReservedWordList = new ArrayList<>();
        ReservedWordList.add("main");
        ReservedWordList.add("const");
        ReservedWordList.add("int");
        ReservedWordList.add("break");
        ReservedWordList.add("continue");
        ReservedWordList.add("if");
        ReservedWordList.add("else");
        ReservedWordList.add("while");
        ReservedWordList.add("getint");
        ReservedWordList.add("printf");
        ReservedWordList.add("return");
        ReservedWordList.add("void");
    }

    static {
        ReservedWordDic = new HashMap<>();
        for (String s : ReservedWordList) {
            ReservedWordDic.put(s, s.toUpperCase() + "TK");
        }
    }

    static {
        SymbolDic = new HashMap<>();
        SymbolDic.put("!","NOT");
        SymbolDic.put("&&","AND");
        SymbolDic.put("||","OR");
        SymbolDic.put("+","PLUS");
        SymbolDic.put("-","MINU");
        SymbolDic.put("*","MULT");
        SymbolDic.put("/","DIV");
        SymbolDic.put("%","MOD");
        SymbolDic.put("<","LSS");
        SymbolDic.put("<=","LEQ");
        SymbolDic.put(">","GRE");
        SymbolDic.put(">=","GEQ");
        SymbolDic.put("==","EQL");
        SymbolDic.put("!=","NEQ");
        SymbolDic.put("=", "ASSIGN");
        SymbolDic.put(";","SEMICN");
        SymbolDic.put(",","COMMA");
        SymbolDic.put("(","LPARENT");
        SymbolDic.put(")","RPARENT");
        SymbolDic.put("[","LBRACK");
        SymbolDic.put("]","RBRACK");
        SymbolDic.put("{","LBRACE");
        SymbolDic.put("}","RBRACE");
    }

    static {
        TypeDic = new HashMap<>();
        //ASCII(0-32)为不可见字符
        for (int i=0; i<=32; i++) {
            TypeDic.put((char)i + "", TokenType.Blank);
        }
        //ASCII(48-57)为数字
        for (int i=48; i<=57; i++) {
            TypeDic.put((char)i + "", TokenType.Digit);
        }
        //ASCII(65-90)和(97-122)为字母
        for (int i=65; i<=90; i++) {
            TypeDic.put((char)i + "", TokenType.Letter);
            TypeDic.put((char)(i+32) + "",TokenType.Letter);
        }
        //下划线95
        TypeDic.put("_",TokenType.Letter);

        //Symbol
        for (String op : SymbolDic.keySet()) {
            TypeDic.put(op,TokenType.Symbol);
        }
        TypeDic.put("\"",TokenType.Symbol);
        TypeDic.put("|",TokenType.Symbol);
        TypeDic.put("&",TokenType.Symbol);
    }

    public TokenType queryTokenType(String token) {
        if(TypeDic.containsKey(token)) {
            return TypeDic.get(token);
        }
        return TokenType.Other;
    }


    public boolean queryTokenIsOpCode(String token) {
        return TypeDic.get(token) == TokenType.Symbol;
    }

    public String queryOpCode(String token) {
        return SymbolDic.get(token);
    }

    public boolean queryTokenIsReservedWord(String token) {
        return ReservedWordDic.containsKey(token);
    }

    public String queryReservedWordCode(String token) {
        return ReservedWordDic.get(token);
    }
}
