package org.diskproject.client.components.searchpanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class SearchableItem extends Composite {
	interface Binder extends UiBinder<Widget, SearchableItem> {};
	private static Binder uiBinder = GWT.create(Binder.class);
}