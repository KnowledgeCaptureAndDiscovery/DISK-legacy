package org.diskproject.client.components.loi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.diskproject.client.components.loi.events.HasLOIHandlers;
import org.diskproject.client.components.loi.events.LOISaveEvent;
import org.diskproject.client.components.loi.events.LOISaveHandler;
import org.diskproject.client.components.triples.SparqlInput;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.Workflow;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.paper.PaperInputElement;
import com.vaadin.polymer.paper.PaperTextareaElement;

public class LOIEditor extends Composite 
    implements HasLOIHandlers {
  private HandlerManager handlerManager;

  interface Binder extends UiBinder<Widget, LOIEditor> {};

  String userid, domain;
  
  boolean editmode;
  boolean metamode;
  int loadcount=0;
  
  @UiField PaperInputElement name;
  @UiField PaperTextareaElement description;
  @UiField SparqlInput hypothesisQuery;
  @UiField SparqlInput dataQuery;
  @UiField LOIWorkflowList workflowlist, metaworkflowlist;

  @UiField SparqlInput testQuery;
  @UiField DivElement resultContainer;
  @UiField DialogBox testDialog; 
  @UiField ListBox displayVariables; 
  
  LineOfInquiry loi;
  List<List<List<String>>> testResults = null;
  
  private static Binder uiBinder =
      GWT.create(Binder.class);

  public LOIEditor() {
    initWidget(uiBinder.createAndBindUi(this));
    handlerManager = new HandlerManager(this);    
  }
  
  public void initialize(String userid, String domain) {
    this.userid = userid;
    this.domain = domain;
    this.loadVocabularies();
    this.loadWorkflows();
  }
  
  public void load(LineOfInquiry loi) {
    this.loi = loi;
    name.setValue(loi.getName());
    description.setValue(loi.getDescription());
    if(loi.getHypothesisQuery() != null && loadcount==12) {
      hypothesisQuery.setValue(loi.getHypothesisQuery());
    }
    if(loi.getDataQuery() != null && loadcount==12) {
      dataQuery.setValue(loi.getDataQuery());    
      testQuery.setValue(loi.getDataQuery());    
    }
    workflowlist.loadBindingsList(loi.getWorkflows());
    metaworkflowlist.loadBindingsList(loi.getMetaWorkflows());  
  }
  
  public void setNamespace(String ns) {
    hypothesisQuery.setDefaultNamespace(ns);
    dataQuery.setDefaultNamespace(ns);
    testQuery.setDefaultNamespace(ns);
  }
  
  private void loadVocabularies() {
    loadcount=0;
    hypothesisQuery.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
    hypothesisQuery.loadVocabulary("neuro", KBConstants.NEUROURI(), vocabLoaded);
    hypothesisQuery.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
    hypothesisQuery.loadUserVocabulary("user", userid, domain, vocabLoaded);
    
    dataQuery.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
    dataQuery.loadVocabulary("neuro", KBConstants.NEUROURI(), vocabLoaded);
    dataQuery.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
    dataQuery.loadUserVocabulary("user", userid, domain, vocabLoaded);

    testQuery.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
    testQuery.loadVocabulary("neuro", KBConstants.NEUROURI(), vocabLoaded);
    testQuery.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
    testQuery.loadUserVocabulary("user", userid, domain, vocabLoaded);
  }
  
  private Callback<String, Throwable> vocabLoaded = 
      new Callback<String, Throwable>() {
    public void onSuccess(String result) {
      loadcount++;
      if (loi != null && loi.getHypothesisQuery() != null && loadcount==16) {
        hypothesisQuery.setValue(loi.getHypothesisQuery());
      }
      if (loi != null && loi.getDataQuery() != null && loadcount==16) {
        dataQuery.setValue(loi.getDataQuery());
        testQuery.setValue(loi.getDataQuery());
      }
    }
    public void onFailure(Throwable reason) {}
  };
  
  private void loadWorkflows() {
    DiskREST.listWorkflows(new Callback<List<Workflow>, Throwable>() {
      @Override
      public void onSuccess(List<Workflow> result) {
        Collections.sort(result, new Comparator<Workflow>() {
          @Override
          public int compare(Workflow o1, Workflow o2) {
            return o1.getName().compareTo(o2.getName());
          }
        });
        workflowlist.setWorkflowList(result);
        metaworkflowlist.setWorkflowList(result);
        metaworkflowlist.setWorkflowSource(workflowlist);
      }      
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
    });
  }
  
  @UiHandler("savebutton")
  void onSaveButtonClicked(ClickEvent event) {   
    boolean ok1 = this.name.validate();
    boolean ok2 = this.description.validate();
    boolean ok3 = this.hypothesisQuery.validate();
    boolean ok4 = this.dataQuery.validate();
    if(!ok1 || !ok2 || !ok3 || !ok4) {
      AppNotification.notifyFailure("Please fix errors before saving");
      return;
    }
    
    loi.setDescription(description.getValue());
    loi.setName(name.getValue());
    loi.setHypothesisQuery(hypothesisQuery.getValue());
    loi.setDataQuery(dataQuery.getValue());
    loi.setWorkflows(workflowlist.getBindingsList());
    loi.setMetaWorkflows(metaworkflowlist.getBindingsList());
    
    fireEvent(new LOISaveEvent(loi));
  }

  @UiHandler("okButton")
  void onOkButtonClicked(ClickEvent event) {
	  dataQuery.setValue(testQuery.getValue());
	  testDialog.hide();
  }

  @UiHandler("cancelButton")
  void onCancelButtonClicked(ClickEvent event) {
	  testDialog.hide();
  }

  @UiHandler("sendTest")
  void onSendClicked(ClickEvent event) {
    if (!this.testQuery.validate()) {
      AppNotification.notifyFailure("Please fix errors before sending the query");
      return;
    }
    
    String query = testQuery.getValue();
    
    resultContainer.setInnerHTML("Loading...");
    GWT.log("Hyp: " + hypothesisQuery.getValue());
    GWT.log("WF:" + workflowlist.getBindingsList());
    GWT.log("Doing query...");

    DiskREST.testLOI(query, new Callback<List<List<List<String>>>, Throwable>() {
        @Override
        public void onSuccess(List<List<List<String>>> result) {
          GWT.log("Success");
          testResults = result;
          renderTestResults();
        }
        @Override
        public void onFailure(Throwable reason) {
          GWT.log("onFailure");
          resultContainer.setInnerHTML("An error occurred while executing the query. Please try again.");
        }
      });
  }

  @UiHandler("displayVariables")
  void onChange(ChangeEvent event) {
	  renderTestResults();
  }

  void renderTestResults () {
	if (testResults != null && testResults.size() > 0) {
		String dv = displayVariables.getSelectedValue();
		List<String> wfVariables = new ArrayList<String>();
		List<String> resVariables = new ArrayList<String>();

		for (WorkflowBindings wf:  workflowlist.getBindingsList()) {
			for (VariableBinding binding: wf.getBindings()) {
				wfVariables.add(binding.getBinding().replace("[?", "").replace("]", ""));
			}
		}

		if (testResults.size() > 0) {
		  for (List<String> row: testResults.get(0)) {
			 resVariables.add(row.get(0));
		  }
		}

		List<String> variables = null;
		if (dv.contentEquals("wf")) {
			variables = wfVariables;
		} else {
			variables = resVariables;
		}
		String innerHTML = "<tr><th>#</th>"; 
		
		for (String v: variables) {
			innerHTML += "<th>" + v + "</th>";
		}
		innerHTML += "</tr>";

		int i = 1;
		for (List<List<String>> rows: testResults) {
			innerHTML += "<tr><td>" + String.valueOf(i++) + "</td>";
			for (List<String> row: rows) {
				if (variables.contains(row.get(0))) {
					String uri = row.get(1).replace("http://localhost:8080/enigma_new/index.php/", "");
					if (uri.contains("http")) {
						String link = "<a target=\"_blank\" href=\"" + uri + "\">" + uri + "</a>";
						uri = link;
					}
					innerHTML += "<td>" + uri + "</td>";
				}
			}
			innerHTML += "</tr>";
		}
		resultContainer.setInnerHTML("<table style=\"width:100%\">" + innerHTML + "</table>");
	} else {
		resultContainer.setInnerHTML("No results found. Please check your query and try again.");
	}
  }

  @UiHandler("testbutton")
  void onTestButtonClicked(ClickEvent event) {   
	  testQuery.setValue(dataQuery.getValue());

	  testDialog.center();
  }  

  @Override
  public void fireEvent(GwtEvent<?> event) {
    handlerManager.fireEvent(event);
  }

  @Override
  public HandlerRegistration addLOISaveHandler(
      LOISaveHandler handler) {
    return handlerManager.addHandler(LOISaveEvent.TYPE, handler);
  }

}
