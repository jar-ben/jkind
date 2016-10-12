package jkind.lustre.visitors;

import jkind.lustre.Expr;
import jkind.lustre.Node;
import jkind.lustre.Program;
import jkind.lustre.Type;

public class TypeAwareAstMapVisitor extends AstMapVisitor {
	private final boolean intEncoding;
	protected TypeReconstructor typeReconstructor;

	public TypeAwareAstMapVisitor(boolean intEncoding) {
		this.intEncoding = intEncoding;
		this.typeReconstructor = new TypeReconstructor(intEncoding);
	}

	public TypeAwareAstMapVisitor() {
		this(TypeReconstructor.INT_ENCODING);
	}

	protected Type getType(Expr e) {
		return e.accept(typeReconstructor);
	}

	@Override
	public Program visit(Program e) {
		typeReconstructor = new TypeReconstructor(e, intEncoding);
		return super.visit(e);
	}

	@Override
	public Node visit(Node e) {
		typeReconstructor.setNodeContext(e);
		return super.visit(e);
	}
}
