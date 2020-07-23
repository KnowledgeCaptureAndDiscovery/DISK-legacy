package org.diskproject.client.components.tloi;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.diskproject.client.Config;
import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.list.events.ListItemActionEvent;
import org.diskproject.client.components.triples.SparqlInput;
import org.diskproject.client.components.triples.TripleViewer;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.paper.widget.PaperButton;

public class TriggeredLOIViewer extends Composite {
  interface Binder extends UiBinder<Widget, TriggeredLOIViewer> {};
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField DivElement header;
  @UiField DivElement hypothesisSection, LOISection, DataSection, data, WFSection, MetaWFSection;
  @UiField LabelElement WFLabel;
  @UiField HTMLPanel revHypothesisSection;
  @UiField TripleViewer hypothesis;
  @UiField SparqlInput loi;
  @UiField ListWidget workflowlist, metaworkflowlist;
  @UiField AnchorElement hypothesisLink, loiLink;
  @UiField PaperButton downloadbutton;
  String username, domain;
  String rawcsv; //USED to save the downloadable CSV.
  String datamode = "all";
  TriggeredLOI tloi;
  Map<String, List<String>> dataRetrieved;

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

    List<WorkflowBindings> mwf = tloi.getMetaWorkflows();
    if (mwf.size() == 0) {
    	MetaWFSection.getStyle().setDisplay(Display.NONE);
    	WFLabel.setInnerText("Triggered Meta-Workflows");
    } else {
    	MetaWFSection.getStyle().setDisplay(Display.INITIAL);
    	WFLabel.setInnerText("Triggered Workflows");
    }
    	
    setWorkflowsHTML(tloi.getWorkflows(), workflowlist);
    setWorkflowsHTML(mwf, metaworkflowlist);
    
    setHypothesisHTML(tloi.getParentHypothesisId(), 
        hypothesisSection, hypothesis, hypothesisLink);
    setLOIHTML(tloi.getLoiId(), LOISection, loi);
    setRevisedHypothesesHTML(tloi.getResultingHypothesisIds(), revHypothesisSection);
    
    showHypothesisData(tloi.getParentHypothesisId());
  }

  private void showHypothesisData(final String id) {
    DiskREST.queryHypothesisData(id,
        new Callback<Map<String, List<String>>, Throwable>() {
      @Override
      public void onSuccess(Map<String, List<String>> result) {
    	  dataRetrieved = result;
    	  setDataHTML(data);
      }
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
    });
  }

  public TriggeredLOI getTLOI() {
    return this.tloi;
  }
  
  private void setDataHTML(final DivElement section) {
    if (dataRetrieved == null) {
      section.getStyle().setVisibility(Visibility.HIDDEN);
      downloadbutton.setVisible(false);
      return;
    }
    
    Set<String> vars = datamode.equals("all") ?
    		dataRetrieved.keySet() : getRelevantVariables();
    
    if (vars.size() == 0) {
    	section.setInnerHTML("No data retrieved.");
    	downloadbutton.setVisible(false);
    	return;
    }
    rawcsv = "";
    section.getStyle().setVisibility(Visibility.VISIBLE);
    downloadbutton.setVisible(true);
    
    int lines = 0;
    
    String html = "<table><thead><tr>";
    for (String v: vars) {
    	//GWT.log("---->" + v);
    	html += "<th><b>" + v + "</b></th>";
    	rawcsv += v + ",";
    	if (dataRetrieved.get(v).size() > lines) {
    		lines = dataRetrieved.get(v).size();
    	}
    }
    html += "</tr></thead><tbody>";
    rawcsv += "\n";
    
    for (int i = 0; i < lines; i++) {
    	html += "<tr>";
    	for (String v: vars) {
    		String url = dataRetrieved.get(v).get(i).replace(
    				"http://localhost:8080/enigma_new/index.php/Special:URIResolver/",
    				"http://organicdatapublishing.org/enigma_new/index.php/");
    		if (url.contains("http")) {
    			String parts[] = url.split("/");
    			String name = (parts.length>3) ?
    					((parts[parts.length-1].length() > 0) ? parts[parts.length-1] : parts[parts.length-2])
    					: url;
    			html += "<td><a href=\"" + url + "\" target=\"_blank\">" + name + "</a></td>";
    		} else {
    			html += "<td>" + url + "</td>";
    		}
    		rawcsv += dataRetrieved.get(v).get(i) + ",";
    	}
    	html += "</tr>";
    	rawcsv += "\n";
    }

    html += "</table>";
    
    section.setInnerHTML(html);
	  /*for (String var: dataRetrieved.keySet()) {
		  GWT.log("-" + var);
		  for (String bind: dataRetrieved.get(var)) {
			  GWT.log("  +" + bind);
		  }
	  }*/
  }

  private void setLOIHTML(final String id, final DivElement section, 
      final SparqlInput tv) {
    if(id == null) {
      section.setAttribute("style", "display:none");
      return;
    }
    section.setAttribute("style", "");
    
    DiskREST.getLOI(id, new Callback<LineOfInquiry, Throwable> () {
      public void onSuccess(LineOfInquiry result) {
        if(result != null && result.getDataQuery() != null) {
          tv.setValue(result.getDataQuery());
        }
      }
      public void onFailure(Throwable reason) {}
    });
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
          if(result.getGraph() != null) {
            tv.setDefaultNamespace(getNamespace(result.getId()));
            tv.load(result.getGraph().getTriples());
          }
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
		  // Get node and set loading text
		  ListNode node = event.getItem();
		  WorkflowBindings bindings = (WorkflowBindings) node.getData();
		  String base = bindings.getHTML();
		  String html = base + "<div>Loading...</div>";
		  node.setFullContent(html);     

		  //Request data from wings
		  WorkflowRun run = bindings.getRun();
		  String id = run.getId();
		  if (id != null) {
			  String[] lid = id.split("#|/");
			  id = lid[lid.length - 1];
		  }

		  GWT.log("Current Run:\n  ID: " + run.getId());
		  DiskREST.monitorWorkflow(id, new Callback<WorkflowRun, Throwable>() {
			  @Override
			  public void onSuccess(WorkflowRun result) {
				  //Save data
				  String sdate = result.getStartDate();
				  String edate = result.getEndDate();
				  List<String> outputs = result.getOutputs();
				  
				  if (sdate != null) run.setStartDate(sdate);
				  if (edate != null) run.setEndDate(edate);
				  if (outputs != null && outputs.size() > 0) run.setOutputs(outputs);
				  run.setFiles(result.getFiles());
				  
				  node.setFullContent(bindings.getHTML());
			  }
			  @Override
			  public void onFailure(Throwable reason) {
				  String html = base 
						  	  + "<div>An error has occurred retrieving this data from the Wings server."
						  	  + "Please try again. </div>";
				  node.setFullContent(html);
			  }
		  });

		  //Window.open(bindings.getRun().getLink(), "_blank", "");
	  }
  }

  	public static native void downloadCSV(String raw) /*-{
  		var blob = new Blob([raw], {type: 'text/csv;encoding:utf-8'});
        var a = document.createElement("a");
        a.style = "display: none";
        document.body.appendChild(a);
        var url = $wnd.window.URL.createObjectURL(blob);
        a.href = url;
        a.download = 'data.csv';
        a.click();
        window.URL.revokeObjectURL(url);
	}-*/;

	@UiHandler("downloadbutton")
	void onSaveButtonClicked(ClickEvent event) {
		GWT.log("download csv!");
		GWT.log(rawcsv);
		downloadCSV(rawcsv);
	}

	@UiHandler("dataRelevant")
	void onRelevantDataClicked(ClickEvent event) {
		datamode = "relevant";
    	setDataHTML(data);
	}

	@UiHandler("dataAll")
	void onAllDataClicked(ClickEvent event) {
		datamode = "all";
    	setDataHTML(data);
	}

    Set<String> getRelevantVariables () {
    	Set<String> r = new HashSet<String>();
    	ListWidget[] wfs = {workflowlist, metaworkflowlist};
    	for (ListWidget wf: wfs) {
			for (ListNode node: wf.getNodes()) {
				WorkflowBindings bindings = (WorkflowBindings) node.getData();
				for (VariableBinding vb: bindings.getBindings()) {
					r.add(vb.getVariable());
				}
			}
    	}
    	return r;
    }
  
}
