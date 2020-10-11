package rs.ac.bg.etf.pp1;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

import java.util.Set;

import javax.sql.rowset.spi.TransactionalWriter;

public class CodeGenerator extends VisitorAdaptor {

	private Obj currClass;

	private int mainPc;

	private LinkedList<Byte> MethodTable = new LinkedList<>();

	private LinkedList<LinkedList<Integer>> toPatchExit = new LinkedList<>();
	private LinkedList<LinkedList<Integer>> toPatchIf = new LinkedList<>();
	private LinkedList<LinkedList<Integer>> toPatchElse = new LinkedList<>();

	private LinkedList<ForControl> forControlStack = new LinkedList<>();
	private LinkedList<ForeachControl> foreachControlStack = new LinkedList<>();

	private LinkedList<String> resolvedNames = new LinkedList<>();

	private static class ForControl {

		Integer condPc;
		Integer postPc;
		Integer exitPc;
		Integer enterPc;

		LinkedList<Integer> toPatchExit = new LinkedList<>();
		LinkedList<Integer> toPatchEnter = new LinkedList<>();

	}

	private static class ForeachControl {

		Integer cyclePc;
		Integer incrementPc;

		LinkedList<Integer> toPatchExit = new LinkedList<>();
		LinkedList<Integer> toPatchEnter = new LinkedList<>();

	}

	private Set<Obj> cantStore = new HashSet<>();

	private void storeProxy(Obj obj) {

		if (cantStore.contains(obj)) {
			Code.error("Cannot store in current iterator");
		} else
			Code.store(obj);

	}

	public CodeGenerator() {

		Obj obj = SymTab.lenObj;
		obj.setAdr(Code.pc);

		Code.put(Code.enter);
		Code.put(obj.getLevel());
		Code.put(obj.getLocalSymbols().size());

		Code.put(Code.load_n);
		Code.put(Code.arraylength);
		Code.put(Code.exit);
		Code.put(Code.return_);

		obj = SymTab.chrObj;
		obj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(obj.getLevel());
		Code.put(obj.getLocalSymbols().size());

		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);

		obj = SymTab.ordObj;
		obj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(obj.getLevel());
		Code.put(obj.getLocalSymbols().size());
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);

	}

	public int getMainPc() {
		return mainPc;
	}

	public void visit(PrintStatement node) {

		switch (node.getExpr().struct.getKind()) {

		case Struct.Int:
		case Struct.Bool:
			Code.loadConst(5);
			Code.put(Code.print);
			break;
		case Struct.Char:
			Code.loadConst(1);
			Code.put(Code.bprint);
			break;
		}

	}

	public void visit(ReadStatement node) {
		if (node.getDesignator().obj.getType().getKind() == Struct.Char)
			Code.put(Code.bread);
		else
			Code.put(Code.read);
		storeProxy(node.getDesignator().obj);

	}

	public void visit(MethodName node) {

		if (node.obj.getName().equals("main")) {
			mainPc = Code.pc;
			tranasctStatic();
		}

		node.obj.setAdr(Code.pc);

		if (node.obj.getLevel() != 0) {
			addFunctionEntry(node.obj.getName(), node.obj.getAdr());
			resolvedNames.add(node.obj.getName());
		}

		Code.put(Code.enter);
		Code.put(node.obj.getLevel());
		Code.put(node.obj.getLocalSymbols().size());
	}

	public void visit(MethodDecl node) {

		Code.put(Code.exit);
		Code.put(Code.return_);

	}

	public void visit(FunInvoke node) {

		SyntaxNode iter = node;
		while (!(iter instanceof FactorComplex) && !(iter instanceof DesignatorStatement))
			iter = iter.getParent();

		Designator desig = iter instanceof FactorComplex ? ((FactorComplex) iter).getDesignator()
				: ((DesignatorStatement) iter).getDesignator();
		Obj funObj = desig.obj;

		if (funObj.getFpPos() == 0) {
			int offset = funObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		} else {

			Code.put(Code.getstatic);
			Code.put2(2);
			Code.put(Code.getfield);
			Code.put2(0);

			Code.put(Code.invokevirtual);
			String funName = funObj.getName();
			for (Byte b : funName.getBytes())
				Code.put4(b);
			Code.put4(-1);
		}

		if (iter instanceof DesignatorStatement && funObj.getType() != SymTab.noType) {
			Code.put(Code.pop);
		}

	}

	public void visit(FactorNum node) {
		Obj newObj = new Obj(Obj.Con, "$", SymTab.intType);
		newObj.setAdr(node.getVal());
		Code.load(newObj);
	}

	public void visit(FactorChar node) {

		Obj newObj = new Obj(Obj.Con, "$", SymTab.charType);
		newObj.setAdr(node.getVal());
		Code.load(newObj);
	}

	public void visit(FactorBool node) {
		Obj newObj = new Obj(Obj.Con, "$", SymTab.boolType);
		newObj.setAdr(node.getVal() ? 1 : 0);
		Code.load(newObj);
	}

	public void visit(FactorNew node) {
		// Array
		if (node.getExprBrackOpt() instanceof ExprBrackOptSome) {
			// ExprDone
			Code.put(Code.newarray);
			Code.put(node.struct.getKind() == Struct.Char ? 0 : 1);
		}
		// Obj
		else {
			Code.put(Code.new_);
			Code.put2(node.struct.getNumberOfFields() * 4);
			Code.put(Code.dup);
			Code.loadConst(SymTab.structToObj.get(node.struct).getAdr());
			Code.put(Code.putfield);
			Code.put2(0);
		}
	}

	public void visit(DesignatorElemAssign node) {

		Designator desig = ((DesignatorStatement) node.getParent()).getDesignator();
		if (isCombinedOpr(node)) {

			switch (typeOfDesignator(desig)) {
			case 0:
				Code.load(desig.obj);
				resolveCombinedOpr(node);
				break;
			case 1:
				Code.put(Code.dup_x1);
				Code.put(Code.pop);

				Code.put(Code.dup);
				Code.put(Code.putstatic);
				Code.put2(0);
				Code.load(desig.obj);
				resolveCombinedOpr(node);

				Code.put(Code.getstatic);
				Code.put2(0);
				Code.put(Code.dup_x1);
				Code.put(Code.pop);

				break;
			case 2:
				Code.put(Code.dup_x2);
				Code.put(Code.pop);

				Code.put(Code.dup2);
				Code.put(Code.putstatic);
				Code.put2(0);
				Code.put(Code.putstatic);
				Code.put2(1);
				Code.load(desig.obj);
				resolveCombinedOpr(node);

				Code.put(Code.getstatic);
				Code.put2(1);
				Code.put(Code.getstatic);
				Code.put2(0);

				Code.put(Code.dup_x2);
				Code.put(Code.pop);
				Code.put(Code.dup_x2);
				Code.put(Code.pop);

				break;
			}

		}
		storeProxy(desig.obj);
	}

	// Designator statement

	public void visit(DesigStart node) {

		boolean notChained = ((Designator) node.getParent())
				.getDesignatorChoiceList() instanceof DesignatorChoiceListNone;

		// Rvalue designator
		if (!isLeftDesignator(node)) {

			switch (node.obj.getKind()) {
			case Obj.Con:
				Code.load(node.obj);
				break;
			case Obj.Var:
				Code.load(node.obj);
				break;
			case Obj.Fld:
				Code.put(Code.load); // this
				Code.put(0);
				Code.put(Code.getfield);
				Code.put2(node.obj.getAdr());
				break;
			case Obj.Meth:
				if (node.obj.getFpPos() != 0) {
					Code.put(Code.load);
					Code.put(0);

					Code.put(Code.dup);
					Code.put(Code.putstatic);
					Code.put2(2);

				}
				break;

			}
		}
		// Lvalue
		else {
			switch (node.obj.getKind()) {
			case Obj.Var:
				if (node.obj.getType().isRefType() && !notChained) {
					Code.load(node.obj); // Dereferencing
				}

				break;
			case Obj.Fld:
				Code.put(Code.load); // this
				Code.put(0);
				break;
			}

		}

	}

	public void visit(DesignatorChoiceSingle node) {

		if (node.obj.getKind() == Obj.Meth && node.obj.getFpPos() != 0) {

			Code.put(Code.dup);
			Code.put(Code.putstatic);
			Code.put2(2);

			return;
		} else if (!isLeftDesignator(node))
			Code.load(node.obj);
		// Lvalue designator
		else if (!isLastDesigRef(node)) // Getting the adr
			Code.load(node.obj);

	}

	public void visit(DesignatorChoiceArr node) {
		if (!isLeftDesignator(node))
			Code.load(node.obj);
		else if (!isLastDesigRef(node)) // Getting the adr
			Code.load(node.obj);

	}

	public void visit(DesignatorElemPlus node) {

		Designator desig = ((DesignatorStatement) node.getParent()).getDesignator();
		switch (typeOfDesignator(desig)) {
		case 0:
			Code.load(desig.obj);
			Code.loadConst(1);
			Code.put(Code.add);
			storeProxy(desig.obj);
			break;
		case 1:
			Code.put(Code.dup);
			Code.load(desig.obj);
			Code.loadConst(1);
			Code.put(Code.add);
			storeProxy(desig.obj);
			break;
		case 2:
			Code.put(Code.dup2);
			Code.load(desig.obj);
			Code.loadConst(1);
			Code.put(Code.add);
			storeProxy(desig.obj);
			break;
		}

	}

	public void visit(DesignatorElemMinus node) {
		Designator desig = ((DesignatorStatement) node.getParent()).getDesignator();
		switch (typeOfDesignator(desig)) {
		case 0:
			Code.load(desig.obj);
			Code.loadConst(1);
			Code.put(Code.sub);
			storeProxy(desig.obj);
			break;
		case 1:
			Code.put(Code.dup);
			Code.load(desig.obj);
			Code.put(Code.dup_x1);
			Code.loadConst(1);
			Code.put(Code.sub);
			storeProxy(desig.obj);
			break;
		case 2:
			Code.put(Code.dup2);
			Code.load(desig.obj);
			Code.put(Code.dup_x2);
			Code.loadConst(1);
			Code.put(Code.sub);
			storeProxy(desig.obj);
			break;
		}
	}

	// Exprs

	public void visit(FactorChainEl node) {

		boolean isLast = ((FactorChainSome) node.getParent()).getFactorChain() instanceof FactorChainNone;

		if (isLast) {

			boolean iterating = true;
			FactorChainEl currEl = node;

			while (iterating) {

				if (currEl.getMulop() instanceof MulopL) {

					MulopL mulopl = (MulopL) currEl.getMulop();
					if (mulopl.getMulopLeft() instanceof Mul)
						Code.put(Code.mul);
					else if (mulopl.getMulopLeft() instanceof Div)
						Code.put(Code.div);
					else
						Code.put(Code.rem);

				} else {

					MulopR mulopr = (MulopR) currEl.getMulop();
					Designator desig = null;
					FactorChainSome fcs = (FactorChainSome) currEl.getParent();

					if (currEl.getParent().getParent() instanceof FactorChainSome) {

						fcs = (FactorChainSome) currEl.getParent().getParent();
						FactorChainEl fce = fcs.getFactorChainEl();
						desig = ((FactorComplex) fce.getFactor()).getDesignator();
					} else {
						Term term = (Term) currEl.getParent().getParent();
						desig = ((FactorComplex) term.getFactor()).getDesignator();
					}

					switch (typeOfDesignator(desig)) {

					case 0:

						Code.load(desig.obj);
						Code.put(Code.dup_x1);
						Code.put(Code.pop);

						if (mulopr.getMulopRight() instanceof Mleq)
							Code.put(Code.mul);
						else if (mulopr.getMulopRight() instanceof Dveq)
							Code.put(Code.div);
						else
							Code.put(Code.rem);

						Code.put(Code.dup);

						storeProxy(desig.obj);

						break;
					case 1:

						Code.put(Code.dup_x1);
						Code.put(Code.pop);
						Code.load(desig.obj);

						if (mulopr.getMulopRight() instanceof Mleq)
							Code.put(Code.mul);
						else if (mulopr.getMulopRight() instanceof Dveq)
							Code.put(Code.div);
						else
							Code.put(Code.rem);

						Code.put(Code.dup_x1);
						storeProxy(desig.obj);

						break;
					case 2:
						Code.put(Code.dup_x2);
						Code.put(Code.pop);
						Code.load(desig.obj);

						if (mulopr.getMulopRight() instanceof Mleq)
							Code.put(Code.mul);
						else if (mulopr.getMulopRight() instanceof Dveq)
							Code.put(Code.div);
						else
							Code.put(Code.rem);

						Code.put(Code.dup_x2);

						storeProxy(desig.obj);
						break;
					}

				}
				if (currEl.getParent().getParent() instanceof FactorChainSome) {

					FactorChainSome fcs = (FactorChainSome) currEl.getParent().getParent();
					currEl = fcs.getFactorChainEl();

				} else
					iterating = false;
			}

		}
	}

	public void visit(MulopR node) {

		Designator desig = null;
		FactorChainSome fcs = (FactorChainSome) node.getParent().getParent();

		if (fcs.getParent() instanceof FactorChainSome) {

			fcs = (FactorChainSome) fcs.getParent();
			FactorChainEl fce = fcs.getFactorChainEl();
			desig = ((FactorComplex) fce.getFactor()).getDesignator();
		} else {
			Term term = (Term) fcs.getParent();
			desig = ((FactorComplex) term.getFactor()).getDesignator();
		}

		switch (typeOfDesignator(desig)) {
		case 0:
			break;
		case 1:
			Code.put(Code.dup);
			break;
		case 2:
			Code.put(Code.dup2);
			break;
		}

	}

	public void visit(TermChainEl node) {

		boolean isLast = ((TermChainSome) node.getParent()).getTermChain() instanceof TermChainNone;

		if (isLast) {
			boolean iterating = true;
			TermChainEl currEl = node;

			while (iterating) {

				if (currEl.getAddop() instanceof AddopL) {

					AddopL addopl = (AddopL) currEl.getAddop();
					if (addopl.getAddopLeft() instanceof AddopLeftPlus)
						Code.put(Code.add);
					else
						Code.put(Code.sub);

				} else {

					AddopR addopr = (AddopR) currEl.getAddop();
					Designator desig = null;
					TermChainSome tcs = (TermChainSome) currEl.getParent();

					if (currEl.getParent().getParent() instanceof TermChainSome) {

						tcs = (TermChainSome) currEl.getParent().getParent();
						TermChainEl tce = tcs.getTermChainEl();
						desig = ((FactorComplex) tce.getTerm().getFactor()).getDesignator();
					} else {
						Expr expr = (Expr) currEl.getParent().getParent();
						desig = ((FactorComplex) expr.getTerm().getFactor()).getDesignator();
					}

					switch (typeOfDesignator(desig)) {

					case 0:

						Code.load(desig.obj);
						Code.put(Code.dup_x1);
						Code.put(Code.pop);

						if (addopr.getAddopRight() instanceof AddopRightPlus)
							Code.put(Code.add);
						else
							Code.put(Code.sub);

						Code.put(Code.dup);
						storeProxy(desig.obj);

						break;
					case 1:

						Code.put(Code.dup_x1);
						Code.put(Code.pop);
						Code.load(desig.obj);

						if (addopr.getAddopRight() instanceof AddopRightPlus)
							Code.put(Code.add);
						else
							Code.put(Code.sub);

						Code.put(Code.dup);
						storeProxy(desig.obj);

						Code.put(Code.dup_x1);
						storeProxy(desig.obj);

						break;
					case 2:
						Code.put(Code.dup_x2);
						Code.put(Code.pop);
						Code.load(desig.obj);

						if (addopr.getAddopRight() instanceof AddopRightPlus)
							Code.put(Code.add);
						else
							Code.put(Code.sub);

						Code.put(Code.dup);
						storeProxy(desig.obj);

						Code.put(Code.dup_x2);

						storeProxy(desig.obj);
						break;
					}

				}

				if (currEl.getParent().getParent() instanceof TermChainSome) {

					TermChainSome tcs = (TermChainSome) currEl.getParent().getParent();
					currEl = tcs.getTermChainEl();

				} else {
					iterating = false;
				}

			}

		}
	}

	public void visit(AddopRight node) {

		Designator desig = null;
		TermChainSome tcs = (TermChainSome) node.getParent().getParent();

		if (tcs.getParent() instanceof FactorChainSome) {

			tcs = (TermChainSome) tcs.getParent();
			TermChainEl tce = tcs.getTermChainEl();
			desig = ((FactorComplex) tce.getTerm().getFactor()).getDesignator();
		} else {
			Expr expr = (Expr) tcs.getParent();
			desig = ((FactorComplex) expr.getTerm().getFactor()).getDesignator();
		}

		switch (typeOfDesignator(desig)) {
		case 0:

			break;
		case 1:
			Code.put(Code.dup);
			break;
		case 2:
			Code.put(Code.dup2);
			break;
		}

	}

	public void visit(Expr node) {

		if (node.getMinusOpt() instanceof MinusOptSome)
			Code.put(Code.neg);

	}

	// Conditions

	public void visit(CondFactSingle node) {

		Code.loadConst(0);
		if (isOrNext(node)) {

			int op = Code.ne;
			Code.putFalseJump(Code.inverse[op], 0);
			if (inFor(node))
				forControlStack.getLast().toPatchEnter.add(Code.pc - 2);
			else
				toPatchIf.getLast().add(Code.pc - 2);

		} else {
			int op = Code.ne;
			Code.putFalseJump(op, 0);
			if (inFor(node))
				forControlStack.getLast().toPatchExit.add(Code.pc - 2);
			else if (inElse(node))
				toPatchElse.getLast().add(Code.pc - 2);
			else
				toPatchExit.getLast().add(Code.pc - 2);

		}

	}

	public void visit(CondFactMulti node) {

		if (isOrNext(node)) {

			int op = rellopToInt(node.getRelop());
			Code.putFalseJump(Code.inverse[op], 0);

			if (inFor(node))
				forControlStack.getLast().toPatchEnter.add(Code.pc - 2);
			else
				toPatchIf.getLast().add(Code.pc - 2);

		} else {
			int op = rellopToInt(node.getRelop());
			Code.putFalseJump(op, 0);

			if (inFor(node))
				forControlStack.getLast().toPatchExit.add(Code.pc - 2);
			else if (inElse(node))
				toPatchElse.getLast().add(Code.pc - 2);
			else
				toPatchExit.getLast().add(Code.pc - 2);

		}
	}

	public void visit(IfCut node) {

		toPatchExit.add(new LinkedList<>());
		toPatchIf.add(new LinkedList<>());
		if (inElse(node))
			toPatchElse.add(new LinkedList<>());
	}

	public void visit(Else node) {

		Code.putJump(0);
		toPatchExit.getLast().add(Code.pc - 2);
		for (Integer toFix : toPatchElse.getLast()) {
			Code.fixup(toFix);
		}

	}

	public void visit(IfStatement node) {
		for (Integer toFix : toPatchExit.getLast()) {
			Code.fixup(toFix);
		}
		toPatchExit.removeLast();

	}

	public void visit(UnmatchedIf node) {

		for (Integer toFix : toPatchExit.getLast()) {
			Code.fixup(toFix);
		}
		toPatchExit.removeLast();

	}

	public void visit(UnmatchedIfElse node) {
		for (Integer toFix : toPatchExit.getLast()) {
			Code.fixup(toFix);
		}
		toPatchExit.removeLast();
	}

	public void visit(Rparen node) {
		for (Integer toFix : toPatchIf.getLast()) {
			Code.fixup(toFix);
		}
		toPatchIf.removeLast();
	}

	public void visit(ForInit node) {
		ForControl fc = new ForControl();
		fc.condPc = Code.pc;
		forControlStack.add(fc);
	}

	public void visit(ForCond node) {

		Code.putJump(0);
		forControlStack.getLast().postPc = Code.pc;
		forControlStack.getLast().toPatchEnter.add(Code.pc - 2);

	}

	public void visit(ForPost node) {
		Code.putJump(forControlStack.getLast().condPc);
		forControlStack.getLast().enterPc = Code.pc;
		for (Integer toFix : forControlStack.getLast().toPatchEnter) {
			Code.fixup(toFix);
		}

	}

	public void visit(MatchedFor node) {

		Code.putJump(forControlStack.getLast().postPc);
		forControlStack.getLast().exitPc = Code.pc;
		for (Integer toFix : forControlStack.getLast().toPatchExit) {
			Code.fixup(toFix);
		}
		forControlStack.removeLast();

	}

	public void visit(UnmatchedFor node) {
		Code.putJump(forControlStack.getLast().postPc);
		forControlStack.getLast().exitPc = Code.pc;
		for (Integer toFix : forControlStack.getLast().toPatchExit) {
			Code.fixup(toFix);
		}
		forControlStack.removeLast();
	}

	public void visit(ForeachRparen node) {

		// Start
		Code.loadConst(0);
		ForeachControl fc = new ForeachControl();
		fc.cyclePc = Code.pc;
		foreachControlStack.add(fc);

		// Cycle
		Code.put(Code.dup2);
		Code.put(Code.pop);
		Code.put(Code.arraylength);
		Code.put(Code.dup2);
		Code.put(Code.pop);

		Code.putFalseJump(Code.ne, 0);
		fc.toPatchExit.add(Code.pc - 2);

		Code.put(Code.dup2);

		Obj obj = node.getParent() instanceof MatchedForeach ? ((MatchedForeach) node.getParent()).getForeachIter().obj
				: ((UnmatchedForeach) node.getParent()).getForeachIter().obj;
		cantStore.add(obj);

		Code.put(Code.aload);
		Code.store(obj);

	}

	public void visit(MatchedForeach node) {

		for (Integer toFix : foreachControlStack.getLast().toPatchEnter) {
			Code.fixup(toFix);
		}

		Code.loadConst(1);
		Code.put(Code.add);

		Code.putJump(foreachControlStack.getLast().cyclePc);

		for (Integer toFix : foreachControlStack.getLast().toPatchExit) {
			Code.fixup(toFix);
		}
		foreachControlStack.removeLast();

		Code.put(Code.pop);
		Code.put(Code.pop);

		Obj obj = node.getForeachIter().obj;
		cantStore.remove(obj);
	}

	public void visit(UnmatchedForeach node) {

		for (Integer toFix : foreachControlStack.getLast().toPatchEnter) {
			Code.fixup(toFix);
		}

		Code.loadConst(1);
		Code.put(Code.add);

		Code.putJump(foreachControlStack.getLast().cyclePc);

		for (Integer toFix : foreachControlStack.getLast().toPatchExit) {
			Code.fixup(toFix);
		}
		foreachControlStack.removeLast();

		Code.put(Code.pop);
		Code.put(Code.pop);

		Obj obj = node.getForeachIter().obj;
		cantStore.remove(obj);
	}

	public void visit(BreakStatement node) {
		Code.putJump(0);

		SyntaxNode iter = node;

		while (!(iter instanceof MatchedForeach || iter instanceof UnmatchedForeach || iter instanceof MatchedFor
				|| iter instanceof UnmatchedFor))
			iter = iter.getParent();

		boolean inFor = iter instanceof MatchedFor || iter instanceof UnmatchedFor;

		if (inFor) {
			forControlStack.getLast().toPatchExit.add(Code.pc - 2);
		} else
			foreachControlStack.getLast().toPatchExit.add(Code.pc - 2);
	}

	public void visit(ContinueStatement node) {

		SyntaxNode iter = node;

		while (!(iter instanceof MatchedForeach || iter instanceof UnmatchedForeach || iter instanceof MatchedFor
				|| iter instanceof UnmatchedFor))
			iter = iter.getParent();

		boolean inFor = iter instanceof MatchedFor || iter instanceof UnmatchedFor;

		if (inFor)
			Code.putJump(forControlStack.getLast().postPc);
		else {
			Code.putJump(0);
			foreachControlStack.getLast().toPatchEnter.add(Code.pc - 2);
		}

	}

	// Virtual functions

	public void visit(ClassNamingValid node) {
		Obj obj = (node.getParent() instanceof ClassDecl) ? ((ClassDecl) node.getParent()).obj
				: ((AbstractClassDecl) node.getParent()).obj;
		currClass = obj;
		obj.setAdr(Code.dataSize);

	}

	public void visit(ClassDecl node) {
		Struct currClass = node.obj.getType();
		Struct superClass = node.obj.getType().getElemType();

		if (superClass != SymTab.nullType) {

			SymbolDataStructure currColl = currClass.getMembersTable();
			SymbolDataStructure superColl = superClass.getMembersTable();

			for (Obj o : superColl.symbols()) {

				if (o.getKind() == Obj.Meth && o.getFpPos() > 0 && !resolvedNames.contains(o.getName())) {
					addFunctionEntry(o.getName(), o.getAdr());
				}

			}

		}
		addTableTerminator();
		resolvedNames = new LinkedList<>();
		currClass = null;

	}

	public void visit(AbstractClassDecl node) {

		Struct currClass = node.obj.getType();
		Struct superClass = node.obj.getType().getElemType();

		if (superClass != SymTab.nullType) {

			SymbolDataStructure currColl = currClass.getMembersTable();
			SymbolDataStructure superColl = superClass.getMembersTable();

			for (Obj o : superColl.symbols()) {

				if (o.getKind() == Obj.Meth && o.getFpPos() > 0 && !resolvedNames.contains(o.getName())) {
					addFunctionEntry(o.getName(), o.getAdr());
				}

			}

		}
		addTableTerminator();
		resolvedNames = new LinkedList<>();
		currClass = null;

	}

	private void tranasctStatic() {
		while (!MethodTable.isEmpty()) {
			Code.put(MethodTable.removeFirst());
		}
	}

	private void addWordToStaticData(int value, int address) {
		MethodTable.add(new Byte((byte) Code.const_));
		MethodTable.add(new Byte((byte) ((value >> 16) >> 8)));
		MethodTable.add(new Byte((byte) (value >> 16)));
		MethodTable.add(new Byte((byte) (value >> 8)));
		MethodTable.add(new Byte((byte) value));
		MethodTable.add(new Byte((byte) Code.putstatic));
		MethodTable.add(new Byte((byte) (address >> 8)));
		MethodTable.add(new Byte((byte) address));
	}

	private void addNameTerminator() {
		addWordToStaticData(-1, Code.dataSize++);
	}

	private void addTableTerminator() {
		addWordToStaticData(-2, Code.dataSize++);
	}

	private void addFunctionAddress(int functionAddress) {
		addWordToStaticData(functionAddress, Code.dataSize++);
	}

	private void addFunctionEntry(String name, int functionAddressInCodeBuffer) {
		for (int j = 0; j < name.length(); j++) {
			addWordToStaticData((int) (name.charAt(j)), Code.dataSize++);
		}
		addNameTerminator();
		addFunctionAddress(functionAddressInCodeBuffer);
	}

	private boolean inFor(SyntaxNode node) {
		SyntaxNode iter = node;

		while (iter != null && !(iter instanceof UnmatchedFor || iter instanceof MatchedFor
				|| iter instanceof UnmatchedIfElse || iter instanceof IfStatement || iter instanceof UnmatchedIf))
			iter = iter.getParent();

		return iter instanceof UnmatchedFor || iter instanceof MatchedFor;
	}

	private boolean inElse(SyntaxNode node) {

		SyntaxNode iter = node;

		while (iter != null && !(iter instanceof UnmatchedFor || iter instanceof MatchedFor
				|| iter instanceof UnmatchedIfElse || iter instanceof IfStatement || iter instanceof UnmatchedIf))
			iter = iter.getParent();

		return iter instanceof UnmatchedIfElse || iter instanceof IfStatement;
	}

	private boolean isOrNext(CondFact node) {
		if (node.getParent().getParent() instanceof ConditionMulti)
			return true;
		return false;
	}

	private int rellopToInt(Relop node) {

		if (node instanceof Eql)
			return Code.eq;
		if (node instanceof Neq)
			return Code.ne;
		if (node instanceof Gt)
			return Code.gt;
		if (node instanceof Gte)
			return Code.ge;
		if (node instanceof Lt)
			return Code.lt;
		if (node instanceof Lte)
			return Code.le;
		return -1;
	}

	private boolean isCombinedOpr(DesignatorElemAssign node) {
		return node.getAssignop() instanceof AssignopADD || node.getAssignop() instanceof AssignopMUL;
	}

	private boolean isLeftDesignator(SyntaxNode node) {

		SyntaxNode iter = node;

		while (!(iter instanceof Designator))
			iter = iter.getParent();
		if (iter.getParent() instanceof DesignatorStatement) {

			DesignatorStatement ref = (DesignatorStatement) iter.getParent();
			return !(ref.getDesignatorElem() instanceof DesignatorElemActPars);

		}
		if (iter.getParent() instanceof ReadStatement)
			return true;

		if (iter.getParent() instanceof FactorComplex) {
			Factor factor = (Factor) iter.getParent();
			if (factor.getParent() instanceof FactorChainEl) {
				FactorChainEl fce = (FactorChainEl) factor.getParent();
				if (((FactorChainSome) fce.getParent()).getFactorChain() instanceof FactorChainSome) {
					FactorChainSome fcs = (FactorChainSome) ((FactorChainSome) fce.getParent()).getFactorChain();
					return fcs.getFactorChainEl().getMulop() instanceof MulopR;
				}
			} else if (factor.getParent() instanceof Term) {
				Term term = (Term) factor.getParent();
				if (term.getFactorChain() instanceof FactorChainSome) {
					FactorChainSome fcs = (FactorChainSome) term.getFactorChain();
					return fcs.getFactorChainEl().getMulop() instanceof MulopR;
				} else if (term.getParent() instanceof TermChainEl) {
					TermChainEl tce = (TermChainEl) term.getParent();
					if (((TermChainSome) tce.getParent()).getTermChain() instanceof TermChainSome) {
						TermChainSome tcs = (TermChainSome) ((TermChainSome) tce.getParent()).getTermChain();
						return tcs.getTermChainEl().getAddop() instanceof AddopR;
					}
				} else if (term.getParent() instanceof Expr) {
					Expr expr = (Expr) term.getParent();
					if (expr.getTermChain() instanceof TermChainSome) {
						TermChainSome tcs = (TermChainSome) expr.getTermChain();
						return tcs.getTermChainEl().getAddop() instanceof AddopR;
					}
				}
			}

		}
		return false;

	}

	private boolean isLastDesigRef(DesignatorChoice node) {
		return (((DesignatorChoiceListSome) node.getParent())
				.getDesignatorChoiceList() instanceof DesignatorChoiceListNone);
	}

	private void resolveCombinedOpr(DesignatorElemAssign node) {

		if (node.getAssignop() instanceof AssignopADD) {

			AssignopADD ref = (AssignopADD) node.getAssignop();
			if (ref.getAddopRight() instanceof AddopRightPlus)
				Code.put(Code.add);
			else
				Code.put(Code.sub);

		} else {
			AssignopMUL ref = (AssignopMUL) node.getAssignop();
			if (ref.getMulopRight() instanceof Mleq)
				Code.put(Code.mul);
			else if (ref.getMulopRight() instanceof Dveq)
				Code.put(Code.div);
			else
				Code.put(Code.rem);
		}

	}

	private int typeOfDesignator(Designator node) {

		DesignatorChoiceList iter = node.getDesignatorChoiceList();
		int ret = 0;
		while (iter instanceof DesignatorChoiceListSome) {

			DesignatorChoiceListSome ref = (DesignatorChoiceListSome) iter;
			if (ref.getDesignatorChoice() instanceof DesignatorChoiceSingle)
				ret = 1;
			else
				ret = 2;

			iter = ref.getDesignatorChoiceList();
		}

		return ret;

	}

}
