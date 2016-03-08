package org.diskproject.shared.classes.loi;

import java.util.ArrayList;
import java.util.List;

public class LineOfInquiry {
  String id;
  String name;
  String description;
  String query;
  List<WorkflowBindings> workflows;
  List<WorkflowBindings> metaWorkflows;

  public LineOfInquiry() {
    this.workflows = new ArrayList<WorkflowBindings>();
    this.metaWorkflows = new ArrayList<WorkflowBindings>();
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

  public void setDescription(String description) {
    this.description = description;
  }  

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
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

}
