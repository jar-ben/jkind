package jkind.lustre;

import java.util.Collections;
import java.util.List;

public class Node extends AST {
	final public List<VarDecl> inputs;
	final public List<VarDecl> outputs;
	final public List<VarDecl> locals;
	final public List<Equation> equations;
	final public List<String> properties;

	public Node(List<VarDecl> inputs, List<VarDecl> outputs, List<VarDecl> locals,
			List<Equation> equations, List<String> properties) {
		this.inputs = Collections.unmodifiableList(inputs);
		this.outputs = Collections.unmodifiableList(outputs);
		this.locals = Collections.unmodifiableList(locals);
		this.equations = Collections.unmodifiableList(equations);
		this.properties = Collections.unmodifiableList(properties);
	}
}