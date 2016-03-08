package org.diskproject.client.components.treeold;

import java.util.HashMap;
import java.util.Map;

import org.diskproject.client.components.treeold.events.HasTreeHandlers;
import org.diskproject.client.components.treeold.events.TreeItemDeletionEvent;
import org.diskproject.client.components.treeold.events.TreeItemDeletionHandler;
import org.diskproject.client.components.treeold.events.TreeItemSelectionEvent;
import org.diskproject.client.components.treeold.events.TreeItemSelectionHandler;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.vaadin.polymer.PolymerWidget;
import com.vaadin.polymer.iron.widget.IronCollapse;
import com.vaadin.polymer.iron.widget.IronFlexLayout;
import com.vaadin.polymer.paper.widget.PaperItem;

public class TreeWidget extends IronFlexLayout implements HasTreeHandlers {
  private HandlerManager handlerManager;
  private boolean showRoot;
  private Map<String, PolymerWidget> widgets;
  
  public TreeWidget(String html) {
    handlerManager = new HandlerManager(this);
    widgets = new HashMap<String, PolymerWidget>();
  }

  public void initialize(TreeNode root) {
    this.clear();
    PaperItem rootitem = this.populateListItems(root, this, this.showRoot);
    rootitem.addStyleName("no-borders");
  }
  
  private PaperItem populateListItems(final TreeNode node, PolymerWidget parent, 
      boolean showItem) {
    final PaperItem item = node.getItem();
    if(showItem)
      parent.add(item);
    
    widgets.put(node.getId(), item);
    
    if(node.getChildren().size() > 0) {
      if(showItem) {
        final IronCollapse collapse = new IronCollapse();
        collapse.addStyleName("pad");
        if(node.isExpanded())
          collapse.setOpened(true);
        
        for(TreeNode childnode : node.getChildren()) {
          populateListItems(childnode, collapse, true);
        }
        
        node.collapser.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            collapse.toggle();
            event.stopPropagation();
          }
        });
        widgets.put(node.getId(), collapse);
        parent.add(collapse);
      }
      else {
        int i = 0;
        for(TreeNode childnode : node.getChildren()) {
          PaperItem childitem = populateListItems(childnode, parent, true);
          if(i==0 && ! showRoot) {
            childitem.addStyleName("no-borders");
          }
          i++;
        }
      }
    }
    
    node.getDeleteicon().addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        event.stopPropagation();
        fireEvent(new TreeItemDeletionEvent(node));
      }
    });
    
    item.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        fireEvent(new TreeItemSelectionEvent(node));
      }
    });

    return item;
  }

  public boolean isShowRoot() {
    return showRoot;
  }

  public void setShowRoot(boolean showRoot) {
    this.showRoot = showRoot;
  }

  public void removeNode(TreeNode node) {
    if(widgets.containsKey(node.getId()))
      widgets.get(node.getId()).removeFromParent();
  }
  
  @Override
  public void fireEvent(GwtEvent<?> event) {
    handlerManager.fireEvent(event);
  }
  
  @Override
  public HandlerRegistration addTreeItemSelectionHandler(
      TreeItemSelectionHandler handler) {
    return handlerManager.addHandler(TreeItemSelectionEvent.TYPE, handler);
  }

  @Override
  public HandlerRegistration addTreeItemDeletionHandler(
      TreeItemDeletionHandler handler) {
    return handlerManager.addHandler(TreeItemDeletionEvent.TYPE, handler);
  }
}
