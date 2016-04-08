package org.diskproject.client.components.loi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.Workflow;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.PolymerWidget;
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
  Map<String, Workflow> workflowcache;
  Map<String, Variable> variablecache;
  
  WorkflowBindings bindings;
  boolean initloaded;
  
  public WorkflowBindingsEditor() {
    initWidget(uiBinder.createAndBindUi(this));  
  }
  
  public void setMetamode(boolean metamode) {
    this.metamode = metamode;
  }
  
  public void setWorkflowList(List<Workflow> list) {
    workflowcache = new HashMap<String, Workflow>();
    for(Workflow w : list)
      workflowcache.put(w.getName(), w);
    List<String> names = new ArrayList<String>();
    for(String wflowName : workflowcache.keySet()) {
      names.add(wflowName);
    }
    Collections.sort(names);
    workflowmenu.setItems(Polymer.asJsArray(names));
    clearVariableBindingsUI();
  }

  public void loadWorkflowBindings(WorkflowBindings bindings) {
    initloaded = false;
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
    
    for(VariableBinding vbinding : bindings.getBindings()) {
      this.addVariableBinding(vbinding.getVariable(), vbinding.getBinding());
    }
  }
  
  private void addVariableBinding(String varid, String binding) {
    final HTMLPanel el = new HTMLPanel("");
    el.setStyleName("horizontal layout");
    
    VaadinComboBox varmenu = new VaadinComboBox();
    varmenu.setLabel("Workflow Variable");
    varmenu.addStyleName("no-label");
    List<String> names = new ArrayList<String>();
    for(String varname : variablecache.keySet()) {
      names.add(varname);
    }
    varmenu.setItems(Polymer.asJsArray(names));
    if(varid != null)
      varmenu.setValue(varid);
    
    PolymerWidget bindwidget = null;
    if(this.metamode) {
      VaadinComboBox bindinput = new VaadinComboBox();
      List<String> workflownames = new ArrayList<String>();
      for(String wflowName : workflowcache.keySet()) {
        workflownames.add(wflowName);
      }
      Collections.sort(workflownames);
      
      bindinput.setItems(Polymer.asJsArray(workflownames));
      bindinput.setLabel("Previous workflow run");
      bindinput.addStyleName("no-label");
      if(binding != null)
        bindinput.setValue(binding);
      
      bindwidget = bindinput;
    }
    else {
      PaperInput bindinput = new PaperInput();
      bindinput.setLabel("Binding");
      bindinput.setNoLabelFloat(true);
      bindinput.addStyleName("no-label");
      if(binding != null)
        bindinput.setValue(binding);
      bindwidget = bindinput;
    }
    
    PaperIconButton delbutton = new PaperIconButton();
    delbutton.setStyleName("smallicon red-button");
    delbutton.setIcon("cancel");
    
    el.add(varmenu);
    el.add(bindwidget);
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
        if(!initloaded) 
          initloaded = true;
        else
          bindings = null;
        addbindingbutton.setVisible(true);
        variablecache = new HashMap<String, Variable>();
        for(Variable v : result)
          variablecache.put(v.getName(), v);
        setBindingsUI();
      }
    });
  }
  
  @UiHandler("workflowlink")
  void onWorkflowLinkClicked(ClickEvent event) {
    if(workflowmenu.getValue() != null) {
      Workflow workflow = workflowcache.get(workflowmenu.getValue());
      if(workflow != null && workflow.getLink() != null)
        Window.open(workflow.getLink(), "_blank", "");
    }
  }
  
  public WorkflowBindings getWorkflowBindings() {
    WorkflowBindings bindings = new WorkflowBindings();
    Workflow workflow = workflowcache.get(workflowmenu.getValue());
    bindings.setWorkflow(workflow.getName());
    bindings.setWorkflowLink(workflow.getLink());
    
    List<VariableBinding> vbindings = new ArrayList<VariableBinding>();
    for(int i=0; i<varbindings.getWidgetCount(); i++) {
      HTMLPanel row = (HTMLPanel) varbindings.getWidget(i);
      VaadinComboBox cb = (VaadinComboBox) row.getWidget(0);
      String varname = cb.getValue();
      VariableBinding vbinding = new VariableBinding();
      vbinding.setVariable(varname);
      if(this.metamode) {
        VaadinComboBox vb = (VaadinComboBox) row.getWidget(1);
        vbinding.setBinding(vb.getValue());
      }
      else {
        PaperInput in = (PaperInput) row.getWidget(1);
        vbinding.setBinding(in.getValue());
      }
      vbindings.add(vbinding);
    }
    bindings.setBindings(vbindings);
    return bindings;
  }  
}
