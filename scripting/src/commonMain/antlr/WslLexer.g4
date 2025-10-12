lexer grammar WslLexer;

options { caseInsensitive = true; }

LineComment
    : '#' ~[\r\n]*
      -> channel(HIDDEN)
    ;

IF
    : 'if' -> pushMode(EXPRESSION)
    ;

ELSE: 'else' Blank* -> pushMode(COMMAND);

Label
    : NameChar+ Blank* ':'
    ;

TEXT
    : Identifier -> pushMode(COMMAND);

NL: ('\n' | '\r' '\n'?) ;

PERCENT_LCURL
    : '%{'
      -> pushMode(EXPRESSION)
    ;

PERCENT
    : '%' -> pushMode(COMMAND), pushMode(VARIABLE);

Blank: [ \t] -> skip;

fragment Digit
    :   [0-9] ;

fragment NameChar
    : NameStartChar | '.' | Digit
    ;
fragment NameStartChar:  [a-z_] ;

fragment Identifier
    : NameStartChar NameChar*
    ;

fragment EscapedIdentifier
    : '\\' .
    ;

mode EXPRESSION;

NUMBER: (Digit | '.')+;
QUOTE_OPEN: '"' -> pushMode(QuotedString);
THEN
	: 'then' -> popMode
	;
RCURL: '}' -> popMode;
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
LSQUARE: '[';
RSQUARE: ']';
EXISTS: 'exists';
CONTAINS: 'contains';
CONTAINSRE: 'containsre';
TRUE: 'true';
FALSE: 'false';
BLANK: [ \t] -> skip;
ADD: '+';
SUB: '-';
MULT: '*';
DIV: '/';
IDENTIFIER: Identifier;
EXP_PERCENT: '%' -> type(PERCENT), pushMode(VARIABLE);
EXP_NL: ('\n' | '\r' '\n'?) -> type(NL), popMode;

mode COMMAND;

COMMAND_PERCENT_LCURL: '%{' -> type(PERCENT_LCURL), pushMode(EXPRESSION);
COMMAND_PERCENT: '%' -> type(PERCENT), pushMode(VARIABLE);
COMMAND_TEXT: (~('\\' | '%' | '\n' | '\r')+ | '\\' | '%') -> type(TEXT);
DOUBLE_PERCENT: '%%';
COMMAND_NL: ('\n' | '\r' '\n'?) -> type(NL), popMode;

mode QuotedString;

QUOTE_CLOSE: '"' -> popMode;
STRING_PERCENT_LCURL: '%{' -> type(PERCENT_LCURL), pushMode(EXPRESSION);
STRING_PERCENT: '%' -> type(PERCENT), pushMode(VARIABLE);
STRING_DOUBLE_PERCENT: '%%' -> type(DOUBLE_PERCENT);
StringText: ~('\\' | '"' | '%')+ | '%';
StringEscapedChar: EscapedIdentifier;

mode VARIABLE;

VARIABLE_NAME: NameChar+ '%'? -> popMode;
