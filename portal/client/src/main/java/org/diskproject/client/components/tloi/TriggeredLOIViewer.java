package org.diskproject.client.components.tloi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.diskproject.client.Config;
import org.diskproject.client.components.brain.Brain;
import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.list.events.ListItemActionEvent;
import org.diskproject.client.components.triples.SparqlInput;
import org.diskproject.client.components.triples.TripleViewer;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;
import org.diskproject.shared.classes.util.GUID;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.paper.widget.PaperButton;
import com.vaadin.polymer.paper.widget.PaperDialog;
import com.vaadin.polymer.iron.widget.event.IronOverlayClosedEvent;

public class TriggeredLOIViewer extends Composite {
  interface Binder extends UiBinder<Widget, TriggeredLOIViewer> {};
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField DivElement header;
  @UiField DivElement hypothesisSection, LOISection, dataDiv, WFSection, MetaWFSection, brainSection, shinySection;
  @UiField LabelElement WFLabel;
  @UiField Label DataLabel;
  @UiField HTMLPanel revHypothesisSection, DataSection, DataQuerySection;
  @UiField HTMLPanel executionNarrative, dataQueryNarrative;
  @UiField TripleViewer hypothesis;
  @UiField SparqlInput dataQuery;
  @UiField ListWidget workflowlist, metaworkflowlist;
  @UiField AnchorElement hypothesisLink, loiLink;
  @UiField PaperButton downloadbutton, triplesbutton, editBindingsButton, runButton;
  @UiField PaperDialog editBindingsDialog;
  @UiField BindingsEditor bindingseditor;
  @UiField Brain brainVisualization;
  private Status status;
  private boolean brainInitialized = false;

  String username, domain;
  String rawcsv; //USED to save the downloadable CSV.
  String datamode = "all";
  TriggeredLOI tloi;
  Map<String, List<String>> dataRetrieved;
  @UiField CheckBox showdata, showdq, showExecNarrative, showDataQueryNarrative;


  public TriggeredLOIViewer() {
    initWidget(uiBinder.createAndBindUi(this));
  }

  public void initialize(String username, String domain) {
    this.username = username;
    this.domain = domain;
    editBindingsButton.setVisible(false);
    hypothesis.initialize(username, domain);
    workflowlist.addCustomAction("runlink", "Run details", 
        "icons:build", "blue-button run-link");
    metaworkflowlist.addCustomAction("runlink", "Run details", 
        "icons:build", "blue-button run-link");    
    downloadbutton.setVisible(false);
    triplesbutton.setVisible(false);
    DataSection.setVisible(false);
    DataQuerySection.setVisible(false);
    DataLabel.setVisible(false);
    brainSection.getStyle().setDisplay(Display.NONE);
    
    //brainVisualization.setVisible(false);
  }
  
  public void enableBindingEdition () {
	  // Only show button if theres multiple files. FIXME: only works for the first wf mwf.
	  List<WorkflowBindings> wfs = tloi.getWorkflows();
	  List<WorkflowBindings> metawfs = tloi.getMetaWorkflows();

	  if (wfs.size() > 0) {
		  List<String> cv = wfs.get(0).getCollectionVariables();
		  if (cv.size() > 0) {
			  editBindingsButton.setVisible(true);
		  }
	  } else if (metawfs.size() > 0) {
		  List<String> cv = metawfs.get(0).getCollectionVariables();
		  if (cv.size() > 0) {
			  editBindingsButton.setVisible(true);
		  }
	  }
  }

  public void load(TriggeredLOI tloi) {
    this.tloi = tloi;
    status = tloi.getStatus();
    runButton.setVisible(status==null);
    
    //header.setInnerHTML(tloi.getHeaderHTML());
    setHeader(tloi);
    setLOILink(tloi.getName(), tloi.getLoiId(), loiLink);

    List<WorkflowBindings> mwf = tloi.getMetaWorkflows();
    WFLabel.setInnerText("Triggered Workflows");
    if (mwf.size() == 0) {
    	MetaWFSection.getStyle().setDisplay(Display.NONE);
    } else {
    	MetaWFSection.getStyle().setDisplay(Display.INITIAL);
    }
    
    if (tloi.getExplanation() != null) {
    	DataLabel.setText(tloi.getExplanation());
    }
    	
    setWorkflowsHTML(tloi.getWorkflows(), workflowlist);
    setWorkflowsHTML(mwf, metaworkflowlist);
    
    setHypothesisHTML(tloi.getParentHypothesisId(), 
        hypothesisSection, hypothesis, hypothesisLink);
    setDataQueryHTML(tloi.getDataQuery(), LOISection, dataQuery);
    setRevisedHypothesesHTML(tloi.getResultingHypothesisIds(), revHypothesisSection);
    
    if (status == Status.SUCCESSFUL) {
    	loadNarratives(tloi);
    }
  }
  
  private void setHeader(TriggeredLOI tloi) {
	  String extra ="", extracls="";
	  Status status = tloi.getStatus();
	  if (status == null) {
		  extracls = " TORUN";
	  } else {
		  String icon = "icons:hourglass-empty";
		  if(status == Status.SUCCESSFUL) {
			  icon = "icons:check";
		  }
		  else if(status == Status.FAILED) {
			  icon = "icons:clear";
		  }
		  extra = " <iron-icon class='"+status+"' icon='"+icon+"' />";
		  extracls = " " +status;
	  }
	  
	  String html = "<div class='name" + extracls+ "'>" + tloi.getName() + extra +"</div>";
	  
	  if (tloi.getDescription() != null) {
		  html += "<div class='description'>" + tloi.getDescription() + "</div>";
	  }

    /* TODO: add date to tloi.
    html += "<div class='footer' style='display: flex;justify-content: space-between;'>";
    html += "<span><b>Creation date:</b> ";
    html += (this.creationDate != null) ? this.creationDate : "None specified";
    html += "</span><span><b>Author:</b> ";
    html += (this.author != null) ? this.author : "None specified";
    html += "</span></div>";*/

	  header.setInnerHTML(html);
  }

  private void loadNarratives (TriggeredLOI tloi) {
	  String id = tloi.getId();
      DiskREST.getTLOINarratives(id, new Callback<Map<String,String>, Throwable>() {
        @Override
        public void onSuccess(Map<String, String> response) {
          if (response != null) {
        	  executionNarrative.clear();
        	  executionNarrative.add(new HTML(response.get("execution")));
        	  onClickShowExecutionNarrative(null);
        	  dataQueryNarrative.clear();
        	  dataQueryNarrative.add(new HTML(response.get("dataquery")));
        	  onClickShowDataQueryNarrative(null);
          }
        }
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure(reason.getMessage());
        }
	  });
  }

  @UiHandler("showdq")
  void onClickShowDQ(ClickEvent event) {
    boolean show = showdq.getValue();
    if (show) {
      dataQuery.setVisible(false);
      dataQuery.setValue("");
      dataQuery.setVisible(true);
      dataQuery.setValue(tloi.getDataQuery());
    }
    DataQuerySection.setVisible(show);
  }

  @UiHandler("showdata")
  void onClickShowData(ClickEvent event) {
    boolean show = showdata.getValue();
    if (show) {
      if (dataRetrieved == null) loadAndShowData();
      DataSection.setVisible(true);
      DataLabel.setVisible(true);
    } else {
      DataSection.setVisible(false);
      DataLabel.setVisible(false);
    }
  }

  @UiHandler("showExecNarrative")
  void onClickShowExecutionNarrative(ClickEvent event) {
    boolean show = showExecNarrative.getValue();
    executionNarrative.setVisible(show);
  }

  @UiHandler("showDataQueryNarrative")
  void onClickShowDataQueryNarrative(ClickEvent event) {
    boolean show = showDataQueryNarrative.getValue();
    dataQueryNarrative.setVisible(show);
  }

  private void loadAndShowData () {
    String vars = tloi.getRelevantVariables();
    String dq = tloi.getDataQuery();
    if (vars != null && dq != null && dataRetrieved == null) {
      dataDiv.setInnerText("Loading...");
      DiskREST.queryExternalStore(dq, vars, new Callback<Map<String, List<String>>, Throwable>() {
        @Override
        public void onSuccess(Map<String, List<String>> response) {
          if (response != null) {
            dataRetrieved = response;
            setDataHTML(dataDiv);
          }
        }
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure(reason.getMessage());
          dataDiv.setInnerText("An error has ocurred");
        }
      });
    }
  }


  private void showHypothesisData() {
    String vars = tloi.getRelevantVariables();
    String dq = tloi.getDataQuery();
    if (vars != null && dq != null) {
      DiskREST.queryExternalStore(dq, vars, new Callback<Map<String, List<String>>, Throwable>() {
        @Override
        public void onSuccess(Map<String, List<String>> response) {
          if (response != null) {
            dataRetrieved = response;
            setDataHTML(dataDiv);
          }
        }
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure(reason.getMessage());
        }
      });
    }
  }

  public TriggeredLOI getTLOI() {
    return this.tloi;
  }
  
  private void setDataHTML(final DivElement section) {
    if (dataRetrieved == null) {
      DataSection.setVisible(false);
      DataLabel.setVisible(false);
      downloadbutton.setVisible(false);
      triplesbutton.setVisible(false);
      return;
    }
    
    Set<String> vars = dataRetrieved.keySet();
    
    if (vars.size() == 0) {
    	section.setInnerHTML("No data retrieved.");
    	downloadbutton.setVisible(false);
    	triplesbutton.setVisible(false);
    	return;
    }
    rawcsv = "";
    DataSection.setVisible(true);
    DataLabel.setVisible(true);
    downloadbutton.setVisible(true);
    if (this.tloi != null) {
    	Status s = this.tloi.getStatus();
    	if (s != null) triplesbutton.setVisible(true);
    }
    
    int lines = 0;
    
    String html = "<table class=\"pure-table\"><thead><tr>";
    for (String v: vars) {
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
  }

  private void setDataQueryHTML(final String strDataQuery, final DivElement section, 
      final SparqlInput tv) {
    if (strDataQuery == null || strDataQuery.equals("")) {
      section.setAttribute("style", "display:none");
      return;
    }
    section.setAttribute("style", "");
    GWT.log("dataquery: " + strDataQuery);
    tv.setValue(strDataQuery);
    GWT.log(tv.getValue());
  }

  private void setLOILink(String tname, String id, AnchorElement anchor) {
    String name = tname.replace("Triggered: ", "").replace("New: ", "");
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
      anchordiv.addStyleName("rev-hyp-title");
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
          if (result != null) {
			  anchor.setText(result.getName());
			  if (result.getGraph() != null) {
				tv.setDefaultNamespace(getNamespace(result.getId()));
				List<Triple> triples = result.getGraph().getTriples();
				tv.load(triples);
				//write the confidence value.
				String cv = "";
				for(final Triple t : triples) {
					if (t.getDetails() != null) {
						cv = "" + t.getDetails().getConfidenceValue();
						if (cv.length() > 4) cv = cv.substring(0, 4);
						break;
					}
				}
				if (!cv.contentEquals("")) {
					final Anchor pval = new Anchor("Confidence: " + cv);
					anchordiv.add(pval);
				}
			  }
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
        if (result.getName() != null) {
          anchor.setInnerText(result.getName());
        }
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
				  Map<String, String> outputs = result.getOutputs();
				  
				  if (sdate != null) run.setStartDate(sdate);
				  if (edate != null) run.setEndDate(edate);
				  if (outputs != null && outputs.size() > 0) run.setOutputs(outputs);
				  run.setFiles(result.getFiles());
				  
				  for (String key: outputs.keySet()) {
					  GWT.log(key + ": " + outputs.get(key));
				  }
				  
				  if (outputs.keySet().contains("brain_visualization")) {
					  //This workflow returns a brain visualizations:
					  //TODO: Assuming theres only one brainviz per tloi.
					  String sp[] = outputs.get("brain_visualization").split("#");
					  String id = sp[sp.length-1];
					  loadBrainViz(id);
				  }

				  if (outputs.keySet().contains("shiny_visualization")) {
					  //This workflow returns a shiny visualization
					  //TODO: Assuming theres only one shiny viz per tloi.
					  String sp[] = outputs.get("shiny_visualization").split("#");
					  String id = sp[sp.length-1];
					  loadShinyViz(id);
				  }
				  
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

  	@UiField IFrameElement shinyIframe;
  	private void loadShinyViz (String shinyLog) {
		DiskREST.getDataFromWingsAsJS(shinyLog, new Callback<JavaScriptObject, Throwable>() {
			@Override
			public void onSuccess(JavaScriptObject result) {
				// TODO Auto-generated method stub
				String url = getShinyURL(result);
				if (url != null && !url.equals("")) {
					GWT.log("RETURN FROM shinyLog " + url);
					shinyIframe.setSrc(url);
				}
			}
			
			@Override
			public void onFailure(Throwable reason) {
				// TODO Auto-generated method stub
				
			}
		});
  		
  	}

  	public static native String getShinyURL(JavaScriptObject shinyobj) /*-{
  		return shinyobj && shinyobj.url ? shinyobj.url : "";
	}-*/;

  	private void loadBrainViz (String brainVizId) {
		if (!brainInitialized) { 
			brainVisualization.initialize();
			brainInitialized = true;
		}

		DiskREST.getDataFromWingsAsJS(brainVizId, new Callback<JavaScriptObject, Throwable>() {
			@Override
			public void onSuccess(JavaScriptObject result) {
				// TODO Auto-generated method stub
				brainVisualization.loadBrainConfigurationFromJSObject(result);
				//brainSection.getStyle().setVisibility(Visibility.VISIBLE);
				brainSection.getStyle().setDisplay(Display.INITIAL);
			}
			
			@Override
			public void onFailure(Throwable reason) {
				// TODO Auto-generated method stub
				
			}
		});
  	}

  	public static native void download(String name, String raw, String enc) /*-{
  		var blob = new Blob([raw], {type: enc});
        var a = document.createElement("a");
        a.style = "display: none";
        document.body.appendChild(a);
        var url = $wnd.window.URL.createObjectURL(blob);
        a.href = url;
        a.download = name;
        a.click();
        window.URL.revokeObjectURL(url);
	}-*/;

	@UiHandler("downloadbutton")
	void onSaveButtonClicked(ClickEvent event) {
		String name = (this.tloi != null) ? this.tloi.getId() + "_metadata.csv" : "metadata.csv";
		download(name, rawcsv, "text/csv;encoding:utf-8");
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
 
	@UiHandler("triplesbutton")
	void onDlTriplesButtonClicked(ClickEvent event) {
		GWT.log("Downloading triples...");
		TriggeredLOI t = tloi;
		DiskREST.getTriggeredLOITriples(t.getId(),
			new Callback<List<Triple>, Throwable>() {
		  @Override
		  public void onSuccess(List<Triple> result) {
			  String name = (t != null) ? t.getId() + "_triples.nt" : "triples.nt";
			  String cont = "";
			  for (Triple t: result) {
				  cont += t.toString() + " .\n";
			  }
			  GWT.log(cont);
			  download(name, cont, "application/n-triples");
		  }
		  @Override
		  public void onFailure(Throwable reason) {
			AppNotification.notifyFailure(reason.getMessage());
		  }
		});
	}

	@UiHandler("runButton")
	void onRunButtonClicked(ClickEvent event) {
		tloi.setName(tloi.getName().replace("New:", "Triggered:"));
		DiskREST.addTriggeredLOI(tloi, new Callback<Void, Throwable>() {
			@Override
			public void onFailure(Throwable reason) {
				AppNotification.notifyFailure(reason.getMessage());
			}
			@Override
			public void onSuccess(Void result) {
				AppNotification.notifySuccess("Submitted.", 1000);
				String token = NameTokens.getTLOIs() + "/" + username + "/"
						+ domain + "/" + tloi.getId();
				History.newItem(token, true);
			}
		});
	}

	@UiHandler("editBindingsButton")
	void onEditBindingsButtonClicked(ClickEvent event) {
		//For the moment this works with only one workflow!
		List<WorkflowBindings> metawfs = tloi.getMetaWorkflows();
		List<WorkflowBindings> wfs = tloi.getWorkflows();
		if (metawfs.size() > 0) {
			bindingseditor.setWorkflowBindings(metawfs.get(0));
		} else if (wfs.size() > 0) {
			bindingseditor.setWorkflowBindings(wfs.get(0));
		} else {
			GWT.log("Cannot edit, workflow not found.");
		}

		editBindingsDialog.open();
	}

	@UiHandler("editBindingsDialog")
	void onDialogClose(IronOverlayClosedEvent event) {
		if(!isConfirmed(event.getPolymerEvent().getDetail()))
			return;
		
		//For the moment this works with only one workflow!
		List<WorkflowBindings> metawfs = tloi.getMetaWorkflows();
		List<WorkflowBindings> wfs = tloi.getWorkflows();
		
		List<WorkflowBindings> toSet = new ArrayList<WorkflowBindings>();
		toSet.add( bindingseditor.getWorkflowBindings() );
		
		if (metawfs.size() > 0) {
			tloi.setMetaWorkflows(toSet);
		} else if (wfs.size() > 0) {
			tloi.setWorkflows(toSet);
		} else {
			GWT.log("Cannot edit, workflow not found.");
		}
		// Clear some properties
		tloi.setResultingHypothesisIds(new ArrayList<String>());
		tloi.setConfidenceValue(0);
		tloi.setStatus(null);
		tloi.setAuthor(null);
		tloi.setDateCreated(null);
		tloi.setId(GUID.randomId("TriggeredLOI"));
		load(tloi);
	}

	private native boolean isConfirmed(Object obj) /*-{
    	return obj.confirmed;
  	}-*/;  
}
