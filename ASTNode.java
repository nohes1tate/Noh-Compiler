import java.util.ArrayList;

public class ASTNode {
    private String type; //当前节点的类型
    private String dataType; //当前节点包含数据的类型，包括Int,ConstInt,Array,ConstArray,Func
    private Token token;
    private ArrayList<ASTNode> children = new ArrayList<>();
    private int num;

    public ASTNode(String type) {
        this.type = type;
    }

    public ASTNode(String type, Token token) {
        this.type = type;
        this.token = token;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataType() {
        return this.dataType;
    }


    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public String getIdentName() {
        if (this.getType().equals("Ident")) {
            return this.getChildren().get(0).getIdentName();
        } else if (this.getType().equals("IDENFR")) {
            return this.getToken().getTokenValue();
        } else {
            return null;
        }
    }

    public void addChild(ASTNode child) {
        children.add(child);
    }

    public String getType() {
        return type;
    }

    public Token getToken() {
        return token;
    }

    public ArrayList<ASTNode> getChildren() {
        return children;
    }

    public int calValue() {
        if (type.equals("AddExp")) {
            if (children.size() == 1) {
                return children.get(0).calValue();
            } else {
                if (children.get(1).getType().equals("PLUS")) {
                    return children.get(0).calValue() + children.get(2).calValue();
                } else {
                    return children.get(0).calValue() - children.get(2).calValue();
                }
            }
        } else if (type.equals("MulExp")) {
            if (children.size() == 1) {
                return children.get(0).calValue();
            } else {
                if (children.get(1).getType().equals("MULT")) {
                    return children.get(0).calValue() * children.get(2).calValue();
                } else if (children.get(1).getType().equals("DIV")) {
                    return children.get(0).calValue() / children.get(2).calValue();
                } else {
                    return children.get(0).calValue() % children.get(2).calValue();
                }
            }
        } else if (type.equals("UnaryExp")) {
            if (children.size() == 1) {
                return children.get(0).calValue();
            } else if (children.size() == 2) {
                if (children.get(0).getType().equals("PLUS")) {
                    return children.get(1).calValue();
                } else if (children.get(0).getType().equals("MINU")) {
                    return -children.get(1).calValue();
                } else {
                    return ~children.get(1).calValue();
                }
            } else {
                System.out.println("Error: Can no cal func");
                return -114514;
            }
        } else if (type.equals("PrimaryExp")) {
            return children.get(0).calValue();
        } else if (type.equals("Number")) {
            return children.get(0).calValue();
        } else if (type.equals("IntConst")) {
            return children.get(0).calValue();
        } else if (type.equals("INTCON")) {
            return Integer.parseInt(token.getTokenValue());
        } else {
            System.err.println("Error: Can not cal type " + type);
            return -114514;
        }

    }

    public ASTNode relToAddExp() {
        if (type.equals("AddExp")) return this;
        else return children.get(0).relToAddExp();
    }

    public ASTNode getLeftRel() {
        if (type.equals("RelExp")) {
            return children.get(0);
        } else {
            return children.get(0).getLeftRel();
        }
    }

    public ASTNode getRightRel() {
        if (type.equals("RelExp")) {
            return children.get(2);
        } else {
            return children.get(0).getRightRel();
        }
    }

    public ASTNode getLeftEq() {
        if (type.equals("EqExp")) {
            return children.get(0);
        } else {
            return children.get(0).getLeftEq();
        }
    }

    public ASTNode getRightEq() {
        if (type.equals("EqExp")) {
            return children.get(2);
        } else {
            return children.get(0).getRightEq();
        }
    }

    public ASTNode getRightCond() {
        if (type.equals("Cond")) {
            return children.get(0).getRightCond();
        } else if (type.equals("LOrExp")) {
            int length = children.size();
            if (length == 1) {
                return children.get(0).getRightCond();
            } else {
                return children.get(2);
            }
        } else if (type.equals("LAndExp")) {
            int length = children.size();
            if (length == 3) {
                return children.get(2);
            } else {
                System.err.println("Error in Finding Right Cond");
                return null;
            }
        } else {
            System.err.println("Unknown cond!");
            return null;
        }
    }

    public ASTNode getLeftCond() {
        if (type.equals("Cond")) {
            return children.get(0).getLeftCond();
        } else if (type.equals("LOrExp")) {
            int length = children.size();
            if (length == 1) {
                return children.get(0).getLeftCond();
            } else {
                return children.get(0);
            }
        } else if (type.equals("LAndExp")) {
            int length = children.size();
            if (length == 3) {
                return children.get(0);
            } else {
                System.err.println("Error in Finding Left Cond");
                return null;
            }
        } else {
            System.err.println("Unknown cond!");
            return null;
        }
    }

    public String getCondType() {
        if (type.equals("Cond")) {
            return children.get(0).getCondType();
        } else if (type.equals("LOrExp")) {
            int length = children.size();
            if (length == 1) {
                return children.get(0).getCondType();
            } else {
                return "OR";
            }
        } else if (type.equals("LAndExp")) {
            int length = children.size();
            if (length == 1) {
                return children.get(0).getCondType();
            } else {
                return "AND";
            }
        } else if (type.equals("EqExp")) {
            int length = children.size();
            if (length == 1) {
                return children.get(0).getCondType();
            } else {
                return children.get(1).getType();
            }
        } else if (type.equals("RelExp")) {
            int length = children.size();
            if (length == 1) {
                return "EXP";
            } else {
                return children.get(1).getType();
            }
        } else if (type.equals("AddExp")) {
            return "EXP";
        } else {
            System.err.println("Unknown cond!");
            return null;
        }
    }


    public String getStrcon() {
        if (type.equals("FormatString")) {
            return children.get(0).getStrcon();
        } else if (type.equals("STRCON")) {
            return token.getTokenValue();
        } else {
            System.out.println("Error: Can not get Strcon");
            return null;
        }
    }
}
