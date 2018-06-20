package org.diskproject.server.repository;

import java.io.File;
import java.util.HashMap;

import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.diskproject.server.util.Config;
import org.diskproject.shared.classes.util.KBConstants;

import edu.isi.wings.ontapi.KBAPI;
import edu.isi.wings.ontapi.KBObject;
import edu.isi.wings.ontapi.OntFactory;
import edu.isi.wings.ontapi.OntSpec;

public class KBRepository {
  protected String server;
  protected String tdbdir;
  protected String onturi;
  protected OntFactory fac;
  protected String owlns, rdfns, rdfsns;
  protected KBAPI ontkb;
  protected HashMap<String, KBObject> pmap, cmap;
  
  protected void setConfiguration(String onturi) {
    if(Config.get() == null)
      return;
    PropertyListConfiguration props = Config.get().getProperties();
    this.server = props.getString("server");
    tdbdir = props.getString("storage.tdb");
    File tdbdirf = new File(tdbdir);
    if(!tdbdirf.exists() && !tdbdirf.mkdirs()) {
      System.err.println("Cannot create tdb directory : "+tdbdirf.getAbsolutePath());
    }

    // TODO: Read execution engines
    owlns = KBConstants.OWLNS();
    rdfns = KBConstants.RDFNS();
    rdfsns = KBConstants.RDFSNS();
    
    this.onturi = onturi;
  }
  
  protected void initializeKB() {
    if(this.tdbdir == null)
      return;
    
    this.fac = new OntFactory(OntFactory.JENA, tdbdir);
    try {
      ontkb = fac.getKB(this.onturi, OntSpec.PELLET, false, true);
      
      // Temporary hacks
      this.temporaryHacks();
      
      pmap = new HashMap<String, KBObject>();
      cmap = new HashMap<String, KBObject>();
      this.cacheKBTerms(ontkb);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void temporaryHacks() {
    this.hackInDataProperty("hasHypothesisQuery", "LineOfInquiry", "string");
    this.hackInDataProperty("hasDataQuery", "LineOfInquiry", "string");    
  }
  
  private void hackInDataProperty(String prop, String domain, String range) {
    String ns = onturi + "#";
    if(!this.ontkb.containsResource(ns+prop)) {
      this.ontkb.createDatatypeProperty(ns+prop);
      this.ontkb.setPropertyDomain(ns+prop, ns+domain);
      this.ontkb.setPropertyRange(ns+prop, ns+range);
      this.ontkb.save();
    }
  }
  
  protected void cacheKBTerms(KBAPI kb) {
    for (KBObject obj : kb.getAllClasses()) {
      if(obj != null)
        cmap.put(obj.getName(), obj);
    }
    for (KBObject obj : kb.getAllObjectProperties()) {
      if(obj != null)
        pmap.put(obj.getName(), obj);
    }
    for (KBObject obj : kb.getAllDatatypeProperties()) {
      if(obj != null)
        pmap.put(obj.getName(), obj);
    }
  }
}
