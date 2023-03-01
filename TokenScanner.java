import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class TokenScanner {
    private static String curToken = "";
    private static ArrayList<Token> tokenList = new ArrayList<>();
    private static TokenDic tokenDic = new TokenDic();

    private static int curRow = 1;
    private static boolean singleComment = false;
    private static boolean multiComment = false;
    private static boolean readingString = false;

    private static final String outDir = "output.txt";
    private static final String inDir = "testfile.txt";

    public static ArrayList<Token> readTokens(boolean output) {
        try {
            readfile(inDir);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (output) {
            try {
                writefile(outDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return tokenList;
    }

    public static ArrayList<Token> readfile(String dir) throws IOException {
        File file = new File(dir);
        FileReader reader = new FileReader(file);

        int c;
        while ((c = reader.read()) != -1) {
            char curChar = (char) c;
            handleChar(curChar);
        }
        return tokenList;
    }

    public static void writefile(String dir) throws IOException {
        FileWriter writer = new FileWriter(dir);
        for (Token t : tokenList) {
            writer.write(t.toString() + "\n");
            System.out.println(t);
        }
        writer.flush();
        writer.close();
    }

    public static void handleChar(char curChar) {
        TokenType type = tokenDic.queryTokenType(curChar + "");
        switch (type) {
            case Letter:
                if (singleComment || multiComment) {
                    curToken = "";
                    break;
                } else if (readingString) {
                    curToken += curChar;
                } else {
                    if (curToken.isEmpty()) {
                        curToken += curChar;
                    } else {
                        char tokenStart = curToken.charAt(0);
                        TokenType startType = tokenDic.queryTokenType(tokenStart + "");
                        if (!startType.equals(TokenType.Letter) ) {
                            addToken(curRow, curToken);
                        }
                        curToken += curChar;
                    }
                }
                break;
            case Blank:
                if (singleComment || multiComment) {
                    curToken = "";
                }
                if (readingString) {
                    curToken += curChar;
                } else if (!curToken.isEmpty()) {
                    addToken(curRow, curToken);
                }
                if (curChar == '\n') {
                    singleComment = false;
                    curRow++;
                }
                break;
            case Digit:
                if (singleComment || multiComment) {
                    curToken = "";
                } else {
                    if (!readingString && tokenDic.queryTokenIsOpCode(curToken)) {
                        addToken(curRow, curToken);
                    }
                }
                curToken += curChar;
                break;
            case Symbol:
                if (multiComment) {
                    if (curToken.equals("*") && curChar == '/') {
                        multiComment = false;
                        curToken = "";
                    } else {
                        curToken = curChar + "";
                    }
                    break;
                } else if (singleComment) {
                    break;
                } else if (readingString) {
                    if (curChar == '\"') {
                        curToken += curChar;
                        addToken(curRow, curToken);
                        readingString = false;
                        break;
                    }
                    curToken += curChar;
                    break;
                } else {
                    if (!tokenDic.queryTokenIsOpCode(curToken)) {
                        addToken(curRow, curToken);
                    }
                    if ((curToken.equals("&") && curChar == '&')
                            || (curToken.equals("|") && curChar == '|')
                            || (curToken.equals("<") && curChar == '=')
                            || (curToken.equals(">") && curChar == '=')
                            || (curToken.equals("=") && curChar == '=')
                            || (curToken.equals("!") && curChar == '=')
                    ) {
                        curToken += curChar;
                        addToken(curRow, curToken);
                        break;
                    } else if (curToken.equals("/") && curChar == '*') {
                        multiComment = true;
                        curToken = "";
                        break;
                    } else if (curToken.equals("/") && curChar == '/') {
                        singleComment = true;
                        curToken = "";
                        break;
                    } else if (curChar == '\"') {
                        addToken(curRow, curToken);
                        curToken += curChar;
                        readingString = true;
                        break;
                    } else {
                        if (curChar == '|' || curChar == '!' || curChar == '&'
                        || curChar == '>' || curChar == '<' || curChar == '=' || curChar == '/') {
                            curToken += curChar;
                            break;
                        }
                        else {
                            addToken(curRow, curToken);
                            curToken += curChar;
                            addToken(curRow, curToken);
                            break;
                        }
                    }
                }
            case Other:
                if (readingString) {
                    curToken += curChar;
                }
                break;
            default:
                System.err.println("Unhandled char scanned!");
        }
    }

    private static void addToken(int row, String token) {
        if(token.isEmpty()) {
            return;
        }
        char tokenFirst = token.charAt(0);
        if (readingString) {
            tokenList.add(new Token(row, "STRCON", token));
        } else if (tokenDic.queryTokenIsReservedWord(token)) {
            tokenList.add(new Token(row, tokenDic.queryReservedWordCode(token), token));
        } else if (tokenDic.queryTokenIsOpCode(token)) {
            tokenList.add(new Token(row, tokenDic.queryOpCode(token), token));
        } else {
            if ('0' <= tokenFirst && tokenFirst <= '9') {
                if(tokenFirst == '0' && !token.equals("0")) {
                    throw new RuntimeException("Error Value Of Int");
                }
                tokenList.add(new Token(row, "INTCON", token));
            } else {
                tokenList.add(new Token(row, "IDENFR", token));
            }
        }
        curToken = "";
        readingString = false;
        singleComment = false;
        multiComment = false;
    }
}
