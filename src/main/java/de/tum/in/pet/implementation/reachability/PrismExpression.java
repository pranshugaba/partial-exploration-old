package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.pet.util.annotation.Tuple;
import de.tum.in.pet.values.StateInterpretation;
import de.tum.in.pet.values.StateVerdict;
import org.immutables.value.Value;
import parser.Values;
import parser.ast.Expression;
import parser.ast.ExpressionProb;
import parser.ast.ExpressionQuant;
import parser.ast.ExpressionTemporal;
import parser.ast.RelOp;
import prism.OpRelOpBound;
import prism.PrismException;
import prism.PrismLangException;

// TODO Connection with target predicate?

@Value.Immutable
@Tuple
public abstract class PrismExpression<R> {
  public abstract ExpressionTemporal expression();

  public abstract StateVerdict verdict();

  public abstract ValueUpdateType updateType();

  public abstract StateInterpretation<R> interpretation();


  public static PrismExpression parse(Expression expression, Values values,
      double precision, boolean relativeError) throws PrismException {
    checkArgument(expression instanceof ExpressionProb,
        "Could not construct a predicate from %s.", expression);
    ExpressionQuant expressionQuant = (ExpressionQuant) expression;
    try {
      checkArgument(expressionQuant.getExpression().isSimplePathFormula(),
          "Property %s is not a simple path formula", expression);
    } catch (PrismLangException e) {
      throw new IllegalArgumentException("Internal PRISM error", e);
    }
    checkArgument(expressionQuant.getExpression() instanceof ExpressionTemporal,
        "Property %s is not a temporal formula", expression);

    ExpressionTemporal expressionTemporal =
        (ExpressionTemporal) expressionQuant.getExpression();
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
    // TODO
    StateInterpretation<?> interpretation = (StateInterpretation<?>) verdict;
    return PrismExpressionTuple.create(expressionTemporal, verdict, updateType, interpretation);
  }


  @Override
  public String toString() {
    return "[" + updateType() + "/" + verdict() + "]";
  }
}
