package org.diskproject.shared.classes.util;

public class KBConstants {
  private static String diskuri = "http://disk-project.org/ontology/disk";
  private static String neurouri = "https://w3id.org/disk/ontology/neuro";
  private static String omicsuri = "http://disk-project.org/ontology/omics";
  private static String hypuri = "http://disk-project.org/ontology/hypothesis";

  private static String owlns = "http://www.w3.org/2002/07/owl#";
  private static String rdfns = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static String rdfsns = "http://www.w3.org/2000/01/rdf-schema#";
  private static String xsdns = "http://www.w3.org/2001/XMLSchema#";

  private static String dctermsns = "http://purl.org/dc/terms/";
  private static String dcns = "http://purl.org/dc/elements/1.1/";

  public static String DISKURI() {
    return diskuri;
  }
  
  public static String DISKNS() {
    return diskuri + "#";
  }
  
  public static String OMICSURI() {
    return omicsuri;
  }

  public static String OMICSNS() {
    return omicsuri + "#";
  }

  public static String NEUROURI() {
    return neurouri;
  }

  public static String NEURONS() {
    return neurouri + "#";
  }

  public static String HYPURI() {
    return hypuri;
  }

  public static String HYPNS() {
    return hypuri + "#";
  }
 
  public static String DCTERMSNS() {
    return dctermsns;
  }

  public static String DCNS() {
    return dcns;
  }

  public static String OWLNS() {
    return owlns;
  }

  public static String RDFNS() {
    return rdfns;
  }

  public static String RDFSNS() {
    return rdfsns;
  }
  
  public static String XSDNS() {
    return xsdns;
  }

}
