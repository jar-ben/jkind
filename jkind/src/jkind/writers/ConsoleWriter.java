package jkind.writers;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jkind.engines.MiniJKind;
import jkind.lustre.Expr;
import jkind.results.Counterexample;
import jkind.results.layout.Layout;
import jkind.util.Tuple;
import jkind.util.Util;

public class ConsoleWriter extends Writer {
	private final Layout layout;
    private MiniJKind miniJkind;
	public ConsoleWriter(Layout layout) {
		super();
		this.layout = layout;
	}
	
	public ConsoleWriter(Layout layout, MiniJKind miniJkind) {
		this(layout);
		this.miniJkind = miniJkind;
	}

	@Override
	public void begin() {
	}

	@Override
	public void end() {
	}

	private void writeLine() {
		System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
	}

	@Override
	public void writeValid(List<String> props, String source, int k, double runtime,
			List<Expr> invariants, Set<String> ivc, Set<Tuple<Set<String>, List<String>>> allIvcs) {
		if(miniJkind != null){
			List<String> stringInvariants = invariants.stream().map(Object::toString).collect(toList());
			miniJkind.getValid(Util.safeStringSortedSet(ivc), Util.safeStringSortedSet(stringInvariants));
		}else{
			writeLine();
			System.out.println("VALID PROPERTIES: " + props + " || " + source + " || K = " + k
				+ " || Time = " + Util.secondsToTime(runtime));
			if (!invariants.isEmpty()) {
				System.out.println("INVARIANTS:");
				List<String> stringInvariants = invariants.stream().map(Object::toString).collect(toList());
				for (String invariant : Util.safeStringSortedSet(stringInvariants)) {
					System.out.println("  " + invariant);
				}
			}
			if (!allIvcs.isEmpty()) {
					int counter = 1;
					System.out.println("\nINDUCTIVE VALIDITY CORES:\n");
					if (!ivc.isEmpty()) {
						System.out.println("MUST ELEMENTS FOR THE PROPERTY:");
						for (String e : Util.safeStringSortedSet(ivc)) {
							System.out.println("  " + e);
						}
					}
					System.out.println("\n"+ allIvcs.size() + " INDUCTIVE VALIDITY CORES WERE FOUND:");
					System.out.println("============================");
					for (Tuple<Set<String>, List<String>> t : allIvcs) {
						System.out.println("IVC  #" + counter + ":");
						counter++;
						System.out.println("INVARIANTS:");
						for(String inv : t.secondElement()){
							System.out.println("  " + inv);
						}
						System.out.println("INDUCTIVE VALIDITY CORE:");
						for(String core : t.firstElement()){
							System.out.println("  " + core);
						}
						System.out.println("============================");
					}
				}
			else if (!ivc.isEmpty()) {
				System.out.println("INDUCTIVE VALIDITY CORE:");
				for (String e : Util.safeStringSortedSet(ivc)) {
					System.out.println("  " + e);
				}
			}
				writeLine();
				System.out.println();
		}
	}

	@Override
	public void writeInvalid(String prop, String source, Counterexample cex,
			List<String> conflicts, double runtime) {
		if(miniJkind != null){
			miniJkind.getInvalid();
		}else{
			writeLine();
			System.out.println("INVALID PROPERTY: " + prop + " || " + source + " || K = "
				+ cex.getLength() + " || Time = " + Util.secondsToTime(runtime));
			System.out.println(cex.toString(layout));
			writeLine();
			System.out.println();
		}
	}

	@Override
	public void writeUnknown(List<String> props, int trueFor,
			Map<String, Counterexample> inductiveCounterexamples, double runtime) {
		if(miniJkind != null){
			miniJkind.getUnknown();
		}else{
			writeLine();
			System.out.println("UNKNOWN PROPERTIES: " + props + " || True for " + trueFor + " steps"
				+ " || Time = " + Util.secondsToTime(runtime));
			writeLine();
			System.out.println();
			for (String prop : props) {
				Counterexample cex = inductiveCounterexamples.get(prop);
				if (cex != null) {
					writeLine();
					System.out.println("INDUCTIVE COUNTEREXAMPLE: " + prop + " || K = "
						+ cex.getLength());
					System.out.println(cex.toString(layout));
					writeLine();
					System.out.println();
				}
			}
		}
	}

	@Override
	public void writeBaseStep(List<String> props, int k) {
	}

	@Override
	public void writeInconsistent(String prop, String source, int k, double runtime) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeValid(List<String> props, String source, int k, double runtime, List<Expr> invariants,
			Set<String> ivc) {
		// TODO Auto-generated method stub
		
	}
}
