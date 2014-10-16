package wci.frontend.pascal.parsers;

import java.util.EnumSet;
import java.util.HashMap;

import wci.frontend.*;
import wci.frontend.pascal.*;
import wci.intermediate.symtabimpl.*;
import wci.intermediate.*;
import wci.intermediate.icodeimpl.*;
import wci.intermediate.typeimpl.*;
import static wci.frontend.pascal.PascalTokenType.*;
import static wci.frontend.pascal.PascalErrorCode.*;
import static wci.intermediate.symtabimpl.SymTabKeyImpl.*;
import static wci.intermediate.symtabimpl.DefinitionImpl.*;
import static wci.intermediate.typeimpl.TypeFormImpl.ARRAY;
import static wci.intermediate.typeimpl.TypeFormImpl.ENUMERATION;
import static wci.intermediate.typeimpl.TypeFormImpl.SUBRANGE;
import static wci.intermediate.typeimpl.TypeKeyImpl.ARRAY_ELEMENT_TYPE;
import static wci.intermediate.typeimpl.TypeKeyImpl.ARRAY_INDEX_TYPE;
import static wci.intermediate.typeimpl.TypeKeyImpl.SET_ELEMENT_COUNT;
import static wci.intermediate.typeimpl.TypeKeyImpl.SET_ELEMENT_TYPE;
import static wci.intermediate.typeimpl.TypeKeyImpl.SUBRANGE_BASE_TYPE;
import static wci.intermediate.typeimpl.TypeKeyImpl.SUBRANGE_MAX_VALUE;
import static wci.intermediate.typeimpl.TypeKeyImpl.SUBRANGE_MIN_VALUE;
import static wci.intermediate.icodeimpl.ICodeNodeTypeImpl.*;
import static wci.intermediate.icodeimpl.ICodeKeyImpl.*;

/**
 * <h1>ExpressionParser</h1>
 *
 * <p>Parse a Pascal expression.</p>
 *
 * <p>Copyright (c) 2009 by Ronald Mak</p>
 * <p>For instructional purposes only.  No warranties.</p>
 */
public class ExpressionParser extends StatementParser
{
    /**
     * Constructor.
     * @param parent the parent parser.
     */
    public ExpressionParser(PascalParserTD parent)
    {
        super(parent);
    }

    // Synchronization set for starting an expression.
    static final EnumSet<PascalTokenType> EXPR_START_SET =
        EnumSet.of(PLUS, MINUS, IDENTIFIER, INTEGER, REAL, STRING,
                   PascalTokenType.NOT, LEFT_PAREN, LEFT_BRACKET);

    /**
     * Parse an expression.
     * @param token the initial token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    public ICodeNode parse(Token token)
        throws Exception
    {
    	Token startToken = token;
        ICodeNode rootNode = parseExpression(token);
        
        token = currentToken();
        
        // Check if the rootNode is lower bound of a subrange
        if (token.getType() == DOT_DOT) {
        	ICodeNode subrangeNode = ICodeFactory.createICodeNode(ICodeNodeTypeImpl.SUBRANGE);
        	TypeSpec subrangeType = TypeFactory.createType(SUBRANGE);
            Object minValue = null;
            Object maxValue = null;
            
        	TypeSpec minType = Predefined.undefinedType;
        	TypeSpec maxType = Predefined.undefinedType;
        	
        	if (rootNode != null) {
        		minType = rootNode.getTypeSpec();
        		if (rootNode.getType() == INTEGER_CONSTANT) {
            		minValue = rootNode.getAttribute(VALUE);
            	}
        		else {
                    errorHandler.flag(startToken, INVALID_SUBRANGE_TYPE, this);
        		}
        	}
        	else {
                errorHandler.flag(startToken, INVALID_SUBRANGE_TYPE, this);
        	}
        	
            token = nextToken();  // consume the operator ..
        	startToken = token;
        	
        	ICodeNode exprNode = parseExpression(token);
        	
        	if (exprNode != null) {
        		maxType = exprNode.getTypeSpec();
        		if (exprNode.getType() == INTEGER_CONSTANT) {
        			maxValue = exprNode.getAttribute(VALUE);
            	}
        		else {
                    errorHandler.flag(startToken, INVALID_SUBRANGE_TYPE, this);
        		}
        	}
        	else {
                errorHandler.flag(startToken, INVALID_SUBRANGE_TYPE, this);
        	}
        	
        	// Are the min and max value types valid? Are they the same?
            if (!TypeChecker.isSubrangeCompatible(minType) || !TypeChecker.isSubrangeCompatible(maxType) ||
            		(minType != maxType)) {
                errorHandler.flag(startToken, INVALID_SUBRANGE_TYPE, this);
            }

            // Min value > max value?
            else if ((minValue != null) && (maxValue != null) &&
                     ((Integer) minValue >= (Integer) maxValue)) {
                errorHandler.flag(startToken, MIN_GT_MAX, this);
            }
            
            subrangeType.setAttribute(SUBRANGE_BASE_TYPE, minType);
            subrangeType.setAttribute(SUBRANGE_MIN_VALUE, minValue);
            subrangeType.setAttribute(SUBRANGE_MAX_VALUE, maxValue);
            
            subrangeNode.setTypeSpec(subrangeType);
            rootNode = subrangeNode;
        }
        
        return rootNode;
    }

    // Set of set relational operators.
    private static final EnumSet<PascalTokenType> SET_REL_OPS =
        EnumSet.of(EQUALS, NOT_EQUALS, LESS_EQUALS, GREATER_EQUALS, PascalTokenType.IN);
    
    // Set of relational operators.
    private static final EnumSet<PascalTokenType> REL_OPS =
    		SET_REL_OPS.clone();
    static {
    	REL_OPS.add(LESS_THAN);
    	REL_OPS.add(GREATER_THAN);
    }

    // Map relational operator tokens to node types.
    private static final HashMap<PascalTokenType, ICodeNodeType>
        REL_OPS_MAP = new HashMap<PascalTokenType, ICodeNodeType>();
    static {
        REL_OPS_MAP.put(EQUALS, EQ);
        REL_OPS_MAP.put(NOT_EQUALS, NE);
        REL_OPS_MAP.put(LESS_THAN, LT);
        REL_OPS_MAP.put(LESS_EQUALS, LE);
        REL_OPS_MAP.put(GREATER_THAN, GT);
        REL_OPS_MAP.put(GREATER_EQUALS, GE);
        REL_OPS_MAP.put(PascalTokenType.IN, ICodeNodeTypeImpl.IN);
    };

    /**
     * Parse an expression.
     * @param token the initial token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    private ICodeNode parseExpression(Token token)
        throws Exception
    {
        // Parse a simple expression and make the root of its tree
        // the root node.
        ICodeNode rootNode = parseSimpleExpression(token);
        TypeSpec resultType = rootNode != null ? rootNode.getTypeSpec()
                                               : Predefined.undefinedType;

        token = currentToken();
        TokenType tokenType = token.getType();

        // Look for a relational operator.
        if (REL_OPS.contains(tokenType)) {

            // Create a new operator node and adopt the current tree
            // as its first child.
            ICodeNodeType nodeType = REL_OPS_MAP.get(tokenType);
            ICodeNode opNode = ICodeFactory.createICodeNode(nodeType);
            opNode.addChild(rootNode);

            token = nextToken();  // consume the operator

            // Parse the second simple expression.  The operator node adopts
            // the simple expression's tree as its second child.
            ICodeNode simExprNode = parseSimpleExpression(token);
            opNode.addChild(simExprNode);

            // The operator node becomes the new root node.
            rootNode = opNode;

            // Type check: The operands must be comparison compatible.
            TypeSpec simExprType = simExprNode != null
                                       ? simExprNode.getTypeSpec()
                                       : Predefined.undefinedType;
                                       
            if (TypeChecker.areComparisonCompatible(resultType, simExprType)) {
                resultType = Predefined.booleanType;
            }
            else if (SET_REL_OPS.contains(tokenType)) {
            	if ((tokenType == PascalTokenType.IN) && TypeChecker.areContainmentCompatible(resultType, simExprType)) {
            		resultType = Predefined.booleanType;
            	}
            	else if (TypeChecker.areSetCompatible(resultType, simExprType)){
            		resultType = Predefined.booleanType;
            	}
            	else {
            		errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    resultType = Predefined.undefinedType;
            	}
            }
            else {
                errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                resultType = Predefined.undefinedType;
            }
        }

        if (rootNode != null) {
            rootNode.setTypeSpec(resultType);
        }

        return rootNode;
    }

    // Set of additive operators.
    private static final EnumSet<PascalTokenType> ADD_OPS =
        EnumSet.of(PLUS, MINUS, PascalTokenType.OR, PascalTokenType.SYM_DIFF);

    // Map additive operator tokens to node types.
    private static final HashMap<PascalTokenType, ICodeNodeTypeImpl>
        ADD_OPS_OPS_MAP = new HashMap<PascalTokenType, ICodeNodeTypeImpl>();
    static {
        ADD_OPS_OPS_MAP.put(PLUS, ADD);
        ADD_OPS_OPS_MAP.put(MINUS, SUBTRACT);
        ADD_OPS_OPS_MAP.put(PascalTokenType.OR, ICodeNodeTypeImpl.OR);
        ADD_OPS_OPS_MAP.put(PascalTokenType.SYM_DIFF, ICodeNodeTypeImpl.SYM_DIFF);
    };

    /**
     * Parse a simple expression.
     * @param token the initial token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    private ICodeNode parseSimpleExpression(Token token)
        throws Exception
    {
        Token signToken = null;
        TokenType signType = null;  // type of leading sign (if any)

        // Look for a leading + or - sign.
        TokenType tokenType = token.getType();
        if ((tokenType == PLUS) || (tokenType == MINUS)) {
            signType = tokenType;
            signToken = token;
            token = nextToken();  // consume the + or -
        }

        // Parse a term and make the root of its tree the root node.
        ICodeNode rootNode = parseTerm(token);
        TypeSpec resultType = rootNode != null ? rootNode.getTypeSpec()
                                               : Predefined.undefinedType;

        // Type check: Leading sign.
        if ((signType != null) && (!TypeChecker.isIntegerOrReal(resultType))) {
            errorHandler.flag(signToken, INCOMPATIBLE_TYPES, this);
        }

        // Was there a leading - sign?
        if (signType == MINUS) {

            // Create a NEGATE node and adopt the current tree
            // as its child. The NEGATE node becomes the new root node.
            ICodeNode negateNode = ICodeFactory.createICodeNode(NEGATE);
            negateNode.addChild(rootNode);
            negateNode.setTypeSpec(rootNode.getTypeSpec());
            rootNode = negateNode;
        }

        token = currentToken();
        tokenType = token.getType();

        // Loop over additive operators.
        while (ADD_OPS.contains(tokenType)) {
            TokenType operator = tokenType;

            // Create a new operator node and adopt the current tree
            // as its first child.
            ICodeNodeType nodeType = ADD_OPS_OPS_MAP.get(operator);
            ICodeNode opNode = ICodeFactory.createICodeNode(nodeType);
            opNode.addChild(rootNode);

            token = nextToken();  // consume the operator

            // Parse another term.  The operator node adopts
            // the term's tree as its second child.
            ICodeNode termNode = parseTerm(token);
            opNode.addChild(termNode);
            TypeSpec termType = termNode != null ? termNode.getTypeSpec()
                                                 : Predefined.undefinedType;

            // The operator node becomes the new root node.
            rootNode = opNode;

            // Determine the result type.
            switch ((PascalTokenType) operator) {

                case PLUS:
                case MINUS: {
                    // Both operands integer ==> integer result.
                    if (TypeChecker.areBothInteger(resultType, termType)) {
                        resultType = Predefined.integerType;
                    }

                    // Both real operands or one real and one integer operand
                    // ==> real result.
                    else if (TypeChecker.isAtLeastOneReal(resultType,
                                                          termType)) {
                        resultType = Predefined.realType;
                    }

                    // Both are set operands ==> set result
                    else if (TypeChecker.areSetCompatible(resultType, termType)) {
                    	resultType = TypeFactory.createType(TypeFormImpl.SET);
                    }
                    
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }

                case OR: {
                    // Both operands boolean ==> boolean result.
                    if (TypeChecker.areBothBoolean(resultType, termType)) {
                        resultType = Predefined.booleanType;
                    }
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }
                
                case SYM_DIFF: {
                	// Both are set operands ==> set result
                    if (TypeChecker.areSetCompatible(resultType, termType)) {
                    	resultType = TypeFactory.createType(TypeFormImpl.SET);
                    }
                    
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }
            }

            rootNode.setTypeSpec(resultType);

            token = currentToken();
            tokenType = token.getType();
        }

        return rootNode;
    }

    // Set of multiplicative operators.
    private static final EnumSet<PascalTokenType> MULT_OPS =
        EnumSet.of(STAR, SLASH, DIV, PascalTokenType.MOD, PascalTokenType.AND);

    // Map multiplicative operator tokens to node types.
    private static final HashMap<PascalTokenType, ICodeNodeType>
        MULT_OPS_OPS_MAP = new HashMap<PascalTokenType, ICodeNodeType>();
    static {
        MULT_OPS_OPS_MAP.put(STAR, MULTIPLY);
        MULT_OPS_OPS_MAP.put(SLASH, FLOAT_DIVIDE);
        MULT_OPS_OPS_MAP.put(DIV, INTEGER_DIVIDE);
        MULT_OPS_OPS_MAP.put(PascalTokenType.MOD, ICodeNodeTypeImpl.MOD);
        MULT_OPS_OPS_MAP.put(PascalTokenType.AND, ICodeNodeTypeImpl.AND);
    };

    /**
     * Parse a term.
     * @param token the initial token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    private ICodeNode parseTerm(Token token)
        throws Exception
    {
        // Parse a factor and make its node the root node.
        ICodeNode rootNode = parseFactor(token);
        TypeSpec resultType = rootNode != null ? rootNode.getTypeSpec()
                                               : Predefined.undefinedType;

        token = currentToken();
        TokenType tokenType = token.getType();

        // Loop over multiplicative operators.
        while (MULT_OPS.contains(tokenType)) {
            TokenType operator = tokenType;

            // Create a new operator node and adopt the current tree
            // as its first child.
            ICodeNodeType nodeType = MULT_OPS_OPS_MAP.get(operator);
            ICodeNode opNode = ICodeFactory.createICodeNode(nodeType);
            opNode.addChild(rootNode);

            token = nextToken();  // consume the operator

            // Parse another factor.  The operator node adopts
            // the term's tree as its second child.
            ICodeNode factorNode = parseFactor(token);
            opNode.addChild(factorNode);
            TypeSpec factorType = factorNode != null ? factorNode.getTypeSpec()
                                                     : Predefined.undefinedType;

            // The operator node becomes the new root node.
            rootNode = opNode;

            // Determine the result type.
            switch ((PascalTokenType) operator) {

                case STAR: {
                    // Both operands integer ==> integer result.
                    if (TypeChecker.areBothInteger(resultType, factorType)) {
                        resultType = Predefined.integerType;
                    }

                    // Both real operands or one real and one integer operand
                    // ==> real result.
                    else if (TypeChecker.isAtLeastOneReal(resultType,
                                                          factorType)) {
                        resultType = Predefined.realType;
                    }
                    
                    // Both are set operands ==> set result
                    else if (TypeChecker.areSetCompatible(resultType, factorType)) {
                    	resultType = TypeFactory.createType(TypeFormImpl.SET);
                    }
                    
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }

                case SLASH: {
                    // All integer and real operand combinations
                    // ==> real result.
                    if (TypeChecker.areBothInteger(resultType, factorType) ||
                        TypeChecker.isAtLeastOneReal(resultType, factorType))
                    {
                        resultType = Predefined.realType;
                    }
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }
                
                case DIV:
                case MOD: {
                    // Both operands integer ==> integer result.
                    if (TypeChecker.areBothInteger(resultType, factorType)) {
                        resultType = Predefined.integerType;
                    }
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }

                case AND: {
                    // Both operands boolean ==> boolean result.
                    if (TypeChecker.areBothBoolean(resultType, factorType)) {
                        resultType = Predefined.booleanType;
                    }
                    else {
                        errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                    }

                    break;
                }
            }

            rootNode.setTypeSpec(resultType);

            token = currentToken();
            tokenType = token.getType();
        }

        return rootNode;
    }

    /**
     * Parse a factor.
     * @param token the initial token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    private ICodeNode parseFactor(Token token)
        throws Exception
    {
        TokenType tokenType = token.getType();
        ICodeNode rootNode = null;

        switch ((PascalTokenType) tokenType) {

            case IDENTIFIER: {
                return parseIdentifier(token);
            }

            case INTEGER: {
                // Create an INTEGER_CONSTANT node as the root node.
                rootNode = ICodeFactory.createICodeNode(INTEGER_CONSTANT);
                rootNode.setAttribute(VALUE, token.getValue());

                token = nextToken();  // consume the number

                rootNode.setTypeSpec(Predefined.integerType);
                break;
            }

            case REAL: {
                // Create an REAL_CONSTANT node as the root node.
                rootNode = ICodeFactory.createICodeNode(REAL_CONSTANT);
                rootNode.setAttribute(VALUE, token.getValue());

                token = nextToken();  // consume the number

                rootNode.setTypeSpec(Predefined.realType);
                break;
            }

            case STRING: {
                String value = (String) token.getValue();

                // Create a STRING_CONSTANT node as the root node.
                rootNode = ICodeFactory.createICodeNode(STRING_CONSTANT);
                rootNode.setAttribute(VALUE, value);

                TypeSpec resultType = value.length() == 1
                                          ? Predefined.charType
                                          : TypeFactory.createStringType(value);

                token = nextToken();  // consume the string

                rootNode.setTypeSpec(resultType);
                break;
            }

            case NOT: {
                token = nextToken();  // consume the NOT

                // Create a NOT node as the root node.
                rootNode = ICodeFactory.createICodeNode(ICodeNodeTypeImpl.NOT);

                // Parse the factor.  The NOT node adopts the
                // factor node as its child.
                ICodeNode factorNode = parseFactor(token);
                rootNode.addChild(factorNode);

                // Type check: The factor must be boolean.
                TypeSpec factorType = factorNode != null
                                          ? factorNode.getTypeSpec()
                                          : Predefined.undefinedType;
                if (!TypeChecker.isBoolean(factorType)) {
                    errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                }

                rootNode.setTypeSpec(Predefined.booleanType);
                break;
            }

            case LEFT_PAREN: {
                token = nextToken();      // consume the (

                // Parse an expression and make its node the root node.
                rootNode = parseExpression(token);
                TypeSpec resultType = rootNode != null
                                          ? rootNode.getTypeSpec()
                                          : Predefined.undefinedType;

                // Look for the matching ) token.
                token = currentToken();
                if (token.getType() == RIGHT_PAREN) {
                    token = nextToken();  // consume the )
                }
                else {
                    errorHandler.flag(token, MISSING_RIGHT_PAREN, this);
                }

                rootNode.setTypeSpec(resultType);
                break;
            }

            case LEFT_BRACKET: {
            	return parseSet(token);
            }
            
            default: {
                errorHandler.flag(token, UNEXPECTED_TOKEN, this);
            }
        }
        
        return rootNode;
    }

    // Synchronization set for the ] token.
    private static final EnumSet<PascalTokenType> RIGHT_BRACKET_SET =
        EnumSet.of(RIGHT_BRACKET, SEMICOLON);
    
    // Synchronization set for the , token.
    private static final EnumSet<PascalTokenType> COMMA_SET = 
    	EXPR_START_SET.clone();
    static {
    	COMMA_SET.add(COMMA);
    	COMMA_SET.addAll(RIGHT_BRACKET_SET);
    }
    
    /**
     * Parse a set.
     * @param token the current token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    private ICodeNode parseSet(Token token)
        throws Exception
    {
    	token = nextToken();  // consume the [

        ICodeNode rootNode = null;
        ExpressionParser expressionParser = new ExpressionParser(this);

        // Create a SET node.
        rootNode = ICodeFactory.createICodeNode(ICodeNodeTypeImpl.SET);
        TypeSpec elementType = Predefined.undefinedType;
        
        int count = 0;
        boolean repeat;
    	do {
    		// check if we have an empty set
            if ((count != 0) || (token.getType() != RIGHT_BRACKET)) {
                // Parse the set element expression.
                ICodeNode exprNode = expressionParser.parse(token);
                
                token = currentToken();
        		
                TypeSpec exprType = exprNode != null ? exprNode.getTypeSpec()
                        : Predefined.undefinedType;
                
                if (!TypeChecker.isSubrangeCompatible(exprType)) {
                    errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                }
                else if (elementType == Predefined.undefinedType) {
                	elementType = exprType;
                }
                else if (elementType.baseType() != exprType.baseType()) {
                    errorHandler.flag(token, INCOMPATIBLE_TYPES, this);
                }
                
                rootNode.addChild(exprNode);
                count++;
            }
            
            repeat = false;
            
            // Synchronize on the , token.
            token = synchronize(COMMA_SET);
            if (token.getType() == COMMA) {
            	token = nextToken();  // consume the , token
            	repeat = true;
            }
            else if (EXPR_START_SET.contains(token.getType())) {
            	repeat = true;
            	errorHandler.flag(token, MISSING_COMMA, this);
            }
            
        } while (repeat);
    	
        if (token.getType() == RIGHT_BRACKET) {
            token = nextToken();  // consume the ] token
        }
        else {
            errorHandler.flag(token, MISSING_RIGHT_BRACKET, this);
        }

        TypeSpec setType = TypeFactory.createType(TypeFormImpl.SET);
        setType.setAttribute(SET_ELEMENT_TYPE, elementType);
        setType.setAttribute(SET_ELEMENT_COUNT, count);
        
        rootNode.setTypeSpec(setType);
        return rootNode;
    }
    
    /**
     * Parse an identifier.
     * @param token the current token.
     * @return the root node of the generated parse tree.
     * @throws Exception if an error occurred.
     */
    private ICodeNode parseIdentifier(Token token)
        throws Exception
    {
        ICodeNode rootNode = null;

        // Look up the identifier in the symbol table stack.
        String name = token.getText().toLowerCase();
        SymTabEntry id = symTabStack.lookup(name);

        // Undefined.
        if (id == null) {
            errorHandler.flag(token, IDENTIFIER_UNDEFINED, this);
            id = symTabStack.enterLocal(name);
            id.setDefinition(UNDEFINED);
            id.setTypeSpec(Predefined.undefinedType);
        }

        Definition defnCode = id.getDefinition();

        switch ((DefinitionImpl) defnCode) {

            case CONSTANT: {
                Object value = id.getAttribute(CONSTANT_VALUE);
                TypeSpec type = id.getTypeSpec();

                if (value instanceof Integer) {
                    rootNode = ICodeFactory.createICodeNode(INTEGER_CONSTANT);
                    rootNode.setAttribute(VALUE, value);
                }
                else if (value instanceof Float) {
                    rootNode = ICodeFactory.createICodeNode(REAL_CONSTANT);
                    rootNode.setAttribute(VALUE, value);
                }
                else if (value instanceof String) {
                    rootNode = ICodeFactory.createICodeNode(STRING_CONSTANT);
                    rootNode.setAttribute(VALUE, value);
                }

                id.appendLineNumber(token.getLineNumber());
                token = nextToken();  // consume the constant identifier

                if (rootNode != null) {
                    rootNode.setTypeSpec(type);
                }

                break;
            }

            case ENUMERATION_CONSTANT: {
                Object value = id.getAttribute(CONSTANT_VALUE);
                TypeSpec type = id.getTypeSpec();

                rootNode = ICodeFactory.createICodeNode(INTEGER_CONSTANT);
                rootNode.setAttribute(VALUE, value);

                id.appendLineNumber(token.getLineNumber());
                token = nextToken();  // consume the enum constant identifier

                rootNode.setTypeSpec(type);
                break;
            }

            default: {
                VariableParser variableParser = new VariableParser(this);
                rootNode = variableParser.parse(token, id);
                break;
            }
        }

        return rootNode;
    }
    
    /**
     * Check a value of a type specification.
     * @param token the current token.
     * @param value the value.
     * @param type the type specification.
     * @return the value.
     */
    private Object checkValueType(Token token, Object value, TypeSpec type)
    {
        if (type == null) {
            return value;
        }
        if (type == Predefined.integerType) {
            return value;
        }
        else if (type == Predefined.charType) {
            char ch = ((String) value).charAt(0);
            return Character.getNumericValue(ch);
        }
        else if (type.getForm() == ENUMERATION) {
            return value;
        }
        else {
            errorHandler.flag(token, INVALID_SUBRANGE_TYPE, this);
            return value;
        }
    }
}
