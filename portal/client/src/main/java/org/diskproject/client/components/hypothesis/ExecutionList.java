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
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.iron.widget.IronIcon;

public class ExecutionList extends SearchableItem {
	interface Binder extends UiBinder<Widget, ExecutionList> {};
	private static Binder uiBinder = GWT.create(Binder.class);

	private static String username, domain;
	private static NumberFormat decimalFormat = NumberFormat.getFormat("#0.000");
	
	@UiField DivElement title, description;
	@UiField TableSectionElement tloilist;
	
	public ExecutionList() {
		initWidget(uiBinder.createAndBindUi(this)); 
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

		tloilist.removeAllChildren();
		int i = 1;
		for (TriggeredLOI tloi: ordered) {
			TableRowElement row = TableRowElement.as(DOM.createTR());

			//row number
			TableCellElement n = TableCellElement.as(DOM.createTD());
			n.setInnerText(Integer.toString(i));
			i += 1;

			//date
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

			//outputs
			TableCellElement out = TableCellElement.as(DOM.createTD());
			int outs = tloi.getOutputFiles().size();
			if (curStatus == Status.SUCCESSFUL) {
				out.setInnerText(Integer.toString(outs));
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

			TableCellElement viz = TableCellElement.as(DOM.createTD());

			TableCellElement options = TableCellElement.as(DOM.createTD());
			IronIcon iconDelete = new IronIcon();
			iconDelete.addStyleName("delete-button");
			iconDelete.setIcon("delete");
			iconDelete.addClickHandler(new ClickHandler() {
			  @Override
			  public void onClick(ClickEvent event) {
				event.stopPropagation();
				//Should delete the tloi.
			  }
			});
			
			options.appendChild(iconDelete.getElement());
			
			row.appendChild(n);
			row.appendChild(date);
			row.appendChild(st);
			row.appendChild(in);
			row.appendChild(out);
			row.appendChild(pval);
			row.appendChild(viz);
			row.appendChild(options);
			tloilist.appendChild(row);
		}
	}

}
