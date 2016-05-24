package jkind;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jkind.analysis.LinearChecker;
import jkind.analysis.StaticAnalyzer;
import jkind.engines.Director;
import jkind.lustre.Node;
import jkind.lustre.Program;
import jkind.lustre.builders.NodeBuilder;
import jkind.translation.InlineSimpleEquations;
import jkind.translation.Specification;
import jkind.translation.Translate;
import jkind.util.Util;

public class JKind {
	public static void main(String[] args) {
		try {
			JKindSettings settings = JKindArgumentParser.parse(args);
			Program program = Main.parseLustre(settings.filename);

			StaticAnalyzer.check(program, settings.solver);
			if (!LinearChecker.isLinear(program)) {
				if (settings.pdrMax > 0) {
					Output.warning("disabling PDR due to non-linearities");
					settings.pdrMax = 0;
				}
			}

			Node main = Translate.translate(program);
			main = setSupport(main, getAllAssigned(main));
			//int initialNumEq = main.equations.size();
			Specification userSpec = new Specification(main);
			Specification analysisSpec = getAnalysisSpec(userSpec, settings);
			
			/**
			 * these comments are related to calculating number of equations in the models
			**/
			/*  String xmlFilename = settings.filename + "_NUMEQ.xml";
			    try (PrintWriter out = new PrintWriter(new FileOutputStream(xmlFilename))) {
				out.println("<?xml version=\"1.0\"?>");
				out.println("<Results xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"); 
				out.println("    <InitialNumberOfEqs>" + initialNumEq + "</InitialNumberOfEqs>");
				out.println("    <SlicedNumberOfEqs>" + userSpec.node.equations.size() + "</SlicedNumberOfEqs>");
				out.println("</Results>");
			
			} catch (Throwable t) {
			t.printStackTrace();
			System.exit(ExitCodes.UNCAUGHT_EXCEPTION);
			}
			System.exit(0);
			*/
			new Director(settings, userSpec, analysisSpec).run();
			System.exit(0); // Kills all threads
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(ExitCodes.UNCAUGHT_EXCEPTION);
		}
	}

	private static List<String> getAllAssigned(Node node) {
		List<String> result = new ArrayList<>();
		result.addAll(Util.getIds(node.locals));
		result.addAll(Util.getIds(node.outputs));
		return result;
	}

	private static Node setSupport(Node node, List<String> newSupport) {
		return new NodeBuilder(node).clearSupport().addSupports(newSupport).build();
	}

	private static Specification getAnalysisSpec(Specification userSpec, JKindSettings settings) {
		if (settings.inline) {
			Node inlined = InlineSimpleEquations.node(userSpec.node);
			return new Specification(inlined);
		} else {
			return userSpec;
		}
	}
}
