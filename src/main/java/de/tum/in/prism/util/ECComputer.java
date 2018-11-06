package de.tum.in.prism.util;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class ECComputer {
  public static List<MEC> computeMECs(Model model, NatBitSet restriction) {
    List<MEC> mecs = new ArrayList<>();
    Deque<MEC> workList = new ArrayDeque<>();
    workList.add(MEC.createMEC(model, restriction));

    while (!workList.isEmpty()) {
      MEC mec = workList.remove();
      assert restriction.containsAll(mec.states);

      Int2ObjectFunction<IntIterable> successorFunction = state -> {
        assert mec.states.contains(state) && restriction.contains(state);

        List<Distribution> choices = model.getChoices(state);
        if (choices == null) {
          return IntSets.EMPTY_SET;
        }
        IntSet allowedActions = mec.actions.get(state);
        if (allowedActions == null || allowedActions.isEmpty()) {
          return IntSets.EMPTY_SET;
        }
        if (allowedActions.size() == 1) {
          return choices.get(allowedActions.iterator().nextInt()).getSupport();
        }
        NatBitSet union = NatBitSets.set();
        allowedActions.forEach((int index) -> union.or(choices.get(index).getSupport()));
        return union;
      };

      List<MEC> preMecs = SccDecomposition.computeSccs(successorFunction, mec.states, true)
          .stream()
          .map(scc -> MEC.createMEC(model, scc))
          .filter(m -> !m.states.isEmpty())
          .collect(Collectors.toList());

      if (preMecs.size() == 1) {
        MEC refinedMEC = preMecs.get(0);
        assert !refinedMEC.states.isEmpty();
        if (mec.equals(refinedMEC)) {
          assert !mecs.contains(refinedMEC);
          mecs.add(refinedMEC);
          continue;
        }
      }
      workList.addAll(preMecs);
    }

    return mecs;
  }
}
