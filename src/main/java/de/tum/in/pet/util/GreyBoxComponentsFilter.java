package de.tum.in.pet.util;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.probmodels.explorer.GreyExplorer;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Model;
import parser.State;

import java.util.List;
import java.util.stream.Collectors;

public class GreyBoxComponentsFilter {

    private static GreyExplorer<State, Model> greyExplorer = null;

    public static void setGreyExplorer(GreyExplorer<State, Model> explorer) {
        greyExplorer = explorer;
    }

    /**
     * Before calling this method, make sure to set grey explorer
     *
     * @param components A list of components, each represented as a set of states
     * @return Components that does not have any state,action pair, that has been partially explored
     */
    public static List<NatBitSet> filterOutUnExploredComponents(List<NatBitSet> components) {
        return components.stream()
                .filter(GreyBoxComponentsFilter::isComponentExplored)
                .collect(Collectors.toList());
    }

    /**
     * Converts the given set of states to a model.
     * Then checks whether the components have been fully explored.
     */
    private static boolean isComponentExplored(NatBitSet componentStates) {
        // The set of states, needs to be converted to model. Because, the model in the explorer, might contain actions
        // that may not be a part of Mec. We only check actions, that belong to Mec.
        Mec componentModel = getModelForComponent(componentStates);
        return isComponentExplored(componentModel);
    }

    /**
     * Checks whether there are any partially explored actions, in each state of the component.
     * Doesn't check every action of a state has been explored, but checks no action has been partially explored.
     *
     * @param componentModel Model that represents the states and actions of the component
     * @return TRUE, if there are no partially explored states in the component.
     */
    private static boolean isComponentExplored(Mec componentModel) {
        return componentModel.states
                .stream()
                .allMatch(state -> isStateExplored(state, componentModel));
    }

    private static Mec getModelForComponent(NatBitSet componentStates) {
        return Mec.create(greyExplorer.model(), componentStates);
    }

    private static boolean isStateExplored(int stateId, Mec componentModel) {
        // For every explored action of s, we check all of it's successors has been visited at-least once.
        return componentModel.actions.get(stateId)
                .stream()
                .filter(actionIndex -> isNotEmptyDistribution(stateId, actionIndex, componentModel))
                .allMatch(actionIndex -> greyExplorer.isStateActionExplored(stateId, actionIndex));
    }

    private static boolean isNotEmptyDistribution(int stateId, int actionIndex, Mec componentModel) {
        Action action = greyExplorer.getActions(stateId).get(actionIndex);
        return action.distribution().size() > 0;
    }
}
