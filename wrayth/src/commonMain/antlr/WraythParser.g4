parser grammar WraythParser;

options { tokenVocab=WraythLexer; }

// NOTE! This is only meant to work one 1 line of data with no newline characters

document    :   content ;

content     :   chardata? ((element | reference) chardata?)* ;

element     :   startTag
            |   endTag
            |   emptyTag
            ;

startTag    :   OPEN Name attribute* CLOSE ;
endTag      :   OPEN SLASH Name CLOSE ;
emptyTag    :   OPEN Name attribute* SLASH_CLOSE ;

reference   :   EntityRef | CharRef ;

attribute   :   Name (EQUALS STRING)? ;

chardata    :   TEXT ;