//Imports

package rs.ac.bg.etf.pp1;
import java_cup.runtime.Symbol;

%%

%{

	private Symbol new_symbol(int type){
		return new Symbol(type,yyline+1,yycolumn);
	}
	private Symbol new_symbol(int type, Object value){
		return new Symbol(type,yyline+1,yycolumn,value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\r\n" 	{ }
"\f" 	{ }

"//"             { yybegin(COMMENT);	}
<COMMENT> .      { yybegin(COMMENT); 	}
<COMMENT> "\r\n" { yybegin(YYINITIAL); 	}

"program"		{ return new_symbol(sym.PROGRAM,yytext()); 	}
"break"         { return new_symbol(sym.BREAK,yytext()); 	}
"class" 		{ return new_symbol(sym.CLASS,yytext()); 	}
"abstract" 		{ return new_symbol(sym.ABSTRACT,yytext()); }
"else" 			{ return new_symbol(sym.ELSE,yytext()); 	}
"const"			{ return new_symbol(sym.CONST,yytext()); 	}
"if"			{ return new_symbol(sym.IF,yytext()); 		}
"new"			{ return new_symbol(sym.NEW,yytext()); 		}
"print"			{ return new_symbol(sym.PRINT,yytext()); 	}
"read"			{ return new_symbol(sym.READ,yytext()); 	}
"return"		{ return new_symbol(sym.RETURN,yytext()); 	}
"void"			{ return new_symbol(sym.VOID,yytext()); 	}
"for"			{ return new_symbol(sym.FOR,yytext()); 		}
"extends"		{ return new_symbol(sym.EXTENDS,yytext()); 	}
"continue"		{ return new_symbol(sym.CONTINUE,yytext()); }
"public"		{ return new_symbol(sym.PUBLIC,yytext()); 	}
"protected"		{ return new_symbol(sym.PROTECTED,yytext());}
"private"		{ return new_symbol(sym.PRIVATE,yytext()); 	}
"foreach"		{ return new_symbol(sym.FOREACH,yytext()); }
 
 "+"  { return new_symbol(sym.PLUS);   	}
"-"   { return new_symbol(sym.MINUS);  	}
"*"   { return new_symbol(sym.MUL);    	}
"/"   { return new_symbol(sym.DIV);    	}
"%"   { return new_symbol(sym.PRC);    	}
"=="  { return new_symbol(sym.EQL);    	}
"!="  { return new_symbol(sym.NEQ);    	}
">"   { return new_symbol(sym.GT);     	}
">="  { return new_symbol(sym.GTE);    	}
"<"   { return new_symbol(sym.LT);     	}
"<="  { return new_symbol(sym.LTE);    	}
"&&"  { return new_symbol(sym.AND);    	}
"||"  { return new_symbol(sym.OR);     	}
"="   { return new_symbol(sym.EQ); 	   	}
"++"  { return new_symbol(sym.DPLUS);  	}
"--"  { return new_symbol(sym.DMINUS); 	}
";"   { return new_symbol(sym.SEMI);	}
","   { return new_symbol(sym.COMMA);	}
"."   { return new_symbol(sym.DOT);		}
"("   { return new_symbol(sym.LPAREN); 	}
")"   { return new_symbol(sym.RPAREN); 	}
"{"   { return new_symbol(sym.LBRACE); 	}
"}"   { return new_symbol(sym.RBRACE); 	}
"["   { return new_symbol(sym.LBRACK); 	}
"]"   { return new_symbol(sym.RBRACK); 	}
":"   { return new_symbol(sym.COL);    	}
"+="  { return new_symbol(sym.PLEQ);   	}
"-="  { return new_symbol(sym.MNEQ);   	}
"*="  { return new_symbol(sym.MLEQ);  	} 
"/="  { return new_symbol(sym.DVEQ);  	}
"%="  { return new_symbol(sym.PREQ);   	}

 
("true"|"false")		  	    	{ return new_symbol(sym.BOOL_CONST, Boolean.valueOf(yytext()));}
 -?[0-9]+ 							{ return new_symbol(sym.NUM_CONST, new Integer(yytext()));		}
([a-z]|[A-Z])[a-z|A-Z|0-9|_]*  		{ return new_symbol(sym.IDENT,yytext()); 						}
\'([a-z|A-Z])\' 					{ return new_symbol(sym.CHAR_CONST,yytext().charAt(1)); 		}




. { System.err.println("Lexer can't recognize ("+yytext()+") as a valid token at line "+(yyline+1)+"("+(yycolumn+1)+")"); }

