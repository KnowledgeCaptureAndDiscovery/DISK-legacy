package org.diskproject.shared.classes.workflow;

import java.io.Serializable;

public class VariableBinding implements Serializable, Comparable<VariableBinding> {
  private static final long serialVersionUID = -847994634505985728L;
  
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

  public int compareTo(VariableBinding o) {
    return this.toString().compareTo(o.toString());
  }

}
