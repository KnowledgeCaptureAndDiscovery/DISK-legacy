package org.diskproject.client.components.hypothesis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.client.components.searchpanel.SearchableItem;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.loi.TriggeredLOI;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

public class HypothesisItem extends SearchableItem {
	interface Binder extends UiBinder<Widget, HypothesisItem> {};
	private static Binder uiBinder = GWT.create(Binder.class);
	
	@UiField DivElement title, description, info, executions;
	private Map<String, ExecutionList> executionLists;
	
	public HypothesisItem() {
		initWidget(uiBinder.createAndBindUi(this)); 
		executionLists = new HashMap<String, ExecutionList>();
	}
	
	public void setTitle (String newTitle) {
		title.setInnerText(newTitle);
	}
	
	public void setDescription (String desc) {
		description.setInnerText(desc);
	}
	
	public void setInfo (String author, String date) {
		SpanElement authSpan = SpanElement.as(DOM.createSpan());
		SpanElement dateSpan = SpanElement.as(DOM.createSpan());
		authSpan.setInnerText(author);
		dateSpan.setInnerText(date);
		info.removeAllChildren();
		info.appendChild(authSpan);
		info.appendChild(dateSpan);
	}
	
	public void load (TreeItem item) {
		//item.get
		setTitle(item.getName());
		setDescription(item.getDescription());
		
		setInfo(item.getAuthor(), item.getCreationDate());
	}

	public void addExecutionList (String loiid, List<TriggeredLOI> tloilist) {
		if (tloilist == null || tloilist.size() < 1) return;
		if (!executionLists.containsKey(loiid)) {
			ExecutionList n = new ExecutionList();
			executionLists.put(loiid, n);
			executions.appendChild(n.getElement());
		}
		ExecutionList l = executionLists.get(loiid);
		//FIXME: get infor from LOI
		l.setTitle(tloilist.get(0).getName().replace("Triggered: ", ""));
		l.setList(tloilist);
	}
}
