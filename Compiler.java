import java.util.ArrayList;

public class Compiler {
    public static void main(String[] args) {
        try {

            ArrayList<Token> myTokenList = TokenScanner.readTokens(false);

            GrammarScanner myGrammarScanner = new GrammarScanner(myTokenList);
            ASTNode astRoot = myGrammarScanner.createASTTree();
            myGrammarScanner.printRoot(false);
            myGrammarScanner.getErrorHandler().printError(false);

            IRGenerator myIRGenerator = new IRGenerator(myGrammarScanner.getAstRoot(), myGrammarScanner.getSymbolTable());
            ArrayList<IRCode> irList =  myIRGenerator.generateIRCode(true);

            MIPSTranslator mipsTranslator = new MIPSTranslator(irList);
            mipsTranslator.IRCodeToMips(true);
            System.out.println("\nfinish");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
