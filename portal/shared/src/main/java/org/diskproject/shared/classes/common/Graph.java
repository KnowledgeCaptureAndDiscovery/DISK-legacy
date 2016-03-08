package org.diskproject.shared.classes.common;

import java.util.ArrayList;
import java.util.List;

public class Graph {
  String id;
  List<Triple> triples;

  public Graph() {
    this.triples = new ArrayList<Triple>();
  }
  
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<Triple> getTriples() {
    return triples;
  }

  public void setTriples(List<Triple> triples) {
    this.triples = triples;
  }
  
  public void addTriple(Triple triple) {
    this.triples.add(triple);
  }
  
  public List<Triple> getTriplesForSubject(String subjectid) {
    List<Triple> striples = new ArrayList<Triple>();
    for(Triple triple : this.triples) {
      if(triple.getSubject() != null && triple.getSubject().equals(subjectid))
        striples.add(triple);
    }
    return striples;
  }
  
}
