package de.tum.in.pet.util;

import static de.tum.in.probmodels.util.PrismHelper.scale;
import static de.tum.in.probmodels.util.Util.isEqual;

import de.tum.in.naturals.set.BoundedNatBitSet;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.values.unbounded.StateValueFunction;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import de.tum.in.probmodels.model.Model;
import explicit.MDPSimple;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.IntPredicate;

public final class ModelHelper {
  private ModelHelper() {
  }

  public static void modelWithBoundsToDotFile(String filename,
      Model model, Explorer<?, ?> explorer, StateValueFunction values, IntPredicate stateFilter,
      IntPredicate highlight) {
    modelWithBoundsToDotFile(filename, new AnnotatedModel<>(model, explorer::getState,
        explorer.exploredStates()), values, stateFilter, highlight);
  }

  public static void modelWithBoundsToDotFile(String filename,
      AnnotatedModel<? extends Model> annotatedModel, StateValueFunction values,
      IntPredicate stateFilter, IntPredicate highlight) {
    Model model = annotatedModel.model;
    NatBitSet exploredStates = annotatedModel.exploredStates;

    StringBuilder dotString = new StringBuilder("digraph Model {\n\tnode [shape=box];\n");

    for (int state = 0; state < model.getNumStates(); state++) {
      if (!stateFilter.test(state)) {
        continue;
      }

      dotString.append(state).append(" [style=filled fillcolor=\"");
      boolean appendBounds;
      if (highlight != null && highlight.test(state)) {
        dotString.append("#22CC22");
        appendBounds = true;
      } else if (model.isInitialState(state)) {
        dotString.append("#9999CC");
        appendBounds = true;
      } else if (!exploredStates.contains(state)) {
        dotString.append("#CC2222");
        appendBounds = false;
      } else if (values.isZeroDifference(state)) {
        dotString.append("#CD9D87");
        appendBounds = true;
      } else {
        dotString.append("#DDDDDD");
        appendBounds = true;
      }
      dotString.append("\",label=\"").append(state);
      if (appendBounds) {
        dotString.append(' ').append(values.bounds(state));
      }
      dotString.append("\"];\n");

      int actionIndex = 0;
      for (Action action : model.getActions(state)) {
        Object actionLabel = action.label();
        if (action.distribution().size() == 1) {
          IntIterator iterator = action.distribution().support().iterator();
          int successor = iterator.nextInt();
          assert !iterator.hasNext();

          dotString.append(state).append(" -> ").append(successor);
          if (actionLabel != null) {
            dotString.append("[label=\"").append(actionLabel).append("\"]");
          }
          dotString.append(";\n");
        } else {
          String actionNode = "a" + state + '_' + actionIndex;

          dotString.append(state).append(" -> ").append(actionNode)
              .append(" [arrowhead=none,label=\"").append(actionIndex);
          if (actionLabel != null) {
            dotString.append(':').append(actionLabel);
          }

          dotString.append("\" ];\n");
          dotString.append(actionNode).append(" [shape=point,height=0.1];\n");

          action.distribution().forEach((target, probability) -> {
            /* if (!stateFilter.test(target)) {
              return;
            } */
            dotString.append(actionNode).append(" -> ").append(target);
            if (!isEqual(probability, 1.0d)) {
              dotString.append(" [label=\"").append(String.format("%.3f", probability))
                  .append("\"]");
            }
            dotString.append(";\n");
          });
        }
        actionIndex += 1;
      }
    }
    dotString.append("}\n");
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename),
        StandardCharsets.UTF_8)) {
      writer.append(dotString.toString());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static RestrictedMdp buildRestrictedModel(MarkovDecisionProcess mdp, IntSet states) {
    MDPSimple restrictedModel = new MDPSimple();
    int[] originalToRestrictedStates = new int[mdp.getNumStates()];
    Arrays.fill(originalToRestrictedStates, -1);
    states.forEach((int allowedState) -> originalToRestrictedStates[allowedState] = restrictedModel
        .addState());

    int restrictedStates = restrictedModel.getNumStates();
    assert restrictedStates == states.size();
    int[] restrictedToOriginalStates = new int[restrictedStates];
    NatBitSet[] restrictedActions = new NatBitSet[restrictedStates];

    for (int originalState = 0; originalState < mdp.getNumStates(); originalState++) {
      if (originalToRestrictedStates[originalState] == -1) {
        continue;
      }
      int restrictedState = originalToRestrictedStates[originalState];
      restrictedToOriginalStates[restrictedState] = originalState;

      int numChoices = mdp.getNumChoices(originalState);
      BoundedNatBitSet removedActions = NatBitSets.boundedSet(numChoices, 0);
      int addedActions = 0;
      for (int choiceIndex = 0; choiceIndex < numChoices; choiceIndex++) {
        Distribution distribution = new Distribution();
        mdp.forEachTransition(originalState, choiceIndex, (__, t, p) -> {
          int restrictedDestination = originalToRestrictedStates[t];
          if (restrictedDestination >= 0) {
            distribution.add(restrictedDestination, p);
          }
        });
        if (distribution.isEmpty()) {
          removedActions.set(choiceIndex);
          continue;
        }
        explicit.Distribution scaledDistribution = scale(distribution);
        restrictedModel.addActionLabelledChoice(restrictedState, scaledDistribution,
            mdp.getAction(originalState, choiceIndex));
        addedActions += 1;
      }
      restrictedActions[restrictedState] = NatBitSets.compact(removedActions.complement());
      if (addedActions == 0) {
        restrictedModel.addDeadlockState(restrictedState);
      }
    }
    mdp.getInitialStates().forEach((int initialState) -> {
      int restrictedInitialState = originalToRestrictedStates[initialState];
      if (restrictedInitialState != -1) {
        restrictedModel.addInitialState(restrictedInitialState);
      }
    });

    // CSOFF: Indentation
    return new RestrictedMdp(restrictedModel, i -> restrictedToOriginalStates[i],
        i -> restrictedActions[i]);
    // CSON: Indentation
  }
}
