package org.diskproject.client.components.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.HTML;
import com.vaadin.polymer.iron.widget.IronCollapse;
import com.vaadin.polymer.iron.widget.IronIcon;
import com.vaadin.polymer.paper.widget.PaperIconButton;
import com.vaadin.polymer.paper.widget.PaperItem;

public class TreeNode implements Comparable<TreeNode> {
  String id;
  String name;
  String description;
  Object data;
  
  PaperItem item;
  IronCollapse childrenSection;
  
  IronIcon icon, collapser;
  String iconName;
  PaperIconButton deleteIcon;
  
  boolean expanded;
  
  TreeNode parent;
  List<TreeNode> children;
  Map<String, TreeNode> nodemap;
  
  public TreeNode(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.icon = new IronIcon();
    this.children = new ArrayList<TreeNode>();
    this.nodemap = new HashMap<String, TreeNode>();
    
    this.deleteIcon = new PaperIconButton();
    deleteIcon.addStyleName("action-button");
    deleteIcon.addStyleName("red-button");
    deleteIcon.setIcon("icons:cancel");
    
    this.childrenSection = new IronCollapse();
    this.childrenSection.addStyleName("pad");
    
    this.collapser = new IronIcon();
    this.collapser.addStyleName("collapser");
    this.collapser.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        event.stopPropagation();
        expanded = !expanded;
        childrenSection.toggle();
        setIcons();
      }
    });
    
    this.item = new PaperItem();
    this.updateItem();
  }

  public IronIcon getIcon() {
    return icon;
  }

  public void setIconStyle(String style) {
    this.icon.addStyleName(style);
  }
  
  public void setIcon(String iconstr) {
    this.iconName = iconstr;
  }
  
  public PaperIconButton getDeleteIcon() {
    return deleteIcon;
  }

  public void setDeleteIcon(PaperIconButton deleteIcon) {
    this.deleteIcon = deleteIcon;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }
  
  public PaperItem getItem() {
    return this.item;
  }
  
  public PaperItem updateItem() {
    if(this.item == null)
      return null;
    
    item.clear();
    icon.setIcon(this.iconName);  
    item.add(collapser);
    item.add(icon);
    String html = "<div class='name'>" + this.name + "</div>";
    html += "<div class='description'>"+this.description+"</div>";
    item.add(new HTML(html));
    item.add(deleteIcon);
    this.setIcons();
    
    return this.item;
  }
  
  public IronCollapse updateChildrenSection() {
    childrenSection.clear();
    if(this.expanded)
      childrenSection.setOpened(true);
    for(TreeNode childnode : this.getChildren())
      childrenSection.add(childnode.getItem());
    
    this.setIcons();
    return childrenSection;
  }
  
  public IronCollapse getChildrenSection() {
    return this.childrenSection;
  }
  
  public TreeNode findNode(String id) {
    if(this.id.equals(id))
      return this;
    
    for(TreeNode child : this.children) {
      TreeNode node = child.findNode(id);
      if(node != null)
        return node;
    }
    return null;
  }
  
  public boolean removeNode(String id) {
    if(this.id.equals(id)) {
      this.item.removeFromParent();
      return true;
    }
    for(TreeNode child : this.children) {
      if(child.removeNode(id))
        return true;
    }
    return false;
  }
  
  public boolean isExpanded() {
    return expanded;
  }

  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
  }
  
  public void addChild(TreeNode child) {
    this.children.add(child);
    this.nodemap.put(child.getId(), child);
    child.setParent(this);
    
    this.childrenSection.add(child.getItem());
  }

  public List<TreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<TreeNode> children) {
    this.children = children;
  }

  public TreeNode getParent() {
    return parent;
  }

  public void setParent(TreeNode parent) {
    this.parent = parent;
  }

  private void setIcons() {
    if(this.children.size() == 0) {
      collapser.setIcon("icons:a");
      collapser.addStyleName("transparent");
    }
    else {
      collapser.removeStyleName("transparent");
      if(this.isExpanded()) {
        collapser.setIcon("icons:expand-more");
      }
      else {
        collapser.setIcon("icons:chevron-right");
      }
    }
  }  
  
  @Override
  public int compareTo(TreeNode node) {
    return id.compareTo(node.getId());
  }
}
