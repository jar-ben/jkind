package jkind.api.extractor;

import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		if (args.length != 1) {
			System.err.println("Usage: extractor <filename.lus>");
			System.exit(-1);
		}
		String filename = args[0];
		Program program = parseLustre(new ANTLRFileStream(filename));
		ExtractorVisitor visitor = new ExtractorVisitor();
		program = visitor.visit(program);
		JKindResult result = runJKind(program);
		reportResults(filename, program, visitor.getLocationMap(), result);
	}

	private static Program parseLustre(CharStream stream) throws Exception {
		LustreLexer lexer = new LustreLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		LustreParser parser = new LustreParser(tokens);
		ProgramContext program = parser.program();
		if (parser.getNumberOfSyntaxErrors() > 0) {
			System.exit(-1);
		}
		return new LustreToEAstVisitor().program(program);
	}

	private static JKindResult runJKind(Program program) {
		JKindResult result = new JKindResult("results");
		JKindApi api = new JKindApi();
		api.setIvcReduction();
		api.execute(program, result, new NullProgressMonitor());
		return result;
	}

	private static void reportResults(String filename, Program program,
			Map<String, ELocation> locationMap, JKindResult result) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename + ".html"))) {
			writer.write("<html>\n");
			writeHeader(writer);
			writer.write("<body>\n");

			for (PropertyResult pr : result.getPropertyResults()) {
				if (!(pr.getProperty() instanceof ValidProperty)) {
					writer.write("<p class='not-valid'>" + pr.getProperty().getName()
							+ " is invalid/unknown</p>\n");
				}
			}

			for (PropertyResult pr : result.getPropertyResults()) {
				if (pr.getProperty() instanceof ValidProperty) {
					ValidProperty vp = (ValidProperty) pr.getProperty();
					List<String> allIvcs = getAllIvcs(program);

					List<ELocation> locations = getUnusedLocations(locationMap, allIvcs,
							vp.getIvc());

					int total = allIvcs.size();
					int covered = total - locations.size();
					String stats = String.format("%s: %.1f%% (%d of %d covered)", vp.getName(),
							100.0 * covered / total, covered, total);
					writer.write("<div class='valid'>\n");
					writer.write("<div class='stats'>" + stats + "</div>\n");
					displayLocations(writer, filename, locations);
					writer.write("</div>\n");
				}
			}

			writer.write("</body>\n");
			writer.write("</html>\n");
		}
	}

	private static List<ELocation> getUnusedLocations(Map<String, ELocation> locationMap,
			List<String> allIvcs, Set<String> usedIvcs) {
		Set<String> baseUsedIvcs = new HashSet<>();
		for (String ivc : usedIvcs) {
			if (ivc.contains(".")) {
				ivc = ivc.substring(ivc.lastIndexOf(".") + 1);
			}
			baseUsedIvcs.add(ivc);
		}

		List<ELocation> result = new ArrayList<>();
		for (String ivc : allIvcs) {
			if (!baseUsedIvcs.contains(ivc)) {
				result.add(locationMap.get(ivc));
			}
		}
		return result;
	}

	private static List<String> getAllIvcs(Program program) {
		return program.nodes.stream().flatMap(n -> n.ivc.stream()).collect(toList());
	}

	private static void writeHeader(BufferedWriter writer) throws IOException {
		String filename = "/jkind/api/extractor/header.html";
		try (InputStream stream = Extractor.class.getResourceAsStream(filename)) {
			int c;
			while ((c = stream.read()) != -1) {
				writer.write((char) c);
			}
		}
	}

	private static void displayLocations(BufferedWriter writer, String filename,
			List<ELocation> locations) throws Exception {
		int i = 0;

		writer.write("<div class='lustre'>\n");
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			int c;
			while ((c = reader.read()) != -1) {
				String prefix = "";
				String suffix = "";
				if (contains(locations, i)) {
					prefix = "<span class='unused'>";
					suffix = "</span>";
				}
				String middle = String.valueOf((char) c);
				if (c == ' ') {
					middle = "&nbsp;";
				}
				writer.write(prefix + middle + suffix);
				if (c == '\n') {
					writer.write("<br>\n");
				}
				i++;
			}
		}
		writer.write("</div>\n");
	}

	private static boolean contains(List<ELocation> locations, int i) {
		return locations.stream().anyMatch(loc -> loc.start <= i && i <= loc.stop);
	}
}
