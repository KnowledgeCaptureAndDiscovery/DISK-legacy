package org.diskproject.client.components.loi;

import java.util.List;

import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.WorkflowBindings;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.paper.widget.PaperButton;

public class TriggeredLOIViewer extends Composite {
  interface Binder extends UiBinder<Widget, TriggeredLOIViewer> {};
  private static Binder uiBinder = GWT.create(Binder.class);
 
  @UiField DivElement name, description, workflows, metaworkflows;
  @UiField PaperButton followbutton;
  
  public TriggeredLOIViewer() {
    initWidget(uiBinder.createAndBindUi(this));  
  }
  
  public void load(TriggeredLOI tloi) {
    name.setInnerText(tloi.getName());
    description.setInnerText(tloi.getDescription());
    workflows.setInnerHTML(getWorkflowsHTML(tloi.getWorkflows()));
    metaworkflows.setInnerHTML(getWorkflowsHTML(tloi.getMetaWorkflows()));
  }
  
  private String getWorkflowsHTML(List<WorkflowBindings> wbindings) {
    String workflowhtml = "<div class='section'>\n";
    for(WorkflowBindings bindings: wbindings) {
      workflowhtml += "<div class='workflow'>"+
          "<b>" + bindings.getWorkflow()+"</b>";
      if(bindings.getBindings().keySet().size() > 0)
          workflowhtml += " ( "+bindings.getBindingsDescription()+" )</div>\n";
    }
    workflowhtml += "</div>\n"; 
    return workflowhtml;
  }
  
  public PaperButton getActionButton() {
    return this.followbutton;
  }
}
