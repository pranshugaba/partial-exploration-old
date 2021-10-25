package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.RoaringNatBitSetFactory;
import de.tum.in.pet.Main;
import de.tum.in.pet.util.CliHelper;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.DefaultExplorer;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.Generator;
import de.tum.in.probmodels.generator.MdpGenerator;
import de.tum.in.probmodels.generator.PrismRewardGenerator;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.ComponentAnalyser;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.model.*;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.util.PrismHelper;
import it.unimi.dsi.fastutil.ints.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.State;
import parser.ast.*;
import prism.*;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/* This code's purpose is to facilitate the independent testing of the RestrictedValueIterator. Right now, this code
* accepts only 3 parameters, -m/--model (model file path) --precision (precision required from vi) --const
* defining constants in models, if any. Right now, the file only supports MDPs having singular initial states and
* runs the VI for the first random mec it chooses. To really test your code, it is advised to use models where
* all states in a model belong to a single mec. */


public class RestrictedValueIteratorChecker {
  private static final Logger logger = Logger.getLogger(RestrictedValueIteratorChecker.class.getName());

  private RestrictedValueIteratorChecker(){

  }

  // This function makes sure the entire model is explored so that the model is fully built.
  private static void exploreFullModel(Explorer<?, ?> explorer) {

    IntSet exploredStates = new IntOpenHashSet(explorer.exploredStates());
    IntSet newExploredStates = new IntOpenHashSet(exploredStates);

    while (newExploredStates.size()!=0) {
      exploredStates.forEach((IntConsumer) s -> {
        List<Distribution> choices = explorer.getChoices(s);
        IntSet neighbours = new IntArraySet();
        choices.forEach(d -> neighbours.addAll(d.support()));

        for(int n: neighbours)
          if (!explorer.isExploredState(n)){
            try {
              explorer.exploreState(n);
            } catch (PrismException e) {
              e.printStackTrace();
            }
          }
      });

      newExploredStates = new IntOpenHashSet(explorer.exploredStates());
      newExploredStates.removeAll(exploredStates);
      exploredStates = new IntOpenHashSet(explorer.exploredStates());
    }

  }

  // Providing general solve function outside. This function calls appropriate function for model type.
  private static Bounds solve(ModelGenerator generator, double precision) {
    ModelType modelType = generator.getModelType();
    switch (modelType) {
      case MDP:
        return solveMdp(generator, precision);
      case CTMC:
      case DTMC:
      case LTS:
      case CTMDP:
      case PTA:
      case STPG:
      case SMG:
      default:
        throw new UnsupportedOperationException();
    }
  }

  // General solving code.
  private static <S, M extends Model> Bounds solve(M partialModel, Generator<S> generator, ComponentAnalyser analyser, RewardGenerator<S> rewardGenerator, double precision) {

    var explorer = DefaultExplorer.of(partialModel, generator, false);
    exploreFullModel(explorer);

    Int2ObjectFunction<S> stateIndexMap = explorer::getState;

    M model = explorer.model();

    List<NatBitSet> components = analyser.findComponents(model, explorer.exploredStates());
    if (components.isEmpty()){
      logger.log(Level.SEVERE, "No mecs found, exiting");
      System.exit(0);
    }

    NatBitSet component = components.get(0);

    Mec mec = Mec.create(model, component);

    RestrictedMecBoundedValueIterator<S> valueIterator = new RestrictedMecBoundedValueIterator<>(mec, precision, rewardGenerator, stateIndexMap, Double.MAX_VALUE);
    valueIterator.setConfidenceWidthFunction(x -> (y -> 0.01));
    valueIterator.setDistributionFunction(x -> (y -> model.getActions(x).get(y).distribution()));
    valueIterator.setLabelFunction(x -> (y -> model.getActions(x).get(y).label()));
    valueIterator.run();
    Bounds bounds = valueIterator.getBounds();
    if(bounds==null){
      logger.log(Level.WARNING, "Value Iterator returns null bounds.");
      bounds = Bounds.unknown();
    }

    return bounds;

  }

  // MDP specific loading operations
  private static Bounds solveMdp(ModelGenerator prismGenerator, double precision) {

    MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
    ComponentAnalyser componentAnalyser = new MecComponentAnalyser();
    Generator<State> generator = new MdpGenerator(prismGenerator);

    RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(0, prismGenerator);

    return solve(partialModel, generator, componentAnalyser, rewardGenerator, precision);
  }

  public static void main(String... args) throws IOException, PrismException{
    Option precisionOption = new Option(null, "precision", true, "Precision");
//    Option heuristicOption = CliHelper.getDefaultHeuristicOption();
    Option modelOption = new Option("m", "model", true, "Path to model file");
//    Option propertiesOption = new Option("p", "properties", true, "Path to properties file");
//    Option propertyNameOption = new Option(null, "property", true, "Name of property to check");
    Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
//    Option expectedValuesOption = new Option(null, "expected", true,
//            "Comma separated list of the true values of the properties");
//    Option onlyPrintResultOption = new Option(null, "only-result", false,
//            "Only print result");
//    Option relativeErrorOption = new Option(null, "relative-error", false,
//            "Use relative error estimate");

    modelOption.setRequired(true);
    precisionOption.setRequired(true);

    Options options = new Options()
            .addOption(precisionOption)
            .addOption(modelOption)
            .addOption(constantsOption);

    CommandLine commandLine = CliHelper.parse(options, args);

    double precision = commandLine.hasOption(precisionOption.getLongOpt())
            ? Double.parseDouble(commandLine.getOptionValue(precisionOption.getLongOpt()))
            : Main.DEFAULT_PRECISION;

    NatBitSets.setFactory(new RoaringNatBitSetFactory());

    PrismHelper.PrismParseResult parse =
            Main.parse(commandLine, modelOption, null, constantsOption);
    ModulesFile modulesFile = parse.modulesFile();

    Prism prism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);

    Bounds bounds = solve(generator, precision);

    System.out.println(bounds);

  }

}
