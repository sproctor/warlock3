lexer grammar WslLexer;

LineComment
    : ('//' | ';' | '#') ~[\r\n]*
      -> channel(HIDDEN)
    ;

IF
    : 'if' -> pushMode(EXPRESSION)
    ;

Label
    : Identifier ':'
    ;

CommandIdentifier
    : Identifier -> pushMode(COMMAND);

PERCENT
    : '%' -> pushMode(COMMAND), pushMode(VARIABLE);

Blank: [ \t] -> skip;

fragment Digit
    :   [0-9] ;

fragment NameChar
    : NameStartChar | '.' | Digit
    ;
fragment NameStartChar:  [a-zA-Z_] ;

fragment Identifier
    : NameStartChar NameChar*
    ;

fragment WS: [ \t\r\n];

fragment EscapedIdentifier
    : '\\' ('t' | 'b' | 'r' | 'n' | '\'' | '"' | '\\' | '$')
    ;

mode EXPRESSION;

NUMBER: (Digit | '.')+;
QUOTE_OPEN: '"' -> pushMode(QuotedString);
THEN
	: 'then' -> popMode
	;
OR
	: ('or' | '||')
	;
AND
	: ('and' | '&&')
	;
EQ: '=';
NEQ: '!=';
LT: '<';
GT: '>';
LTE: '<=';
GTE: '>=';
NOT: 'not' | '!';
LPAREN: '(';
RPAREN: ')';
EXISTS: 'exists';
CONTAINS: 'contains' | 'indexof';
CONTAINSRE: 'containsre';
TRUE: 'true';
FALSE: 'false';
BLANK: [ \t] -> skip;
ADD: '+';
SUB: '-';
MULT: '*';
DIV: '/';

mode COMMAND;

CommandText: ~('\\' | '%' | '\n' | '\r') | '\\' | '%';
CommandRef: '%' Identifier '%'?;
NL: ('\n' | '\r' '\n'?) -> popMode;

mode QuotedString;

QUOTE_CLOSE: '"' -> popMode;
StringRef: '%' Identifier '%'?;
StringText: ~('\\' | '"' | '%')+ | '%';
StringEscapedChar: EscapedIdentifier;

mode VARIABLE;

VARIABLE_NAME: Identifier -> popMode;
