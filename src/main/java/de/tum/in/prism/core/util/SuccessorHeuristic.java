package de.tum.in.prism.core.util;

public enum SuccessorHeuristic {
  /**
   * Sample a maximizing distribution d, sample successor according to d(s') * diff(s')
   */
  WEIGHTED,
  /**
   * Sample a maximizing distribution d, sample successor according to d(s')
   */
  PROB,
  /**
   * Sample a maximizing distribution, sample successor according to diff(s')
   */
  BOUNDS,
  /**
   * Sample successor according to max_d d(s') * diff(s')
   */
  GRAPH_WEIGHTED,
  /**
   * Sample successor according to max_d diff(s')
   */
  GRAPH_BOUNDS
}