package jkind.lustre.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jkind.lustre.ArrayAccessExpr;
import jkind.lustre.ArrayExpr;
import jkind.lustre.ArrayUpdateExpr;
import jkind.lustre.BinaryExpr;
import jkind.lustre.BoolExpr;
import jkind.lustre.CallExpr;
import jkind.lustre.CastExpr;
import jkind.lustre.CondactExpr;
import jkind.lustre.Expr;
import jkind.lustre.IdExpr;
import jkind.lustre.IfThenElseExpr;
import jkind.lustre.IntExpr;
import jkind.lustre.RealExpr;
import jkind.lustre.RecordAccessExpr;
import jkind.lustre.RecordExpr;
import jkind.lustre.TupleExpr;
import jkind.lustre.UnaryExpr;

public class ExprMapVisitor implements ExprVisitor<Expr> {
	@Override
	public Expr visit(ArrayAccessExpr e) {
		return new ArrayAccessExpr(e.location, e.array.accept(this), e.index.accept(this));
	}

	@Override
	public Expr visit(ArrayExpr e) {
		return new ArrayExpr(e.location, visitAll(e.elements));
	}

	@Override
	public Expr visit(ArrayUpdateExpr e) {
		return new ArrayUpdateExpr(e.location, e.array.accept(this), e.index.accept(this),
				e.value.accept(this));
	}

	@Override
	public Expr visit(BinaryExpr e) {
		return new BinaryExpr(e.location, e.left.accept(this), e.op, e.right.accept(this));
	}

	@Override
	public Expr visit(BoolExpr e) {
		return e;
	}

	@Override
	public Expr visit(CallExpr e) {
		return new CallExpr(e.location, e.name, visitAll(e.args));
	}

	@Override
	public Expr visit(CastExpr e) {
		return new CastExpr(e.location, e.type, e.expr.accept(this));
	}

	@Override
	public Expr visit(CondactExpr e) {
		return new CondactExpr(e.location, e.clock.accept(this), (CallExpr) e.call.accept(this),
				visitAll(e.args));
	}

	@Override
	public Expr visit(IdExpr e) {
		return e;
	}

	@Override
	public Expr visit(IfThenElseExpr e) {
		return new IfThenElseExpr(e.location, e.cond.accept(this), e.thenExpr.accept(this),
				e.elseExpr.accept(this));
	}

	@Override
	public Expr visit(IntExpr e) {
		return e;
	}

	@Override
	public Expr visit(RealExpr e) {
		return e;
	}

	@Override
	public Expr visit(RecordAccessExpr e) {
		return new RecordAccessExpr(e.location, e.record.accept(this), e.field);
	}

	@Override
	public Expr visit(RecordExpr e) {
		Map<String, Expr> fields = new HashMap<>();
		for (Entry<String, Expr> entry : e.fields.entrySet()) {
			fields.put(entry.getKey(), entry.getValue().accept(this));
		}
		return new RecordExpr(e.location, e.id, fields);
	}

	@Override
	public Expr visit(TupleExpr e) {
		return new TupleExpr(e.location, visitAll(e.elements));
	}

	@Override
	public Expr visit(UnaryExpr e) {
		return new UnaryExpr(e.location, e.op, e.expr.accept(this));
	}

	protected List<Expr> visitAll(List<? extends Expr> list) {
		List<Expr> result = new ArrayList<>();
		for (Expr e : list) {
			result.add(e.accept(this));
		}
		return result;
	}
}
