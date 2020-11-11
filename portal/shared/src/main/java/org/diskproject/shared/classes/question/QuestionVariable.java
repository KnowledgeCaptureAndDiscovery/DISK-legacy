package org.diskproject.shared.classes.question;

public class QuestionVariable {
  String id;
  String varname;
  String constraints;

  public QuestionVariable (String id, String varname, String constraints) {
	  this.id = id;
	  this.varname = varname;
	  this.constraints = constraints;
  }
  
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getVarName() {
    return varname;
  }

  public void setVarName(String name) {
    this.varname = name;
  }

  public String getConstraints() {
    return constraints;
  }

  public String setConstraints() {
    return constraints;
  }
}
