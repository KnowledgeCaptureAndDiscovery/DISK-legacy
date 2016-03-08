package org.diskproject.shared.classes.loi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.diskproject.shared.classes.util.GUID;

public class TriggeredLOI {
  public static enum Status {
    QUEUED, RUNNING, FINISHED
  };
  
  String id;
  String name;
  String description;
  Status status;

  String loiId;
  String parentHypothesisId;
  String resultingHypothesisId;
  List<WorkflowBindings> workflows;
  List<WorkflowBindings> metaWorkflows;

  public TriggeredLOI() {
    workflows = new ArrayList<WorkflowBindings>();
    metaWorkflows = new ArrayList<WorkflowBindings>();
  }
  
  public TriggeredLOI(LineOfInquiry loi, String hypothesisId) {
    this.id = GUID.randomId("TriggeredLOI");
    this.loiId = loi.getId();
    this.name = "Triggered: " + loi.getName();
    this.description = loi.getDescription();
    this.parentHypothesisId = hypothesisId;
    workflows = new ArrayList<WorkflowBindings>();
    metaWorkflows = new ArrayList<WorkflowBindings>();
    this.copyWorkflowBindings(loi.getWorkflows(), this.workflows);
    this.copyWorkflowBindings(loi.getMetaWorkflows(), this.metaWorkflows);
  }
  
  private void copyWorkflowBindings(List<WorkflowBindings> fromlist,
      List<WorkflowBindings> tolist) {
    for(WorkflowBindings from : fromlist) {
      WorkflowBindings to = new WorkflowBindings();
      to.setWorkflow(from.getWorkflow());
      to.setBindings(new HashMap<String, String>(from.getBindings()));
      tolist.add(to);
    }
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

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public String getLoiId() {
    return loiId;
  }

  public void setLoiId(String loiId) {
    this.loiId = loiId;
  }

  public String getParentHypothesisId() {
    return parentHypothesisId;
  }

  public void setParentHypothesisId(String parentHypothesisId) {
    this.parentHypothesisId = parentHypothesisId;
  }

  public String getResultingHypothesisId() {
    return resultingHypothesisId;
  }

  public void setResultingHypothesisId(String resultingHypothesisId) {
    this.resultingHypothesisId = resultingHypothesisId;
  }

  public List<WorkflowBindings> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(List<WorkflowBindings> workflows) {
    this.workflows = workflows;
  }

  public List<WorkflowBindings> getMetaWorkflows() {
    return metaWorkflows;
  }

  public void setMetaWorkflows(List<WorkflowBindings> metaWorkflows) {
    this.metaWorkflows = metaWorkflows;
  }

}
