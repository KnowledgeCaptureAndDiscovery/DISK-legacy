package org.diskproject.client.components.tloi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.workflow.VariableBinding;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.vaadin.widget.VaadinComboBox;
import com.vaadin.polymer.vaadin.widget.event.ValueChangedEvent;

public class BindingsEditor extends Composite {
  interface Binder extends UiBinder<Widget, BindingsEditor> {};
  private static Binder uiBinder = GWT.create(Binder.class);
 
  @UiField HTMLPanel varsection;
  @UiField VaadinComboBox variableMenu;

  Map<String, HTMLPanel> variablePanels;
  
  WorkflowBindings bindings;

  public BindingsEditor() {
    initWidget(uiBinder.createAndBindUi(this));  
    variablePanels = new HashMap<String, HTMLPanel>();
  }

  public void setWorkflowBindings(WorkflowBindings bindings) {
    this.bindings = bindings;
    clearUI();
    if(bindings != null) {
        List<String> names = new ArrayList<String>();
    	for (VariableBinding var: bindings.getBindings()) {
    		if (var.isCollection()) {
				String varname = var.getVariable();
				String[] values = var.getBindingAsArray();
				if (values.length > 1) {
					names.add(varname);

					if (!variablePanels.containsKey(varname))
						variablePanels.put(varname, new HTMLPanel(""));
					HTMLPanel panel = variablePanels.get(varname);
					panel.addStyleName("options");
					for (String value: values) {
						CheckBox item = new CheckBox(value);
						item.setValue(true);
						panel.add(item);
					}
					varsection.add(panel);
				}
    		}
    	}
    	variableMenu.setItems(Polymer.asJsArray(names));
    	if (names.size() > 0) variableMenu.setValue(names.get(0));
    	onVariableMenuChanged(null);
    }
  }
  
  private void clearUI () {
    variableMenu.clear();
    varsection.clear();
    variablePanels = new HashMap<String, HTMLPanel>();
  }

  public WorkflowBindings getWorkflowBindings() {
    //WorkflowBindings newBindings = new WorkflowBindings();
    //newBindings.setParameters(bindings.getParameters());
    //newBindings.setOptionalParameters(bindings.getOptionalParameters());
    
    List<VariableBinding> newVarBindings = new ArrayList<VariableBinding>();
    for (VariableBinding origBinding: bindings.getBindings()) {
    	String varname = origBinding.getVariable();
    	if (origBinding.isCollection() && variablePanels.containsKey(varname)) {
    		HTMLPanel varPanel = variablePanels.get(varname);
    		int len = varPanel.getWidgetCount();
    		String newVal = "[";
    		boolean first = true;
    		for (int i = 0; i < len; i++) {
    			CheckBox option = (CheckBox) varPanel.getWidget(i);
    			if (option.getValue()) {
    				if (!first) newVal += ", ";
    				newVal += option.getText();
    				first = false;
    			}
    		}
    		newVal += "]";
    		GWT.log(newVal);
    		newVarBindings.add(new VariableBinding(varname, newVal));
    	} else {
    		newVarBindings.add(origBinding);
    	}
    }
    bindings.setBindings(newVarBindings);
    
    return bindings;
  }  
  
  
  @UiHandler("variableMenu")
  void onVariableMenuChanged(ValueChangedEvent event) {
	  String selected = variableMenu.getValue();
	  for (String varname: variablePanels.keySet()) {
		  variablePanels.get(varname).setVisible(varname.equals(selected));
	  }
  }
}
