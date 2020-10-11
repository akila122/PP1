package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.mj.runtime.Run;
import rs.etf.pp1.mj.runtime.disasm;

public class MJCodeGeneratorTest {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	public static void main(String[] args) throws Exception {

		Logger log = Logger.getLogger(MJParserTest.class);

		Reader br = null;

		String[] files = {"301", "302", "303" };

		for (String file : files) {

			try {
				File sourceCode = new File("test/publicTests/test"+file+".mj");
				log.info("Compiling source file: " + sourceCode.getAbsolutePath());

				br = new BufferedReader(new FileReader(sourceCode));
				Yylex lexer = new Yylex(br);

				MJParser p = new MJParser(lexer);
				Symbol s = p.parse();

				Program prog = (Program) (s.value);
				SymTab.init();
				log.info(prog.toString(""));
				log.info("===================================");

				
				SemanticAnalyizer v = new SemanticAnalyizer();
				prog.traverseBottomUp(v);

				log.info("===================================");
				SymTab.dump(new DumpVisitorFix());

				if (!p.errorDetected && !v.errorDetected) {
					File objFile = new File("test/program.obj");
					if (objFile.exists())
						objFile.delete();

					Code.dataSize = v.nVars;
					CodeGenerator codeGenerator = new CodeGenerator();
					prog.traverseBottomUp(codeGenerator);
					Code.mainPc = codeGenerator.getMainPc();
					Code.write(new FileOutputStream(objFile));
					log.info("Compilation done!");
					String[] argv = { "test/program.obj" };
					disasm.main(argv);
					Run.main(argv);
				} else {
					log.error("Compilation failed.");
					if (p.errorDetected)
						log.error("Parsing failed.");
					if (v.errorDetected)
						log.error("Semantics errors encouterd in code.");
				}
			} finally {
				if (br != null)
					try {
						br.close();
					} catch (IOException e1) {
						log.error(e1.getMessage(), e1);
					}
			}

		}
	}
}
