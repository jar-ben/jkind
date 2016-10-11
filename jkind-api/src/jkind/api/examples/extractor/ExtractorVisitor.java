package jkind.api.examples.extractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jkind.lustre.ArrayAccessExpr;
import jkind.lustre.ArrayExpr;
import jkind.lustre.ArrayUpdateExpr;
import jkind.lustre.BinaryExpr;
import jkind.lustre.BoolExpr;
import jkind.lustre.CastExpr;
import jkind.lustre.CondactExpr;
import jkind.lustre.Constant;
import jkind.lustre.Equation;
import jkind.lustre.Expr;
import jkind.lustre.IdExpr;
import jkind.lustre.IfThenElseExpr;
import jkind.lustre.IntExpr;
import jkind.lustre.Node;
import jkind.lustre.NodeCallExpr;
import jkind.lustre.RealExpr;
import jkind.lustre.RecordAccessExpr;
import jkind.lustre.RecordExpr;
import jkind.lustre.RecordUpdateExpr;
import jkind.lustre.SubrangeIntType;
import jkind.lustre.TupleExpr;
import jkind.lustre.UnaryExpr;
import jkind.lustre.VarDecl;
import jkind.lustre.builders.NodeBuilder;
import jkind.lustre.visitors.TypeAwareAstMapVisitor;

public class ExtractorVisitor extends TypeAwareAstMapVisitor {
	public final static String PREFIX = "__extracted__";
	private final Map<String, ELocation> locationMap = new HashMap<>();
	private int extractedCounter = 0;

	private final List<VarDecl> newLocals = new ArrayList<>();
	private final List<Equation> newEquations = new ArrayList<>();
	private final List<String> newIvc = new ArrayList<>();

	public Map<String, ELocation> getLocationMap() {
		return locationMap;
	}

	@Override
	public Constant visit(Constant e) {
		return e;
	}

	@Override
	public Node visit(Node e) {
		newLocals.clear();
		newEquations.clear();
		newIvc.clear();

		e = super.visit(e);

		NodeBuilder builder = new NodeBuilder(e);
		builder.addLocals(newLocals);
		builder.addEquations(newEquations);
		builder.clearIvc().addIvcs(newIvc);
		return builder.build();
	}

	private Expr makeVar(Expr e) {
		if (!(e.location instanceof ELocation)) {
			throw new IllegalArgumentException("Unknown location for: " + e);
		}

		String name = PREFIX + extractedCounter++;
		locationMap.put(name, (ELocation) e.location);
		IdExpr var = new IdExpr(name);
		VarDecl varDecl = new VarDecl(name, getType(e));
		typeReconstructor.addVariable(varDecl);
		newLocals.add(varDecl);
		newEquations.add(new Equation(var, e));
		newIvc.add(name);
		return var;
	}

	@Override
	public Expr visit(BinaryExpr e) {
		// Avoid creating non-linearities
		switch (e.op) {
		case MULTIPLY:
			if (e.left instanceof RealExpr || e.left instanceof IntExpr) {
				return makeVar(new BinaryExpr(e.location, e.left, e.op, e.right.accept(this)));
			} else if (e.right instanceof RealExpr || e.right instanceof IntExpr) {
				return makeVar(new BinaryExpr(e.location, e.left.accept(this), e.op, e.right));
			} else {
				System.err.println("Unable to handle expression: " + e);
				System.exit(-1);
				return null;
			}

		case DIVIDE:
		case INT_DIVIDE:
		case MODULUS:
			return makeVar(new BinaryExpr(e.location, e.left.accept(this), e.op, e.right));

		default:
			return makeVar(super.visit(e));
		}
	}

	@Override
	public Expr visit(BoolExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(CastExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(IdExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(IfThenElseExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(IntExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(NodeCallExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(RealExpr e) {
		return makeVar(super.visit(e));
	}

	@Override
	public Expr visit(UnaryExpr e) {
		return makeVar(super.visit(e));
	}

	/** Disabled expressions due to the way IVCs interact with flattening **/

	@Override
	public Expr visit(ArrayAccessExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(ArrayExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(ArrayUpdateExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(CondactExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(RecordAccessExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(RecordExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(RecordUpdateExpr e) {
		return unsupported(e);
	}

	@Override
	public Expr visit(TupleExpr e) {
		return unsupported(e);
	}

	@Override
	public VarDecl visit(VarDecl e) {
		if (e.type instanceof SubrangeIntType) {
			unsupported(e.type);
		}
		return e;
	}

	private Expr unsupported(Object e) {
		System.err.println(e.getClass().getSimpleName() + " is not supported");
		System.exit(-1);
		return null;
	}
}
