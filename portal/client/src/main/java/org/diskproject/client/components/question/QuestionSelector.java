package org.diskproject.client.components.question;


import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;

import org.diskproject.client.components.loi.LOIEditor;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;

public class QuestionSelector extends Composite {
  interface Binder extends UiBinder<Widget, QuestionSelector> {};
  private static Binder uiBinder = GWT.create(Binder.class);
  
  @UiField ListBox hypQuestion, h1r1, h1r2, h1r3, h2r1, h2r2;
  @UiField SpanElement h1Section, h2Section, h3Section;
  LOIEditor parent;

  public QuestionSelector() {
    initWidget(uiBinder.createAndBindUi(this)); 
  }
  
  public void setParent (LOIEditor parent) {
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
    this.onHypChange(null);
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
		
		String t1 =  "?EffectSize neuro:sourceGene ?" + p1v;
		String t2 =  "?EffectSize neuro:targetCharacteristic ?" + p2v;
		String t3 =  "?EffectSize hyp:associatedWith ?" + p3v;
		
		String merged =  t1 + '\n' + t2 + '\n' + t3;
		return merged;
	}

	private String getH2 () {
		String p1v = h2r1.getSelectedValue();
		String p2v = h2r2.getSelectedValue();
		if (p1v == "" || p2v == "") {
			return null;
		}
		
		String t = "?" + p1v + " hyp:associatedWith ?" + p2v;
		return t;
	}

	private String getH3 () {
		return "?EffectSize neuro:sourceGene ?Gene\n" + 
				"?EffectSize neuro:targetCharacteristic ?BrainImagingTrait\n" + 
				"?EffectSize neuro:targetCharacteristic ?Area\n" + 
				"?EffectSize hyp:associatedWith ?Demographic\n" + 
				"?Gene rdfs:label ?SNP";
	}

}