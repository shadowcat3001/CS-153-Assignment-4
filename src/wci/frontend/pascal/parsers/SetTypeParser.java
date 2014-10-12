package wci.frontend.pascal.parsers;

import java.util.EnumSet;
import java.util.ArrayList;

import wci.frontend.*;
import wci.frontend.pascal.*;
import wci.intermediate.*;
import wci.intermediate.symtabimpl.*;
import wci.intermediate.typeimpl.TypeFormImpl;
import static wci.frontend.pascal.PascalTokenType.*;
import static wci.frontend.pascal.PascalErrorCode.*;
import static wci.intermediate.symtabimpl.SymTabKeyImpl.*;
import static wci.intermediate.typeimpl.TypeFormImpl.ARRAY;
import static wci.intermediate.typeimpl.TypeFormImpl.SUBRANGE;
import static wci.intermediate.typeimpl.TypeFormImpl.ENUMERATION;
import static wci.intermediate.typeimpl.TypeKeyImpl.*;

class SetTypeParser extends TypeSpecificationParser
{
    /**
     * Constructor.
     * @param parent the parent parser.
     */
    protected SetTypeParser(PascalParserTD parent)
    {
        super(parent);
    }

    // Synchronization set for OF.
    private static final EnumSet<PascalTokenType> OF_SET =
        TypeSpecificationParser.TYPE_START_SET.clone();
    static {
        OF_SET.add(OF);
        OF_SET.add(SEMICOLON);
    }

    /**
     * Parse a Pascal set type specification.
     * @param token the current token.
     * @return the set type specification.
     * @throws Exception if an error occurred.
     */
    public TypeSpec parse(Token token)
        throws Exception
    {
        TypeSpec setType = TypeFactory.createType(TypeFormImpl.SET);
        token = nextToken();  // consume SET

        // Synchronize at OF.
        token = synchronize(OF_SET);
        if (token.getType() == OF) {
            token = nextToken();  // consume OF
        }
        else {
            errorHandler.flag(token, MISSING_OF, this);
        }

        Token typeToken = token;
        SimpleTypeParser simpleTypeParser = new SimpleTypeParser(this);
        TypeSpec elementType = simpleTypeParser.parse(token);
        setType.setAttribute(SET_ELEMENT_TYPE, elementType);

        
        if (elementType == null) {
        	errorHandler.flag(typeToken, INCOMPATIBLE_TYPES, this);
        }
        
        TypeForm form = elementType.getForm();
        int count = 0;
        
        /*if ((form != SUBRANGE) || (form != ENUMERATION)) {
        	errorHandler.flag(token, INVALID_SET_TYPE, this);
        }*/
        
        // Check the index type and set the element count.
        if (form == SUBRANGE) {
            Integer minValue =
                (Integer) elementType.getAttribute(SUBRANGE_MIN_VALUE);
            Integer maxValue =
                (Integer) elementType.getAttribute(SUBRANGE_MAX_VALUE);

            if ((minValue != null) && (maxValue != null)) {
                count = maxValue - minValue + 1;
            }
        }
        else if (form == ENUMERATION) {
            ArrayList<SymTabEntry> constants = (ArrayList<SymTabEntry>)
                elementType.getAttribute(ENUMERATION_CONSTANTS);
            count = constants.size();
        }
        else {
            errorHandler.flag(token, INVALID_SET_TYPE, this);
        }
        setType.setAttribute(SET_ELEMENT_COUNT, count);

        return setType;
    }
}
