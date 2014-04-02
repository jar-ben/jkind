package jkind.interval;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import jkind.lustre.Equation;
import jkind.lustre.Expr;
import jkind.lustre.IdExpr;
import jkind.lustre.NamedType;
import jkind.lustre.SubrangeIntType;
import jkind.lustre.Type;
import jkind.lustre.values.IntegerValue;
import jkind.lustre.values.RealValue;
import jkind.lustre.values.Value;
import jkind.results.Counterexample;
import jkind.results.Signal;
import jkind.solvers.BoolValue;
import jkind.solvers.Model;
import jkind.translation.Specification;
import jkind.util.Util;

public class ModelGeneralizer {
	private final Specification spec;
	private final String property;
	private final Model basisModel;
	private final int k;

	private final Map<IdIndexPair, Interval> cache = new HashMap<>();
	private final Map<String, Expr> equations = new HashMap<>();

	private final Queue<IdIndexPair> toGeneralize = new ArrayDeque<>();
	private final Map<IdIndexPair, Interval> generalized = new HashMap<>();

	private final ReverseDependencyMap dependsOn;

	private final IntIntervalGeneralizer intIntervalGeneralizer;
	private final RealIntervalGeneralizer realIntervalGeneralizer;

	public ModelGeneralizer(Specification spec, String property, Model model, int k) {
		this.spec = spec;
		this.property = property;
		this.basisModel = model;
		this.k = k;

		for (Equation eq : spec.node.equations) {
			equations.put(eq.lhs.get(0).id, eq.expr);
		}

		dependsOn = new ReverseDependencyMap(spec.node, spec.dependencyMap.get(property));

		intIntervalGeneralizer = new IntIntervalGeneralizer(this);
		realIntervalGeneralizer = new RealIntervalGeneralizer(this);
	}

	public Counterexample generalize() {
		// This fills the initial toGeneralize queue as a side-effect
		if (!modelConsistent()) {
			throw new IllegalStateException("Internal JKind error during interval generalization");
		}

		// More items may be added to the toGeneralize queue as prior
		// generalizations make them relevant
		while (!toGeneralize.isEmpty()) {
			IdIndexPair pair = toGeneralize.remove();
			Interval interval = generalizeInterval(pair.id, pair.i);
			generalized.put(pair, interval);
		}

		return extractCounterexample();
	}

	private Interval generalizeInterval(String id, int i) {
		Type type = spec.typeMap.get(id);
		if (type == NamedType.BOOL) {
			return generalizeBoolInterval(id, i);
		} else if (type == NamedType.INT) {
			NumericInterval initial = (NumericInterval) originalInterval(id, i);
			return intIntervalGeneralizer.generalize(id, i, initial);
		} else if (type == NamedType.REAL) {
			NumericInterval initial = (NumericInterval) originalInterval(id, i);
			return realIntervalGeneralizer.generalize(id, i, initial);
		} else if (type instanceof SubrangeIntType) {
			return generalizeSubrangeIntInterval(id, i);
		} else {
			throw new IllegalArgumentException("Unknown type in generalization: " + type);
		}
	}

	private Interval generalizeBoolInterval(String id, int i) {
		if (modelConsistent(id, i, BoolInterval.ARBITRARY)) {
			return BoolInterval.ARBITRARY;
		} else {
			return originalInterval(id, i);
		}
	}

	private Interval generalizeSubrangeIntInterval(String id, int i) {
		NumericInterval next = new NumericInterval(IntEndpoint.NEGATIVE_INFINITY,
				IntEndpoint.POSITIVE_INFINITY);
		if (modelConsistent(id, i, next)) {
			return next;
		} else {
			return originalInterval(id, i);
		}
	}

	private Counterexample extractCounterexample() {
		// This fills the cache as a side-effect
		if (!modelConsistent()) {
			throw new IllegalStateException("Internal JKind error during interval generalization");
		}

		Counterexample cex = new Counterexample(k, spec.functions);
		int todo_add_function_values_to_cex;
		for (Entry<IdIndexPair, Interval> entry : cache.entrySet()) {
			IdIndexPair pair = entry.getKey();
			Interval value = entry.getValue();

			if (!value.isArbitrary()) {
				Signal<Value> signal = cex.getSignal(pair.id);
				if (signal == null) {
					signal = new Signal<>(pair.id);
					cex.addSignal(signal);
				}

				signal.putValue(pair.i, value);
			}
		}
		return cex;
	}

	private Interval originalInterval(String id, int i) {
		return eval(id, i);
	}

	private boolean modelConsistent() {
		BoolInterval interval = (BoolInterval) eval(property, k - 1);
		if (!interval.isFalse()) {
			return false;
		}
		for (Expr as : spec.node.assertions) {
			for (int i = 0; i < k; i++) {
				interval = (BoolInterval) eval(as, i);
				if (!interval.isTrue()) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean modelConsistent(String id, int i, Interval proposedValue) {
		clearCacheFrom(id, i);
		cache.put(new IdIndexPair(id, i), proposedValue);
		boolean result = modelConsistent();
		clearCacheFrom(id, i);
		return result;
	}

	private void clearCacheFrom(String id, int step) {
		for (String recompute : dependsOn.get(id)) {
			for (int i = step; i < k; i++) {
				cache.remove(new IdIndexPair(recompute, i));
			}
		}
	}

	public static class AlgebraicLoopException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private final Set<IdIndexPair> working = new HashSet<>();

	private Interval eval(Expr expr, int i) {
		return expr.accept(new IntervalEvaluator(i, this));
	}

	private Interval eval(String id, int i) {
		return eval(new IdExpr(id), i);
	}

	/*
	 * The IntervalEvaluator will call back in to this class to look up ids.
	 */
	public Interval evalId(String id, int i) {
		IdIndexPair pair = new IdIndexPair(id, i);
		if (cache.containsKey(pair)) {
			return cache.get(pair);
		}

		Interval result;
		if (equations.containsKey(id) && i >= 0) {
			pair = new IdIndexPair(id, i);
			if (working.contains(pair)) {
				throw new AlgebraicLoopException();
			}

			working.add(pair);
			result = eval(equations.get(id), i);
			working.remove(pair);

		} else if (generalized.containsKey(pair)) {
			result = generalized.get(pair);
		} else {
			result = getFromBasisModel(pair);
			// Due to checking all assertions, we may hit variables that our
			// property doesn't depend on. We detect and ignore these variables.
			if (i >= 0 && dependsOn.get(id) != null) {
				toGeneralize.add(pair);
			}
		}
		cache.put(pair, result);
		return result;
	}

	private Interval getFromBasisModel(IdIndexPair pair) {
		jkind.solvers.Value raw = basisModel.getStreamValue("$" + pair.id,
				BigInteger.valueOf(pair.i));

		if (raw instanceof BoolValue) {
			BoolValue bv = (BoolValue) raw;
			return bv.getBool() ? BoolInterval.TRUE : BoolInterval.FALSE;
		} else {
			Value parsed = Util.parseValue(Util.getName(spec.typeMap.get(pair.id)), raw.toString());
			if (parsed instanceof IntegerValue) {
				IntegerValue iv = (IntegerValue) parsed;
				IntEndpoint endpoint = new IntEndpoint(iv.value);
				return new NumericInterval(endpoint, endpoint);
			} else {
				RealValue rv = (RealValue) parsed;
				RealEndpoint endpoint = new RealEndpoint(rv.value);
				return new NumericInterval(endpoint, endpoint);
			}
		}
	}
}
