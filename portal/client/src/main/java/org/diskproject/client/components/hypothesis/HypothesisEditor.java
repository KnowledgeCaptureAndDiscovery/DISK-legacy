package org.diskproject.client.components.hypothesis;

import java.util.HashMap;
import java.util.Map;

import org.diskproject.client.components.hypothesis.events.HasHypothesisHandlers;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveEvent;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveHandler;
import org.diskproject.client.components.triples.HypothesisTripleInput;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Type;
import org.diskproject.shared.classes.vocabulary.Vocabulary;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
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
  @UiField SpanElement h1Section, h2Section;

  @UiField ListBox hypQuestion, h1r1, h1r2, h1r3, h2r1, h2r2;

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
    if(hypothesis.getGraph() != null && loadcount==4)
      triples.setValue(hypothesis.getGraph().getTriples());
  }
  
  public void setNamespace(String ns) {
    this.triples.setDefaultNamespace(ns);
  }

  private void loadVocabularies() {
    loadcount=0;
    triples.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
    triples.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
    triples.loadVocabulary("neuro", KBConstants.NEUROURI(), vocabLoaded);
    triples.loadUserVocabulary("user", this.userid, this.domain, vocabLoaded);
  }

  private Callback<String, Throwable> vocabLoaded = 
      new Callback<String, Throwable>() {
    public void onSuccess(String result) {
      loadcount++;
      if (hypothesis != null && hypothesis.getGraph() != null && loadcount==4)
        triples.setValue(hypothesis.getGraph().getTriples());
      	//--
        ListBox[] lists = {h1r1, h1r2, h1r3, h2r1, h2r2};
        for (ListBox l: lists) {
        	l.clear();
        }

		String[] prefixes = {"bio", "neuro", "hyp", "disk", "user"};
		Map<String, Map<String, Individual>> individuals = new HashMap<String, Map<String,Individual>>();
		Map<String, Map<String, Type>> types = new HashMap<String, Map<String,Type>>();
		for (String prefix: prefixes) {
			Vocabulary v = triples.getVocabulary(prefix);
			if (v != null) {
				Map<String, Individual> ind = v.getIndividuals();
				Map<String, Type> ty = v.getTypes();
				if (ind != null) individuals.put(prefix, ind);
				if (ty != null) types.put(prefix, ty);
			}
		}

		GWT.log("---------");
		for (String prefix: individuals.keySet()) {
			for (String k: individuals.get(prefix).keySet()) {
				String id = individuals.get(prefix).get(k).getName();
				String label = individuals.get(prefix).get(k).getLabel();
				GWT.log("inv: "+label + " | " + prefix + ":" + id);
				for (ListBox l: lists) {
					l.addItem("(" + prefix + ") " + label, prefix + ":" + id);
				}
			}
		}
		/*for (String prefix: types.keySet()) {
			for (String k: types.get(prefix).keySet()) {
				String id = types.get(prefix).get(k).getName();
				String label = types.get(prefix).get(k).getLabel();
				GWT.log("type: " +label + " | " + prefix + ":" + id);
			}
		}*/
      //--
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

	@UiHandler("addPattern")
	void onAddTermButtonClicked(ClickEvent event) {
		String selectedHyp = hypQuestion.getSelectedValue();
		if (selectedHyp.equals("h1")) {
			setH1();
		} else if (selectedHyp.equals("h2")) {
			setH2();
		}
	}

	@UiHandler("hypQuestion")
	void onChange(ChangeEvent event) {
		String h = hypQuestion.getSelectedValue();
		if (h.equals("h1")) {
			h1Section.getStyle().setDisplay(Display.INITIAL);
			h2Section.getStyle().setDisplay(Display.NONE);
		} else if (h.equals("h2")) {
			h2Section.getStyle().setDisplay(Display.INITIAL);
			h1Section.getStyle().setDisplay(Display.NONE);
		}
	}

	void setH1 () {
		String p1v = h1r1.getSelectedValue();
		String p2v = h1r2.getSelectedValue();
		String p3v = h1r3.getSelectedValue();
		if (p1v == "" || p2v == "" || p3v == "") {
		    AppNotification.notifyFailure("Must select all requested properties.");
			return;
		}
		
		String t1 =  ":EffectSize neuro:sourceGene " + p1v;
		String t2 =  ":EffectSize neuro:targetCharacteristic " + p2v;
		String t3 =  ":EffectSize hyp:associatedWith " + p3v;
		
		String merged =  t1 + '\n' + t2 + '\n' + t3;
		GWT.log(merged);
		triples.setStringValue(merged);
		
	}

	void setH2 () {
		String p1v = h2r1.getSelectedValue();
		String p2v = h2r2.getSelectedValue();
		if (p1v == "" || p2v == "") {
		    AppNotification.notifyFailure("Must select all requested properties.");
			return;
		}
		
		String t =  p1v + " hyp:associatedWith " + p2v;
		triples.setStringValue(t);
	}

}
