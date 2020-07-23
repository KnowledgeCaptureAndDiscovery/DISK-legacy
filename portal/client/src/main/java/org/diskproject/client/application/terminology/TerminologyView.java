package org.diskproject.client.application.terminology;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.diskproject.client.application.ApplicationSubviewImpl;
import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.loader.Loader;
import org.diskproject.client.components.triples.TripleInput;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Type;
import org.diskproject.shared.classes.vocabulary.Vocabulary;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;

public class TerminologyView extends ApplicationSubviewImpl implements
		TerminologyPresenter.MyView {

	String userid;
	String domain;
	boolean editmode;

	int loadcount = 0;

	@UiField Loader loader;
	@UiField HTMLPanel form;
	@UiField ListWidget datalist;
	@UiField TripleInput triples;
	
	@UiField InputElement termid;
	@UiField ListBox termtype;
	@UiField DivElement example1;
	List<Triple> loadedTriples;

	Vocabulary vocab;

	Graph graph;

	interface Binder extends UiBinder<Widget, TerminologyView> {
	}

	@Inject
	public TerminologyView(Binder binder) {
		initWidget(binder.createAndBindUi(this));
	}


	void loadVocabularies() {
		loadcount = 0;
		triples.loadVocabulary("bio", KBConstants.OMICSURI(), vocabLoaded);
		triples.loadVocabulary("neuro", KBConstants.NEUROURI(), vocabLoaded);
		triples.loadVocabulary("hyp", KBConstants.HYPURI(), vocabLoaded);
		triples.loadVocabulary("disk", KBConstants.DISKURI(), vocabLoaded);
		triples.loadUserVocabulary("", userid, domain, vocabLoaded);
	}

	private Callback<String, Throwable> vocabLoaded = new Callback<String, Throwable>() {
		public void onSuccess(String result) {
			loadcount++;
			if (loadcount == 5) {
				String[] prefixes = {"bio", "neuro", "hyp", "disk", ""};
				vocab = new Vocabulary();
				for (String p: prefixes)
					vocab.mergeWith(triples.getVocabulary(p));
				/*vocab.mergeWith(triples.getVocabulary("bio"));
				vocab.mergeWith(triples.getVocabulary("neuro"));
				vocab.mergeWith(triples.getVocabulary("hyp"));
				vocab.mergeWith(triples.getVocabulary("disk"));
				vocab.mergeWith(triples.getVocabulary(""));*/
				vocab.refreshChildren();

				
				termtype.clear();
				for (String p: prefixes) {
					Set<String> keys = triples.getVocabulary(p).getTypes().keySet();
					for (String k: keys) {
						String id = triples.getVocabulary(p).getTypes().get(k).getName();
						String label = triples.getVocabulary(p).getTypes().get(k).getLabel();
						termtype.addItem("(" + p + ") " + label, p + ":" + id);
					}
				}

				if (graph != null)
					showAssertions();
			}
		}

		public void onFailure(Throwable reason) {
		}
	};

	@Override
	public void initializeParameters(String userid, String domain,
			String[] params, boolean edit, SimplePanel sidebar,
			SimplePanel toolbar) {

		clear();

		 if(this.userid != userid || this.domain != domain) {
		      this.userid = userid;
		      this.domain = domain;
		      DiskREST.setDomain(domain);
		      DiskREST.setUsername(userid);
		      this.loadVocabularies();
		    }
		    this.loadAssertions();
		    
		    this.setHeader(toolbar);    
		    this.setSidebar(sidebar);
		  }

	private void clear() {
		loader.setVisible(false);
		datalist.setVisible(false);
		form.setVisible(false);
	}

	void loadAssertions() {
		   loader.setVisible(true);
		    DiskREST.listAssertions(new Callback<Graph, Throwable>() {
		      @Override
		      public void onSuccess(final Graph result) {
		        loader.setVisible(false);
		        form.setVisible(true);
		        graph = result;
		        if(graph != null && loadcount == 5)
		          showAssertions();
		      }
		      @Override
		      public void onFailure(Throwable reason) {
		        loader.setVisible(false);
		        AppNotification.notifyFailure(reason.getMessage());
		      }      
		    });
	}

	private void showAssertions() {
		Polymer.ready(triples.getElement(), new Function<Object, Object>() {
			@Override
			public Object call(Object arg) {
				// Show triples in the editor
				if (graph != null) {
					triples.setValue(graph.getTriples());
					loadedTriples = triples.getTriples();
				}

				// Show data list
				datalist.clear();
				showDataList(); 

				loader.setVisible(false);
				datalist.setVisible(true);

				return null;
			}
		});
	}

	private void showDataList() {
		List<Individual> datasets = new ArrayList<Individual>();
		String parentTypeId = KBConstants.DISKNS() + "Data";
		Type topDataType = vocab.getType(parentTypeId);
		for (Type subtype : vocab.getSubTypes(topDataType)) {
			datasets.addAll(vocab.getIndividualsOfType(subtype));
		}
		for (Individual dataset : datasets) {
			String dname = "<b>" + dataset.getName() + "</b>";
			dname += " ( " + dataset.getType().replaceAll("^.*#", "") + " )";
			ListNode node = new ListNode(dataset.getId(), new HTML(dname));
			node.setIcon("icons:list");
			node.setIconStyle("blue");
			datalist.addNode(node);
		}
	}

	@UiHandler("savebutton")
	void onSaveButtonClicked(ClickEvent event) {
		this.graph.setTriples(triples.getTriples());
		// GWT.log(graph.getTriples().toString());
		if (!this.triples.validate()) {
			AppNotification.notifyFailure("Please fix errors before saving");
			return;
		}
		AppNotification.notifyLoading("Saving data and running queries");
		DiskREST.updateAssertions(graph, new Callback<Void, Throwable>() {
			@Override
			public void onSuccess(Void result) {
				triples.loadUserVocabulary("", userid, domain, true,
						vocabLoaded);
				AppNotification.stopShowing();
				AppNotification.notifySuccess("Saved", 1000);
				loadcount--;
			}

			@Override
			public void onFailure(Throwable reason) {
				AppNotification.stopShowing();
				AppNotification.notifyFailure("Could not save: "
						+ reason.getMessage());
			}
		});
	}

  static String toVarName (String stdname) {
	  String[] parts = stdname.split(" ");
	  String endString = "";
	  Boolean first = true;
	  for (String p: parts) {
		  if (first) {
			  endString += p;
			  first = false;
		  } else {
			  endString += p.substring(0, 1).toUpperCase() + p.substring(1).toLowerCase();
		  }
	  }
	  return endString;
  }

	@UiHandler("addterm")
	void onAddTermButtonClicked(ClickEvent event) {
		String name = termid.getValue();
		String id = toVarName(name);
		if (id == "" || name == "") {
		    AppNotification.notifyFailure("You must add a name and identifier to any new term.");
			return;
		}

		String fullid = "http://localhost:8080/disk-project-server/admin/test/assertions#" + id;
		List<Triple> allt = triples.getTriples();
		for (Triple t: allt) {
			if (t.getSubject() == fullid) {
				AppNotification.notifyFailure("You can not use the same identifier.");
				return;
			}
		}
		
		String all = triples.getTripleString(triples.getTriples());
		String t1 =  ":" + id + " a " + termtype.getSelectedValue();
		String t2 =  ":" + id + " rdfs:label \"" + name + "\"^^xsd:string";
		
		String merged =  all + '\n' + t1 + '\n' + t2;
		GWT.log(merged);
		triples.setStringValue(merged);
	}

	@UiHandler("helpterm")
	void onHelpTermButtonClicked(ClickEvent event) {
		String actual = example1.getStyle().getDisplay();
		if (actual == "none")
			example1.getStyle().setDisplay(Display.INITIAL);
		else
			example1.getStyle().setDisplay(Display.NONE);
	}

	@UiHandler("discardbutton")
	void onDiscardButtonClicked(ClickEvent event) {
		if (loadedTriples != null)
			triples.setValue(loadedTriples);
	}

	private void setHeader(SimplePanel toolbar) {
		// Set Toolbar header
		toolbar.clear();
		String title = "<h3>Terminology</h3>";
		String icon = "icons:chrome-reader-mode";

		HTML div = new HTML("<nav><div class='layout horizontal center'>"
				+ "<iron-icon class='blue' icon='" + icon + "'/></div></nav>");
		div.getElement().getChild(0).getChild(0)
				.appendChild(new HTML(title).getElement());
		toolbar.add(div);
	}

	private void setSidebar(SimplePanel sidebar) {
		// TODO: Modify sidebar
	}
}
