package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

public class SymTab extends Tab {

	public static final Map<Struct, String> typeNames = new LinkedHashMap<>();

	protected static final Map<Struct, Struct> arrTypes = new LinkedHashMap<>();

	public static final Map<Struct, Set<Obj>> accessRules = new LinkedHashMap<>();

	public static final Map<Struct, Obj> structToObj = new LinkedHashMap<>();

	public static final Struct boolType = new Struct(Struct.Bool);

	public static final Set<Struct> abstractClasses = new HashSet<>();

	public static int getLevel() {
		int ret = -1;

		Scope iter = currentScope;
		while (iter != null) {
			iter = iter.getOuter();
			ret++;

		}
		return ret;

	}

	public static void init() {
		Tab.init();
		currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
	}

	public static Struct getArrType(Struct type) {
		if (!arrTypes.containsKey(type)) {
			arrTypes.put(type, new Struct(Struct.Array, type));
		}
		return arrTypes.get(type);
	}

	public static Obj clone(Obj obj) {

		Obj newObj = new Obj(obj.getKind(), obj.getName(), obj.getType(), obj.getAdr(), obj.getLevel());
		newObj.setFpPos(obj.getFpPos());

		if (!obj.getLocalSymbols().isEmpty()) {
			SymbolDataStructure list = new HashTableDataStructure();
			for (Obj clone : obj.getLocalSymbols()) {
				list.insertKey(clone(clone));
			}
			newObj.setLocals(list);
		}

		return newObj;

	}

	// If null none shared, else common upper class returned
	public static boolean isLower(Struct lower, Struct upper) {

		Struct iter = lower;

		while (iter != SymTab.nullType && iter != null) {
			if (iter == upper)
				return true;
			else
				iter = iter.getElemType();
		}
		return false;

	}

	public static String findTypeName(Struct type) {
		switch (type.getKind()) {
		case Struct.Bool:
			return "bool";
		case Struct.Char:
			return "char";
		case Struct.Int:
			return "int";
		case Struct.Class:
			return typeNames.get(type);
		case Struct.Array:
			return "Array of " + findTypeName(type.getElemType());
		}
		return "none";
	}

	public static String ObjToStr(Obj obj) {
		if (obj == null)
			return "";
		return obj.getName() + " : " + findTypeName(obj.getType()) + " [" + objInfoMap.get(obj.getKind()) + "]["
				+ obj.getAdr() + "," + obj.getLevel() + "," + obj.getFpPos() + "]";
	}

	public static boolean assignableTo(Struct src, Struct dst) {
		return SymTab.isLower(src, dst) || (src == Tab.nullType && dst.isRefType())
				|| (src.getKind() == Struct.Array && dst.getKind() == Struct.Array && dst.getElemType() == Tab.noType);

	}

	private static Map<Integer, String> objInfoMap = new HashMap<>();
	static {
		String[] input = { "Con", "Var", "Type", "Meth", "Fld", "Elem", "Prog" };
		for (int i = 0; i < input.length; i++)
			objInfoMap.put(i, input[i]);
		objInfoMap.put(-1, "NO_VAL");
	}

}
