package org.diskproject.shared.classes.loi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WorkflowBindings implements Comparable<WorkflowBindings>{
  String workflow;
  String workflowLink;
  List<VariableBinding> bindings;
  
  WorkflowRun run;
  MetaWorkflowDetails meta;

  public WorkflowBindings() {
    bindings = new ArrayList<VariableBinding>();
    run = new WorkflowRun();
    meta = new MetaWorkflowDetails();
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
  public List<String> getVariableBindings(String variable) {
    List<String> bindings = new ArrayList<String>();
    for(VariableBinding vb : this.bindings) {
      if(vb.getVariable().equals(variable))
        bindings.add(vb.getBinding());
    }
    return bindings;
  }
  
  @JsonIgnore
  public List<String> getBindingVariables(String binding) {
    List<String> variables = new ArrayList<String>();
    for(VariableBinding vb : this.bindings) {
      if(vb.getVariable().equals(binding))
        variables.add(vb.getVariable());
    }
    return variables;
  }

  @JsonIgnore
  public String getBindingsDescription() {
    String description = "";
    int i=0;
    Collections.sort(bindings);
    description += "{";
    for(VariableBinding vbinding : bindings) {
      if(i > 0)
        description += ", ";
      description += vbinding.getVariable() + " = " + vbinding.getBinding();
      i++;
    }
    if(this.meta.getHypothesis() != null) {
      if(i > 0)
        description += ", ";
      description += this.meta.getHypothesis() + " = [Hypothesis]";
      i++;
    }
    if(this.meta.getRevisedHypothesis() != null) {
      if(i > 0)
        description += ", ";
      description += this.meta.getRevisedHypothesis() + " = [Revised Hypothesis]";
      i++;
    }
    description += "}";
    return description;
  }
  
  public String toString() {
    return this.getBindingsDescription();
  }
  
  public MetaWorkflowDetails getMeta() {
    return meta;
  }

  public void setMeta(MetaWorkflowDetails meta) {
    this.meta = meta;
  }

  @JsonIgnore
  public String getHTML() {
    String id = this.getWorkflow();
    
    String status = this.getRun().getStatus();
    String extra = "";
    String extracls = "";
    
    if(status != null) {
      String icon = "icons:hourglass-empty";
      if(status.equals("SUCCESS")) {
        icon = "icons:check";
      }
      else if(status.equals("FAILURE")) {
        icon = "icons:clear";
      }
      extra = " <iron-icon class='"+status+"' icon='"+icon+"' />";
      extracls = " " +status;
    }
    
    String html = "<div class='name" + extracls+ "'>"+ id + extra +"</div>";
    html += "<div class='description'>";
    String description = this.getBindingsDescription();
    if(!description.equals(""))
      html += "<b>Variable Bindings:</b> "+description + "<br />";
    html += "</div>";
    return html;
  }

  public int compareTo(WorkflowBindings o) {
    return this.toString().compareTo(o.toString());
  }
  
}
