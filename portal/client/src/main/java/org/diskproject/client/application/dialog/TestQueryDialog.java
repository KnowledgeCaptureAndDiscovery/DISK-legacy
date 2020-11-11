package org.diskproject.client.application.dialog;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.diskproject.client.components.triples.SparqlInput;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.util.KBConstants;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.core.client.Callback;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.Widget;

public class TestQueryDialog extends DialogBox { //implements ClickHandler {
  interface Binder extends UiBinder<Widget, TestQueryDialog> {};
  private static Binder uiBinder = GWT.create(Binder.class);

  private String dataQuery, requestedVariables;
  @UiField SparqlInput sparqlQuery, sparqlVariables;
  @UiField DivElement resultContainer;

  public TestQueryDialog() {
    setText("Test data query");
    setAnimationEnabled(true);
    setWidget(uiBinder.createAndBindUi(this));
	GWT.log("A");
    //initialize();
  }

  private void initialize () {
    //LOAD vocabularies TODO
    sparqlQuery.loadVocabulary("bio", KBConstants.OMICSURI(), new Callback<String, Throwable>() {
      public void onSuccess(String result) {
        sparqlQuery.setValue(dataQuery);
        GWT.log("B");
      }
      public void onFailure(Throwable reason) {
        GWT.log("C");
      }
    });
  }

  public void show () {
    super.show();
    center();
  }

  @UiHandler("cancelButton")
  void cancelButtonClicked(ClickEvent event) {
    hide();
  }

  @UiHandler("sendButton")
  void sendButtonClicked(ClickEvent event) {
    /*if (!sparqlQuery.validate()) {
      return;
    }*/

    String query = sparqlQuery.getValue();
    String variables = sparqlVariables.getValue();
    GWT.log("query*" + query);
    GWT.log("vars*" + variables);
    resultContainer.setInnerHTML("Loading...");
    DiskREST.queryExternalStore(query, variables, new Callback<Map<String, List<String>>, Throwable>() {
      @Override
      public void onSuccess(Map<String, List<String>> result) {
        renderResults(result);
      }
      @Override
      public void onFailure(Throwable reason) {
        resultContainer.setInnerHTML("An error occurred while executing the query. Please try again.");
      }
    });
  }

  @UiHandler("saveButton")
  void saveButtonClicked(ClickEvent event) {
  }

  public void setDataQuery (String dq) {
    GWT.log("SETTING DATA QUERY: " + dq);
    dataQuery = dq;
    sparqlQuery.setValue(dataQuery);
  }

  public void setVariables (String variables) {
    GWT.log("SETTING VARIABLES: " + variables);
    requestedVariables = variables;
    sparqlVariables.setValue(requestedVariables);
  }

  private void renderResults (Map<String, List<String>> results) {
    if (results != null) {
      Set<String> vars = results.keySet();
      if (vars != null && vars.size() > 0) {
        int lines = 0;
        String html = "<table class=\"pure-table\"><thead><tr>";
        for (String v: vars) {
          html += "<th><b>" + v + "</b></th>";
          if (results.get(v).size() > lines) {
            lines = results.get(v).size();
          }
        }
        html += "</tr></thead><tbody>";

        for (int i = 0; i < lines; i++) {
          html += "<tr>";
          for (String v: vars) {
            String url = results.get(v).get(i).replace(
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
          }
          html += "</tr>";
        }
        html += "</table>";
        resultContainer.setInnerHTML("<table style=\"width:100%\">" + html + "</table>");
        return;
      }
    }
    resultContainer.setInnerHTML("No results found. Please check your query and try again.");
  }
}