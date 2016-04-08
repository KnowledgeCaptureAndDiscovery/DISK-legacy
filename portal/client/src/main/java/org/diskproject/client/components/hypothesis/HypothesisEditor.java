package org.diskproject.client.components.hypothesis;

import org.diskproject.client.components.hypothesis.events.HasHypothesisHandlers;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveEvent;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveHandler;
import org.diskproject.client.components.triples.HypothesisTripleInput;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.util.KBConstants;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.paper.PaperInputElement;
import com.vaadin.polymer.paper.PaperTextareaElement;
import com.vaadin.polymer.paper.widget.PaperDialog;
import com.vaadin.polymer.paper.widget.PaperDialogScrollable;

public class HypothesisEditor extends Composite 
    implements HasHypothesisHandlers {
  private HandlerManager handlerManager;
  
  interface Binder extends UiBinder<Widget, HypothesisEditor> {}
  
  int loadcount=0;
  
  String userid, domain;
  Hypothesis hypothesis;
  
  @UiField PaperInputElement name;
  @UiField PaperTextareaElement description;
  @UiField HypothesisTripleInput triples;
  @UiField PaperDialog triggerdialog;
  @UiField PaperDialogScrollable dialogcontent;

  private static Binder uiBinder = GWT.create(Binder.class);
  
  public HypothesisEditor() {
    initWidget(uiBinder.createAndBindUi(this));
    handlerManager = new HandlerManager(this); 
  }
  
  public void initialize(String username, String domain) {
    this.userid = username;
    this.domain = domain;
    triples.setDomainInformation(username, domain);
    this.loadVocabularies();
  }
  
  public void load(Hypothesis hypothesis) {
    this.hypothesis = hypothesis;
    name.setValue(hypothesis.getName());
    description.setValue(hypothesis.getDescription());
    if(hypothesis.getGraph() != null && loadcount==3)
      triples.setValue(hypothesis.getGraph().getTriples());
  }
  
  public void setNamespace(String ns) {
    this.triples.setDefaultNamespace(ns);
  }
  
  private void loadVocabularies() {
    loadcount=0;
    triples.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
    triples.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
    triples.loadUserVocabulary("user", this.userid, this.domain, vocabLoaded);
  }
  
  private Callback<String, Throwable> vocabLoaded = 
      new Callback<String, Throwable>() {
    public void onSuccess(String result) {
      loadcount++;
      if(hypothesis != null && hypothesis.getGraph() != null && loadcount==3)
        triples.setValue(hypothesis.getGraph().getTriples());
    }
    public void onFailure(Throwable reason) {}
  };
  
  @UiHandler("savebutton")
  void onSaveButtonClicked(ClickEvent event) {
    boolean ok1 = this.name.validate();
    boolean ok2 = this.description.validate();
    boolean ok3 = this.triples.validate();
    
    if(!ok1 || !ok2 || !ok3) {
      AppNotification.notifyFailure("Please fix errors before saving");
      return;
    }
    
    hypothesis.setDescription(description.getValue());
    hypothesis.setName(name.getValue());
    Graph graph = new Graph();
    graph.setTriples(triples.getTriples());
    hypothesis.setGraph(graph);
    
    fireEvent(new HypothesisSaveEvent(hypothesis));
  }
  
  @UiHandler("runbutton")
  void onRunButtonClicked(ClickEvent event) {
    History.newItem(this.getQueryHistoryToken(hypothesis.getId()));
  }
  
  private String getQueryHistoryToken(String id) {    
    return NameTokens.getHypotheses()+"/" + this.userid+"/"+this.domain + "/" 
        + id + "/query";    
  }
  
  @Override
  public void fireEvent(GwtEvent<?> event) {
    handlerManager.fireEvent(event);
  }
  
  @Override
  public HandlerRegistration addHypothesisSaveHandler(
      HypothesisSaveHandler handler) {
    return handlerManager.addHandler(HypothesisSaveEvent.TYPE, handler);
  }
}
