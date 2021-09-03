parser grammar WslParser;

options { tokenVocab=WslLexer; }

script
	: line (NL line)* EOF
	;

line
	: Label? statement;

statement
    : IF expression THEN statement
    | command
    ;

command
    : commandContent*
    ;

commandContent
    : TEXT
    | PERCENT VARIABLE_NAME
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
    : variable
    | NUMBER
    | TRUE
    | FALSE
    | stringLiteral
    ;

variable
    : PERCENT VARIABLE_NAME
    ;

stringLiteral
    : QUOTE_OPEN stringContent* QUOTE_CLOSE;

stringContent
    : StringText
    | PERCENT VARIABLE_NAME
    | StringEscapedChar
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