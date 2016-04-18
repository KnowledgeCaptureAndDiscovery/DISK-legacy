package org.diskproject.shared.classes.workflow;

public class Variable {
  String name;
  String type;
  int dimensionality;
  boolean param;
  boolean input;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getDimensionality() {
    return dimensionality;
  }

  public void setDimensionality(int dimensionality) {
    this.dimensionality = dimensionality;
  }

  public boolean isParam() {
    return param;
  }

  public void setParam(boolean param) {
    this.param = param;
  }

  public boolean isInput() {
    return input;
  }

  public void setInput(boolean input) {
    this.input = input;
  }
}
