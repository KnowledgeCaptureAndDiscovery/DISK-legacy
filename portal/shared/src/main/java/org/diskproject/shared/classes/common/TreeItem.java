package org.diskproject.shared.classes.common;

public class TreeItem {
  String id;
  String name;
  String description;
  String creationDate;
  String author;
  String parentId;

  public TreeItem() { }
  
  public TreeItem(String id, String name, String description, String parentId, String creationDate, String author) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.parentId = parentId;
    this.creationDate = creationDate;
    this.author = author;
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

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }
  
  public void setCreationDate (String date ) {
	  this.creationDate = date;
  }
  
  public String getCreationDate () {
	  return creationDate;
  }
  
  public void setAuthor (String author) {
	  this.author = author;
  }
  
  public String getAuthor () {
	  return this.author;
  }
}
