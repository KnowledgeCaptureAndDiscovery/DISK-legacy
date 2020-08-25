package org.diskproject.client.application.loi;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.diskproject.client.Config;
import org.diskproject.client.application.ApplicationSubviewImpl;
import org.diskproject.client.components.list.ListNode;
import org.diskproject.client.components.list.ListWidget;
import org.diskproject.client.components.list.events.ListItemActionEvent;
import org.diskproject.client.components.list.events.ListItemSelectionEvent;
import org.diskproject.client.components.loader.Loader;
import org.diskproject.client.components.loi.LOIEditor;
import org.diskproject.client.components.loi.events.LOISaveEvent;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.util.GUID;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;
import com.vaadin.polymer.paper.widget.PaperFab;

public class LOIView extends ApplicationSubviewImpl 
  implements LOIPresenter.MyView {

  String userid;
  String domain;
  boolean addmode;
  List<TreeItem> LOIList;

  @UiField Loader loader;
  @UiField PaperFab addicon;
  @UiField ListWidget loilist; 
  @UiField LOIEditor form;
  @UiField HTMLPanel description;
  @UiField ListBox order;
  
  @UiField DialogBox helpDialog;
  
  interface Binder extends UiBinder<Widget, LOIView> {
  }

  @Inject
  public LOIView(Binder binder) {
    initWidget(binder.createAndBindUi(this));
    loilist.addDeleteAction();
  }

  @Override
  public void initializeParameters(String userid, String domain, 
      String[] params, boolean edit, 
      SimplePanel sidebar, SimplePanel toolbar) {
    
    clear();
    
    if(this.userid != userid || this.domain != domain) {
      this.userid = userid;
      this.domain = domain;
      DiskREST.setDomain(domain);
      DiskREST.setUsername(userid);
      form.initialize(userid, domain);
    }
    
    this.setHeader(toolbar);    
    this.setSidebar(sidebar);
    
    if(params.length == 0) {
    	if (LOIList == null) {
    		this.loadLOIList();
    	} else {
    		this.showLOIList();
    	}
    }
    else {
      this.showLOI(params[0]);
    }
  }

  private void clear() {
    loader.setVisible(false);
    form.setVisible(false);
    loilist.setVisible(false);
    description.setVisible(false);
    addicon.setVisible(false);
    addmode = false;
  }

  private void loadLOIList () {
    loader.setVisible(true);
    DiskREST.listLOI(new Callback<List<TreeItem>, Throwable>() {
      @Override
      public void onSuccess(List<TreeItem> result) {
    	  LOIList = result;
    	  showLOIList();
      }
      @Override
      public void onFailure(Throwable reason) {
        loader.setVisible(false);   
        AppNotification.notifyFailure(reason.getMessage());
      }
    });    
  }

	@UiHandler("order")
	void onChange(ChangeEvent event) {
    	showLOIList();
	}

  private void applyOrder () {
    String orderType = order.getSelectedValue();
    GWT.log(">> " + orderType);
    if (orderType != null) {
    	if (orderType.compareTo("date") == 0) {
			Collections.sort(LOIList, new Comparator<TreeItem>(){
				@Override
				public int compare (TreeItem l, TreeItem r) {
					String lc = l.getCreationDate();
					String lr = r.getCreationDate();
					if (lc != null && lr != null) {
						DateTimeFormat fm = DateTimeFormat.getFormat("HH:mm:ss yyyy-MM-dd");
						Date dl = fm.parse(lc);
						Date dr = fm.parse(lr);
						if (dl.after(dr)) return -1;
						else return 1;
					} else if (lc != null) {
						return -1;
					} else if (lr != null) {
						return 1;
					}
					return 0;
				}
			});
    	} else if (orderType.compareTo("author") == 0) {
			Collections.sort(LOIList, new Comparator<TreeItem>(){
				@Override
				public int compare (TreeItem l, TreeItem r) {
					String la = l.getAuthor();
					String ra = r.getAuthor();
					if (la != null && ra != null) {
						return la.compareTo(ra);
					} else if (la != null) {
						return -1;
					} else if (ra != null) {
						return 1;
					}
					return 0;
				}
			});
    	}
    }
  }

  private void showLOIList() {
	loilist.clear();
	applyOrder();
	for(TreeItem item : LOIList) {
	  ListNode node = new ListNode(
		  item.getId(),
		  item.getName(), 
		  item.getDescription(),
		  item.getCreationDate(),
		  item.getAuthor());
	  node.setIcon("icons:explore");
	  node.setIconStyle("green");
	  loilist.addNode(node);
	}
	loader.setVisible(false);  
	addicon.setVisible(true);
	loilist.setVisible(true);
	description.setVisible(true);
  }

  private void showLOI(final String loiId) {
    loilist.setVisible(false);
    description.setVisible(false);
    addicon.setVisible(false);
    loader.setVisible(true);
    Polymer.ready(form.getElement(), new Function<Object, Object>() {
      @Override
      public Object call(Object o) {
        DiskREST.getLOI(loiId, new Callback<LineOfInquiry, Throwable>() {
          @Override
          public void onSuccess(LineOfInquiry result) {
            loader.setVisible(false);
            form.setVisible(true);
            form.setNamespace(getNamespace(result.getId()));
            form.load(result);            
          }
          @Override
          public void onFailure(Throwable reason) {
            loader.setVisible(false);
            AppNotification.notifyFailure(reason.getMessage());
          }
        });
        return null;
      }
    });
  }

  @UiHandler("addicon")
  void onAddIconClicked(ClickEvent event) {
    loilist.setVisible(false);
    description.setVisible(false);
    addicon.setVisible(false);
    form.setVisible(true);    
    addmode = true;

    String id = GUID.randomId("LOI");

    LineOfInquiry loi = new LineOfInquiry();
    loi.setId(id);
    loi.setHypothesisQuery("");
    form.setNamespace(this.getNamespace(id));
    form.load(loi);
    
    History.newItem(this.getHistoryToken(id), false);
  }

  @UiHandler("form")
  void onLOIFormSave(LOISaveEvent event) {
    LineOfInquiry loi = event.getLOI();
    
    if(this.addmode) {
      DiskREST.addLOI(loi, new Callback<Void, Throwable>() {
        @Override
        public void onSuccess(Void result) {
          LOIList = null;
          AppNotification.notifySuccess("Saved", 500);
        }        
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure("Could not save: " + reason.getMessage()); 
        }
      });
    } else {
      DiskREST.updateLOI(loi, new Callback<Void, Throwable>() {
        @Override
        public void onSuccess(Void result) {
          LOIList = null;
          AppNotification.notifySuccess("Updated", 500);
        }        
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure("Could not save: " + reason.getMessage()); 
        }
      });      
    }
  }
  
  @UiHandler("loilist")
  void onListItemSelected(ListItemSelectionEvent event) {
    ListNode node = event.getItem();
    String token = NameTokens.getLOIs()+"/"+this.userid+"/"+this.domain;
    token += "/" + node.getId();
    History.newItem(token);
  }
  
  @UiHandler("loilist")
  void onListItemDeleted(ListItemActionEvent event) {
    final ListNode node = event.getItem();
    if(loilist.getNode(node.getId()) != null) {
      if(Window.confirm("Are you sure you want to delete " + node.getName())) {
        DiskREST.deleteLOI(node.getId(), new Callback<Void, Throwable>() {
          @Override
          public void onFailure(Throwable reason) {
            AppNotification.notifyFailure(reason.getMessage());
          }
          @Override
          public void onSuccess(Void result) {
            loilist.removeNode(node);
          }
        });
      }
    }
  }
  
  private void setHeader(SimplePanel toolbar) {
    // Set Toolbar header
    toolbar.clear();
    String title = "<h3>Lines of Inquiry</h3>";
    String icon = "icons:explore";

    HTML div = new HTML("<nav><div class='layout horizontal center'>"
        + "<iron-icon class='green' icon='" + icon + "'/></div></nav>");
    div.getElement().getChild(0).getChild(0).appendChild(new HTML(title).getElement());
    toolbar.add(div);    
  }
  
  private void setSidebar(SimplePanel sidebar) {
    // TODO: Modify sidebar
  }
  
  private String getHistoryToken(String id) {    
    return NameTokens.getLOIs()+"/" + this.userid+"/"+this.domain + "/" + id;    
  }
  
  private String getNamespace(String id) {
    return Config.getServerURL() + "/"+userid+"/"+domain + "/loi/" + id + "#";
  }

  @UiHandler("helpicon")
  void onHelpIconClicked(ClickEvent event) {
	  helpDialog.center();
	  helpDialog.setWidth("800px");
	  helpDialog.center();
  }

  @UiHandler("closeDialog")
  void onCloseButtonClicked(ClickEvent event) {
	  helpDialog.hide();
  }
}
