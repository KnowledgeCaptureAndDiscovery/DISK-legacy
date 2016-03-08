package org.diskproject.client.components.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.client.components.list.events.HasListHandlers;
import org.diskproject.client.components.list.events.ListItemDeletionEvent;
import org.diskproject.client.components.list.events.ListItemDeletionHandler;
import org.diskproject.client.components.list.events.ListItemSelectionEvent;
import org.diskproject.client.components.list.events.ListItemSelectionHandler;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.vaadin.polymer.iron.widget.IronFlexLayout;
import com.vaadin.polymer.paper.widget.PaperItem;

public class ListWidget extends IronFlexLayout implements HasListHandlers {
  private HandlerManager handlerManager;
  private List<ListNode> nodes;
  private Map<String, ListNode> nodemap;
  
  public ListWidget(String html) {
    handlerManager = new HandlerManager(this);
    nodes = new ArrayList<ListNode>();
    nodemap = new HashMap<String, ListNode>();
  }

  public void addNode(final ListNode node) {
    PaperItem item = node.getItem();
    this.add(item);
    item.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        fireEvent(new ListItemSelectionEvent(node));
      }
    });
    node.getDeleteIcon().addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        event.stopPropagation();
        fireEvent(new ListItemDeletionEvent(node));
      }
    });
    nodes.add(node);
    nodemap.put(node.getId(), node);
  }
  
  @Override
  public void clear() {
    super.clear();
    nodes.clear();
    nodemap.clear();
  }
  
  public void removeNode(ListNode node) {
    ListNode n = nodemap.get(node.getId());
    if(n != null) {
      nodes.remove(n);      
      nodemap.remove(n.getId());
      n.getItem().removeFromParent();
    }
  }
  
  public void updateNode(String oldid, ListNode node) {
    nodemap.remove(oldid);
    nodemap.put(node.getId(), node);
    node.updateItem();
  }

  public ListNode getNode(String id) {
    return nodemap.get(id);
  }
  
  public List<ListNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<ListNode> nodes) {
    this.nodes = nodes;
  }

  @Override
  public void fireEvent(GwtEvent<?> event) {
    handlerManager.fireEvent(event);
  }
  
  @Override
  public HandlerRegistration addListItemSelectionHandler(
      ListItemSelectionHandler handler) {
    return handlerManager.addHandler(ListItemSelectionEvent.TYPE, handler);
  }

  @Override
  public HandlerRegistration addListItemDeletionHandler(
      ListItemDeletionHandler handler) {
    return handlerManager.addHandler(ListItemDeletionEvent.TYPE, handler);
  }
}
