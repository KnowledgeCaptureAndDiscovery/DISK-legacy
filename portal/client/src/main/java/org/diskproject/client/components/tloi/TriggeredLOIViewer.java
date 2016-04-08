package org.diskproject.client.components.tloi;

import java.util.List;

import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
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
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

public class TriggeredLOIViewer extends Composite {
  interface Binder extends UiBinder<Widget, TriggeredLOIViewer> {};
  private static Binder uiBinder = GWT.create(Binder.class);
  
  @UiField DivElement name, description;
  @UiField DivElement hypothesisSection, revHypothesisSection;
  @UiField TripleViewer hypothesis, revHypothesis;
  @UiField ListWidget workflowlist, metaworkflowlist;
  @UiField AnchorElement hypothesisLink, revHypothesisLink;
  
  String username, domain;
  TriggeredLOI tloi;
  
  public TriggeredLOIViewer() {
    initWidget(uiBinder.createAndBindUi(this));
  }
  
  public void initialize(String username, String domain) {
    this.username = username;
    this.domain = domain;
    hypothesis.initialize(username, domain);
    revHypothesis.initialize(username, domain);
  }
  
  public void load(TriggeredLOI tloi) {
    this.tloi = tloi;
    name.setInnerText(tloi.getName());
    String desctxt = tloi.getDescription();
    if(tloi.getStatus() != null) {
      desctxt += "<div class='"+tloi.getStatus()+"'>";
      desctxt += tloi.getStatus()+"</div>";
    }
    description.setInnerHTML(desctxt);
    setWorkflowsHTML(tloi.getWorkflows(), workflowlist);
    setWorkflowsHTML(tloi.getMetaWorkflows(), metaworkflowlist);
    setHypothesisHTML(tloi.getParentHypothesisId(), 
        hypothesisSection, hypothesis, hypothesisLink);
    setHypothesisHTML(tloi.getResultingHypothesisId(), 
        revHypothesisSection, revHypothesis, revHypothesisLink);
  }
  
  public TriggeredLOI getTLOI() {
    return this.tloi;
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
        if(result.getGraph() != null)
          tv.load(result.getGraph().getTriples());
      }
      public void onFailure(Throwable reason) {}
    });  
  }
  
  private String getHypothesisLink(String id) {
    return NameTokens.getHypotheses()+"/" + this.username+"/"+this.domain + "/" + id;
  }
  
  private void setWorkflowsHTML(List<WorkflowBindings> wbindings, 
      ListWidget list) {
    list.clear();
    for(WorkflowBindings bindings: wbindings) {
      String id = bindings.getWorkflow();
      
      String html = "<div class='name'>"+id+"</div>";
      html += "<div class='description'>";
      String description = bindings.getBindingsDescription();
      if(!description.equals(""))
        html += "<b>Variable Bindings:</b> "+description + "<br />";
      String status = bindings.getRun().getStatus();
      if(status != null) {
        html += "<div><span class='"+status+"'>"
            + bindings.getRun().getStatus()+"</span>\n";
        html += " [ <a target='_blank' "
            + "href='"+bindings.getRun().getLink()+"'>"
            + "Run details</a> ]</div>";
      }
      html += "</div>";
      
      ListNode tnode = new ListNode(id, new HTML(html));
      tnode.setIcon("icons:dashboard");
      tnode.setIconStyle("orange");
      tnode.setData(bindings);
      list.addNode(tnode);
    }
  }

}
