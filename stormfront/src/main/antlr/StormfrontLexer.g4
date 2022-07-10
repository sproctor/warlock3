lexer grammar StormfrontLexer;

// NOTE! This is only meant to work one 1 line of data with no newline characters

// Default mode. Everything outside of a tag
EntityRef   :   '&' Name ';' ;
CharRef     :   '&#' DIGIT+ ';'
            |   '&#x' HEXDIGIT+ ';'
            ;

OPEN        :   '<'                     -> pushMode(INSIDE) ;

TEXT        :   ~[<&]+ ;

// Everything inside of a tag
mode INSIDE;

CLOSE       :   '>'                     -> popMode ;
SLASH_CLOSE :   '/>'                    -> popMode ;
SLASH       :   '/' ;
EQUALS      :   '=' ;
STRING      :   '"' ~'"'* ('"' ~["=>]*)* '"' // handles strings in elements. accepts non-xml strings like: " - ["Kertigen's Honor"]" by reading too much an not taking an > or = from the next attribute or end of the tag
            |   '\'' ~'\''* ('\'' ~['=>]*)* '\''
            ;
Name        :   NameStartChar NameChar* ;
S           :   [ \t\r\n]               -> skip ;

fragment
HEXDIGIT    :   [a-fA-F0-9] ;
fragment
DIGIT       :   [0-9] ;

fragment
NameChar    :   NameStartChar
            |   DIGIT
            ;
fragment
NameStartChar:  [a-zA-Z_] ;