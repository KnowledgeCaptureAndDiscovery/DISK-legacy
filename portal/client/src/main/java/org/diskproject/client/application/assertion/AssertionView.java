package org.diskproject.client.application.assertion;

import org.diskproject.client.application.ApplicationSubviewImpl;
import org.diskproject.client.components.triples.TripleInput;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.util.KBConstants;

import com.google.gwt.core.client.Callback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;

public class AssertionView extends ApplicationSubviewImpl 
  implements AssertionPresenter.MyView {

  String userid;
  String domain;
  boolean editmode;
  
  @UiField TripleInput triples;
  
  interface Binder extends UiBinder<Widget, AssertionView> {
  }

  @Inject
  public AssertionView(Binder binder) {
    initWidget(binder.createAndBindUi(this));
  }

  void loadVocabularies() {
    triples.loadVocabulary("bio", KBConstants.OMICSURI());
    triples.loadVocabulary("hyp", KBConstants.HYPURI());
    triples.loadUserVocabulary("", userid, domain);
  }
  
  @Override
  public void initializeParameters(String userid, String domain, String[] params, boolean edit, 
      SimplePanel sidebar, SimplePanel toolbar) {
    
    clear();
    
    this.userid = userid;
    this.domain = domain;
    DiskREST.setDomain(domain);
    DiskREST.setUsername(userid);
    
    this.loadVocabularies();
    this.loadAssertions();
    
    this.setHeader(toolbar);    
    this.setSidebar(sidebar);
  }
  
  private void clear() {

  }
  
  void loadAssertions() { 
    DiskREST.listAssertions(new Callback<Graph, Throwable>() {
      @Override
      public void onSuccess(final Graph result) {
        Polymer.ready(triples.getElement(), new Function<Object, Object>() {
          @Override
          public Object call(Object o) {               
            triples.setValue(result.getTriples());
            return null;
          }
        });
      }
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }      
    });
  }
  
  @UiHandler("savebutton")
  void onSaveButtonClicked(ClickEvent event) {
    Graph graph = new Graph();
    graph.setTriples(triples.getTriples());
    
    if(!this.triples.validate()) {
      AppNotification.notifyFailure("Please fix errors before saving");
      return;
    }
    DiskREST.updateAssertions(graph, new Callback<Void, Throwable>() {
      @Override
      public void onSuccess(Void result) {
        AppNotification.notifySuccess("Saved", 500);
      }        
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure("Could not save: " + reason.getMessage()); 
      }
    });
  }
  
  private void setHeader(SimplePanel toolbar) {
    // Set Toolbar header
    toolbar.clear();
    String title = "<h3>Assertions</h3>";
    String icon = "icons:list";

    HTML div = new HTML("<nav><div class='layout horizontal center'>"
        + "<iron-icon class='blue' icon='" + icon + "'/></div></nav>");
    div.getElement().getChild(0).getChild(0).appendChild(new HTML(title).getElement());
    toolbar.add(div);     
  }
  
  private void setSidebar(SimplePanel sidebar) {
    // TODO: Modify sidebar
  }
  

}
