package org.diskproject.client.components.searchpanel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.diskproject.client.components.hypothesis.HypothesisItem;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.paper.widget.PaperDropdownMenu;
import com.vaadin.polymer.paper.widget.PaperIconButton;
import com.vaadin.polymer.paper.widget.PaperInput;

public class SearchPanel extends Composite {
	interface Binder extends UiBinder<Widget, SearchPanel> {};
	private static Binder uiBinder = GWT.create(Binder.class);
  
	@UiField PaperInput inputSearch;
	@UiField PaperIconButton clearSearchButton;
	@UiField PaperDropdownMenu orderMenu;
	@UiField DivElement itemContainer;
	
	private String activeOrder;
	private Map<String, SearchableItem> items;

	public SearchPanel () {
		initWidget(uiBinder.createAndBindUi(this)); 
		items = new HashMap<String, SearchableItem>();
	}
	
	@UiHandler("clearSearchButton")
	void onAddIconClicked(ClickEvent event) {
	    inputSearch.setValue("");
	}

	@UiHandler("orderMenu")
	void onOrderClicked(ClickEvent event) {
		String order = orderMenu.getSelectedItemLabel();
		if (activeOrder == null || !activeOrder.equals(order)) {
			activeOrder = order;
			GWT.log("Order changed");
			//TODO apply order!
		}
	}
	
	public void addItem (String id, SearchableItem item) {
		items.put(id, item);
		updateList();
	}

	public SearchableItem getItem (String id) {
		return items.containsKey(id) ? items.get(id) : null;
	}
	
	private void updateList () {
		itemContainer.removeAllChildren();
		//TODO Apply order
		Set<String> ordered = items.keySet();
		for (String key : ordered) {
			itemContainer.appendChild( items.get(key).getElement() );
		}
	}
}