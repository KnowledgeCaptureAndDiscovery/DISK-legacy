package org.diskproject.client.components.loi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.Workflow;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.paper.widget.PaperIconButton;
import com.vaadin.polymer.paper.widget.PaperInput;
import com.vaadin.polymer.vaadin.widget.VaadinComboBox;
import com.vaadin.polymer.vaadin.widget.event.ValueChangedEvent;

public class WorkflowBindingsEditor extends Composite {
  interface Binder extends UiBinder<Widget, WorkflowBindingsEditor> {};
  private static Binder uiBinder = GWT.create(Binder.class);
 
  @UiField HTMLPanel varsection, varbindings;
  @UiField VaadinComboBox workflowmenu;
  @UiField PaperIconButton addbindingbutton;
  
  boolean metamode;
  List<Workflow> workflowcache;
  List<Variable> variablecache;
  
  WorkflowBindings bindings;
  
  public WorkflowBindingsEditor() {
    initWidget(uiBinder.createAndBindUi(this));  
  }
  
  public void setMetamode(boolean metamode) {
    this.metamode = metamode;
    if(this.metamode)
      this.varsection.addStyleName("hidden");
    else
      this.varsection.removeStyleName("hidden");
  }
  
  public void setWorkflowList(List<Workflow> list) {
    this.workflowcache = list;
    List<String> names = new ArrayList<String>();
    for(Workflow wflow : workflowcache) {
      names.add(wflow.getName());
    }
    workflowmenu.setItems(Polymer.asJsArray(names));
    clearVariableBindingsUI();
  }

  public void loadWorkflowBindings(WorkflowBindings bindings) {
    this.bindings = bindings;
    workflowmenu.setValue(null);  
    if(bindings != null)
      workflowmenu.setValue(bindings.getWorkflow());        
  }
  
  
  private void clearVariableBindingsUI() {
    varbindings.clear();
    addbindingbutton.setVisible(false);
  }
  
  private void setBindingsUI() {
    if(bindings == null)
      return;
    
    for(String varid : bindings.getBindings().keySet()) {
      String binding = bindings.getBindings().get(varid);
      this.addVariableBinding(varid, binding);
    }
  }
  
  private void addVariableBinding(String varid, String binding) {
    final HTMLPanel el = new HTMLPanel("");
    el.setStyleName("horizontal layout");
    
    VaadinComboBox varmenu = new VaadinComboBox();
    varmenu.setLabel("Workflow Variable");
    varmenu.addStyleName("no-label");
    
    List<String> names = new ArrayList<String>();
    for(Variable var : variablecache) {
      if(var.isInput())
        names.add(var.getName());
    }
    varmenu.setItems(Polymer.asJsArray(names));
    if(varid != null)
      varmenu.setValue(varid);
    
    PaperInput bindinput = new PaperInput();
    bindinput.setLabel("Binding");
    bindinput.setNoLabelFloat(true);
    if(binding != null)
      bindinput.setValue(binding);
    
    PaperIconButton delbutton = new PaperIconButton();
    delbutton.setStyleName("smallicon");
    delbutton.setIcon("clear");
    
    el.add(varmenu);
    el.add(bindinput);
    el.add(delbutton);
    varbindings.add(el);
    
    delbutton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        el.removeFromParent();
      }
    });
  }
  
  @UiHandler("addbindingbutton")
  void onAddBindingClicked(ClickEvent event) {
    this.addVariableBinding(null, null);
  }
  
  @UiHandler("workflowmenu")
  void onWorkflowMenuSelected(ValueChangedEvent event) {
    String workflowid = workflowmenu.getValue();
    if(workflowid == null) {
      this.clearVariableBindingsUI();
      return;
    }
    
    DiskREST.getWorkflowVariables(workflowid, 
        new Callback<List<Variable>, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
      @Override
      public void onSuccess(List<Variable> result) {
        clearVariableBindingsUI();
        addbindingbutton.setVisible(true);
        variablecache = result;
        setBindingsUI();
      }
    });
  }
  
  public WorkflowBindings getWorkflowBindings() {
    WorkflowBindings bindings = new WorkflowBindings();
    bindings.setWorkflow(workflowmenu.getValue());
    Map<String, String> map = new HashMap<String, String>();
    for(int i=0; i<varbindings.getWidgetCount(); i++) {
      HTMLPanel row = (HTMLPanel) varbindings.getWidget(i);
      VaadinComboBox cb = (VaadinComboBox) row.getWidget(0);
      PaperInput in = (PaperInput) row.getWidget(1);
      map.put(cb.getValue(), in.getValue());
    }
    bindings.setBindings(map);
    return bindings;
  }  
}
