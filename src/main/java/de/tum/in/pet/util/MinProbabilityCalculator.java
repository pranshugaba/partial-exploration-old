package de.tum.in.pet.util;

import de.tum.in.pet.Input.DefaultInputValues;
import de.tum.in.pet.Main;
import de.tum.in.pet.implementation.meanPayoff.MeanPayoffChecker;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.explorer.Explorers;
import de.tum.in.probmodels.explorer.InformationLevel;
import de.tum.in.probmodels.generator.Generator;
import de.tum.in.probmodels.generator.MdpGenerator;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import de.tum.in.probmodels.util.PrismHelper;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import parser.State;
import parser.ast.ModulesFile;
import prism.*;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

// Here we also compute the maximum successors of a model
public class MinProbabilityCalculator {
  private static final Logger logger = Logger.getLogger(MeanPayoffChecker.class.getName());



  private static double findPMinMDP(Explorer<?, ?> explorer) {

    IntSet exploredStates = new IntOpenHashSet(explorer.exploredStates());
    IntSet newExploredStates = new IntOpenHashSet(exploredStates);

    final double[] pMin = {1};

    int numStates = 1000000;
    int printCount = 1;
    final int[] maxSuccessorsInModel = {-1};

    while (newExploredStates.size()!=0) {

      if (explorer.exploredStateCount() >= (printCount * numStates)) {
        logger.log(Level.INFO, "PMin is " + pMin[0]);
        printCount += 1;
      }

      exploredStates.forEach((IntConsumer) s -> {
        List<Distribution> choices = explorer.getChoices(s);
        IntSet neighbours = new IntArraySet();
        choices.forEach(d -> {
          neighbours.addAll(d.support());
        });

        for(Distribution choice: choices) {
          if (choice.support().size() > maxSuccessorsInModel[0]) {
            maxSuccessorsInModel[0] = choice.support().size();
          }

          for(int succ: choice.support()) {
            if (choice.get(succ) < pMin[0]) {
              pMin[0] = choice.get(succ);
            }
          }
        }

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

    logger.log(Level.INFO, "Execution finished with pMin " + pMin[0]);
    System.out.println("MaxSuccessors in model is " + maxSuccessorsInModel[0]);

    return pMin[0];

  }

  public static double solve(ModelGenerator generator)
          throws PrismException {
    ModelType modelType = generator.getModelType();
    switch (modelType) {
      case MDP:
        return solveMdp(generator);
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

  public static double solveMdp(ModelGenerator prismGenerator){

    MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
    Generator<State> generator = new MdpGenerator(prismGenerator);

    var explorer = Explorers.getExplorer(partialModel, generator,
            InformationLevel.WHITEBOX, false, System.currentTimeMillis() + DefaultInputValues.TIMEOUT);

    return findPMinMDP(explorer);

  }

  public static void main(String[] args) throws PrismException, IOException {
    Option modelOption = new Option("m", "model", true, "Path to model file");
    Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
    modelOption.setRequired(true);

    Options options = new Options()
            .addOption(modelOption)
            .addOption(constantsOption);


    CommandLine commandLine = CliHelper.parse(options, args);

    PrismHelper.PrismParseResult parse =
            Main.parse(commandLine, modelOption, null, constantsOption);
    ModulesFile modulesFile = parse.modulesFile();

    Prism prism = new Prism(new PrismDevNullLog());

    ModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);

    double pMin = solve(generator);

    System.out.println(pMin);

  }

}
