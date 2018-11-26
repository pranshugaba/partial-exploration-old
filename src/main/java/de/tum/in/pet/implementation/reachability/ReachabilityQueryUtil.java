package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.pet.values.StateInterpretation;
import de.tum.in.pet.values.StateVerdict;
import parser.Values;
import parser.ast.Expression;
import parser.ast.ExpressionProb;
import parser.ast.ExpressionQuant;
import parser.ast.ExpressionTemporal;
import parser.ast.RelOp;
import prism.OpRelOpBound;
import prism.PrismException;
import prism.PrismLangException;

public final class ReachabilityQueryUtil {
  private ReachabilityQueryUtil() {}

  public static ReachabilityQuery create(Expression expression, Values values, double precision,
      boolean relativeError)
      throws PrismException {
    checkArgument(expression instanceof ExpressionProb,
        "Could not construct a predicate from %s.", expression);
    ExpressionQuant expressionQuant = (ExpressionQuant) expression;
    try {
      checkArgument(expressionQuant.getExpression().isSimplePathFormula(),
          "Property %s is not a simple path formula", expression);
    } catch (PrismLangException e) {
      throw new IllegalArgumentException(e);
    }
    checkArgument(expressionQuant.getExpression() instanceof ExpressionTemporal,
        "Property %s is not a temporal formula", expression);

    ExpressionTemporal expressionTemporal =
        (ExpressionTemporal) expressionQuant.getExpression();

    checkArgument(expressionTemporal.getOperator() == ExpressionTemporal.P_F,
        "Property %s is not an F formula", expression);
    OpRelOpBound boundInfo = expressionQuant.getRelopBoundInfo(values);

    RelOp relOp = expressionQuant.getRelOp();
    StateVerdict verdict;
    ValueUpdateType updateType;
    switch (relOp) {
      case GT:
        verdict = new QualitativeVerdict(QualitativeQuery.GREATER_THAN, boundInfo.getBound());
        updateType = ValueUpdateType.MIN_VALUE;
        break;
      case GEQ:
        verdict = new QualitativeVerdict(QualitativeQuery.GREATER_OR_EQUAL, boundInfo.getBound());
        updateType = ValueUpdateType.MIN_VALUE;
        break;
      case MIN:
        verdict = new QuantitativeVerdict(precision, relativeError);
        updateType = ValueUpdateType.MIN_VALUE;
        break;
      case LT:
        verdict = new QualitativeVerdict(QualitativeQuery.LESS_THAN, boundInfo.getBound());
        updateType = ValueUpdateType.MAX_VALUE;
        break;
      case LEQ:
        verdict = new QualitativeVerdict(QualitativeQuery.LESS_OR_EQUAL, boundInfo.getBound());
        updateType = ValueUpdateType.MAX_VALUE;
        break;
      case MAX:
        verdict = new QuantitativeVerdict(precision, relativeError);
        updateType = ValueUpdateType.MAX_VALUE;
        break;
      case EQ:
        verdict = new QuantitativeVerdict(precision, relativeError);
        updateType = ValueUpdateType.UNIQUE_VALUE;
        break;
      default:
        throw new AssertionError();
    }

    PrismTargetPredicate predicate = new PrismTargetPredicate(expressionTemporal);
    return ReachabilityQueryTuple.create(predicate, updateType, verdict,
        (StateInterpretation) verdict);
  }
}
