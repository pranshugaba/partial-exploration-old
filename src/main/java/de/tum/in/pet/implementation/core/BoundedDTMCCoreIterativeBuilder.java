package de.tum.in.pet.implementation.core;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.explorer.DefaultExplorer;
import de.tum.in.pet.explorer.Explorer;
import de.tum.in.pet.generator.DtmcGenerator;
import de.tum.in.pet.model.DTMC;
import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.sampler.AnnotatedModel;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;

public class BoundedDTMCCoreIterativeBuilder {
  private static final Logger logger =
      Logger.getLogger(BoundedDTMCCoreIterativeBuilder.class.getName());

  private final Explorer<State, DTMC> explorer;
  private final int stepBound;
  private final double precision;

  public BoundedDTMCCoreIterativeBuilder(ModelGenerator generator, int stepBound, double precision)
      throws PrismException {
    this.explorer = new DefaultExplorer<>(new DTMC(), new DtmcGenerator(generator));
    this.stepBound = stepBound;
    this.precision = precision;
  }

  public AnnotatedModel<DTMC> build() throws PrismException {
    logger.log(Level.INFO, () -> String.format("Building iterative core for step bound %d and "
        + "precision %g", stepBound, precision));

    long timer = System.nanoTime();

    for (int initialState : explorer.initialStates()) {
      double totalExitSum;

      boolean finished = false;
      while (!finished) {
        finished = true;

        Int2DoubleMap weight = new Int2DoubleOpenHashMap();
        Int2DoubleMap fringeWeight = new Int2DoubleOpenHashMap();
        weight.put(initialState, 1.0d);
        NatBitSet exploredNonZeroStates = NatBitSets.set();
        exploredNonZeroStates.set(initialState);

        totalExitSum = 0.0d;
        for (int step = 0; step <= stepBound; step++) {
          Int2DoubleMap newWeight = new Int2DoubleOpenHashMap(weight.size());
          NatBitSet newNonZeroStates = NatBitSets.set();

          IntIterator iterator = exploredNonZeroStates.iterator();
          while (iterator.hasNext()) {
            int state = iterator.nextInt();
            assert explorer.isExploredState(state);

            double stateWeight = weight.get(state);
            assert stateWeight > 0.0d;

            Distribution distribution = explorer.getChoices(state).get(0);
            for (Map.Entry<Integer, Double> transitionEntry : distribution) {
              int successor = transitionEntry.getKey();
              double probability = transitionEntry.getValue();
              assert probability > 0.0d;

              double transitionWeight = stateWeight * probability;
              if (transitionWeight == 0.0d) {
                // Could happen due to rounding errors
                continue;
              }

              if (explorer.isExploredState(successor)) {
                assert !fringeWeight.containsKey(successor);

                newWeight.merge(successor, transitionWeight, Double::sum);
                newNonZeroStates.set(successor);
              } else {
                assert !weight.containsKey(successor);

                double previousWeight = fringeWeight.remove(successor);
                double weightSum = transitionWeight + previousWeight;
                assert 0.0d < weightSum && weightSum <= 1.0d;

                if (weightSum > precision) {
                  explorer.exploreState(successor);
                  newNonZeroStates.set(successor);
                  newWeight.put(successor, weightSum);

                  totalExitSum -= previousWeight;
                } else {
                  fringeWeight.put(successor, weightSum);
                  totalExitSum += transitionWeight;
                }
              }
            }
          }
          weight = newWeight;
          exploredNonZeroStates = newNonZeroStates;
          assert exploredNonZeroStates.equals(weight.keySet());
          if (weight.isEmpty()) {
            assert exploredNonZeroStates.isEmpty();

            finished = false;
            break;
          }
        }

        if (totalExitSum > precision) {
          Queue<Integer> queue = new PriorityQueue<>(
              Comparator.comparingDouble(s -> -fringeWeight.get(s.intValue())));
          queue.addAll(fringeWeight.keySet());
          while (totalExitSum > precision && !queue.isEmpty()) {
            finished = false;
            int state = queue.poll();
            explorer.exploreState(state);
            totalExitSum -= fringeWeight.remove(state);
          }
        }
      }
    }

    timer = System.nanoTime() - timer;

    if (logger.isLoggable(Level.INFO)) {
      double secondsToNanoseconds = TimeUnit.SECONDS.toNanos(1L);
      String progressString = String
          .format("%n== Finished finite core (precision %g, step bound %d) ==%n"
                  + "  States: %d in partial model%n"
                  + "  Time: %f sec%n",
              precision, stepBound, explorer.exploredStateCount(), timer / secondsToNanoseconds);
      logger.info(progressString);
    }

    return new AnnotatedModel<>(explorer.model(), explorer::getState, explorer.exploredStates());
  }
}
