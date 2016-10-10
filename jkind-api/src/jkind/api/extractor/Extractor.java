package jkind.api.extractor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jkind.api.JKindApi;
import jkind.api.results.JKindResult;
import jkind.api.results.PropertyResult;
import jkind.lustre.Program;
import jkind.lustre.parsing.LustreLexer;
import jkind.lustre.parsing.LustreParser;
import jkind.lustre.parsing.LustreParser.ProgramContext;
import jkind.results.ValidProperty;
import jkind.util.Util;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.core.runtime.NullProgressMonitor;

public class Extractor {
	public static void main(String[] args) throws Exception {
		String filename = args[0];

		Program program = parseLustre(new ANTLRFileStream(filename));
		if (program.nodes.size() > 1) {
			System.err.println("Only single node supported");
			System.exit(-1);
		}

		program = ExtractorVisitor.extract(program);
		JKindResult result = new JKindResult("results");
		JKindApi api = new JKindApi();
		api.setIvcReduction();
		api.execute(program, result, new NullProgressMonitor());

		reportResults(filename, program, result);
	}

	public static Program parseLustre(CharStream stream) throws Exception {
		LustreLexer lexer = new LustreLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		LustreParser parser = new LustreParser(tokens);
		ProgramContext program = parser.program();

		if (parser.getNumberOfSyntaxErrors() > 0) {
			System.exit(-1);
		}

		return new ExtendedLustreToAstVisitor().program(program);
	}

	private static void reportResults(String filename, Program program, JKindResult result)
			throws Exception {
		for (PropertyResult pr : result.getPropertyResults()) {
			if (!(pr.getProperty() instanceof ValidProperty)) {
				System.out.println("Property [" + pr.getProperty().getName() + "] is not valid");
			}
		}
		System.out.println();
		System.out.println();

		for (PropertyResult pr : result.getPropertyResults()) {
			if (pr.getProperty() instanceof ValidProperty) {
				ValidProperty vp = (ValidProperty) pr.getProperty();
				List<String> unused = new ArrayList<>(program.getMainNode().ivc);
				unused.removeAll(vp.getIvc());
				List<Span> spans = convertToSpans(unused);

				String header = "Annotations for [" + vp.getName() + "]";
				System.out.println(Util.makeString('=', header.length()));
				System.out.println(header);
				System.out.println(Util.makeString('=', header.length()));
				System.out.println();
				displaySpans(filename, spans);
				System.out.println();
				System.out.println();
			}
		}
	}

	private static List<Span> convertToSpans(List<String> names) {
		Pattern pattern = Pattern.compile(ExtractorVisitor.PREFIX + "_(\\d+)_(\\d+)");
		List<Span> result = new ArrayList<>();
		for (String name : names) {
			Matcher matcher = pattern.matcher(name);
			if (matcher.matches()) {
				int start = Integer.parseInt(matcher.group(1));
				int stop = Integer.parseInt(matcher.group(2));
				result.add(new Span(start, stop));
			} else {
				throw new IllegalArgumentException("Unknown variable: " + name);
			}
		}
		return result;
	}

	private static void displaySpans(String filename, List<Span> spans) throws Exception {
		StringBuilder annotation = new StringBuilder();
		int i = 0;
		int c;
		
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			while ((c = reader.read()) != -1) {
				System.out.print((char) c);
				annotation.append(contains(spans, i) ? "^" : " ");
				if (c == '\n') {
					String line = annotation.toString();
					if (line.contains("^")) {
						System.out.println(line);
					}
					annotation = new StringBuilder();
				}
				i++;
			}
		}
	}

	private static boolean contains(List<Span> spans, int i) {
		for (Span span : spans) {
			if (span.contains(i)) {
				return true;
			}
		}
		return false;
	}
}
