package org.spinrdf.inference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.compose.MultiUnion;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.spinrdf.system.SPINModuleRegistry;
import org.spinrdf.util.CommandWrapper;
import org.spinrdf.util.JenaUtil;
import org.spinrdf.util.SPINQueryFinder;
import org.spinrdf.vocabulary.SPIN;

/* Based on OWL2RLExample.java
 */

public class SPINInferenceCmd {

	public static void main(String[] args) {
		Options options = new Options();
		options.addOption(
				Option.builder("spin").argName("spin").hasArg().desc("input SPIN file)").required(true).build());
		options.addOption(Option.builder("data").argName("data").hasArg().desc("input data").required(true).build());
		options.addOption(Option.builder("out").argName("out").hasArg().desc("output path").required(true).build());
		options.addOption(
				Option.builder("verbose").argName("verbose").desc("print some output").required(false).build());

		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);

		} catch (ParseException exp) {
			System.err.println("ERROR: " + exp.getMessage());
			System.exit(1);
		}

		String data = line.getOptionValue("data");
		String spin = line.getOptionValue("spin");
		boolean verbose = line.hasOption("verbose");
		String out = line.getOptionValue("out");

		data = "file://" + new File(data).getAbsolutePath();
		spin = "file://" + new File(spin).getAbsolutePath();

		run(data, spin, verbose, out);
	}

	private static void run(String dataPath, String spinPath, boolean verbose, String out) {
		long start = System.nanoTime();

		// Initialize system functions and templates
		SPINModuleRegistry.get().init();

		// Load domain model with imports
//		System.out.println("Loading domain ontology...");
		OntModel queryModel = loadModelWithImports(dataPath);

		// Create and add Model for inferred triples
		Model newTriples = ModelFactory.createDefaultModel();
		queryModel.addSubModel(newTriples);

		// Load OWL RL library from the web
//		System.out.println("Loading OWL RL ontology...");
		OntModel owlrlModel = loadModelWithImports(spinPath);

		// Register any new functions defined in OWL RL
		SPINModuleRegistry.get().registerAll(owlrlModel, null);

		// Build one big union Model of everything
		MultiUnion multiUnion = JenaUtil.createMultiUnion(new Graph[] { queryModel.getGraph(), owlrlModel.getGraph() });
		Model unionModel = ModelFactory.createModelForGraph(multiUnion);

		// Collect rules (and template calls) defined in OWL RL
		long start_collect = System.nanoTime();
		
		Map<Resource, List<CommandWrapper>> cls2Query = SPINQueryFinder.getClass2QueryMap(unionModel, queryModel,
				SPIN.rule, true, false);
		Map<Resource, List<CommandWrapper>> cls2Constructor = SPINQueryFinder.getClass2QueryMap(queryModel, queryModel,
				SPIN.constructor, true, false);
		SPINRuleComparator comparator = new DefaultSPINRuleComparator(queryModel);

		long end_collect = System.nanoTime();
		double collect_time = ((double) (end_collect - start_collect) / 1000000000);
		
		if (verbose)
			System.out.println("# rules: " + cls2Query.values().stream().mapToInt(l -> l.size()).sum());

		SPINInferences.run(queryModel, newTriples, cls2Query, cls2Constructor, null, null, false, SPIN.rule, comparator,
				null);

		if (verbose) {
			System.out.println("# inferred: " + newTriples.size());
			System.out.println("\n");
		}

//		newTriples.write(System.out, "TTL");
		try {
			newTriples.write(new FileOutputStream(out), "TTL");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		long end = System.nanoTime();
		double total_time = ((double) (end - start) / 1000000000);

		System.out.println("collect rules: " + collect_time);
		System.out.println("execute spin: " + total_time);
	}

	private static OntModel loadModelWithImports(String url) {
		Model baseModel = ModelFactory.createDefaultModel();
		baseModel.read(url, null, "TTL");
		return JenaUtil.createOntologyModel(OntModelSpec.OWL_MEM, baseModel);
	}
}
