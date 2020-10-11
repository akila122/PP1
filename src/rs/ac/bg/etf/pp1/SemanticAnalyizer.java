package rs.ac.bg.etf.pp1;


import java.util.HashSet;
import java.util.Iterator;

import java.util.Set;
import java.util.function.Consumer;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

public class SemanticAnalyizer extends VisitorAdaptor {

	Obj currentMeth = null;
	Struct currClass = null;

	public static final int STATIC = 0;
	public static final int PUBLIC = 1;
	public static final int PROTECTED = 2;
	public static final int PRIVATE = 3;

	private boolean detect = true;
	
	public SemanticAnalyizer(boolean detect) {
		this.detect = detect;
	}
	public SemanticAnalyizer() {
		
	}
	
	
	@Override
	public void visit(ProgName progName) {

		progName.obj = SymTab.insert(Obj.Prog, progName.getProgName(), SymTab.noType);

		SymTab.openScope();

		SymTab.insert(Obj.Var, "%HELPER_1%", SymTab.intType);
		SymTab.insert(Obj.Var, "%HELPER_2%", SymTab.intType);
		SymTab.insert(Obj.Var, "$THIS$", SymTab.intType);
		this.nVars += 3;

	}

	@Override
	public void visit(Program program) {
		SymTab.chainLocalSymbols(program.getProgName().obj);
		SymTab.closeScope();
		checkForMain(program);
	}

	private void checkForMain(Program program) {

		MethodDeclList iter;

		boolean mainFound = false;

		if (program.getMethodDeclList() instanceof MethodDeclListSome) {

			iter = (MethodDeclListSome) program.getMethodDeclList();

			while (iter instanceof MethodDeclListSome) {

				Obj obj = ((MethodDeclListSome) iter).getMethodDecl().getMethodName().obj;

				if (obj.getName().equals("main") && obj.getKind() == Obj.Meth && obj.getLevel() == 0
						&& obj.getType() == SymTab.noType) {
					mainFound = true;
					break;
				}

				iter = ((MethodDeclListSome) iter).getMethodDeclList();

			}

			if (!mainFound) {
				report_error("Entry point void main() not found", null);
			}

		} else
			report_error("Entry point void main() not found", null);

	}

	@Override
	public void visit(VarChainElSingle elem) {

		String name = elem.getVarName();
		SyntaxNode classIter = elem;
		while (classIter != null && !(classIter instanceof ClassVarDecl))
			classIter = classIter.getParent();

		boolean inClass = classIter != null;

		if (SymTab.currentScope.findSymbol(name) != null) {
			report_error("Symbol " + elem.getVarName() + " already declared in current scope", elem);
		} else {

			SyntaxNode iter = elem;
			while (!(iter instanceof VarDeclValid))
				iter = iter.getParent();

			Struct type = ((VarDeclValid) iter).getType().struct;
			elem.obj = SymTab.insert(inClass ? Obj.Fld : Obj.Var, name, type);

			int level = 0;
			if (inClass)
				level++;
			if (currentMeth != null)
				level++;
			elem.obj.setLevel(level);

			if (inClass) {
				int access = ((ClassVarDecl) classIter).getAccessModifi() instanceof Public ? 1
						: ((ClassVarDecl) classIter).getAccessModifi() instanceof Protected ? 2 : 3;

				elem.obj.setFpPos(access);
				SymTab.accessRules.get(currClass).add(elem.obj);
			}

			if (level == 0)
				nVars++;
		}
	}

	private void printAccess(Struct type) {

		StringBuffer sb = new StringBuffer();
		SymTab.accessRules.get(currClass).forEach(new Consumer<Obj>() {

			@Override
			public void accept(Obj t) {
				sb.append(SymTab.ObjToStr(t) + ",");
			}

		});
		System.out.println(sb.toString());

	}

	@Override
	public void visit(VarChainElArr elem) {

		String name = elem.getArrName();
		SyntaxNode classIter = elem;
		while (classIter != null && !(classIter instanceof ClassVarDecl))
			classIter = classIter.getParent();

		boolean inClass = classIter != null;

		if (SymTab.currentScope.findSymbol(name) != null) {
			report_error("Symbol " + elem.getArrName() + " already declared in current scope", elem);
		} else {

			SyntaxNode iter = elem;
			while (!(iter instanceof VarDeclValid))
				iter = iter.getParent();

			Struct type = ((VarDeclValid) iter).getType().struct;
			elem.obj = SymTab.insert(inClass ? Obj.Fld : Obj.Var, name, SymTab.getArrType(type));
			int level = 0;
			if (inClass)
				level++;
			if (currentMeth != null)
				level++;
			elem.obj.setLevel(level);

			if (inClass) {
				int access = ((ClassVarDecl) classIter).getAccessModifi() instanceof Public ? 1
						: ((ClassVarDecl) classIter).getAccessModifi() instanceof Protected ? 2 : 3;

				elem.obj.setFpPos(access);
				SymTab.accessRules.get(currClass).add(elem.obj);

			}
			if (level == 0)
				nVars++;
		}
	}

	@Override
	public void visit(ConstNum elem) {

		String name = ((ConstChainElValid) elem.getParent()).getConstName();

		if (SymTab.currentScope.findSymbol(name) != null)
			report_error("Symbol " + name + " already declared in current scope", elem.getParent());
		else {

			SyntaxNode iter = elem;
			while (!(iter instanceof ConstDeclValid))
				iter = iter.getParent();

			if (!((ConstDeclValid) iter).getType().getType().equals("int"))
				report_error("Symbol " + name + " initialization invalid", elem.getParent());

			Obj obj = SymTab.insert(Obj.Con, name, SymTab.intType);
			obj.setAdr(elem.getVal());
			obj.setLevel(0);
			((ConstChainEl) elem.getParent()).obj = obj;

		}

	}

	@Override
	public void visit(ConstChar elem) {

		String name = ((ConstChainElValid) elem.getParent()).getConstName();

		if (SymTab.currentScope.findSymbol(name) != null)
			report_error("Symbol " + name + " already declared in current scope", elem.getParent());
		else {

			SyntaxNode iter = elem;
			while (!(iter instanceof ConstDeclValid))
				iter = iter.getParent();

			if (!((ConstDeclValid) iter).getType().getType().equals("char"))
				report_error("Symbol " + name + " initialization invalid", elem.getParent());

			Obj obj = SymTab.insert(Obj.Con, name, SymTab.charType);
			obj.setAdr(elem.getVal());
			obj.setLevel(0);
			((ConstChainEl) elem.getParent()).obj = obj;
		}
	}

	@Override
	public void visit(ConstBool elem) {

		String name = ((ConstChainElValid) elem.getParent()).getConstName();

		if (SymTab.currentScope.findSymbol(name) != null)
			report_error("Symbol " + name + " already declared in current scope", elem.getParent());
		else {

			SyntaxNode iter = elem;
			while (!(iter instanceof ConstDeclValid))
				iter = iter.getParent();

			if (!((ConstDeclValid) iter).getType().getType().equals("bool"))
				report_error("Symbol " + name + " initialization invalid", elem.getParent());

			Obj obj = SymTab.insert(Obj.Con, name, SymTab.boolType);
			obj.setAdr(elem.getVal() ? 1 : 0);
			obj.setLevel(0);
			((ConstChainEl) elem.getParent()).obj = obj;
		}
	}

	@Override
	public void visit(Type type) {

		Obj typeNode = SymTab.find(type.getType());
		if (typeNode != SymTab.noObj) {
			if (typeNode.getKind() == Obj.Type) {
				type.struct = typeNode.getType();
			} else {
				type.struct = SymTab.noType;
				report_error("Used symbol is no a valid type " + type.getType(), type);
			}
		} else {
			report_error("Type used but not declared " + type.getType(), type);
		}

	}

	public void visit(MethodName node) {
		if (SymTab.currentScope == null)
			return;
		if (SymTab.currentScope.findSymbol(node.getName()) != null) {
			report_error("Symbol " + node.getName() + " already declared in current scope", node);
		} else {

			((MethodDecl) node.getParent()).obj = node.obj = SymTab.insert(Obj.Meth, node.getName(),
					node.getMethodReturn() instanceof MethodReturnSome
							? ((MethodReturnSome) node.getMethodReturn()).getType().struct
							: SymTab.noType);
			SymTab.openScope();
			currentMeth = node.obj;
			SyntaxNode iter = node;
			while (iter != null && !(iter instanceof ClassMethodDecl || iter instanceof AbstractMethodDecl))
				iter = iter.getParent();

			if (iter != null) {
				int access = 0;
				if (iter instanceof ClassMethodDecl)
					access = ((ClassMethodDecl) iter).getAccessModifi() instanceof Public ? 1
							: ((ClassMethodDecl) iter).getAccessModifi() instanceof Protected ? 2 : 3;
				if (iter instanceof AbstractMethodDeclA)
					access = ((AbstractMethodDeclA) iter).getAccessModifi() instanceof Public ? 1
							: ((AbstractMethodDeclA) iter).getAccessModifi() instanceof Protected ? 2 : 3;
				if (iter instanceof AbstractMethodDeclB)
					access = ((AbstractMethodDeclB) iter).getAccessModifi() instanceof Public ? 1
							: ((AbstractMethodDeclB) iter).getAccessModifi() instanceof Protected ? 2 : 3;

				node.obj.setFpPos(access);

				SymTab.accessRules.get(currClass).add(node.obj);

				while (!(iter instanceof ClassDecl || iter instanceof AbstractClassDecl))
					iter = iter.getParent();

				if (iter instanceof ClassDecl)
					SymTab.insert(Obj.Var, "this", ((ClassDecl) iter).obj.getType());
				if (iter instanceof AbstractClassDecl)
					SymTab.insert(Obj.Var, "this", ((AbstractClassDecl) iter).obj.getType());
			}

		}
	}

	public void visit(MethodDecl node) {

		currentMeth = null;

		SymTab.closeScope();

	}

	public void visit(MethodVars node) {
		if(((MethodDecl) node.getParent()).getMethodName().obj == null)
			return;
		SymTab.chainLocalSymbols(((MethodDecl) node.getParent()).getMethodName().obj);
	}

	public void visit(FormParSingle node) {

		if (SymTab.currentScope().findSymbol(node.getParName()) != null) {
			report_error("Symbol " + node.getParName() + " already declared in current scope", node);
		} else {
			SyntaxNode iter = node;
			while (!(iter instanceof MethodDecl) && !(iter instanceof AbstractMethodDecl))
				iter = iter.getParent();
			int lvl = iter instanceof MethodDecl ? ((MethodDecl) iter).obj.getLevel()
					: ((AbstractMethodDecl) iter).obj.getLevel();
			if (iter instanceof MethodDecl)
				((MethodDecl) iter).obj.setLevel(lvl + 1);
			else
				((AbstractMethodDecl) iter).obj.setLevel(lvl + 1);

			Obj newObj = SymTab.insert(Obj.Var, node.getParName(), node.getType().struct);
			// newObj.setFpPos(lvl);
			int lvlObj = 1;
			if (currClass != null)
				lvlObj++;
			newObj.setLevel(lvlObj);
		}

	}

	public void visit(FormParArr node) {
		if (SymTab.currentScope().findSymbol(node.getParArrName()) != null) {
			report_error("Symbol " + node.getParArrName() + " already declared in current scope", node);
		} else {
			SyntaxNode iter = node;
			while (!(iter instanceof MethodDecl) && !(iter instanceof AbstractMethodDecl))
				iter = iter.getParent();
			int lvl = iter instanceof MethodDecl ? ((MethodDecl) iter).obj.getLevel()
					: ((AbstractMethodDecl) iter).obj.getLevel();
			if (iter instanceof MethodDecl)
				((MethodDecl) iter).obj.setLevel(lvl + 1);
			else
				((AbstractMethodDecl) iter).obj.setLevel(lvl + 1);

			Obj newObj = SymTab.insert(Obj.Var, node.getParArrName(), SymTab.getArrType(node.getType().struct));
			// newObj.setFpPos(lvl);
			int lvlObj = 1;
			if (currClass != null)
				lvlObj++;
			newObj.setLevel(lvlObj);
		}
	}

	public void visit(ClassNamingValid node) {
		if (SymTab.currentScope().findSymbol(node.getClassName()) != null) {
			report_error("Symbol " + node.getClassName() + " already declared in current scope", node);
		} else {

			Struct superClass = null;

			try {
				superClass = node.getExtendOpt() instanceof ExtendOptSome
						? ((ExtendOptSome) node.getExtendOpt()).getType().struct
						: SymTab.nullType;
			} catch (NullPointerException err) {
				report_error("Cannot extend class " + node.getClassName() + " from non extendable type", node);
			}
			if (superClass == null || superClass != SymTab.nullType && superClass.getKind() != Struct.Class) {
				report_error("Cannot extend class " + node.getClassName() + " from non extendable type", node);
			} else {
				Struct newStruct = new Struct(Struct.Class);
				newStruct.setElementType(superClass);
				if (node.getParent() instanceof ClassDecl)
					((ClassDecl) node.getParent()).obj = SymTab.insert(Obj.Type, node.getClassName(), newStruct);
				if (node.getParent() instanceof AbstractClassDecl) {
					((AbstractClassDecl) node.getParent()).obj = SymTab.insert(Obj.Type, node.getClassName(),
							newStruct);
					((AbstractClassDecl) node.getParent()).obj.setLevel(-1);
					SymTab.abstractClasses.add(newStruct);
				}
				Obj obj = node.getParent() instanceof ClassDecl ? ((ClassDecl) node.getParent()).obj
						: ((AbstractClassDecl) node.getParent()).obj;
				SymTab.structToObj.put(newStruct, obj);
				currClass = newStruct;
				SymTab.openScope();
				// Cloning

				SymbolDataStructure list = new HashTableDataStructure();

				Set<Obj> newSet = new HashSet<>();
				SymTab.accessRules.put(newStruct, newSet);
				if (superClass != SymTab.nullType) {
					for (Obj member : superClass.getMembers()) {
						Obj clone = SymTab.clone(member);
						if (Math.abs(member.getFpPos()) != PRIVATE) {
							newSet.add(clone);
						}
						list.insertKey(clone);
					}
					newStruct.setMembers(list);

				}
			}
		}
	}

	public void visit(MatchedFor node) {
		detection("For control block", null, node.getParent());

	}

	public void visit(UnmatchedFor node) {
		detection("For control block", null, node.getParent());
	}

	public void visit(AbstractClassDecl node) {
		SymbolDataStructure fields = new HashTableDataStructure();
		SymbolDataStructure methods = new HashTableDataStructure();
		SymbolDataStructure union = new HashTableDataStructure();

		Struct newType = node.obj.getType();

		for (Obj obj : newType.getMembers())
			if (obj.getKind() == Obj.Fld)
				fields.insertKey(obj);
			else if (obj.getKind() == Obj.Meth)
				methods.insertKey(obj);

		for (Obj newField : SymTab.currentScope.values()) {
			if (newField.getKind() == Obj.Fld) {
				if (fields.searchKey(newField.getName()) != null) {
					report_error("Invalid class extenstion for symbol " + newField.getName()
							+ ". Field hiding not implemented ", node);
				} else {
					fields.insertKey(newField);

				}
			}
			if (newField.getKind() == Obj.Meth) {
				if (methods.searchKey(newField.getName()) != null) {

					Obj methodIn = methods.searchKey(newField.getName());

					if (!SymTab.isLower(newField.getType(), methodIn.getType())) {
						report_error("Invalid overriding of " + methodIn.getName() + " method. Return types differ",
								null);
					} else if (newField.getLevel() != methodIn.getLevel()) {
						report_error("Invalid overriding of " + methodIn.getName()
								+ " method. Number of formal parameters differ", null);
					} else {

						int cnt = methodIn.getLevel();
						int i = 0;
						Iterator<Obj> oIter = newField.getLocalSymbols().iterator();
						boolean test = true;
						for (Obj o : methodIn.getLocalSymbols()) {

							if (i == cnt)
								break;
							Obj newObj = oIter.next();
							Struct newStruct = newObj.getType();

							if (!SymTab.isLower(newStruct, o.getType())) {
								report_error("Invalid overriding of " + methodIn.getName()
										+ " method. Formal params at " + i + " position are not compatible", null);
								test = false;
								break;
							}
							i++;
						}
						if (test) {

							if (Math.abs(newField.getFpPos()) > Math.abs(methodIn.getFpPos())) {
								report_error("Invalid overriding of " + methodIn.getName()
										+ " method. Cannot restrict access modifier.", null);
							}

							methods.deleteKey(newField.getName());
							methods.insertKey(newField);

						}
					}
				} else {
					methods.insertKey(newField);

				}
			}
		}

		Obj vftp = new Obj(Obj.Fld,"$vftp$",SymTab.intType);
		union.insertKey(vftp);
		
		
		for (Obj o : fields.symbols()) {
			union.insertKey(o);
		}
		
		int cnt = 0;
		
		for(Obj o : union.symbols())
			o.setAdr(cnt++);
		
		for (Obj o : methods.symbols()) {
			union.insertKey(o);
		}

		newType.setMembers(union);

		SymTab.closeScope();
		currClass = null;

		detection("Abstract class declaration", node.obj, node);

	}

	public void visit(ClassDecl node) {

		SymbolDataStructure fields = new HashTableDataStructure();
		SymbolDataStructure methods = new HashTableDataStructure();
		SymbolDataStructure union = new HashTableDataStructure();

		if (node.obj == null) {
			report_error("Class declaration invalid", node);
			return;

		}

		Struct newType = node.obj.getType();

		for (Obj obj : newType.getMembers())
			if (obj.getKind() == Obj.Fld)
				fields.insertKey(obj);
			else if (obj.getKind() == Obj.Meth)
				methods.insertKey(obj);

		for (Obj newField : SymTab.currentScope.values()) {
			if (newField.getKind() == Obj.Fld) {
				if (fields.searchKey(newField.getName()) != null) {
					report_error("Invalid class extenstion for symbol " + newField.getName()
							+ ". Field hiding not implemented ", node);
				} else {
					fields.insertKey(newField);

				}
			}
			if (newField.getKind() == Obj.Meth) {
				if (methods.searchKey(newField.getName()) != null) {

					Obj methodIn = methods.searchKey(newField.getName());

					if (!SymTab.isLower(newField.getType(), methodIn.getType())) {
						report_error("Invalid overriding of " + methodIn.getName() + " method. Return types differ",
								null);
					} else if (newField.getLevel() != methodIn.getLevel()) {
						report_error("Invalid overriding of " + methodIn.getName()
								+ " method. Number of formal parameters differ", null);
					} else {

						int cnt = methodIn.getLevel();
						int i = 0;
						Iterator<Obj> oIter = newField.getLocalSymbols().iterator();
						boolean test = true;
						for (Obj o : methodIn.getLocalSymbols()) {

							if (i == cnt)
								break;
							Obj newObj = oIter.next();
							Struct newStruct = newObj.getType();

							if (!SymTab.isLower(newStruct, o.getType())) {
								report_error("Invalid overriding of " + methodIn.getName()
										+ " method. Formal params at " + i + " position are not compatible", null);
								test = false;
								break;
							}
							i++;
						}
						if (test) {

							if (Math.abs(newField.getFpPos()) > Math.abs(methodIn.getFpPos())) {
								report_error("Invalid overriding of " + methodIn.getName()
										+ " method. Cannot restrict access modifier.", null);
							}

							methods.deleteKey(newField.getName());
							methods.insertKey(newField);

						}
					}
				} else {
					methods.insertKey(newField);

				}
			}

		}
		Obj vftp = new Obj(Obj.Fld,"$vftp$",SymTab.intType);
		union.insertKey(vftp);
				
		
		
		for (Obj o : fields.symbols()) {
			union.insertKey(o);
		}
		
		int cnt = 0;
		
		for(Obj o : union.symbols())
			o.setAdr(cnt++);
		
		for (Obj o : methods.symbols()) {
			union.insertKey(o);
		}

		newType.setMembers(union);

		// Not abstract
		checkAbstractionIntegrity(node);

		SymTab.closeScope();
		currClass = null;
	}

	private void checkAbstractionIntegrity(ClassDecl node) {

		if (node.obj.getLevel() != -1) {

			Obj obj = node.obj;

			for (Obj local : obj.getType().getMembers()) {

				if (local.getKind() == Obj.Meth && local.getFpPos() < 0) {
					report_error("Non abstract class containg abstract method " + local.getName(), node);
				}
			}
		}

	}

	public void visit(AbstractMethodDeclA node) {
		if (SymTab.currentScope.findSymbol(node.getName()) != null) {
			report_error("Symbol " + node.getName() + " already declared in current scope", node);
		} else {

			((AbstractMethodDeclValid) node.getParent()).obj = node.obj = SymTab.insert(Obj.Meth, node.getName(),
					node.getMethodReturn() instanceof MethodReturnSome
							? ((MethodReturnSome) node.getMethodReturn()).getType().struct
							: SymTab.noType);
			SymTab.openScope();
			currentMeth = node.obj;

			int access = node.getAccessModifi() instanceof Public ? 1
					: node.getAccessModifi() instanceof Protected ? 2 : 3;

			node.obj.setFpPos(-access);

			SyntaxNode iter = node;

			while (!(iter instanceof AbstractClassDecl))
				iter = iter.getParent();

			SymTab.insert(Obj.Var, "this", ((AbstractClassDecl) iter).obj.getType());

		}

	}

	public void visit(AbstractMethodDeclB node) {
		if (SymTab.currentScope.findSymbol(node.getName()) != null) {
			report_error("Symbol " + node.getName() + " already declared in current scope", node);
		} else {

			((AbstractMethodDeclValid) node.getParent()).obj = node.obj = SymTab.insert(Obj.Meth, node.getName(),
					node.getMethodReturn() instanceof MethodReturnSome
							? ((MethodReturnSome) node.getMethodReturn()).getType().struct
							: SymTab.noType);
			SymTab.openScope();
			currentMeth = node.obj;

			int access = node.getAccessModifi() instanceof Public ? 1
					: node.getAccessModifi() instanceof Protected ? 2 : 3;

			node.obj.setFpPos(-access);
			SyntaxNode iter = node;

			while (!(iter instanceof AbstractClassDecl))
				iter = iter.getParent();

			SymTab.insert(Obj.Var, "this", ((AbstractClassDecl) iter).obj.getType());

		}
	}

	public void visit(AbstractMethodDeclValid node) {
		SymTab.chainLocalSymbols(node.obj);
		SymTab.closeScope();
		currentMeth = null;
	}

	public void visit(Designator node) {

		Obj obj = designate(node);
		if (obj != null) {
			node.obj = obj;
		}
	}

	public void visit(FactorNum node) {
		node.struct = SymTab.intType;
	}

	public void visit(FactorChar node) {
		node.struct = SymTab.charType;
	}

	public void visit(FactorBool node) {
		node.struct = SymTab.boolType;
	}

	public void visit(FactorNew node) {
		node.struct = node.getType().struct;
		if (node.getExprBrackOpt() instanceof ExprBrackOptSome) {
			node.struct = SymTab.getArrType(node.struct);
			Expr expr = ((ExprBrackOptSome) node.getExprBrackOpt()).getExpr();
			if (expr.struct.getKind() != Struct.Int)
				report_error("Expression is not a valid number", node);
		} else {
			if (node.struct == SymTab.boolType || node.struct == SymTab.intType || node.struct == SymTab.charType)
				report_error("Cannot combine new with non ref. types", node);
			if (SymTab.abstractClasses.contains(node.struct)) {
				report_error("Cannot instantiate abstract class", node);
			}
		}
	}

	public void visit(FactorComplex node) {

		Obj obj = designate(node.getDesignator());

		if (obj == null)
			return;

		ActParsParenOpt pars = node.getActParsParenOpt();

		if (pars instanceof ActParsParenOptSome && (obj == null || obj.getKind() != Obj.Meth))
			report_error("Cannot invoke non method object", node);
		else
			node.struct = obj.getType();

	}

	public void visit(FactorParen node) {
		node.struct = node.getExpr().struct;
	}

	public void visit(Term node) {
		node.struct = node.getFactor().struct;
		if (node.getFactorChain() instanceof FactorChainSome) {
			if (node.struct.getKind() != Struct.Int) {
				report_error("Invalid factor chaining in a term, factor must be an Int type", node);
				return;
			}

			Factor factor = node.getFactor();

			FactorChain iter = node.getFactorChain();

			while (!(iter instanceof FactorChainNone)) {

				FactorChainEl el = ((FactorChainSome) iter).getFactorChainEl();
				Mulop mulop = el.getMulop();
				Factor factorNext = el.getFactor();

				if (factorNext.struct == null)
					return;

				if (factorNext.struct.getKind() != Struct.Int) {
					report_error("Invalid factor chaining in a term, factor musta be an Int type", node);
					return;
				}

				if (mulop instanceof MulopR) {
					if (!isFactorLvalue(factor)) {
						report_error("Invalid factor chaining in a term, factor must be an lvalue ref if MULOPR used",
								node);
						return;
					}
				}

				factor = factorNext;

				iter = ((FactorChainSome) iter).getFactorChain();
			}

		}
	}

	public void visit(Expr node) {

		node.struct = node.getTerm().struct;

		MinusOpt optMin = node.getMinusOpt();
		Term term = node.getTerm();
		TermChain chain = node.getTermChain();

		if ((optMin instanceof MinusOptSome || chain instanceof TermChainSome) && term.struct.getKind() != Struct.Int) {
			report_error("Term must be an Int type", node);
			return;
		}

		TermChain iter = chain;

		while (!(iter instanceof TermChainNone)) {

			TermChainEl el = ((TermChainSome) iter).getTermChainEl();
			Addop addop = el.getAddop();
			Term termNext = el.getTerm();

			if (termNext.struct == null)
				return;

			if (termNext.struct.getKind() != Struct.Int) {
				report_error("Invalid term chaining in an expression, term must be an Int type", node);
				return;
			}

			if (!termNext.struct.compatibleWith(term.struct)) {
				report_error("Invalid term chaining in an expression, terms must be compatible", node);
				return;
			}

			if (addop instanceof AddopR) {
				if (!isTermLvalue(term)) {
					report_error("Invalid factor chaining in a term, factor must be an lvalue ref if MULOPR used",
							node);
					return;
				}
			}

			term = termNext;

			iter = ((TermChainSome) iter).getTermChain();
		}

	}

	private boolean isExprLvalue(Expr expr) {

		if (expr.getMinusOpt() instanceof MinusOptSome)
			return false;
		boolean test = isTermLvalue(expr.getTerm());
		if (expr.getTermChain() instanceof TermChainNone)
			return test;
		else {
			Addop addop = ((TermChainSome) expr.getTermChain()).getTermChainEl().getAddop();
			if (addop instanceof AddopR)
				return test;
			else
				return false;
		}

	}

	private boolean isTermLvalue(Term term) {

		boolean test = isFactorLvalue(term.getFactor());
		if (term.getFactorChain() instanceof FactorChainNone)
			return test;
		else {
			Mulop mulop = ((FactorChainSome) term.getFactorChain()).getFactorChainEl().getMulop();
			if (mulop instanceof MulopR)
				return test;
			else
				return false;
		}

	}

	private boolean isFactorLvalue(Factor node) {

		if (!(node instanceof FactorComplex))
			return false;

		Obj obj = designate(((FactorComplex) node).getDesignator());

		if (obj == null)
			return false;

		return isObjLvalue(obj);
	}

	Obj designate(Designator node) {

		if (node.obj != null)
			return node.obj;

		Obj obj = SymTab.find(node.getDesigStart().getIdent());
		Struct type = obj.getType();

		if (obj == SymTab.noObj) {

			if (currClass != null) {
				obj = currClass.getMembersTable().searchKey(node.getDesigStart().getIdent());
				if (obj == null) {
					report_error("Symbol " + node.getDesigStart().getIdent() + " not found in current scope", node);
					return null;
				}
				type = obj.getType();

			} else {
				report_error("Symbol " + node.getDesigStart().getIdent() + " not found in current scope", node);
				return null;
			}

		}

		if (obj.getKind() == Obj.Fld)
			detection("Class field access", obj, node);

		if (obj.getKind() == Obj.Con)
			detection("Symbolic constant usage", obj, node);

		if (obj.getKind() == Obj.Var && obj.getLevel() == 0)
			detection("Static variable usage", obj, node);

		if (obj.getKind() == Obj.Var && currentMeth != null && currentMeth.getLocalSymbols().contains(obj)
				&& obj.getAdr() < currentMeth.getLevel())
			detection("Usage of formal parameter", obj, node);

		int access = Math.abs(obj.getFpPos());

		if (access != STATIC && access != PUBLIC) {
			if (currClass == null)
				report_error("Cannot access object " + obj.getName() + ", acess denied. FATAL.", node);
			else if (!SymTab.accessRules.get(currClass).contains(obj)) {
				report_error("Access denied, object " + obj.getName() + " not accessible", node);
				printAccess(currClass);
				System.out.println(SymTab.ObjToStr(obj));

			}

		}

		node.getDesigStart().obj = obj;

		SyntaxNode iter = node.getDesignatorChoiceList();

		while (!(iter instanceof DesignatorChoiceListNone)) {

			SyntaxNode tmp = ((DesignatorChoiceListSome) iter).getDesignatorChoice();

			if (tmp instanceof DesignatorChoiceSingle) {

				DesignatorChoiceSingle ref = (DesignatorChoiceSingle) tmp;

				if (type.getKind() != Struct.Class) {
					report_error("Cannot derefference by " + ref.getDesigName() + "something that is not an object",
							node);
					return null;
				}
				if (type.getMembersTable().searchKey(ref.getDesigName()) == null && (currClass == type
						&& SymTab.currentScope.getOuter().getLocals().searchKey(ref.getDesigName()) == null)) {
					report_error("Object does not have a field/method ", node);
					return null;
				}
				obj = type.getMembersTable().searchKey(ref.getDesigName());

				if (obj == null)
					obj = SymTab.currentScope.getOuter().getLocals().searchKey(ref.getDesigName());

				ref.obj = obj;

				type = obj.getType();

				detection("Class field access", obj, node);

				access = Math.abs(obj.getFpPos());

				if (access != STATIC && access != PUBLIC) {
					if (currClass == null)
						report_error("Cannot access object " + obj.getName() + ", acess denied.", node);
					else if (!SymTab.accessRules.get(currClass).contains(obj))
						report_error("Access denied, object " + obj.getName() + " not accessible", node);

				}

			} else {
				DesignatorChoiceArr ref = (DesignatorChoiceArr) tmp;
				if (type.getKind() != Struct.Array) {
					report_error(obj.getName() + "is not an arry, cannot be indexed", node);
					return null;
				}
				if(ref.getExpr().struct == null)
					return null;
				if (ref.getExpr().struct.getKind() != Struct.Int) {
					report_error("Expression is not an int expression, cannot be index of " + obj.getName(), node);
					return null;
				}

				detection("Array element access ", obj, node);

				type = type.getElemType();
				obj = new Obj(Obj.Elem, "ELEM_" + SymTab.findTypeName(type), type);
				ref.obj = obj;

			}

			iter = ((DesignatorChoiceListSome) iter).getDesignatorChoiceList();
		}

		node.obj = obj;
		return obj;

	}

	public boolean isObjLvalue(Obj obj) {
		if (obj == null)
			return false;
		return obj.getKind() == Obj.Elem || obj.getKind() == Obj.Var || obj.getKind() == Obj.Fld;

	}

	public void visit(DesignatorElemAssign node) {
		Obj obj = designate(((DesignatorStatement) node.getParent()).getDesignator());
		if (obj == null)
			return;
		if (!isObjLvalue(obj)) {
			report_error("Bad assign, designator" + obj.getName() + " is not an Lvalue", node);
			return;
		}
		if (node.getExpr().struct == null)
			return;

		if (!SymTab.assignableTo(node.getExpr().struct, obj.getType())) {
			report_error("Bad assign, expression cannot be assigned to given designator " + obj.getName(), node);
		}
	}

	public void visit(DesignatorElemPlus node) {
		Obj obj = designate(((DesignatorStatement) node.getParent()).getDesignator());
		if (obj == null)
			return;
		if (!isObjLvalue(obj)) {
			report_error("Bad increment, designator " + obj.getName() + " is not an Lvalue", node);
			return;
		}
		if (obj.getType().getKind() != Struct.Int)
			report_error("Bad increment, designator " + obj.getName() + " is not an Int type", node);
	}

	public void visit(DesignatorElemMinus node) {
		Obj obj = designate(((DesignatorStatement) node.getParent()).getDesignator());
		if (obj == null)
			return;
		if (!isObjLvalue(obj)) {
			report_error("Bad decrement, designator " + obj.getName() + " is not an Lvalue", node);
			return;
		}
		if (obj.getType().getKind() != Struct.Int)
			report_error("Bad decrement, designator " + obj.getName() + " is not an Int type", node);
	}

	public void visit(DesignatorElemActPars node) {
		Obj obj = designate(((DesignatorStatement) node.getParent()).getDesignator());
		if (obj == null)
			return;
		if (obj == null || obj.getKind() != Obj.Meth) {
			report_error("Cannot invoke something thats is not an method object " + obj.getName(), node);
			return;
		}

	}

	public void visit(BreakStatement node) {
		SyntaxNode iter = node;
		while (iter != null && !(iter instanceof UnmatchedFor) && !(iter instanceof MatchedFor)
				&& !(iter instanceof MatchedForeach) && !(iter instanceof UnmatchedForeach))
			iter = iter.getParent();

		if (iter == null) {
			report_error("Unexpected break statement", node);
		}

	}

	public void visit(ContinueStatement node) {
		SyntaxNode iter = node;
		while (iter != null && !(iter instanceof UnmatchedFor) && !(iter instanceof MatchedFor)
				&& !(iter instanceof MatchedForeach) && !(iter instanceof UnmatchedForeach))
			iter = iter.getParent();

		if (iter == null) {
			report_error("Unexpected continue statement", node);
		}

	}

	public void visit(ReadStatement node) {
		Obj obj = designate(node.getDesignator());
		if (obj == null)
			return;
		if (!isObjLvalue(obj) || obj.getKind() == Obj.Con) {
			report_error("Bad read, designator is not an Lvalue " + obj.getName(), node);
			return;
		}
		if (obj.getType().getKind() != Struct.Int && obj.getType().getKind() != Struct.Bool
				&& obj.getType().getKind() != Struct.Char)
			report_error("Bad read, designator is not a primtive type " + obj.getName(), node);
	}

	public void visit(PrintStatement node) {

		Struct type = node.getExpr().struct;
		if (type == null)
			return;
		if (type.getKind() != Struct.Int && type.getKind() != Struct.Bool && type.getKind() != Struct.Char)
			report_error("Bad print, expression is not a primtive type", node);

	}

	public void visit(ReturnStatement node) {
		if (currentMeth == null) {
			report_error("Unexpected return statement encountered", node.getParent());
			return;
		}
		if (node.getExprOpt() instanceof ExprOptNone) {
			if (currentMeth.getType() != SymTab.noType)
				report_error("Return statement must return declared type", node.getParent());
		} else {
			Struct type = ((ExprOptSome) node.getExprOpt()).getExpr().struct;
			if (!SymTab.isLower(type, currentMeth.getType())) {
				report_error("Return statement must return declared type", node.getParent());
			}
			;
			// Can return interface
		}
	}

	public void visit(CondFactMulti node) {
		node.struct = SymTab.boolType;
		if (!node.getExpr().struct.compatibleWith(node.getExpr1().struct))
			report_error("Invalid condition fact, expressions not compatible", node);
		if (node.getExpr().struct.getKind() == Struct.Array || node.getExpr1().struct.getKind() == Struct.Array
				|| node.getExpr().struct.getKind() == Struct.Class
				|| node.getExpr1().struct.getKind() == Struct.Class) {
			if (!(node.getRelop() instanceof Eql || node.getRelop() instanceof Neq))
				report_error("Array/Object type can only be compared by == or !=", node);
		}
	}

	public void visit(CondFactSingle node) {
		node.struct = SymTab.boolType;
		if (node.getExpr().struct.getKind() != Struct.Bool)
			report_error("Invalid condition fact, must contain bools", null);
	}

	public void visit(CondTermMulti node) {
		node.struct = SymTab.boolType;
	}

	public void visit(CondTermSingle node) {
		node.struct = SymTab.boolType;
	}

	public void visit(ConditionMulti node) {
		node.struct = SymTab.boolType;
	}

	public void visit(ConditionSingle node) {
		node.struct = SymTab.boolType;
	}

	public void visit(FunInvoke node) {

		Designator designator = null;
		SyntaxNode iter = node;

		while (iter != null && !(iter instanceof DesignatorStatement) && !(iter instanceof FactorComplex))
			iter = iter.getParent();

		if (iter == null) {
			report_error("Invalid function invoke", node.getParent());
			return;
		}

		designator = iter instanceof DesignatorStatement ? ((DesignatorStatement) iter).getDesignator()
				: ((FactorComplex) iter).getDesignator();

		Obj obj = designate(designator);
		if (obj == null || obj.getKind() != Obj.Meth) {
			report_error("Invalid function invoke", node.getParent());
			return;
		}

		node.obj = obj;

		if (node.obj.getFpPos() == 0) {
			detection("Static function call", node.obj, node.getParent());
		} else
			detection("Method call", node.obj, node.getParent());

		int formalPars = node.obj.getLevel();
		int actualPars = 0;

		Iterator<Obj> fI = node.obj.getLocalSymbols().iterator();

		// If is a class method (has an access modifi) THIS
		if (obj.getFpPos() != 0) {
			fI.next();
			actualPars++;
		}
		if (node.getActParsOpt() instanceof ActParsOptSome) {

			ActPars aI = ((ActParsOptSome) node.getActParsOpt()).getActPars();

			while (!(aI instanceof ActParsSingle)) {

				if (!fI.hasNext()) {
					report_error("More actual parameters found than expected", node);
					return;
				}

				Obj currFormal = fI.next();
				Expr expr = ((ActParsMulti) aI).getExpr();

				if (!SymTab.assignableTo(expr.struct, currFormal.getType())) {
					report_error("Formal and actual parameter are not compatible at position " + actualPars,
							node.getParent());
					return;
				}

				aI = ((ActParsMulti) aI).getActPars();

				actualPars++;

			}

			if (!fI.hasNext()) {
				report_error("More actual parameters found than expected", node.getParent());
				return;
			}

			Obj currFormal = (Obj) fI.next();
			Expr expr = ((ActParsSingle) aI).getExpr();

			if (!SymTab.assignableTo(expr.struct, currFormal.getType())) {
				report_error("Formal and actual parameter are not compatible at position " + actualPars,
						node.getParent());
				return;
			}

			actualPars++;

		}
		if (actualPars != formalPars) {
			report_error("Formal and actual parameters differ in size", node.getParent());
			return;
		}

	}

	public void visit(MatchedForeach node) {
		Obj obj = SymTab.find(node.getForeachIter().getIter());
		if (obj == SymTab.noObj) {
			report_error("Iterator symbol not declared", node);
			return;
		}
		node.getForeachIter().obj = obj;
		if (node.getDesignator().obj.getType().getKind() != Struct.Array) {
			report_error("Cannot iterate over non arry object", node);
			return;
		}
		if (!SymTab.isLower(node.getDesignator().obj.getType(), obj.getType())) {
			report_error("Iterator and array types are not compatible", node);
			return;
		}

	}

	public void visit(UnmatchedForeach node) {
		Obj obj = SymTab.find(node.getForeachIter().getIter());
		if (obj == SymTab.noObj) {
			report_error("Iterator symbol not declared", node);
			return;
		}
		node.getForeachIter().obj = obj;
		if (node.getDesignator().obj.getType().getKind() != Struct.Array) {
			report_error("Cannot iterate over non arry object", node);
			return;
		}
		if (!SymTab.isLower(node.getDesignator().obj.getType(), obj.getType())) {
			report_error("Iterator and array types are not compatible", node);
			return;
		}
	}

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" at line ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" at line ").append(line);
		log.info(msg.toString());
	}

	private void detection(String type, Obj obj, SyntaxNode node) {
		if(detect)
			report_info(type + " detected - " + SymTab.ObjToStr(obj), node);
	}

	boolean errorDetected;
	org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(getClass());
	public int nVars;

}
