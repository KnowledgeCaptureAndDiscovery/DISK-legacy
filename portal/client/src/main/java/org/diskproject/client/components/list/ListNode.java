package org.diskproject.client.components.list;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.polymer.iron.widget.IronIcon;
import com.vaadin.polymer.paper.widget.PaperItem;

public class ListNode implements Comparable<ListNode> {
  String id;
  String name;
  String description;
  Object data;
  Widget content;
  
  PaperItem item;
  IronIcon icon;
  String iconName;
  
  public ListNode(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.icon = new IronIcon();
    this.setContent(name,  description);
  }
  
  public ListNode(String id, Widget content) {
    this.id = id;
    this.content = content;
    this.icon = new IronIcon();
  }
  
  private void setContent(String name, String description) {
    String html = "<div class='name'>" + this.name + "</div>";
    html += "<div class='description'>"+this.description+"</div>";
    this.content = new HTML(html);
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
    this.setContent(name, description);
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
    this.setContent(name, description);
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
    if(this.iconName != null) {
      icon.setIcon(this.iconName);    
      item.add(icon);
    }
    item.add(content);
    return this.item;
  }
  
  @Override
  public int compareTo(ListNode node) {
    return id.compareTo(node.getId());
  }
}
