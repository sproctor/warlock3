parser grammar WslParser;

options { tokenVocab=WslLexer; }

script
	: line (NL line)* EOF
	;

line
	: Label* statement;

statement
    : ifExpression
    | command
    ;

ifExpression
    : IF expression THEN command (NL* ELSE command)?;

command
    : commandContent*
    ;

commandContent
    : TEXT
    | PERCENT VARIABLE_NAME
    | PERCENT_LCURL expression RCURL
    | DOUBLE_PERCENT
    ;

expression
    : disjunction
    ;

disjunction
    : conjunction (OR conjunction)*
    ;

conjunction
    : equality (AND equality)*
    ;

equality
    : comparison (equalityOperator comparison)*
    ;

comparison
    : infixExpression (comparisonOperator infixExpression)*
    ;

infixExpression
    : additiveExpression (infixOperator additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression (additiveOperator multiplicativeExpression)*
    ;

multiplicativeExpression
    : prefixUnaryExpression (multiplicativeOperator prefixUnaryExpression)*
    ;

prefixUnaryExpression
    : prefixUnaryOperator* postfixUnaryExpression
    ;

postfixUnaryExpression
    : primaryExpression indexingSuffix*
    ;

primaryExpression
    : LPAREN disjunction RPAREN
    | valueExpression
    ;

valueExpression
    : variableExpression
    | NUMBER
    | TRUE
    | FALSE
    | stringLiteral
    ;

variableExpression
    : IDENTIFIER
    | PERCENT VARIABLE_NAME
    ;

stringLiteral
    : QUOTE_OPEN stringContent* QUOTE_CLOSE;

stringContent
    : StringText
    | PERCENT VARIABLE_NAME
    | StringEscapedChar
    | DOUBLE_PERCENT
    ;

equalityOperator
    : EQ
    | NEQ
    ;

comparisonOperator
    : GT
    | LT
    | GTE
    | LTE
    ;

infixOperator
    : CONTAINS
    | CONTAINSRE
    ;

additiveOperator
    : ADD
    | SUB
    ;

multiplicativeOperator
    : MULT
    | DIV
    ;

prefixUnaryOperator
    : NOT
    | EXISTS
    ;

indexingSuffix
    : LSQUARE expression RSQUARE
    ;