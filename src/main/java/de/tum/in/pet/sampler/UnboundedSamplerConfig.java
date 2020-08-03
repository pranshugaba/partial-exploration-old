package de.tum.in.pet.sampler;

import org.immutables.value.Value;

@Value.Immutable(builder = true)
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class UnboundedSamplerConfig {
  public static final int DEFAULT_MAX_EXPLORES_PER_SAMPLE = 4;
  public static final int DEFAULT_MAX_BACK_TRACE_PER_SAMPLE = 4;
  public static final long DEFAULT_REPORT_PROGRESS_EVERY_STEPS = 500_000;
  public static final int DEFAULT_INITIAL_COLLAPSE_THRESHOLD = 10;

  @Value.Default
  public int maxExploresPerSample() {
    return DEFAULT_MAX_EXPLORES_PER_SAMPLE;
  }

  @Value.Default
  public int maxBacktrackPerSample() {
    return DEFAULT_MAX_BACK_TRACE_PER_SAMPLE;
  }

  @Value.Default
  public long reportProgressEverySteps() {
    return DEFAULT_REPORT_PROGRESS_EVERY_STEPS;
  }

  @Value.Default
  public int initialCollapseThreshold() {
    return DEFAULT_INITIAL_COLLAPSE_THRESHOLD;
  }

  @Value.Default
  public CollapseMethod collapseMethod() {
    return CollapseMethod.ONLY_SAMPLED_STATES;
  }

  public static ImmutableUnboundedSamplerConfig.Builder builder() {
    return ImmutableUnboundedSamplerConfig.builder();
  }

  public static UnboundedSamplerConfig getDefault() {
    return builder().build();
  }
}
