package org.diskproject.shared.classes.loi;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WorkflowBindings {
  String workflow;
  Map<String, String> bindings;

  public WorkflowBindings() {
    bindings = new HashMap<String, String>();
  }
  
  public String getWorkflow() {
    return workflow;
  }

  public void setWorkflow(String workflow) {
    this.workflow = workflow;
  }

  public Map<String, String> getBindings() {
    return bindings;
  }

  public void setBindings(Map<String, String> bindings) {
    this.bindings = bindings;
  }
   
  @JsonIgnore
  public String getBindingsDescription() {
    String description = "";
    int i=0;
    for(String key : bindings.keySet()) {
      if(i > 0)
        description += ", ";
      description += key + " = " + bindings.get(key);
      i++;
    }
    return description;
  }
}
