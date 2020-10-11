package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.PrintStatement;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.ast.Visitor;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.util.Log4JUtils;

public class MJParserTest {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	public static void main(String[] args) throws Exception {
		Logger log = Logger.getLogger(MJParserTest.class);

	String[] filesTests = { "1", "2", "3", "4", "5", "ErrA", "ErrB", "ErrC" };
		for (String file : filesTests) {
			try (BufferedReader br = new BufferedReader(new FileReader("test/parserTests/test"+file + ".mj"))) {

				System.out.println("STARTED TEST "+file);

				Yylex lexer = new Yylex(br);
				MJParser p = new MJParser(lexer);
				Symbol s = p.parse();

				Program prog = (Program) (s.value);
				
				System.out.println(prog.toString(""));

			}
		}
	}
}