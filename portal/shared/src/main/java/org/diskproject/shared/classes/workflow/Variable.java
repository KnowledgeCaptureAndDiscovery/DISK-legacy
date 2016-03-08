package org.diskproject.shared.classes.workflow;

public class Variable {
  String name;
  boolean input;
  boolean param;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isInput() {
    return input;
  }

  public void setInput(boolean input) {
    this.input = input;
  }

  public boolean isParam() {
    return param;
  }

  public void setParam(boolean param) {
    this.param = param;
  }
}
