package org.diskproject.client.components.tloi;

import java.util.List;

import org.diskproject.client.Config;
import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.list.events.ListItemActionEvent;
import org.diskproject.client.components.triples.TripleViewer;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.WorkflowBindings;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class TriggeredLOIViewer extends Composite {
  interface Binder extends UiBinder<Widget, TriggeredLOIViewer> {};
  private static Binder uiBinder = GWT.create(Binder.class);
  
  @UiField DivElement header;
  @UiField DivElement hypothesisSection;
  @UiField HTMLPanel revHypothesisSection;
  @UiField TripleViewer hypothesis;
  @UiField ListWidget workflowlist, metaworkflowlist;
  @UiField AnchorElement hypothesisLink, loiLink;
  
  String username, domain;
  TriggeredLOI tloi;
  
  public TriggeredLOIViewer() {
    initWidget(uiBinder.createAndBindUi(this));
  }
  
  public void initialize(String username, String domain) {
    this.username = username;
    this.domain = domain;
    hypothesis.initialize(username, domain);
    workflowlist.addCustomAction("runlink", "Run details", 
        "icons:build", "blue-button run-link");
    metaworkflowlist.addCustomAction("runlink", "Run details", 
        "icons:build", "blue-button run-link");    
  }
  
  public void load(TriggeredLOI tloi) {
    this.tloi = tloi;
    header.setInnerHTML(tloi.getHeaderHTML());
    setLOILink(tloi.getName(), tloi.getLoiId(), loiLink);
    setWorkflowsHTML(tloi.getWorkflows(), workflowlist);
    setWorkflowsHTML(tloi.getMetaWorkflows(), metaworkflowlist);
    setHypothesisHTML(tloi.getParentHypothesisId(), 
        hypothesisSection, hypothesis, hypothesisLink);
    setRevisedHypothesesHTML(tloi.getResultingHypothesisIds(), revHypothesisSection);
  }
  
  public TriggeredLOI getTLOI() {
    return this.tloi;
  }
  
  private void setLOILink(String tname, String id, AnchorElement anchor) {
    String name = tname.replace("Triggered: ", "");
    anchor.setInnerText(name);
    anchor.setHref(this.getLOILink(id));
  }
  
  private void setRevisedHypothesesHTML(List<String> ids, final HTMLPanel section) {
    if(ids.size() == 0) {
      section.setVisible(false);
      return;
    }
    section.setVisible(true);
    section.clear();
    
    Label label = new Label("Revised Hypothesis");
    label.addStyleName("small-grey");
    section.add(label);
    
    for(final String id : ids) {
      HTMLPanel panel = new HTMLPanel("");
      panel.addStyleName("bordered-list padded");
      HTMLPanel anchordiv = new HTMLPanel("");
      final Anchor anchor = new Anchor();
      anchordiv.add(anchor);
      final TripleViewer tv = new TripleViewer("");
      tv.initialize(username, domain);
      panel.add(anchordiv);
      panel.add(tv);
      section.add(panel);
      
      DiskREST.getHypothesis(id, 
          new Callback<Hypothesis, Throwable>() {
        public void onSuccess(Hypothesis result) {
          anchor.setHref(getHypothesisLink(id));
          anchor.setText(result.getName());
          if(result.getGraph() != null)
            tv.load(result.getGraph().getTriples());
        }
        public void onFailure(Throwable reason) {}
      });
    }
  }
  
  private void setHypothesisHTML(final String id, final DivElement section, 
      final TripleViewer tv, final AnchorElement anchor) {
    if(id == null) {
      section.setAttribute("style", "display:none");
      return;
    }
    section.setAttribute("style", "");

    DiskREST.getHypothesis(id, 
        new Callback<Hypothesis, Throwable>() {
      public void onSuccess(Hypothesis result) {
        anchor.setHref(getHypothesisLink(id));
        anchor.setInnerText(result.getName());
        if(result.getGraph() != null) {
          tv.setDefaultNamespace(getNamespace(id));
          tv.load(result.getGraph().getTriples());
        }
      }
      public void onFailure(Throwable reason) {}
    });  
  }
  
  private String getNamespace(String id) {
    return Config.getServerURL() + "/"+username+"/"+domain + "/hypotheses/" + id + "#";
  }
  
  private String getHypothesisLink(String id) {
    return "#" + NameTokens.getHypotheses()+"/" + this.username+"/"+this.domain + "/" + id;
  }

  private String getLOILink(String id) {
    return "#" + NameTokens.getLOIs()+"/" + this.username+"/"+this.domain + "/" + id;
  }
  
  private void setWorkflowsHTML(List<WorkflowBindings> wbindings, 
      ListWidget list) {
    list.clear();
    for(WorkflowBindings bindings: wbindings) {
      String type = bindings.getRun().getLink() == null ? "no-run-link" : "";
      ListNode tnode = new ListNode(bindings.getWorkflow(), 
          new HTML(bindings.getHTML()));
      tnode.setIcon("icons:dashboard");
      tnode.setIconStyle("orange");
      tnode.setData(bindings);
      tnode.setType(type);
      list.addNode(tnode);
    }
  }
  
  @UiHandler({"workflowlist", "metaworkflowlist"})
  void onWorkflowListAction(ListItemActionEvent event) {
    if(event.getAction().getId().equals("runlink")) {
      ListNode node = event.getItem();
      WorkflowBindings bindings = (WorkflowBindings) node.getData();
      Window.open(bindings.getRun().getLink(), "_blank", "");
    }
  }

}
