package org.diskproject.client.components.hypothesis;

import java.util.List;

import org.diskproject.client.components.searchpanel.SearchableItem;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.iron.widget.IronIcon;
import com.vaadin.polymer.paper.widget.PaperSpinner;

@SuppressWarnings("deprecation")
public class ExecutionList extends SearchableItem {
	interface Binder extends UiBinder<Widget, ExecutionList> {};
	private static Binder uiBinder = GWT.create(Binder.class);

	private static String username, domain;
	private static NumberFormat decimalFormat = NumberFormat.getFormat("#0.000");
	
	@UiField DivElement title, description;
	@UiField TableSectionElement tloilist;
	@UiField FocusPanel toggle;
	@UiField HTMLPanel tableContainer;
	@UiField PaperSpinner updating;
	
	public ExecutionList() {
		initWidget(uiBinder.createAndBindUi(this)); 
		super.onAttach();
	    tableContainer.setVisible(false);
	    updating.setVisible(false);
	}
	
	public static void setUsenameAndDomain (String username, String domain) {
		ExecutionList.username = username;
		ExecutionList.domain = domain;
	}
	
	public void setTitle (String newTitle) {
		title.setInnerText(newTitle);
	}
	
	public void setDescription (String desc) {
		description.setInnerText(desc);
	}
	
	public void load (TreeItem item) {
		//item.get
		setTitle(item.getName());
		setDescription(item.getDescription());
	}

	List<TriggeredLOI> list;
	public void setList(List<TriggeredLOI> tloilist) {
		list = tloilist;
		updateTable();
	}
	
	private void updateTable () {
		List<TriggeredLOI> ordered = list;//FIXME!
        Element el = null; //Pointer to pseudo elements

		tloilist.removeAllChildren();
		int i = 1;
		for (TriggeredLOI tloi: ordered) {
			TableRowElement row = TableRowElement.as(DOM.createTR());

			//row number
			TableCellElement n = TableCellElement.as(DOM.createTD());
			n.setInnerText(Integer.toString(i));
			i += 1;

			//Date
			TableCellElement date = TableCellElement.as(DOM.createTD());
			
			AnchorElement link = AnchorElement.as(DOM.createAnchor());
			link.setInnerText(tloi.getDateCreated());
			link.setHref("/#tlois/" + ExecutionList.username + "/" + ExecutionList.domain + "/" + tloi.getId());
			//date.setInnerText(tloi.getDateCreated());
			date.appendChild(link);
			
			//status
			TableCellElement st = TableCellElement.as(DOM.createTD());
			Status curStatus = tloi.getStatus();
			if (curStatus == Status.SUCCESSFUL) {
				st.setInnerText("SUCCESSFUL");
				st.getStyle().setColor("green");
			} else if (curStatus == Status.QUEUED) {
				st.setInnerText("QUEUED");
				//st.getStyle().setColor("yellow");
			} else if (curStatus == Status.RUNNING) {
				st.setInnerText("RUNNING");
				//st.getStyle().setColor("yellow");
			} else if (curStatus == Status.FAILED) {
				st.setInnerText("FAILED");
				st.getStyle().setColor("red");
			}
			
			//inputs
			TableCellElement in = TableCellElement.as(DOM.createTD());
			in.setInnerText(Integer.toString(tloi.getInputFiles().size()));
			IronIcon iconInputList = new IronIcon();
			iconInputList.addStyleName("inline-button");
			iconInputList.setIcon("description");
			el = iconInputList.getElement();
			Event.sinkEvents(el, Event.ONCLICK);
            Event.setEventListener(el, new EventListener() {
              @Override
              public void onBrowserEvent(Event event) {
                  for (String in: tloi.getInputFiles())
                      GWT.log(in);
              }
            });
			in.appendChild(el);

			//outputs
			TableCellElement out = TableCellElement.as(DOM.createTD());
			int outs = tloi.getOutputFiles().size();
			if (curStatus == Status.SUCCESSFUL) {
				out.setInnerText(Integer.toString(outs));
                IronIcon iconOutputList = new IronIcon();
                iconOutputList.addStyleName("inline-button");
                iconOutputList.setIcon("description");
                el = iconOutputList.getElement();
                Event.sinkEvents(el, Event.ONCLICK);
                Event.setEventListener(el, new EventListener() {
                  @Override
                  public void onBrowserEvent(Event event) {
                      for (String in: tloi.getOutputFiles())
                          GWT.log(in);
                  }
                });
                out.appendChild(el);
			} else if (curStatus == Status.FAILED) {
				out.setInnerText("-");
			} else {
				out.setInnerText("...");
			}
			
			//p value
			TableCellElement pval = TableCellElement.as(DOM.createTD());
			if (curStatus == Status.SUCCESSFUL) {
				pval.setInnerText(ExecutionList.decimalFormat.format(tloi.getConfidenceValue()));
			} else if (curStatus == Status.FAILED) {
				pval.setInnerText("-");
			} else {
				pval.setInnerText("...");
			}

			//Buttons at the end
			TableCellElement options = TableCellElement.as(DOM.createTD());

			IronIcon iconDelete = new IronIcon();
			iconDelete.addStyleName("delete-button");
			iconDelete.setIcon("delete");
            el = iconDelete.getElement();
			Event.sinkEvents(el, Event.ONCLICK);
            Event.setEventListener(el, new EventListener() {
              @Override
              public void onBrowserEvent(Event event) {
                  GWT.log("Should delete " + tloi.getId());
              }
            });

			options.appendChild(el);
			
			row.appendChild(n);
			row.appendChild(date);
			row.appendChild(st);
			row.appendChild(in);
			row.appendChild(out);
			row.appendChild(pval);
			row.appendChild(options);
			tloilist.appendChild(row);
		}
	};

	@UiHandler("toggle")
	void onToggleClicked(ClickEvent event) {
	    tableContainer.setVisible(!tableContainer.isVisible());
	}

}
