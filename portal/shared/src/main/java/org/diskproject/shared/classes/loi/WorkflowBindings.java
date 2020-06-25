package org.diskproject.shared.classes.loi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WorkflowBindings implements Comparable<WorkflowBindings>{
  String workflow;
  String workflowLink;
  List<VariableBinding> bindings;
  
  WorkflowRun run;
  MetaWorkflowDetails meta;

  public WorkflowBindings(String workflow,
  String workflowLink,
  List<VariableBinding> bindings) {
	  this.workflow = workflow;
	  this.workflowLink = workflowLink;
	  this.bindings = bindings;
    run = new WorkflowRun();
    meta = new MetaWorkflowDetails();
  }
  
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
  
  public void addBinding(VariableBinding binding) {
    this.bindings.add(binding);
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

  @JsonIgnore
  public String getBindingsDescriptionAsTable() {
    String html = "<ul style=\"margin: 0\">";
    Map<String, String> files = this.getRun().getFiles();
    String prefix = "https://enigma-disk.wings.isi.edu/wings-portal/users/admin/test/data/fetch?data_id=";
    
    Collections.sort(bindings);
    for(VariableBinding vbinding : bindings) {
      String[] barr = vbinding.getBindingAsArray();
      if (barr.length < 2) {
    	  html += "<li><b>" + vbinding.getVariable() + " = </b>";
    	  if (files != null && files.containsKey(barr[0])) {
    		  html += "<a target='_blank' href='" + prefix + files.get(barr[0]).replace(":", "%3A").replace("#", "%23") + "'>" + barr[0] + "</a>";
    	  } else html += barr[0];
    	  html += "</li>";
      } else {
    	  html += "<li><b>" + vbinding.getVariable() + " = </b></li><ul>";
		  for (String b: barr) {
			  html += "<li>";
			  if (files != null && files.containsKey(b))
				  html += "<a target='_blank' href='" + prefix + files.get(b).replace(":", "%3A").replace("#", "%23") + "'>" + b + "</a>";
			  else html += b;
			  html += "</li>";
		  }
		  html += "</ul>";
      }
    }
    if(this.meta.getHypothesis() != null) {
    	html += "<li><b>" + this.meta.getHypothesis() + "</b>: [Hypothesis]</li>";
    }
    if(this.meta.getRevisedHypothesis() != null) {
    	html += "<li><b>" + this.meta.getRevisedHypothesis() + "</b>: [Revised Hypothesis]</li>";
    }
    html += "</ul>";
    return html;
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
    html += "<div class='description workflow-description'>";

    String startts = this.getRun().getStartDate();
    if (startts != null) {
    	html += "<span><b>Start date:</b></span> <span>" + startts + "</span>";
    }
    
    String endts = this.getRun().getEndDate();
    if (endts != null) {
    	html += "<span><b>End date:</b></span> <span>" + endts + "</span>";
    }

    String description = this.getBindingsDescriptionAsTable();
    if(!description.equals(""))
      html += "<span><b>Variable Bindings:</b></span> <span>" + description + "</span>";
    
    List<String> outputs = this.getRun().getOutputs();
    if (outputs != null) {
    	int osize = outputs.size();
    	String prefix = "https://enigma-disk.wings.isi.edu/wings-portal/users/admin/test/data/fetch?data_id=";
    	html += "<span><b>Output files (" + Integer.toString(osize) + "):</b></span><span><ol style='margin:0'>";
    	for (String link: this.getRun().getOutputs()) {
    		String dl = prefix + link.replace(":", "%3A").replace("#", "%23");
    		html += "<li><a target=\"_blank\" href=\"" + dl + "\">" + link.replaceAll(".*?#", "") + "</a></li>";
    	}
    	html += "</ol></span>";
    }
    
    html += "</div>";
    return html;
  }

  public int compareTo(WorkflowBindings o) {
    return this.toString().compareTo(o.toString());
  }
  
}
