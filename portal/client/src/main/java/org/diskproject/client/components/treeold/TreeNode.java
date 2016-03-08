package org.diskproject.client.components.treeold;

import java.util.Set;
import java.util.TreeSet;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.HTML;
import com.vaadin.polymer.iron.widget.IronIcon;
import com.vaadin.polymer.paper.widget.PaperIconButton;
import com.vaadin.polymer.paper.widget.PaperItem;

public class TreeNode implements Comparable<TreeNode> {
  IronIcon collapser;
  IronIcon icon;
  PaperIconButton deleteicon;
  PaperItem item;
  
  String defaultIconName;
  String expandedIconName;
  String color;
  
  String id;
  String name;
  String description;  
  String type;
  boolean leaf;
  boolean expanded;
  Object data;  
  Set<TreeNode> children;
  
  public TreeNode(String id, String name, String description, boolean leaf) {
    this.id = id;
    this.name = name;
    this.description = description;    
    this.leaf = leaf;
    this.children = new TreeSet<TreeNode>();
    
    this.icon = new IronIcon();
    
    this.deleteicon = new PaperIconButton();
    deleteicon.addStyleName("action-button");
    deleteicon.addStyleName("red-button");
    deleteicon.setIcon("icons:cancel");
        
    this.collapser = new IronIcon();
    this.collapser.addStyleName("collapser");
    this.collapser.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        expanded = !expanded;
        setIcons();
      }
    });
  }

  public IronIcon getIcon() {
    return icon;
  }

  public void setIconStyle(String style) {
    this.icon.addStyleName(style);
  }
  
  public void setDefaultIcon(String iconstr) {
    this.defaultIconName = iconstr;
  }

  public void setExpandedIcon(String iconstr) {
    this.expandedIconName = iconstr;
  }

  public PaperIconButton getDeleteicon() {
    return deleteicon;
  }

  public void setDeleteicon(PaperIconButton deleteicon) {
    this.deleteicon = deleteicon;
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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isLeaf() {
    return leaf;
  }

  public void setLeaf(boolean leaf) {
    this.leaf = leaf;
  }

  public boolean isExpanded() {
    return expanded;
  }

  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
  }

  public Set<TreeNode> getChildren() {
    return children;
  }

  public void setChildren(Set<TreeNode> children) {
    this.children = children;
  }
  
  public void addChild(TreeNode n) {
    children.add(n);
    this.setLeaf(false);
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public PaperItem getItem() {
    if(this.item != null)
      return this.item;
    
    this.item = new PaperItem();
    item.add(collapser);
    item.add(icon);
    String html = "<div class='name'>" + this.name + "</div>";
    html += "<div class='description'>"+this.description+"</div>";
    item.add(new HTML(html));
    item.add(deleteicon);
    this.setIcons();
    return item;
  }
  
  protected void setIcons() {
    if(this.isLeaf() || this.children.size() == 0) {
      collapser.setIcon("icons:a");
      collapser.addStyleName("transparent");
      icon.setIcon(this.defaultIconName);
    }
    else {
      collapser.removeStyleName("transparent");
      if(this.isExpanded()) {
        collapser.setIcon("icons:expand-more");
        icon.setIcon(this.expandedIconName);
      }
      else {
        collapser.setIcon("icons:chevron-right");
        icon.setIcon(this.defaultIconName);
      }
    }
  }
  
  @Override
  public int compareTo(TreeNode node) {
    return id.compareTo(node.getId());
  }
}
