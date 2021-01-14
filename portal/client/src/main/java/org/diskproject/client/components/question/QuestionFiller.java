package org.diskproject.client.components.question;


import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;

import java.util.List;
import java.util.Map;

import org.diskproject.client.components.hypothesis.HypothesisEditor;
import org.diskproject.client.components.loi.LOIEditor;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Vocabulary;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;

public class QuestionFiller extends Composite {
  interface Binder extends UiBinder<Widget, QuestionFiller> {};
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField ListBox hypQuestion, h1r1, h1r2, h1r3, h2r1, h2r2, h3r1, h3r2, h3r3, h3r4;
  @UiField SpanElement h1Section, h2Section, h3Section;
  HypothesisEditor parent;
  String username, domain;
  
  public void setUsername (String user) {
    username= user;
  }
  
  public void setDomain (String dom) {
    domain = dom;
  }

  public QuestionFiller() {
    initWidget(uiBinder.createAndBindUi(this)); 
    initialize();
  }

  private void initialize () {
    ListBox[] all = {h1r1, h1r2, h1r3, h2r1, h2r2, h3r1, h3r2, h3r3, h3r4};
    for (ListBox h: all) {
      h.clear();
    }
    this.loadVocabulary("neuro", KBConstants.NEUROURI(), all);
    //this.loadUserVocabulary(all);
  }
  
  public void loadVocabulary (String prefix, String uri, ListBox[] boxes) {
    DiskREST.getVocabulary(new Callback<Vocabulary, Throwable>() {
      @Override
      public void onSuccess(Vocabulary result) {
        Map<String, Individual> inds = result.getIndividuals();
        for (String key: inds.keySet()) {
          String id = inds.get(key).getName();
          GWT.log("--- " + id);
          String label = inds.get(key).getLabel();
          if (id != null && label != null) {
            for (ListBox l: boxes) {
              l.addItem("(" + prefix + ") " + label, prefix + ":" + id);
            }
          }
        }
      }      
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure("Could not load vocabulary for "+uri
            +" : " + reason.getMessage());
      }
    }, uri, false);
  }

  public void loadUserVocabulary(ListBox[] boxes) {
    DiskREST.getUserVocabulary(new Callback<Vocabulary, Throwable>() {
      @Override
      public void onSuccess(Vocabulary result) {
        Map<String, Individual> inds = result.getIndividuals();
        for (String key: inds.keySet()) {
          String id = inds.get(key).getName();
          String label = inds.get(key).getLabel();
          if (id != null && label != null) {
            for (ListBox l: boxes) {
              l.addItem("(user) " + label, "user:" + id);
            }
          }
        }
        /*GWT.log("user vocabulary loaded:");
        Map<String, Individual> indvs = result.getIndividuals();
        for (String key: indvs.keySet()) {
          GWT.log(key + ": " + indvs.get(key).getName());
        }*/
          
      }      
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure("Could not load user vocabulary"
            +" : " + reason.getMessage());
      }      
    }, this.username, this.domain, false);
  }

  public void setParent (HypothesisEditor parent) {
    this.parent = parent;
  }

  public String getSelectedQuestion () {
    return hypQuestion.getSelectedValue();
  }

  public void setQuestion (String q) {
    for (int i = hypQuestion.getItemCount() - 1; i >= 0; i--) {
      String val = hypQuestion.getValue(i);
      if (val.contains(q)) {
         hypQuestion.setItemSelected(i, true);
         break;
      }
    }
    this.onChange();
  }

	@UiHandler("addPattern")
	void onAddTermButtonClicked(ClickEvent event) {
		String selectedHyp = hypQuestion.getSelectedValue();
		String hyp = null;
		if (selectedHyp.equals("h1")) {
			hyp = getH1();
		} else if (selectedHyp.equals("h2")) {
			hyp = getH2();
		} else if (selectedHyp.equals("h3")) {
			hyp = getH3();
		}
		if (hyp != null) this.parent.setHypothesis(hyp);
	}

	@UiHandler("hypQuestion")
	void onHypChange(ChangeEvent event) {
	  this.onChange();
	}
	
	private void onChange () {
		String h = hypQuestion.getSelectedValue();
		if (h.equals("h1")) {
			h1Section.getStyle().setDisplay(Display.INITIAL);
			h2Section.getStyle().setDisplay(Display.NONE);
			h3Section.getStyle().setDisplay(Display.NONE);
		} else if (h.equals("h2")) {
			h2Section.getStyle().setDisplay(Display.INITIAL);
			h1Section.getStyle().setDisplay(Display.NONE);
			h3Section.getStyle().setDisplay(Display.NONE);
		} else if (h.equals("h3")) {
			h3Section.getStyle().setDisplay(Display.INITIAL);
			h1Section.getStyle().setDisplay(Display.NONE);
			h2Section.getStyle().setDisplay(Display.NONE);
		}
	}

	private String getH1 () {
		String p1v = h1r1.getSelectedValue();
		String p2v = h1r2.getSelectedValue();
		String p3v = h1r3.getSelectedValue();
		if (p1v == "" || p2v == "" || p3v == "") {
			return null;
		}
		
		String t1 =  ":EffectSize neuro:sourceGene " + p1v;
		String t2 =  ":EffectSize neuro:targetCharacteristic " + p2v;
		String t3 =  ":EffectSize hyp:associatedWith " + p3v;
		
		String merged =  t1 + '\n' + t2 + '\n' + t3;
		return merged;
	}

	private String getH2 () {
		String p1v = h2r1.getSelectedValue();
		String p2v = h2r2.getSelectedValue();
		if (p1v == "" || p2v == "") {
			return null;
		}
		
		String t = "?" + p1v + " hyp:associatedWith " + p2v;
		return t;
	}

	private String getH3 () {
		String p1v = h3r1.getSelectedValue();
		String p2v = h3r2.getSelectedValue();
		String p3v = h3r3.getSelectedValue();
		String p4v = h3r4.getSelectedValue();
		if (p1v == "" || p2v == "" || p3v == "" || p4v == "") {
			return null;
		}
		String t1 = ":EffectSize neuro:sourceGene " + p1v;
		String t2 = ":EffectSize neuro:targetCharacteristic " + p2v;
		String t3 = ":EffectSize neuro:targetCharacteristic " + p3v;
		String t4 = ":EffectSize hyp:associatedWith " + p4v;
		return t1 + '\n' + t2 + '\n' + t3 + '\n' + t4;
	}
}