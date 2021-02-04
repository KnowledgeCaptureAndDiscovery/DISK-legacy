package org.diskproject.client.components.question;


import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.client.components.hypothesis.HypothesisEditor;
import org.diskproject.client.components.loi.LOIEditor;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.question.Question;
import org.diskproject.shared.classes.question.QuestionVariable;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.workflow.VariableBinding;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SetSelectionModel;

public class QuestionFiller extends Composite {
  interface Binder extends UiBinder<Widget, QuestionFiller> {};
  private static Binder uiBinder = GWT.create(Binder.class);
  
  @UiField DivElement questionTemplate;
  @UiField ListBox questionListBox;
  private List<Question> questions;
  private Map<String, ListBox> options;
  private Map<String,List<List<String>>> optionsCache;
  private String selectedQuestionId;
  private static RegExp varPattern = RegExp.compile("\\?[a-zA-Z0-9]*", "g");
  

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
    //initialize();
    this.options = new HashMap<String, ListBox>();
    this.optionsCache = new HashMap<String, List<List<String>>>();
    getQuestions();
  }

  public void getQuestions () {
	DiskREST.listHypothesesQuestions(new Callback<List<Question>, Throwable>() {
		@Override
		public void onSuccess(List<Question> result) {
			questions = result;
			setQuestions();
		}
		@Override
		public void onFailure(Throwable reason) {
			AppNotification.notifyFailure(reason.getMessage());
		}
	});
  }

  //Set the list of questions retrieved from the server.
  private void setQuestions () {
	  if (questions == null) return;
	  questionListBox.clear();
	  int len = questions.size();
	  int index = 0;
	  for (int i = 0; i < len; i++) {
		  Question q = questions.get(i);
		  questionListBox.addItem(q.getName(), q.getId());
		  if (selectedQuestionId != null && selectedQuestionId.equals(q.getId())) {
			  index = i;
		  }
	  }
	  questionListBox.setSelectedIndex(index);
	  onQuestionChange(null);
  }

  @UiHandler("questionListBox")
	void onQuestionChange(ChangeEvent event) {
	  String id = questionListBox.getSelectedValue();
	  if (id != null && !id.equals("")) {
		  selectedQuestionId = id;
		  updateQuestionPattern();
	  }
	}

  private void updateQuestionPattern () {
	  Question selectedQuestion = null;
	  for (Question q: questions) {
		  if (q.getId().equals(selectedQuestionId)) {
			  selectedQuestion = q;
			  break;
		  }
	  }
	  if (selectedQuestion != null) {
		  String template = selectedQuestion.getTemplate();
		  GWT.log(selectedQuestionId + " " + selectedQuestion.getVariables());
		  List<QuestionVariable> variables = selectedQuestion.getVariables();

		  if (template == null || variables == null || template.equals("") || variables.size() == 0) {
			  GWT.log("ERROR: " + selectedQuestionId + " does not have a template or variables.");
			  return;
		  }

		  //Map varname -> variable
		  Map<String,QuestionVariable> varmap = new HashMap<String, QuestionVariable>();
		  for (QuestionVariable q: variables) {
			  varmap.put(q.getVarName(), q);
		  }
		  
		  // Text parts and variables
		  String parts[] = template.split("\\?[a-zA-Z0-9]*");
		  List<String> vars = new ArrayList<String>();
		  for (MatchResult varMatcher = varPattern.exec(template); varMatcher != null; varMatcher = varPattern.exec(template)) {
			  vars.add(varMatcher.getGroup(0));
		  }
		  
		  int varlen = vars.size();
		  int len = varlen > parts.length ? varlen : parts.length;
		  
		  //Assuming each questions start with a text part.
		  questionTemplate.removeAllChildren();
		  for (int i = 0; i < len; i++) {
			 if (i < parts.length) {
				 Element spanEl = DOM.createSpan();
				 SpanElement mySpan = SpanElement.as(spanEl);
				 mySpan.setInnerText(parts[i]);
				 questionTemplate.appendChild(mySpan);
			 }
			 if (i < varlen) {
				 String varname = vars.get(i);
				 QuestionVariable q = varmap.get(varname);
				 String optid = q.getId();
				 ListBox lb = new ListBox();
				 if (options.containsKey(optid)) {
					 lb = options.get(optid);
				 } else {
					 lb = new ListBox();
					 options.put(optid, lb);
					 lb.addItem("<" + varname.substring(1) + ">" , "");
					 lb.getElement().getFirstChildElement().setAttribute("disabled", "disabled");
					 if (q.getFixedOptions() != null) {
						 for (String opt: q.getFixedOptions()) {
							 lb.addItem(opt); //FIXME, this should be a pair id-label.
						 }
					 } else if (q.getConstraints() != null) {
						 lb.addItem("Loading...", "");
						 requestOptions(q);
					 } else {
						 lb.addItem("ERROR!", "");
						 GWT.log("ERROR: " + q.getVarName() + " does not have restrictions.");
					 }
				 }
				 questionTemplate.appendChild(lb.getElement());
			 }
		  }
	  } 	  
  }

  private void requestOptions (QuestionVariable q) {
	  String id = q.getId();
	  if (optionsCache.containsKey(id)) fillVariableOptions(q);
	  else 
		  DiskREST.listVariableOptions(q.getId(), new Callback<List<List<String>>, Throwable>() {
			  @Override
			  public void onSuccess(List<List<String>> result) {
				  optionsCache.put(id, result);
				  ListBox lb = options.get(q.getId());
				  String val = lb.getSelectedValue();
				  fillVariableOptions(q);
				  if (!val.equals("")) setListBoxSelectedValue(lb, val);
			  }
			  @Override
			  public void onFailure(Throwable reason) {
				  AppNotification.notifyFailure(reason.getMessage());
			  }
		  });
  }

  private void fillVariableOptions (QuestionVariable q) {
	  List<List<String>> variableOptions = optionsCache.get(q.getId());
	  ListBox optList = options.get(q.getId());
	  optList.clear();
	  optList.addItem("<" + q.getVarName().substring(1) + ">", "");
	  optList.getElement().getFirstChildElement().setAttribute("disabled", "disabled");
	  for (List<String> idname: variableOptions) {
		  String[] parts = idname.get(0).split("/");
		  String name = parts[parts.length - 1];
		  if (name.length() > 15) {
			  name = name.substring(0, 15) +  "...";
		  }
		  optList.addItem(name, idname.get(1));
	  }
  }

  public String getSelectedQuestion () {
    return questionListBox.getSelectedValue();
  }

  public void setQuestion (String id, List<VariableBinding> bindings) {
	//This assumes that the questions where loaded already.
    if (setListBoxSelectedValue(questionListBox, id)) this.onQuestionChange(null);
    if (bindings != null) {
    	for (VariableBinding vb: bindings) {
    		String var = vb.getVariable();
    		String val = vb.getBinding();
			ListBox lb = options.get(var);
			if (lb != null) {
				if (!setListBoxSelectedValue(lb, val)) {
					String[] sp = val.split("/");
					lb.addItem(sp[sp.length -1] , val);
					lb.setSelectedIndex(lb.getItemCount()-1);
				}
			} else {
				// Questions has not loaded?
			}
    	}
    }
  }
  
  public boolean setListBoxSelectedValue (ListBox lb, String value) {
	  if (lb != null) for (int i = lb.getItemCount() -1; i >= 0; i--) {
		  String option = lb.getValue(i);
		  if (option.equals(value)) {
			  lb.setSelectedIndex(i);
			  return true;
		  }
	  }
	  return false;
  }
  

	@UiHandler("addPattern")
	void onAddTermButtonClicked(ClickEvent event) {
		String id = questionListBox.getSelectedValue();
		Question myQuestion = null;
		for (Question q: questions) {
			if (q.getId().equals(id)) {
				myQuestion = q;
				break;
			}
		}
		if (myQuestion != null) {
			String base = myQuestion.getPattern();
			for (QuestionVariable var : myQuestion.getVariables()) {
				ListBox lb = options.get(var.getId());
				String selected = lb.getSelectedValue();
				base = base.replace(var.getVarName(), '"' + selected + '"');
			}
			
			base = base.replace("?", ":");
			parent.setHypothesis(base);
		}
	}

  public void setParent (HypothesisEditor parent) {
    this.parent = parent;
  }
  
  public List<VariableBinding> getVariableBindings () {
	  String id = questionListBox.getSelectedValue();
	  Question myQuestion = null;
	  for (Question q: questions) {
		  if (q.getId().equals(id)) {
			  myQuestion = q;
			  break;
		  }
	  }
	  if (myQuestion != null) {
		  List<VariableBinding> bindings = new ArrayList<VariableBinding>();
		  for (QuestionVariable var : myQuestion.getVariables()) {
			  String varid = var.getId();
			  ListBox lb = options.get(varid);
			  String selected = lb.getSelectedValue();
			  if (!selected.equals(""))
				  bindings.add(new VariableBinding(varid, selected));
		  }
		  return bindings;
	  } else {
		  return null;
	  }
  }
}