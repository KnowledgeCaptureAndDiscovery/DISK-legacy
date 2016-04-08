package org.diskproject.shared.classes.workflow;

public class VariableBinding {
  String variable;
  String binding;

  public String getVariable() {
    return variable;
  }

  public void setVariable(String variable) {
    this.variable = variable;
  }

  public String getBinding() {
    return binding;
  }

  public void setBinding(String binding) {
    this.binding = binding;
  }

  public String toString() {
    return variable+" = "+binding;
  }
}
