package jkind.api.extractor;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import jkind.lustre.Location;
import jkind.lustre.parsing.LustreToAstVisitor;
import jkind.lustre.parsing.LustreParser.BinaryExprContext;

public class ExtendedLustreToAstVisitor extends LustreToAstVisitor {
	@Override
	protected Location loc(ParserRuleContext ctx) {
		Token token = ctx.getStart();
		if (ctx instanceof BinaryExprContext) {
			BinaryExprContext binExpr = (BinaryExprContext) ctx;
			token = binExpr.op;
		}
		int start = ctx.getStart().getStartIndex();
		int stop = ctx.getStop().getStopIndex();
		return new ExtendedLocation(token.getLine(), token.getCharPositionInLine(), start, stop);
	}
}
