package org.diskproject.client.components.tree;

import org.diskproject.client.components.tree.events.HasTreeHandlers;
import org.diskproject.client.components.tree.events.TreeItemDeletionEvent;
import org.diskproject.client.components.tree.events.TreeItemDeletionHandler;
import org.diskproject.client.components.tree.events.TreeItemSelectionEvent;
import org.diskproject.client.components.tree.events.TreeItemSelectionHandler;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.vaadin.polymer.iron.widget.IronFlexLayout;
import com.vaadin.polymer.paper.widget.PaperItem;

public class TreeWidget extends IronFlexLayout implements HasTreeHandlers {
  private HandlerManager handlerManager;
  private TreeNode root;
  private boolean showRoot;
  
  public TreeWidget(String html) {
    handlerManager = new HandlerManager(this);
    showRoot = true;
  }
  
  public void removeNode(TreeNode node) {
    this.root.removeNode(node.getId());
  }
  
  public void updateNode(String oldid, TreeNode node) {
    if(node.getParent() != null) {
      node.nodemap.remove(oldid);
      node.nodemap.put(node.getId(), node);
    }
    node.updateItem();
  }

  public TreeNode getNode(String id) {
    return this.root.findNode(id);
  }
  
  public TreeNode getRoot() {
    return this.root;
  }

  public void setRoot(TreeNode root) {
    this.root = root;
    this.clear();
    
    if(showRoot)
      this.add(root.getItem());
  }

  public void addNode(TreeNode parent, final TreeNode node) {
    parent.addChild(node);
    parent.getItem().add(node.getChildrenSection());
    GWT.log("Adding "+node.getName()+" to "+parent.getName());
    PaperItem item = node.getItem();
    
    item.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        fireEvent(new TreeItemSelectionEvent(node));
      }
    });
    node.getDeleteIcon().addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        event.stopPropagation();
        fireEvent(new TreeItemDeletionEvent(node));
      }
    });
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
