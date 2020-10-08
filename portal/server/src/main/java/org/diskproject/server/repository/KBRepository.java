package org.diskproject.server.repository;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.diskproject.server.util.Config;
import org.diskproject.shared.classes.util.KBConstants;

import edu.isi.kcap.ontapi.KBAPI;
import edu.isi.kcap.ontapi.KBObject;
import edu.isi.kcap.ontapi.OntFactory;
import edu.isi.kcap.ontapi.OntSpec;
import edu.isi.kcap.ontapi.jena.transactions.TransactionsJena;
import edu.isi.kcap.ontapi.transactions.TransactionsAPI;

public class KBRepository implements TransactionsAPI {
  protected String server;
  protected String tdbdir;
  protected String onturi;
  protected String ontns;
  protected OntFactory fac;
  protected transient TransactionsAPI transaction;
  
  protected String owlns, rdfns, rdfsns;
  protected KBAPI ontkb;
  protected HashMap<String, KBObject> pmap, cmap;
  
  protected void setConfiguration(String onturi, String ontns) {
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
    this.ontns = ontns;
  }
  
  protected void initializeKB() {
    if(this.tdbdir == null)
      return;
    
    this.fac = new OntFactory(OntFactory.JENA, tdbdir);
    try {
      this.transaction = new TransactionsJena(this.fac);
      System.out.println(">> " + this.onturi);
      
      ontkb = fac.getKB(this.onturi, OntSpec.PELLET, false, true);
      TimeUnit.SECONDS.sleep(2); 
      // Temporary hacks
      this.start_write();
      this.temporaryHacks();
      
      pmap = new HashMap<String, KBObject>();
      cmap = new HashMap<String, KBObject>();
      this.cacheKBTerms(ontkb);
      this.end();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void temporaryHacks() {
    this.hackInDataProperty("hasHypothesisQuery", "LineOfInquiry", "string");
    this.hackInDataProperty("hasDataQuery", "LineOfInquiry", "string");    
    this.hackInDataProperty("author", "LineOfInquiry", "string");    
  }
  
  private void hackInDataProperty(String prop, String domain, String range) {
    String ns = ontns;
    if(!this.ontkb.containsResource(ns+prop)) {
      this.ontkb.createDatatypeProperty(ns+prop);
      this.ontkb.setPropertyDomain(ns+prop, ns+domain);
      this.ontkb.setPropertyRange(ns+prop, ns+range);
      this.ontkb.save();
    }
  }

  protected void cacheKBTerms(KBAPI kb) {
    System.out.println("kb:" + kb.getURI());
    for (KBObject obj : kb.getAllClasses()) {
      if(obj != null) {
        cmap.put(obj.getName(), obj);
        System.out.println("cmap: " + obj.getName());
      }
    }
    for (KBObject obj : kb.getAllObjectProperties()) {
      if(obj != null) {
        pmap.put(obj.getName(), obj);
        System.out.println("pmap: " + obj.getName());
      }
    }
    for (KBObject obj : kb.getAllDatatypeProperties()) {
      if(obj != null) {
        pmap.put(obj.getName(), obj);
        System.out.println("dt pmap: " + obj.getName());
      }
    }
  }

//TransactionsAPI functions
 @Override
 public boolean start_read() {
   if(transaction != null)
     return transaction.start_read();
   return true;
 }

 @Override
 public boolean start_write() {
   if(transaction != null)
     return transaction.start_write();
   return true;
 }
 
 @Override
 public boolean save(KBAPI kb) {
   return transaction.save(kb);
 }
 
 @Override
 public boolean saveAll() {
   return transaction.saveAll();
 }

 @Override
 public boolean end() {
   if(transaction != null)
     return transaction.end();
   return true;
 }

 @Override
 public boolean start_batch_operation() {
   return transaction.start_batch_operation();
 }

 @Override
 public void stop_batch_operation() {
   transaction.stop_batch_operation();
 }
}
