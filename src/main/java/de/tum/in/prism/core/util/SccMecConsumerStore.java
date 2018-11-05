package de.tum.in.prism.core.util;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import explicit.NondetModel;
import explicit.SCCConsumer;
import java.util.ArrayList;
import java.util.List;

class SccMecConsumerStore implements SCCConsumer {
  private final NondetModel model;
  private final List<MEC> preMecs;
  private NatBitSet currentSCC;

  public SccMecConsumerStore(NondetModel model) {
    this.model = model;
    this.preMecs = new ArrayList<>();
  }

  public List<MEC> getPreMecs() {
    return preMecs;
  }

  @Override
  public void notifyStartSCC() {
    currentSCC = NatBitSets.set();
  }

  @Override
  public void notifyStateInSCC(int stateIndex) {
    assert currentSCC != null;
    currentSCC.set(stateIndex);
  }

  @Override
  public void notifyEndSCC() {
    NatBitSet preMecStates = currentSCC;
    currentSCC = null;

    if (preMecStates.isEmpty()) {
      return;
    }
    MEC preMec = MEC.createMEC(model, preMecStates);
    if (preMec.states.isEmpty()) {
      return;
    }
    assert !preMecs.contains(preMec);
    preMecs.add(preMec);
  }
}
