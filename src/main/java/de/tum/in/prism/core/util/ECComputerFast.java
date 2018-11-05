/*
 * Copyright (C) 2016  (Salomon Sickert)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tum.in.prism.core.util;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import explicit.NondetModel;
import explicit.SCCComputer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.IntPredicate;
import prism.PrismComponent;
import prism.PrismException;

public class ECComputerFast {
  private final PrismComponent prism;

  public ECComputerFast(PrismComponent prism) {
    this.prism = prism;
  }

  public List<MEC> computeMECs(NondetModel model, IntPredicate restriction) throws PrismException {
    return computeMECs(model, restriction, null);
  }

  /**
   * Computes maximal end components of a nondeterministic model such as an MDP.
   * The implementation uses a variation of the algorithm from p.48 of:
   * Luca de Alfaro. Formal Verification of Probabilistic Systems. Ph.D. thesis, Stanford
   * University (1997)
   *
   * @param model
   *     The model
   * @param preMECs
   *     if null all reachable states are considered.
   *
   * @return A list of all MECs obtained from refining the preMECs.
   */
  public List<MEC> computeMECs(NondetModel model, IntPredicate restriction, Collection<MEC> preMECs)
      throws PrismException {
    List<MEC> mecs = new ArrayList<>();
    Deque<MEC> workList = new ArrayDeque<>();

    if (preMECs == null) {
      NatBitSet bs = NatBitSets.boundedFilledSet(model.getNumStates());
      workList.add(MEC.createMEC(model, bs));
    } else {
      workList.addAll(preMECs);
    }

    SccMecConsumerStore consumer = new SccMecConsumerStore(model);
    List<MEC> refinedMECs = consumer.getPreMecs();

    while (!workList.isEmpty()) {
      MEC mec = workList.remove();
      refinedMECs.clear();

      SCCComputer sccComputer = SCCComputer.createSCCComputer(prism, model, consumer);
      sccComputer.computeSCCs(false, i -> mec.states.contains(i) && restriction.test(i));

      if (!refinedMECs.isEmpty()) {
        if (refinedMECs.size() == 1) {
          MEC refinedMEC = refinedMECs.get(0);
          assert !refinedMEC.states.isEmpty();
          if (mec.equals(refinedMEC)) {
            assert !mecs.contains(refinedMEC);
            mecs.add(refinedMEC);
            continue;
          }
        }
        workList.addAll(refinedMECs);
      }
    }

    return mecs;
  }
}
