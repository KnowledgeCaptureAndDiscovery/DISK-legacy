package org.diskproject.shared.classes.loi;

import java.util.ArrayList;
import java.util.List;

import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WorkflowBindings {
  String workflow;
  String workflowLink;
  List<VariableBinding> bindings;
  
  WorkflowRun run;

  public WorkflowBindings() {
    bindings = new ArrayList<VariableBinding>();
    run = new WorkflowRun();
  }
  
  public String getWorkflow() {
    return workflow;
  }

  public void setWorkflow(String workflow) {
    this.workflow = workflow;
  }

  public String getWorkflowLink() {
    return workflowLink;
  }

  public void setWorkflowLink(String workflowLink) {
    this.workflowLink = workflowLink;
  }

  public WorkflowRun getRun() {
    return run;
  }

  public void setRun(WorkflowRun run) {
    this.run = run;
  }

  public List<VariableBinding> getBindings() {
    return bindings;
  }

  public void setBindings(List<VariableBinding> bindings) {
    this.bindings = bindings;
  }

  @JsonIgnore
  public String getBindingsDescription() {
    String description = "";
    int i=0;
    for(VariableBinding vbinding : bindings) {
      if(i > 0)
        description += ", ";
      description += vbinding.getVariable() + " = " + vbinding.getBinding();
      i++;
    }
    return description;
  }  
}
