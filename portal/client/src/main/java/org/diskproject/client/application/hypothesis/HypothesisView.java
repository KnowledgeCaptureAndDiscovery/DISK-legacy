package org.diskproject.client.application.hypothesis;

import java.util.HashMap;
import java.util.List;

import org.diskproject.client.Config;
import org.diskproject.client.application.ApplicationSubviewImpl;
import org.diskproject.client.components.hypothesis.HypothesisEditor;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveEvent;
import org.diskproject.client.components.loader.Loader;
import org.diskproject.client.components.treeold.TreeNode;
import org.diskproject.client.components.treeold.TreeWidget;
import org.diskproject.client.components.treeold.events.TreeItemDeletionEvent;
import org.diskproject.client.components.treeold.events.TreeItemSelectionEvent;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.util.GUID;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;
import com.vaadin.polymer.paper.widget.PaperFab;

public class HypothesisView extends ApplicationSubviewImpl 
  implements HypothesisPresenter.MyView {

  String userid;
  String domain;
  boolean editmode;
  boolean addmode;
  
  @UiField TreeWidget tree;
  @UiField Loader loader;
  @UiField HypothesisEditor form;
  @UiField PaperFab addicon;
  
  interface Binder extends UiBinder<Widget, HypothesisView> {
  }

  @Inject
  public HypothesisView(Binder binder) {
    initWidget(binder.createAndBindUi(this));
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
      this.showHypothesisList();
    }
    else {
      this.showHypothesis(params[0]);
    }
  }
  
  private void clear() {
    loader.setVisible(false);
    form.setVisible(false);
    tree.setVisible(false);
    addicon.setVisible(false);
    addmode = false;
  }
  
  private void showHypothesisList() {
    loader.setVisible(true);
    DiskREST.listHypotheses(new Callback<List<TreeItem>, Throwable>() {
      @Override
      public void onSuccess(List<TreeItem> result) {
        loadHypothesisTree(result);
        loader.setVisible(false);  
        addicon.setVisible(true);
        tree.setVisible(true);
      }
      @Override
      public void onFailure(Throwable reason) {
        loader.setVisible(false);   
        AppNotification.notifyFailure(reason.getMessage());
        GWT.log("Failed", reason);
      }
    });    
  }
  
  private void loadHypothesisTree(List<TreeItem> treelist) {
    TreeNode root = new TreeNode("Hypotheses", "Hypotheses", 
        "A List of Hypotheses", false);
    
    HashMap<String, TreeNode> map = new HashMap<String, TreeNode>();
    for(TreeItem item : treelist) {
      TreeNode node = new TreeNode(
          item.getId(),
          item.getName(), 
          item.getDescription(), true);
      node.setDefaultIcon("icons:help-outline");
      node.setExpandedIcon("icons:help");
      node.setIconStyle("orange");
      map.put(item.getId(), node);
    }
    for(TreeItem item : treelist) {
      TreeNode node = map.get(item.getId());
      TreeNode parent = root;
      if(map.containsKey(item.getParentId()))
        parent = map.get(item.getParentId());
      
      parent.addChild(node);
      //tree.addNode(parent, node);
      parent.setExpanded(true);
    }
    tree.initialize(root);
    //tree.setRoot(root);
  }
  
  private void showHypothesis(final String id) {
    tree.setVisible(false);
    addicon.setVisible(false);
    loader.setVisible(true);
    Polymer.ready(form.getElement(), new Function<Object, Object>() {
      @Override
      public Object call(Object o) {
        DiskREST.getHypothesis(id, new Callback<Hypothesis, Throwable>() {
          @Override
          public void onSuccess(Hypothesis result) {
            loader.setVisible(false);
            form.setVisible(true);
            form.load(result);
            form.setNamespace(getNamespace(result.getId()));            
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
    tree.setVisible(false);
    addicon.setVisible(false);
    form.setVisible(true);        
    addmode = true;
    
    String id = GUID.randomId("Hypothesis");

    Hypothesis hypothesis = new Hypothesis();
    hypothesis.setId(id);
    form.load(hypothesis);
    form.setNamespace(this.getNamespace(id));
    
    History.newItem(this.getHistoryToken(id), false);
  }
  
  @UiHandler("form")
  void onHypothesisFormSave(HypothesisSaveEvent event) {
    Hypothesis hypothesis = event.getHypothesis();
    if(this.addmode) {
      DiskREST.addHypothesis(hypothesis, new Callback<Void, Throwable>() {
        @Override
        public void onSuccess(Void result) {
          AppNotification.notifySuccess("Saved", 500);
        }        
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure("Could not save: " + reason.getMessage()); 
        }
      });
    } else {
      DiskREST.updateHypothesis(hypothesis, new Callback<Void, Throwable>() {
        @Override
        public void onSuccess(Void result) {
          AppNotification.notifySuccess("Updated", 500);
        }        
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure("Could not save: " + reason.getMessage()); 
        }
      });      
    }
  }
  
  @UiHandler("tree")
  void onTreeItemSelected(TreeItemSelectionEvent event) {
    TreeNode node = event.getItem();
    String token = NameTokens.getHypotheses()+"/"+this.userid+"/"+this.domain;
    token += "/" + node.getId();
    History.newItem(token);
  }
  
  @UiHandler("tree")
  void onTreeItemDeleted(TreeItemDeletionEvent event) {
    final TreeNode node = event.getItem();
    if(Window.confirm("Are you sure you want to delete " + node.getName())) {
      DiskREST.deleteHypothesis(node.getId(), new Callback<Void, Throwable>() {
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure(reason.getMessage());
        }
        @Override
        public void onSuccess(Void result) {
          AppNotification.notifySuccess("Deleted", 500);
          tree.removeNode(node);
        }
      });
    }
  }
  
  private void setHeader(SimplePanel toolbar) {
    // Set Toolbar header
    toolbar.clear();
    String title = "<h3>Hypotheses</h3>";
    String icon = "icons:help";

    HTML div = new HTML("<nav><div class='layout horizontal center'>"
        + "<iron-icon class='orange' icon='" + icon + "'/></div></nav>");
    div.getElement().getChild(0).getChild(0).appendChild(new HTML(title).getElement());
    toolbar.add(div);    
  }
  
  private void setSidebar(SimplePanel sidebar) {
    // TODO: Modify sidebar
  }
  
  private String getHistoryToken(String id) {    
    return NameTokens.getHypotheses()+"/" + this.userid+"/"+this.domain + "/" + id;    
  }
  
  private String getNamespace(String id) {
    return Config.getServerURL() + "/"+userid+"/"+domain + "/hypotheses/" + id + "#";
  }
}
