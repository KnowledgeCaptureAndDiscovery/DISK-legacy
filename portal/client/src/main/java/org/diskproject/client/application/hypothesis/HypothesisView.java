package org.diskproject.client.application.hypothesis;

import java.util.HashMap;
import java.util.List;

import org.diskproject.client.Config;
import org.diskproject.client.application.ApplicationSubviewImpl;
import org.diskproject.client.components.hypothesis.HypothesisEditor;
import org.diskproject.client.components.hypothesis.events.HypothesisSaveEvent;
import org.diskproject.client.components.loader.Loader;
import org.diskproject.client.components.tloi.TriggeredLOIViewer;
import org.diskproject.client.components.tree.TreeNode;
import org.diskproject.client.components.tree.TreeWidget;
import org.diskproject.client.components.tree.events.TreeItemActionEvent;
import org.diskproject.client.components.tree.events.TreeItemSelectionEvent;
import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.util.GUID;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;
import com.vaadin.polymer.iron.widget.IronIcon;
import com.vaadin.polymer.paper.widget.PaperButton;
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
  @UiField HTMLPanel matchlist;
  
  List<TreeItem> treelist;
  List<TriggeredLOI> tloilist; 
  
  interface Binder extends UiBinder<Widget, HypothesisView> {
  }

  @Inject
  public HypothesisView(Binder binder) {
    initWidget(binder.createAndBindUi(this));
    tree.addCustomAction("query", null, "icons:explore", 
        "green-button query-action");
    tree.addDeleteAction();
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
    else if(params.length == 1) {
      this.showHypothesis(params[0]);
    }
    else if(params.length == 2 && params[1].equals("query")) {
      this.showHypothesisMatches(params[0]);
    }
  }
  
  private void clear() {
    loader.setVisible(false);
    form.setVisible(false);
    tree.setVisible(false);
    addicon.setVisible(false);
    matchlist.setVisible(false);
    addmode = false;
  }
  
  private void showHypothesisList() {
    loader.setVisible(true);
    DiskREST.listHypotheses(new Callback<List<TreeItem>, Throwable>() {
      @Override
      public void onSuccess(List<TreeItem> result) {
        treelist = result;
        loadHypothesisTLOITree();
      }
      @Override
      public void onFailure(Throwable reason) {
        loader.setVisible(false);   
        AppNotification.notifyFailure(reason.getMessage());
        GWT.log("Failed", reason);
      }
    });
    DiskREST.listTriggeredLOIs(new Callback<List<TriggeredLOI>, Throwable>() {
      @Override
      public void onSuccess(List<TriggeredLOI> result) {
        tloilist = result;
        loadHypothesisTLOITree();
      }
      @Override
      public void onFailure(Throwable reason) {
        loader.setVisible(false);   
        AppNotification.notifyFailure(reason.getMessage());
        GWT.log("Failed", reason);
      }
    });
  }
  
  private void loadHypothesisTLOITree() {
    if(treelist == null || tloilist == null)
      return;
    
    loader.setVisible(false);  
    addicon.setVisible(true);
    tree.setVisible(true);
    
    TreeNode root = new TreeNode("Hypotheses", "Hypotheses", 
        "A List of Hypotheses");
    
    HashMap<String, TreeNode> map = new HashMap<String, TreeNode>();
    for(TreeItem item : treelist) {
      TreeNode node = new TreeNode(
          item.getId(),
          item.getName(), 
          item.getDescription());
      node.setIcon("icons:help-outline");
      node.setExpandedIcon("icons:help-outline");
      node.setIconStyle("orange");
      node.setType(NameTokens.hypotheses);
      map.put(item.getId(), node);
    }
    for(TriggeredLOI item : tloilist) {
      TreeNode node = new TreeNode(
          item.getId(),
          new HTML(item.getHeaderHTML()));
      node.setName(item.getName(), false);
      node.setIcon("icons:explore");
      node.setExpandedIcon("icons:explore");
      node.setIconStyle("orange");
      node.setType(NameTokens.tlois);
      
      TreeNode parent = root;
      if(map.containsKey(item.getParentHypothesisId()))
        parent = map.get(item.getParentHypothesisId());
      parent.setExpanded(true);      
      tree.addNode(parent, node);
      
      for(String hypid : item.getResultingHypothesisIds()) {
        if(map.containsKey(hypid)) {
          TreeNode child = map.get(hypid);
          node.setExpanded(true);      
          tree.addNode(node, child);
        }
      }
    }    
    for(TreeItem item : this.treelist) {
      TreeNode node = map.get(item.getId());
      if(node.getParent() == null)      
        tree.addNode(root, node);
    }
    tree.setRoot(root);
  }
  
  private void showHypothesis(final String id) {
    loader.setVisible(true);
    Polymer.ready(form.getElement(), new Function<Object, Object>() {
      @Override
      public Object call(Object o) {
        DiskREST.getHypothesis(id, new Callback<Hypothesis, Throwable>() {
          @Override
          public void onSuccess(Hypothesis result) {
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
  
 private void showHypothesisMatches(final String id) {
   loader.setVisible(true);   
    DiskREST.queryHypothesis(id, 
        new Callback<List<TriggeredLOI>, Throwable>() {
      @Override
      public void onSuccess(List<TriggeredLOI> result) {
        loader.setVisible(false);
        matchlist.setVisible(true);
        showTriggeredLOIOptions(result);
      }
      @Override
      public void onFailure(Throwable reason) {
        loader.setVisible(false);
        AppNotification.notifyFailure(reason.getMessage());
      }
    });
  }

  private void showTriggeredLOIOptions(List<TriggeredLOI> tlois) {
    matchlist.clear();
    
    for(final TriggeredLOI tloi : tlois) {
      final TriggeredLOIViewer tviewer = new TriggeredLOIViewer();
      tviewer.initialize(userid, domain);
      tviewer.load(tloi);
      
      final HTMLPanel panel = new HTMLPanel("");
      panel.setStyleName("bordered-section");
      panel.add(tviewer);
      
      HTMLPanel buttonPanel = new HTMLPanel("");
      buttonPanel.setStyleName("horizontal end-justified layout");      
      PaperButton button = new PaperButton();
      IronIcon icon = new IronIcon();
      icon.setIcon("build");
      button.add(icon);
      button.add(new InlineHTML("Trigger"));
      button.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          triggerMatchedLOI(tloi);
          matchlist.remove(panel);
        }
      });
      buttonPanel.add(button);
      panel.add(buttonPanel);            
      matchlist.add(panel);
    }
  }
  
  void triggerMatchedLOI(final TriggeredLOI tloi) {
    DiskREST.addTriggeredLOI(tloi, new Callback<Void, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
      @Override
      public void onSuccess(Void result) {
        AppNotification.notifySuccess("Submitted.", 1000);
        String token = NameTokens.getTLOIs()+"/" + userid+"/"
            + domain + "/" + tloi.getId();
        History.newItem(token, true);
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
    hypothesis.setGraph(new Graph());
    form.setNamespace(this.getNamespace(id));    
    form.load(hypothesis);
    
    History.newItem(this.getHistoryToken(NameTokens.hypotheses, id), false);
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
    String token = this.getHistoryToken(node.getType(), node.getId());
    History.newItem(token); 
  }
  
  @UiHandler("tree")
  void onTreeItemAction(TreeItemActionEvent event) {
    final TreeNode node = event.getItem();
    if(event.getAction().getId().equals("delete")) {
      if(Window.confirm("Are you sure you want to delete " + node.getName())) {
        if(node.getType().equals(NameTokens.hypotheses)) {
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
        else if(node.getType().equals(NameTokens.tlois)) {
          DiskREST.deleteTriggeredLOI(node.getId(), new Callback<Void, Throwable>() {
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
    }
    else if(event.getAction().getId().equals("query")) {
      String token = 
          this.getHistoryToken(NameTokens.hypotheses, node.getId()) + "/query";
      History.newItem(token);
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
  
  private String getHistoryToken(String type, String id) {    
    return type+"/" + this.userid+"/"+this.domain + "/" + id;    
  }
  
  private String getNamespace(String id) {
    return Config.getServerURL() + "/"+userid+"/"+domain + "/hypotheses/" + id + "#";
  }
}
