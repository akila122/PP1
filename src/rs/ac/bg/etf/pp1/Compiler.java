package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.mj.runtime.Run;
import rs.etf.pp1.mj.runtime.disasm;

public class Compiler {

	public static SymTab tab;
	public static void tsdump() {
		SymTab.dump(new DumpVisitorFix());
		
	}
	public static void main(String[] args) {
		Logger log = Logger.getLogger(Compiler.class);

		if (args.length == 0 || args.length > 3 || args.length == 3 && !args[2].equals("run")) {
			log.error("Ivalid arguments passed");
			return;
		}
		
		Reader br = null;
		try {
			File sourceCode = new File(args[0]);
			if(!args[0].matches("^.+\\.mj$")) {
				log.error("Invalid input file passed");
				return;
			}
			if(!args[1].matches("^.+\\.obj$")) {
				log.error("Invalid output file passed");
				return;
			}
			
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());

			try {
				br = new BufferedReader(new FileReader(sourceCode));
			} catch (FileNotFoundException e) {
				log.error("Input file does not exist");
				return;
			}
			Yylex lexer = new Yylex(br);

			MJParser p = new MJParser(lexer);
			Symbol s;
			try {
				s = p.parse();
			} catch (Exception e) {
				log.error("Parsing failed.");
				return;
			}

			Program prog = (Program) (s.value);
			SymTab.init();
			log.info(prog.toString(""));
			log.info("===================================");

			SemanticAnalyizer v = new SemanticAnalyizer();
			try {
			prog.traverseBottomUp(v);
			}
			catch(Exception e) {
				log.error("Semantic Analyizer failed");
			}
			log.info("===================================");
			

			if (!p.errorDetected && !v.errorDetected) {
				tsdump();
				File objFile = new File(args[1]);
				if (objFile.exists())
					objFile.delete();

				Code.dataSize = v.nVars;
				CodeGenerator codeGenerator = new CodeGenerator();
				try {
				prog.traverseBottomUp(codeGenerator);
				}
				catch(Exception e) {
					log.error("Code generator failed.");
				}
				Code.mainPc = codeGenerator.getMainPc();
				try {
					Code.write(new FileOutputStream(objFile));
				} catch (FileNotFoundException e) {
					log.error("Output file path does not exist.");
					return;
				}
				log.info("Compilation done!");
				if(args.length == 3) {
					String[] argv = {args[1]};
					disasm.main(argv);
					Run.main(argv);
				}
			} else {
				log.error("Compilation failed.");
				if (p.errorDetected)
					log.error("Parsing failed.");
				if (v.errorDetected)
					log.error("Semantics errors encouterd in code.");
			}
		}
		finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e1) {
					log.error(e1.getMessage(), e1);
				}
		}

	}

}
