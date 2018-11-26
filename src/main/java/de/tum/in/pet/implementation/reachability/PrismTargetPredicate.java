package de.tum.in.pet.implementation.reachability;

import parser.State;
import parser.ast.ExpressionTemporal;
import prism.PrismLangException;

public class PrismTargetPredicate implements TargetPredicate {
  private final ExpressionTemporal expression;

  public PrismTargetPredicate(ExpressionTemporal expression) {
    this.expression = expression;
  }

  public ExpressionTemporal expression() {
    return expression;
  }

  @Override
  public boolean isTargetState(State state) throws PrismLangException {
    return expression.getOperand2().evaluateBoolean(state);
  }

  @Override
  public String toString() {
    return expression.toString();
  }
}
