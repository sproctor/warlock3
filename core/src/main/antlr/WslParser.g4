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
    : unaryExpression (multiplicativeOperator unaryExpression)*
    ;

unaryExpression
    : unaryOperator unaryExpression
    | primaryExpression
    ;

primaryExpression
    : LPAREN disjunction RPAREN
    | valueExpression
    ;

valueExpression
    : IDENTIFIER
    | NUMBER
    | TRUE
    | FALSE
    | stringLiteral
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

unaryOperator
    : NOT
    | EXISTS
    ;