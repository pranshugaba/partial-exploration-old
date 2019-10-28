package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;

import de.tum.in.pet.util.annotation.Tuple;
import de.tum.in.pet.values.ValueInterpretation;
import de.tum.in.pet.values.ValueVerdict;
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

@Value.Immutable
@Tuple
public abstract class PrismQuery<R> {
  public abstract ExpressionTemporal expression();

  public abstract int lowerBound();

  public abstract int upperBound();

  public abstract QueryType<R> type();


  private static int parseBound(Expression expression) throws PrismLangException {
    try {
      return expression.evaluateInt();
    } catch (PrismLangException e) {
      double doubleBound = expression.evaluateDouble();
      //noinspection FloatingPointEquality
      if (Math.rint(doubleBound) != doubleBound) {
        throw e;
      }
      //noinspection NumericCastThatLosesPrecision
      return (int) doubleBound;
    }
  }

  public static PrismQuery<?> parse(Expression expression, Values values,
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

    int lowerBound = 0;
    if (expressionTemporal.getLowerBound() != null) {
      lowerBound = parseBound(expressionTemporal.getLowerBound());
      if (expressionTemporal.lowerBoundIsStrict()) {
        lowerBound += 1;
      }
    }

    int upperBound = Integer.MAX_VALUE;
    if (expressionTemporal.getUpperBound() != null) {
      upperBound = parseBound(expressionTemporal.getUpperBound());
      if (expressionTemporal.upperBoundIsStrict()) {
        upperBound -= 1;
      }
    }

    OpRelOpBound boundInfo = expressionQuant.getRelopBoundInfo(values);
    RelOp relOp = expressionQuant.getRelOp();

    ValueVerdict verdict;
    ValueUpdate updateType;
    switch (relOp) {
      case GT:
        verdict = new QualitativeVerdict(QualitativeQuery.GREATER_THAN, boundInfo.getBound());
        updateType = ValueUpdate.MIN_VALUE;
        break;
      case GEQ:
        verdict = new QualitativeVerdict(QualitativeQuery.GREATER_OR_EQUAL, boundInfo.getBound());
        updateType = ValueUpdate.MIN_VALUE;
        break;
      case MIN:
        verdict = new QuantitativeVerdict(precision, relativeError);
        updateType = ValueUpdate.MIN_VALUE;
        break;
      case LT:
        verdict = new QualitativeVerdict(QualitativeQuery.LESS_THAN, boundInfo.getBound());
        updateType = ValueUpdate.MAX_VALUE;
        break;
      case LEQ:
        verdict = new QualitativeVerdict(QualitativeQuery.LESS_OR_EQUAL, boundInfo.getBound());
        updateType = ValueUpdate.MAX_VALUE;
        break;
      case MAX:
        verdict = new QuantitativeVerdict(precision, relativeError);
        updateType = ValueUpdate.MAX_VALUE;
        break;
      case EQ:
        verdict = new QuantitativeVerdict(precision, relativeError);
        updateType = ValueUpdate.UNIQUE_VALUE;
        break;
      default:
        throw new AssertionError();
    }
    // TODO
    ValueInterpretation<?> interpretation = (ValueInterpretation<?>) verdict;

    QueryType<?> type = QueryType.of(verdict, updateType, interpretation);
    return PrismQueryTuple.create(expressionTemporal, lowerBound, upperBound, type);
  }


  public boolean isBounded() {
    return 0 < lowerBound() || upperBound() < Integer.MAX_VALUE;
  }

  @Override
  public String toString() {
    return String.format("%s[%s]%s", expression(), type(),
        isBounded() ? "[" + lowerBound() + ";" + upperBound() + "]" : "");
  }


  @Value.Check
  protected void check() {
    checkArgument(0 <= lowerBound());
    checkArgument(lowerBound() <= upperBound());
  }
}
