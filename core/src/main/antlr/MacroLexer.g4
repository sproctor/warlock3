lexer grammar MacroLexer;

Entity: '\\' . ;

Percent: '%' -> skip, pushMode(VARIABLE);

LCurl: '{' -> skip, pushMode(COMMAND);

Question: '?' ;

At: '@' ;

Character: . ;

fragment Digit
    :   [0-9] ;

fragment NameChar
    : NameStartChar | '.' | Digit
    ;
fragment NameStartChar:  [a-zA-Z_] ;

mode VARIABLE;

VariableName: NameChar+ '%'? -> popMode;

VARIABLE_Character: '%' -> type(Character), popMode;

mode COMMAND;

CommandText: ~('}')+ ;

RCurl: '}' -> skip, popMode;