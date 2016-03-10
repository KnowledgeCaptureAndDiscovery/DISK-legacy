package org.diskproject.client.components.hypothesis;

import java.util.List;

import org.diskproject.client.components.hypothesis.events.HasHypothesisHandlers;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveEvent;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveHandler;
import org.diskproject.client.components.loi.TriggeredLOIViewer;
import org.diskproject.client.components.triples.HypothesisTripleInput;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.util.KBConstants;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
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
import com.vaadin.polymer.paper.widget.PaperDialog;

public class HypothesisEditor extends Composite 
    implements HasHypothesisHandlers {
  private HandlerManager handlerManager;
  
  interface Binder extends UiBinder<Widget, HypothesisEditor> {}
  
  String userid, domain;
  Hypothesis hypothesis;
  
  @UiField PaperInputElement name;
  @UiField PaperTextareaElement description;
  @UiField HypothesisTripleInput triples;
  @UiField PaperDialog triggerdialog;

  private static Binder uiBinder = GWT.create(Binder.class);
  
  public HypothesisEditor() {
    initWidget(uiBinder.createAndBindUi(this));
    handlerManager = new HandlerManager(this); 
  }
  
  public void initialize(String username, String domain) {
    this.userid = username;
    this.domain = domain;
    this.loadVocabularies();
  }
  
  public void load(Hypothesis hypothesis) {
    this.hypothesis = hypothesis;
    name.setValue(hypothesis.getName());
    description.setValue(hypothesis.getDescription());
    triples.setValue(hypothesis.getTriples());
  }
  
  public void setNamespace(String ns) {
    this.triples.setDefaultNamespace(ns);
  }
  
  private void loadVocabularies() {
    triples.loadVocabulary("bio", KBConstants.OMICSURI());
    triples.loadVocabulary("hyp", KBConstants.HYPURI());
    triples.loadUserVocabulary("user", this.userid, this.domain);
  }
  
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
    hypothesis.setTriples(triples.getTriples());
    
    fireEvent(new HypothesisSaveEvent(hypothesis));
  }
  
  @UiHandler("runbutton")
  void onRunButtonClicked(ClickEvent event) {
    DiskREST.queryHypothesis(hypothesis.getId(), 
        new Callback<List<TriggeredLOI>, Throwable>() {
      @Override
      public void onSuccess(List<TriggeredLOI> result) {
        showTriggeredLOIOptions(result);
      }
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
    });
  }

  private void showTriggeredLOIOptions(List<TriggeredLOI> tlois) {
    triggerdialog.clear();
    for(final TriggeredLOI tloi : tlois) {
      final TriggeredLOIViewer tviewer = new TriggeredLOIViewer();
      tviewer.load(tloi);
      tviewer.getActionButton().addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          submitTriggeredLOI(tloi, tviewer);
        }
      });
      triggerdialog.add(tviewer);
    }
    triggerdialog.open();
  }
  
  private void submitTriggeredLOI(TriggeredLOI tloi, final TriggeredLOIViewer viewer) {
    DiskREST.addTriggeredLOI(tloi, new Callback<Void, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
      @Override
      public void onSuccess(Void result) {
        AppNotification.notifySuccess("Submitted. "
            + "Go to hypothesis list view to see results", 2000);
        triggerdialog.remove(viewer);
        if(triggerdialog.getWidgetCount() == 0)
          triggerdialog.close();
      }
    });
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
