package de.tum.in.pet.implementation.meanPayoff;

public interface ValueIterator {

  public void run();

  public boolean stoppingCriterion();

  public void getResult();

}
