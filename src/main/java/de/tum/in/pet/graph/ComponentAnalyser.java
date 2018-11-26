package de.tum.in.pet.graph;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.model.CollapseModel;
import java.util.List;

@FunctionalInterface
public interface ComponentAnalyser {
  List<NatBitSet> findComponents(CollapseModel<?> model, NatBitSet states);
}
