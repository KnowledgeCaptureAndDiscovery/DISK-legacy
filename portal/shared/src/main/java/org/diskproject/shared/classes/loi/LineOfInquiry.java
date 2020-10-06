package org.diskproject.shared.classes.loi;

import java.util.ArrayList;
import java.util.List;

public class LineOfInquiry {
  String id;
  String name;
  String description;
  String hypothesisQuery;
  String dataQuery;
  String notes;
  List<WorkflowBindings> workflows;
  List<WorkflowBindings> metaWorkflows;
  String creationDate, author;
  String dateModified;

  public LineOfInquiry() {
    this.workflows = new ArrayList<WorkflowBindings>();
    this.metaWorkflows = new ArrayList<WorkflowBindings>();
  }
  
  public LineOfInquiry(String id,
		  String name, 
		  String description, 
		  String hypothesisQuery, 
		  String dataQuery, 
		  List<WorkflowBindings> workflows, 
		  List<WorkflowBindings> metaWorkflows){
	  this.id = id;
	  this.name = name;
	  this.description = description;
	  this.hypothesisQuery = hypothesisQuery;
	  this.dataQuery = dataQuery;
	  this.workflows = workflows;
	  this.metaWorkflows = metaWorkflows;
  }
  
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public void setDescription(String description) {
    this.description = description;
  }  

  public String getHypothesisQuery() {
    return hypothesisQuery;
  }

  public void setHypothesisQuery(String query) {
    this.hypothesisQuery = query;
  }

  public String getDataQuery() {
    return dataQuery;
  }

  public void setDataQuery(String dataQuery) {
    this.dataQuery = dataQuery;
  }
  
  public List<WorkflowBindings> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(List<WorkflowBindings> workflows) {
    this.workflows = workflows;
  }
  
  public void addWorkflow(WorkflowBindings workflowid) {
    this.workflows.add(workflowid);
  }
  
  public List<WorkflowBindings> getMetaWorkflows() {
    return metaWorkflows;
  }

  public void setMetaWorkflows(List<WorkflowBindings> metaWorkflows) {
    this.metaWorkflows = metaWorkflows;
  }
  
  public void addMetaWorkflow(WorkflowBindings metaWorkflowid) {
    this.metaWorkflows.add(metaWorkflowid);
  }
  
  public void setCreationDate(String date) {
	  this.creationDate = date;
  }
  
  public void setAuthor (String author) {
	  this.author = author;
  }
  
  public String getCreationDate () {
	  return this.creationDate;
  }
  
  public String getAuthor () {
	  return this.author;
  }
  
  public void setDateModified(String date) {
	  this.dateModified = date;
  }
  
  public String getDateModified () {
	  return this.dateModified;
  }
}
