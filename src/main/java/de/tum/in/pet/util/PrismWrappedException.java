package de.tum.in.pet.util;

import prism.PrismException;

public class PrismWrappedException extends RuntimeException {
  public PrismWrappedException(PrismException e) {
    super(e);
  }
}
