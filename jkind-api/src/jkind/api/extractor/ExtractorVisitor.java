package jkind.api.extractor;

import java.util.ArrayList;
import java.util.List;

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
import jkind.lustre.Program;
import jkind.lustre.RealExpr;
import jkind.lustre.RecordAccessExpr;
import jkind.lustre.RecordExpr;
import jkind.lustre.RecordUpdateExpr;
import jkind.lustre.TupleExpr;
import jkind.lustre.UnaryExpr;
import jkind.lustre.VarDecl;
import jkind.lustre.builders.NodeBuilder;
import jkind.lustre.visitors.TypeAwareAstMapVisitor;

public class ExtractorVisitor extends TypeAwareAstMapVisitor {
	private final List<VarDecl> newLocals = new ArrayList<>();
	private final List<Equation> newEquations = new ArrayList<>();
	private final List<String> newIvc = new ArrayList<>();
	public final static String PREFIX = "extracted";

	public static Program extract(Program program) {
		return (Program) program.accept(new ExtractorVisitor());
	}

	@Override
	public Constant visit(Constant e) {
		return e;
	}

	@Override
	public Node visit(Node e) {
		e = super.visit(e);
		NodeBuilder builder = new NodeBuilder(e);
		builder.addLocals(newLocals);
		builder.addEquations(newEquations);
		builder.addIvcs(newIvc);
		return builder.build();
	}

	private Expr makeVar(Expr e) {
		if (!(e.location instanceof ExtendedLocation)) {
			throw new IllegalArgumentException("Unknown location for: " + e);
		}

		ExtendedLocation el = (ExtendedLocation) e.location;
		String name = String.format("%s_%d_%d", PREFIX, el.start, el.stop);
		IdExpr var = new IdExpr(name);
		VarDecl varDecl = new VarDecl(name, getType(e));
		typeReconstructor.addVariable(varDecl);
		newLocals.add(varDecl);
		newEquations.add(new Equation(var, e));
		newIvc.add(name);
		return var;
	}

	@Override
	public Equation visit(Equation e) {
		return new Equation(e.location, e.lhs, makeVar(e.expr.accept(this)));
	}

	@Override
	public Expr visit(BinaryExpr e) {
		return new BinaryExpr(e.location, makeVar(e.left.accept(this)), e.op,
				makeVar(e.right.accept(this)));
	}

	@Override
	public Expr visit(IdExpr e) {
		return e;
	}

	@Override
	public Expr visit(IfThenElseExpr e) {
		return new IfThenElseExpr(e.location, makeVar(e.cond.accept(this)),
				makeVar(e.thenExpr.accept(this)), makeVar(e.elseExpr.accept(this)));
	}

	@Override
	public Expr visit(IntExpr e) {
		return makeVar(e);
	}

	@Override
	public Expr visit(RealExpr e) {
		return makeVar(e);
	}

	@Override
	public Expr visit(UnaryExpr e) {
		return new UnaryExpr(e.location, e.op, makeVar(e.expr.accept(this)));
	}

	/**********************************************************************
	 * 
	 * Unsupported expressions
	 * 
	 **********************************************************************/

	@Override
	public Expr visit(ArrayAccessExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(ArrayExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(ArrayUpdateExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(BoolExpr e) {
		return e;
	}

	@Override
	public Expr visit(CastExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(CondactExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(NodeCallExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(RecordAccessExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(RecordExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(RecordUpdateExpr e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Expr visit(TupleExpr e) {
		throw new UnsupportedOperationException();
	}
}
