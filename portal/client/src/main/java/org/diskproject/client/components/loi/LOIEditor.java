package org.diskproject.client.components.loi;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.diskproject.client.components.loi.events.HasLOIHandlers;
import org.diskproject.client.components.loi.events.LOISaveEvent;
import org.diskproject.client.components.loi.events.LOISaveHandler;
import org.diskproject.client.components.triples.TripleInput;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.workflow.Workflow;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
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
  @UiField TripleInput query;
  @UiField LOIWorkflowList workflowlist, metaworkflowlist;
  
  LineOfInquiry loi;
  
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
    if(loi.getQuery() != null && loadcount==3)
      query.setValue(loi.getQuery());
    workflowlist.loadBindingsList(loi.getWorkflows());
    metaworkflowlist.loadBindingsList(loi.getMetaWorkflows());  
  }
  
  public void setNamespace(String ns) {
    query.setDefaultNamespace(ns);
  }
  
  private void loadVocabularies() {
    loadcount=0;
    query.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
    query.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
    query.loadUserVocabulary("user", userid, domain, vocabLoaded);
  }
  
  private Callback<String, Throwable> vocabLoaded = 
      new Callback<String, Throwable>() {
    public void onSuccess(String result) {
      loadcount++;
      if(loi != null && loi.getQuery() != null && loadcount==3)
        query.setValue(loi.getQuery());
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
    boolean ok3 = this.query.validate();
    if(!ok1 || !ok2 || !ok3) {
      AppNotification.notifyFailure("Please fix errors before saving");
      return;
    }
    
    loi.setDescription(description.getValue());
    loi.setName(name.getValue());
    loi.setQuery(query.getValue());
    loi.setWorkflows(workflowlist.getBindingsList());
    loi.setMetaWorkflows(metaworkflowlist.getBindingsList());
    
    fireEvent(new LOISaveEvent(loi));
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
