package org.diskproject.shared.classes.hypothesis;

import org.diskproject.shared.classes.common.Graph;

public class Hypothesis {
  String id;
  String name;
  String description;
  String parentId;
  Graph graph;
  String dateCreated;
  String dateModified;
  String author;
  String notes;
  //FIXME
  String question;
  
  public void setQuestion (String q) {
    this.question = q;
  }
  
  public String getQuestion () {
    return this.question;
  }

  public Hypothesis (String id, String name, String description, String parentId, Graph graph){
	  this.id = id;
	  this.name = name;
	  this.description = description;
	  this.parentId = parentId;
	  this.graph = graph;
  }

  public Hypothesis(){}
  
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

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
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

  public Graph getGraph() {
    return graph;
  }

  public void setGraph(Graph graph) {
    this.graph = graph;
  }

  public void setDateCreated(String date) {
	  this.dateCreated = date;
  }

  public void setAuthor (String author) {
	  this.author = author;
  }

  public String getDateCreated () {
	  return this.dateCreated;
  }

  public String getAuthor () {
	  return this.author;
  }

  public void setDateModified (String date) {
	  this.dateModified = date;
  }

  public String getDateModified () {
	  return dateModified;
  }
}
