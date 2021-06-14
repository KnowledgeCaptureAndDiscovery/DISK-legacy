package org.diskproject.client.components.hypothesis;

import java.util.ArrayList;
import java.util.List;

import org.diskproject.client.components.searchpanel.SearchableItem;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;
import org.diskproject.shared.classes.loi.WorkflowBindings;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

public class ExecutionList extends SearchableItem {
	interface Binder extends UiBinder<Widget, ExecutionList> {};
	private static Binder uiBinder = GWT.create(Binder.class);
	
	@UiField DivElement title, description;
	@UiField TableSectionElement tloilist;
	
	public ExecutionList() {
		initWidget(uiBinder.createAndBindUi(this)); 
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
			date.setInnerText(tloi.getDateCreated());
			
			//status
			TableCellElement st = TableCellElement.as(DOM.createTD());
			Status curStatus = tloi.getStatus();
			if (curStatus == Status.SUCCESSFUL) {
				st.setInnerText("SUCCESSFUL");
				st.getStyle().setColor("green");
			} else if (curStatus == Status.QUEUED) {
				st.setInnerText("QUEUED");
			} else if (curStatus == Status.RUNNING) {
				st.setInnerText("RUNNING");
			} else if (curStatus == Status.FAILED) {
				st.setInnerText("FAILED");
				st.getStyle().setColor("red");
			}
			
			//inputs
			TableCellElement in = TableCellElement.as(DOM.createTD());
			int bindings = 0;
			List<List<WorkflowBindings>> allwf = new ArrayList<List<WorkflowBindings>>();
			allwf.add(tloi.getMetaWorkflows());
			allwf.add(tloi.getWorkflows());
			
			for (List<WorkflowBindings> wbl: allwf) {
				for (WorkflowBindings wb: wbl) {
					if (wb != null && wb.getBindings() != null)
						bindings += wb.getBindings().size();
				}
			}
			in.setInnerText(Integer.toString(bindings));

			//outputs
			TableCellElement out = TableCellElement.as(DOM.createTD());
			int outs = 0;
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
				pval.setInnerText(Double.toString(tloi.getConfidenceValue()));
			} else if (curStatus == Status.FAILED) {
				pval.setInnerText("-");
			} else {
				pval.setInnerText("...");
			}

			TableCellElement viz = TableCellElement.as(DOM.createTD());
			
			row.appendChild(n);
			row.appendChild(date);
			row.appendChild(st);
			row.appendChild(in);
			row.appendChild(out);
			row.appendChild(pval);
			row.appendChild(viz);
			tloilist.appendChild(row);
		}
	}

}
