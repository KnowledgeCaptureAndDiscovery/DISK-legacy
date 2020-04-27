package org.diskproject.shared.classes.workflow;

import java.util.List;

public class WorkflowRun {
  String id;
  String link;
  String status;
  List<String> outputs;
  String startDate;
  String endDate;

  public WorkflowRun(){}
  
  public WorkflowRun(String id,
  String link,
  String status){
	  this.id = id;
	  this.link = link;
	  this.status = status;
  }
  
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
  
  public void setStartDate (String date) {
	  this.startDate = date;
  }
  
  public String getStartDate () {
	  return this.startDate;
  }
  
  public void setEndDate (String date) {
	  this.endDate = date;
  }
  
  public String getEndDate () {
	  return this.endDate;
  }
  
  public void setOutputs(List<String> outputs) {
	  this.outputs = outputs;
  }
  
  public List<String> getOutputs () {
	  return outputs;
  }
}
