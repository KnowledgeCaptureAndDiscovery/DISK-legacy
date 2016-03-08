package org.diskproject.client.components.list;

import com.google.gwt.user.client.ui.HTML;
import com.vaadin.polymer.iron.widget.IronIcon;
import com.vaadin.polymer.paper.widget.PaperIconButton;
import com.vaadin.polymer.paper.widget.PaperItem;

public class ListNode implements Comparable<ListNode> {
  String id;
  String name;
  String description;
  Object data;
  
  PaperItem item;
  IronIcon icon;
  String iconName;
  PaperIconButton deleteIcon;
  
  public ListNode(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.icon = new IronIcon();
    
    deleteIcon = new PaperIconButton();
    deleteIcon.addStyleName("action-button");
    deleteIcon.addStyleName("red-button");
    deleteIcon.setIcon("icons:cancel");
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
    if(this.item != null)
      return this.item;
    
    this.item = new PaperItem();
    return this.updateItem();
  }
  
  public PaperItem updateItem() {
    if(this.item == null)
      return null;
    
    item.clear();
    icon.setIcon(this.iconName);    
    item.add(icon);
    String html = "<div class='name'>" + this.name + "</div>";
    html += "<div class='description'>"+this.description+"</div>";
    item.add(new HTML(html));
    item.add(deleteIcon);
    return this.item;
  }
  
  @Override
  public int compareTo(ListNode node) {
    return id.compareTo(node.getId());
  }
}
