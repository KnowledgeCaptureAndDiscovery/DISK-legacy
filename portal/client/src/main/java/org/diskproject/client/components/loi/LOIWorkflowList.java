package org.diskproject.client.components.loi;

import java.util.ArrayList;
import java.util.List;

import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.list.events.ListItemDeletionEvent;
import org.diskproject.client.components.list.events.ListItemSelectionEvent;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.workflow.Workflow;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiConstructor;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.iron.widget.event.IronOverlayClosedEvent;
import com.vaadin.polymer.paper.widget.PaperDialog;
import com.vaadin.polymer.paper.widget.PaperIconButton;

public class LOIWorkflowList extends Composite {
  interface Binder extends UiBinder<Widget, LOIWorkflowList> {};
  private static Binder uiBinder = GWT.create(Binder.class);

  ListNode update;
  
  @UiField WorkflowBindingsEditor bindingseditor;
  @UiField PaperDialog workflowdialog;
  @UiField ListWidget workflowlist;
  @UiField PaperIconButton addwflowbutton;
  @UiField LabelElement label;
  
  public @UiConstructor LOIWorkflowList(boolean metamode, String label) {
    initWidget(uiBinder.createAndBindUi(this)); 
    
    bindingseditor.setMetamode(metamode);
    this.label.setInnerText(label);
  }

  public void setWorkflowList(List<Workflow> list) {
    bindingseditor.setWorkflowList(list);
  }
  
  public List<WorkflowBindings> getBindingsList() {
    List<WorkflowBindings> bindings = new ArrayList<WorkflowBindings>();
    for(ListNode node : workflowlist.getNodes()) {
      bindings.add((WorkflowBindings)node.getData());
    }
    return bindings;
  }
  
  public void loadBindingsList(List<WorkflowBindings> bindingslist) {
    workflowlist.clear();
    if(bindingslist == null)
      return;
    for(WorkflowBindings bindings: bindingslist) {
      this.addWorkflowBindingsToList(bindings, null);
    }
  }
  
  @UiHandler("workflowlist")
  void onWorkflowListItemSelected(ListItemSelectionEvent event) {
    WorkflowBindings bindings = (WorkflowBindings)event.getItem().getData();
    update = event.getItem();    
    bindingseditor.loadWorkflowBindings(bindings);
    workflowdialog.open();
  }
  
  @UiHandler("workflowlist")
  void onWorkflowListItemDeleted(ListItemDeletionEvent event) {
    ListNode node = event.getItem();
    if(workflowlist.getNode(node.getId()) != null)
      workflowlist.removeNode(node);
  }
  
  @UiHandler("addwflowbutton")
  void onAddWorkflowButtonClicked(ClickEvent event) {
    update = null;
    bindingseditor.loadWorkflowBindings(null);
    workflowdialog.open();
  }
  
  @UiHandler("workflowdialog")
  void onDialogClose(IronOverlayClosedEvent event) {
    if(!isConfirmed(event.getPolymerEvent().getDetail()))
      return;

    WorkflowBindings bindings = bindingseditor.getWorkflowBindings();
    if(bindings == null)
      return;
    
    this.addWorkflowBindingsToList(bindings, update);
  }
  
  private void addWorkflowBindingsToList(WorkflowBindings bindings, 
      ListNode tnode) {
    String id = bindings.getWorkflow();
    String description = bindings.getBindingsDescription();
    
    if(tnode == null) {
      tnode = new ListNode(id, id, description);
      tnode.setIcon("dashboard");
      tnode.setIconStyle("green");
      tnode.setData(bindings);
      workflowlist.addNode(tnode);      
    }
    else {
      String oldid = tnode.getId();
      tnode.setId(id);
      tnode.setName(id);
      tnode.setDescription(description);
      tnode.setData(bindings);
      workflowlist.updateNode(oldid, tnode);
    }    
  }
  
  private native boolean isConfirmed(Object obj) /*-{
    return obj.confirmed;
  }-*/;  

}
