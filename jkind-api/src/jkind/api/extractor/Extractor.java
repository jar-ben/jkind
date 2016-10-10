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
		System.out.println("<html>");
		printHeader();
		System.out.println("<body>");

		for (PropertyResult pr : result.getPropertyResults()) {
			if (!(pr.getProperty() instanceof ValidProperty)) {
				System.out.println("<p class='not-valid'>Property " + pr.getProperty().getName()
						+ " is invalid/unknown</p>");
			}
		}

		for (PropertyResult pr : result.getPropertyResults()) {
			if (pr.getProperty() instanceof ValidProperty) {
				ValidProperty vp = (ValidProperty) pr.getProperty();
				List<String> unused = new ArrayList<>(program.getMainNode().ivc);
				unused.removeAll(vp.getIvc());
				List<Span> spans = convertToSpans(unused);

				int total = program.getMainNode().ivc.size();
				int covered = total - spans.size();
				System.out.printf("<div class='valid'><h3>%s - coverage %d of %d = %.1f%%</h3>",
						vp.getName(), covered, total, 100.0 * covered / total);
				System.out.println();
				displaySpans(filename, spans);
				System.out.println("</div>");
			}
		}

		System.out.println("</body>");
		System.out.println("</html>");
	}

	private static void printHeader() {
		System.out.println("<head>");
		System.out.println("<style>");
		System.out.println(".not-valid {");
		System.out.println("  font-family: monospace;");
		System.out.println("  background-color: lightcoral;");
		System.out.println("  margin: 5px;");
		System.out.println("  padding: 5px;");
		System.out.println("}");
		System.out.println("");
		System.out.println(".valid {");
		System.out.println("  font-family: monospace;");
		System.out.println("  margin: 20px 5px 5px 5px;");
		System.out.println("  padding: 5px;");
		System.out.println("}");
		System.out.println("");
		System.out.println(".lustre {");
		System.out.println("  font-family: monospace;");
		System.out.println("  background-color: linen;");
		System.out.println("  margin: 5px 5px 30px 5px;");
		System.out.println("  padding: 5px;");
		System.out.println("}");
		System.out.println("");
		System.out.println(".lustre .unused {");
		System.out.println("  color: #B3B3B3;");
		System.out.println("}");
		System.out.println("</style>");
		System.out.println("</head>");
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
		int i = 0;

		System.out.println("<div class='lustre'>");
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			int c;
			while ((c = reader.read()) != -1) {
				String prefix = "";
				String suffix = "";
				if (contains(spans, i)) {
					prefix = "<span class='unused'>";
					suffix = "</span>";
				}
				String middle = String.valueOf((char) c);
				if (c == ' ') {
					middle = "&nbsp;";
				}
				System.out.print(prefix + middle + suffix);
				if (c == '\n') {
					System.out.println("<br>");
				}
				i++;
			}
		}
		System.out.println("</div>");
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
