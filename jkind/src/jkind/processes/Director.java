package jkind.processes;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jkind.JKindException;
import jkind.JKindSettings;
import jkind.invariant.Invariant;
import jkind.lustre.Function;
import jkind.lustre.Type;
import jkind.lustre.VarDecl;
import jkind.lustre.values.Value;
import jkind.processes.messages.CounterexampleMessage;
import jkind.processes.messages.InductiveCounterexampleMessage;
import jkind.processes.messages.InvalidMessage;
import jkind.processes.messages.Message;
import jkind.processes.messages.ValidMessage;
import jkind.results.Counterexample;
import jkind.results.Signal;
import jkind.results.layout.NodeLayout;
import jkind.slicing.CounterexampleSlicer;
import jkind.solvers.Decl;
import jkind.solvers.Model;
import jkind.solvers.StreamDef;
import jkind.solvers.yices.YicesModel;
import jkind.translation.Specification;
import jkind.util.Util;
import jkind.writers.ConsoleWriter;
import jkind.writers.ExcelWriter;
import jkind.writers.Writer;
import jkind.writers.XmlWriter;

public class Director {
	private JKindSettings settings;
	private Specification spec;
	private Writer writer;

	private List<String> remainingProperties = new ArrayList<>();
	private List<String> validProperties = new ArrayList<>();
	private List<String> invalidProperties = new ArrayList<>();
	private Map<String, InductiveCounterexampleMessage> inductiveCounterexamples = new HashMap<>();
	private Map<String, Decl> declarations;

	private BaseProcess baseProcess;
	private InductiveProcess inductiveProcess;
	private InvariantProcess invariantProcess;
	private ReduceProcess reduceProcess;
	private SmoothProcess smoothProcess;
	private IntervalProcess intervalProcess;

	private List<Process> processes = new ArrayList<>();
	private List<Thread> threads = new ArrayList<>();

	protected BlockingQueue<Message> incoming = new LinkedBlockingQueue<>();

	public Director(JKindSettings settings, Specification spec) {
		this.settings = settings;
		this.spec = spec;
		this.writer = getWriter(spec);
		this.remainingProperties.addAll(spec.node.properties);
		this.declarations = spec.translation.getDeclarationsTable();
	}

	private Writer getWriter(Specification spec) {
		try {
			if (settings.excel) {
				return new ExcelWriter(spec.filename + ".xls", spec.node);
			} else if (settings.xml) {
				return new XmlWriter(spec.filename + ".xml", spec.typeMap);
			} else {
				return new ConsoleWriter(new NodeLayout(spec.node));
			}
		} catch (IOException e) {
			throw new JKindException("Unable to open output file", e);
		}
	}

	public void run() {
		printHeader();
		writer.begin();
		startThreads();

		long startTime = System.currentTimeMillis();
		long timeout = startTime + ((long) settings.timeout) * 1000;
		while (System.currentTimeMillis() < timeout && !remainingProperties.isEmpty()
				&& someThreadAlive() && !someProcessFailed()) {
			processMessages(startTime);
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}

		processMessages(startTime);
		if (!remainingProperties.isEmpty()) {
			writer.writeUnknown(remainingProperties, convertInductiveCounterexamples());
		}

		writer.end();
		printSummary();
		reportFailures();
	}

	private boolean someThreadAlive() {
		for (Thread thread : threads) {
			if (thread.isAlive()) {
				return true;
			}
		}

		return false;
	}

	private boolean someProcessFailed() {
		for (Process process : processes) {
			if (process.getThrowable() != null) {
				return true;
			}
		}

		return false;
	}

	private void reportFailures() {
		for (Process process : processes) {
			if (process.getThrowable() != null) {
				Throwable t = process.getThrowable();
				System.out.println(process.getName() + " process failed");
				t.printStackTrace(System.out);
			}
		}
	}

	private void printHeader() {
		System.out.println("==========================================");
		System.out.println("  JAVA KIND");
		System.out.println("==========================================");
		System.out.println();
		System.out
				.println("There are " + remainingProperties.size() + " properties to be checked.");
		System.out.println("PROPERTIES TO BE CHECKED: " + remainingProperties);
		System.out.println();
	}

	private void startThreads() {
		baseProcess = new BaseProcess(spec, settings, this);
		registerProcess(baseProcess);

		if (settings.useInductiveProcess) {
			inductiveProcess = new InductiveProcess(spec, settings, this);
			baseProcess.setInductiveProcess(inductiveProcess);
			inductiveProcess.setBaseProcess(baseProcess);
			registerProcess(inductiveProcess);
		}

		if (settings.useInvariantProcess) {
			invariantProcess = new InvariantProcess(spec, settings);
			invariantProcess.setInductiveProcess(inductiveProcess);
			inductiveProcess.setInvariantProcess(invariantProcess);
			registerProcess(invariantProcess);
		}

		if (settings.reduceInvariants) {
			reduceProcess = new ReduceProcess(spec, settings, this);
			inductiveProcess.setReduceProcess(reduceProcess);
			registerProcess(reduceProcess);
		}

		if (settings.smoothCounterexamples) {
			smoothProcess = new SmoothProcess(spec, settings, this);
			baseProcess.setCounterexampleProcess(smoothProcess);
			registerProcess(smoothProcess);
		}

		if (settings.intervalGeneralization) {
			intervalProcess = new IntervalProcess(spec, settings, this);
			if (smoothProcess == null) {
				baseProcess.setCounterexampleProcess(intervalProcess);
			} else {
				smoothProcess.setCounterexampleProcess(intervalProcess);
			}
			registerProcess(intervalProcess);
		}

		for (Thread thread : threads) {
			thread.start();
		}
	}

	private void registerProcess(Process process) {
		processes.add(process);
		threads.add(new Thread(process, process.getName()));
	}

	private void processMessages(long startTime) {
		while (!incoming.isEmpty()) {
			Message message = incoming.poll();
			double runtime = (System.currentTimeMillis() - startTime) / 1000.0;
			if (message instanceof ValidMessage) {
				ValidMessage vm = (ValidMessage) message;
				remainingProperties.removeAll(vm.valid);
				validProperties.addAll(vm.valid);
				inductiveCounterexamples.keySet().removeAll(vm.valid);
				List<Invariant> invariants = vm.invariants;
				if (reduceProcess == null) {
					invariants = Collections.<Invariant> emptyList();
				}
				writer.writeValid(vm.valid, vm.k, runtime, invariants);
			} else if (message instanceof InvalidMessage) {
				InvalidMessage im = (InvalidMessage) message;
				remainingProperties.removeAll(im.invalid);
				invalidProperties.addAll(im.invalid);
				inductiveCounterexamples.keySet().removeAll(im.invalid);
				CounterexampleSlicer cexSlicer = new CounterexampleSlicer(spec.dependencyMap);
				for (String invalidProp : im.invalid) {
					Model slicedModel = cexSlicer.slice(invalidProp, im.model);
					Counterexample cex = extractCounterexample(im.k, BigInteger.ZERO, slicedModel);
					writer.writeInvalid(invalidProp, cex, runtime);
				}
			} else if (message instanceof CounterexampleMessage) {
				CounterexampleMessage cm = (CounterexampleMessage) message;
				remainingProperties.remove(cm.invalid);
				invalidProperties.add(cm.invalid);
				inductiveCounterexamples.keySet().remove(cm.invalid);
				writer.writeInvalid(cm.invalid, cm.cex, runtime);
			} else if (message instanceof InductiveCounterexampleMessage) {
				InductiveCounterexampleMessage icm = (InductiveCounterexampleMessage) message;
				inductiveCounterexamples.put(icm.property, icm);
			} else {
				throw new JKindException("Unknown message type in director: "
						+ message.getClass().getCanonicalName());
			}
		}
	}

	private void printSummary() {
		System.out.println("    -------------------------------------");
		System.out.println("    --^^--        SUMMARY          --^^--");
		System.out.println("    -------------------------------------");
		System.out.println();
		if (!validProperties.isEmpty()) {
			System.out.println("VALID PROPERTIES: " + validProperties);
			System.out.println();
		}
		if (!invalidProperties.isEmpty()) {
			System.out.println("INVALID PROPERTIES: " + invalidProperties);
			System.out.println();
		}
		if (!remainingProperties.isEmpty()) {
			System.out.println("UNKNOWN PROPERTIES: " + remainingProperties);
			System.out.println();
		}
	}

	private Map<String, Counterexample> convertInductiveCounterexamples() {
		Map<String, Counterexample> result = new HashMap<>();

		CounterexampleSlicer cexSlicer = new CounterexampleSlicer(spec.dependencyMap);
		for (String prop : inductiveCounterexamples.keySet()) {
			InductiveCounterexampleMessage icm = inductiveCounterexamples.get(prop);
			Model slicedModel = cexSlicer.slice(icm.property, icm.model);
			result.put(prop, extractCounterexample(icm.k, icm.n, slicedModel));
		}

		return result;
	}

	private Counterexample extractCounterexample(int k, BigInteger offset, Model model) {
		Counterexample cex = new Counterexample(k, spec.functions);
		model.setDeclarations(declarations);
		model.setDefinitions(Collections.<String, StreamDef> emptyMap());
		for (String fn : new TreeSet<>(model.getFunctions())) {
			if (!fn.startsWith("$$")) {
				cex.addSignal(extractSignal(fn, k, offset, model));
			}
		}

		int todo_support_other_solvers;
		YicesModel yicesModel = (YicesModel) model;
		for (String fn : new TreeSet<>(model.getFunctions())) {
			if (fn.startsWith("$$")) {
				for (Entry<List<jkind.solvers.Value>, jkind.solvers.Value> entry : yicesModel
						.getFunction(fn).entrySet()) {
					addFunctionValue(cex, fn.substring(2), entry.getKey(), entry.getValue());
				}
			}
		}

		return cex;
	}

	private void addFunctionValue(Counterexample cex, String name,
			List<jkind.solvers.Value> inputs, jkind.solvers.Value value) {
		Function function = getFunction(spec.functions, name);
		List<Value> inputValues = parseValues(Util.getTypes(function.inputs), inputs);
		VarDecl output = function.outputs.get(0);
		Type outputType = output.type;
		Value outputValue = Util.parseValue(Util.getName(outputType), value.toString());
		cex.addFunctionValue(getBase(name), inputValues, output, outputValue);
	}

	private Function getFunction(List<Function> functions, String name) {
		for (Function function : functions) {
			if (function.id.equals(name)) {
				return function;
			}
		}
		return null;
	}

	private String getBase(String name) {
		return name.substring(0, name.indexOf('.'));
	}

	private List<Value> parseValues(List<Type> types, List<jkind.solvers.Value> values) {
		List<Value> result = new ArrayList<>();
		for (int i = 0; i < types.size(); i++) {
			result.add(Util.parseValue(Util.getName(types.get(i)), values.get(i).toString()));
		}
		return result;
	}

	private Signal<Value> extractSignal(String fn, int k, BigInteger offset, Model model) {
		String name = fn.substring(1);
		Signal<Value> signal = new Signal<>(name);
		Type type = spec.typeMap.get(name);

		for (int i = 0; i < k; i++) {
			BigInteger key = BigInteger.valueOf(i).add(offset);
			jkind.solvers.Value value = model.getStreamValue(fn, key);
			if (value != null) {
				signal.putValue(i, Util.parseValue(Util.getName(type), value.toString()));
			}
		}

		return signal;
	}
}
