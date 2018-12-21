package org.diskproject.client.application.assertion;

import java.util.ArrayList;
import java.util.List;

import org.diskproject.client.application.ApplicationSubviewImpl;
import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.loader.Loader;
import org.diskproject.client.components.triples.TripleInput;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Type;
import org.diskproject.shared.classes.vocabulary.Vocabulary;

import com.google.gwt.core.client.Callback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;

public class AssertionView extends ApplicationSubviewImpl implements
		AssertionPresenter.MyView {

	String userid;
	String domain;
	boolean editmode;

	int loadcount = 0;

	@UiField
	Loader loader;
	@UiField
	HTMLPanel form;
	@UiField
	ListWidget datalist;
	@UiField
	TripleInput triples;

	Vocabulary vocab;

	Graph graph;

	interface Binder extends UiBinder<Widget, AssertionView> {
	}

	@Inject
	public AssertionView(Binder binder) {
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
				vocab = new Vocabulary();
				vocab.mergeWith(triples.getVocabulary("bio"));
				vocab.mergeWith(triples.getVocabulary("neuro"));
				vocab.mergeWith(triples.getVocabulary("hyp"));
				vocab.mergeWith(triples.getVocabulary("disk"));
				vocab.mergeWith(triples.getVocabulary(""));
				vocab.refreshChildren();
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
				if (graph != null)
					triples.setValue(graph.getTriples());

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

	private void setHeader(SimplePanel toolbar) {
		// Set Toolbar header
		toolbar.clear();
		String title = "<h3>Data</h3>";
		String icon = "icons:list";

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
