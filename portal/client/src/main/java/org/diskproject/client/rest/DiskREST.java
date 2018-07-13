package org.diskproject.client.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.client.Config;
import org.diskproject.client.authentication.AuthenticatedDispatcher;
import org.diskproject.shared.api.DiskService1;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.Workflow;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.REST;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;

public class DiskREST {
  public static DiskService1 diskService;

  static class VocabularyCallbacks {
    List<Callback<Vocabulary, Throwable>> callbacks;
    public VocabularyCallbacks() {
      callbacks = new ArrayList<Callback<Vocabulary, Throwable>>();
    };
    public void add(Callback<Vocabulary, Throwable> callback) {
      this.callbacks.add(callback);
    }
    public boolean isEmpty() {
      return this.callbacks.isEmpty();
    }
    public void clear() {
      this.callbacks.clear();
    }
    public List<Callback<Vocabulary, Throwable>> getCallbacks() {
      return this.callbacks;
    }
  };
  
  private static Vocabulary user_vocabulary;
  private static VocabularyCallbacks user_vocabulary_callbacks =
      new VocabularyCallbacks();
  
  private static Map<String, Vocabulary> vocabularies = 
      new HashMap<String, Vocabulary>();
  private static Map<String, VocabularyCallbacks> vocabulary_callbacks =
      new HashMap<String, VocabularyCallbacks>(); 
  
  private static List<Workflow> workflows = 
      new ArrayList<Workflow>();
  private static Map<String, List<Variable>> workflow_variables =
      new HashMap<String, List<Variable>>();
  
  private static String username, domain;
  
  public static DiskService1 getDiskService() {
    if(diskService == null) {
      Defaults.setServiceRoot(Config.getServerURL());
      Defaults.setDateFormat(null);
      Defaults.setDispatcher(new AuthenticatedDispatcher());
      diskService = GWT.create(DiskService1.class);
    }
    return diskService;
  }

  public static void setUsername(String username) {
    DiskREST.username = username;
  }

  public static void setDomain(String domain) {
    DiskREST.domain = domain;
  }

  /*
   * Vocabulary
   */
  public static void getVocabulary(
      final Callback<Vocabulary, Throwable> callback,
      final String uri,
      boolean reload) {
    if(vocabularies.containsKey(uri) && !reload) {
      callback.onSuccess(vocabularies.get(uri));
    }
    else {
      if(!vocabulary_callbacks.containsKey(uri)) {  
        vocabulary_callbacks.put(uri, new VocabularyCallbacks());
        vocabulary_callbacks.get(uri).add(callback);
        
        REST.withCallback(new MethodCallback<Map<String, Vocabulary>>() {
          @Override
          public void onSuccess(Method method, Map<String, Vocabulary> vocabs) {
            vocabularies = vocabs;
            for(Callback<Vocabulary, Throwable> cb : 
                vocabulary_callbacks.get(uri).getCallbacks())
              cb.onSuccess(vocabularies.get(uri));
            vocabulary_callbacks.get(uri).clear();
          }
          @Override
          public void onFailure(Method method, Throwable exception) {
            AppNotification.notifyFailure("Could not load vocabularies");
            callback.onFailure(exception);
          }
        }).call(getDiskService()).getVocabularies();
      }
      else {
        vocabulary_callbacks.get(uri).add(callback);
      }
    }
  }
  
  public static void getUserVocabulary(
      final Callback<Vocabulary, Throwable> callback,
      String username, String domain,
      boolean reload) {
    if(user_vocabulary != null && !reload) {
      callback.onSuccess(user_vocabulary);
    }
    else {
      if(user_vocabulary_callbacks.isEmpty()) {
        user_vocabulary_callbacks.add(callback);        
        REST.withCallback(new MethodCallback<Vocabulary>() {
          @Override
          public void onSuccess(Method method, Vocabulary vocab) {
            user_vocabulary = vocab;
            for(Callback<Vocabulary, Throwable> vcb : 
                user_vocabulary_callbacks.getCallbacks())
              vcb.onSuccess(user_vocabulary);
            user_vocabulary_callbacks.clear();
          }
          @Override
          public void onFailure(Method method, Throwable exception) {
            AppNotification.notifyFailure("Could not load user vocabulary");
            callback.onFailure(exception);
          }
        }).call(getDiskService()).getUserVocabulary(username, domain);        
      }
      else {
        user_vocabulary_callbacks.add(callback);
      }
    }
  }
  
  /*
   * Hypotheses
   */
  public static void listHypotheses(final Callback<List<TreeItem>, Throwable> callback) {
    REST.withCallback(new MethodCallback<List<TreeItem>>() {
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
      @Override
      public void onSuccess(Method method, List<TreeItem> response) {
        callback.onSuccess(response);
      }
    }).call(getDiskService()).listHypotheses(username, domain);
  }
  
  public static void getHypothesis(String id, 
      final Callback<Hypothesis, Throwable> callback) {
    REST.withCallback(new MethodCallback<Hypothesis>() {
      @Override
      public void onSuccess(Method method, Hypothesis response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
    }).call(getDiskService()).getHypothesis(username, domain, id);
  }
  
  public static void addHypothesis(Hypothesis hypothesis,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).addHypothesis(username, domain, hypothesis);
  }
  
  public static void updateHypothesis(Hypothesis hypothesis,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).updateHypothesis(username, domain, 
        hypothesis.getId(), hypothesis);
  }
  
  public static void deleteHypothesis(String id,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).deleteHypothesis(username, domain, id);
  }  
  
  public static void queryHypothesis(String id,
      final Callback<List<TriggeredLOI>, Throwable> callback) {
    REST.withCallback(new MethodCallback<List<TriggeredLOI>>() {
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
      @Override
      public void onSuccess(Method method, List<TriggeredLOI> response) {
        callback.onSuccess(response);
      }
    }).call(getDiskService()).queryHypothesis(username, domain, id);
  }
  
  /*
   * Lines of Inquiry
   */
  public static void listLOI(final Callback<List<TreeItem>, Throwable> callback) {
    REST.withCallback(new MethodCallback<List<TreeItem>>() {
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
      @Override
      public void onSuccess(Method method, List<TreeItem> response) {
        callback.onSuccess(response);
      }
    }).call(getDiskService()).listLOIs(username, domain);
  }
  
  public static void getLOI(String id, 
      final Callback<LineOfInquiry, Throwable> callback) {
    REST.withCallback(new MethodCallback<LineOfInquiry>() {
      @Override
      public void onSuccess(Method method, LineOfInquiry response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
    }).call(getDiskService()).getLOI(username, domain, id);
  }
  
  public static void addLOI(LineOfInquiry loi,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).addLOI(username, domain, loi);
  }
  
  public static void deleteLOI(String id,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).deleteLOI(username, domain, id);
  }
  
  public static void updateLOI(LineOfInquiry loi,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).updateLOI(username, domain, 
        loi.getId(), loi);
  }  
  
  /*
   * Triggered LOIs
   */
  public static void addTriggeredLOI(TriggeredLOI tloi, 
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).addTriggeredLOI(username, domain, tloi);
  }
  
  public static void listTriggeredLOIs(final Callback<List<TriggeredLOI>, Throwable> callback) {
    REST.withCallback(new MethodCallback<List<TriggeredLOI>>() {
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
      @Override
      public void onSuccess(Method method, List<TriggeredLOI> response) {
        callback.onSuccess(response);
      }
    }).call(getDiskService()).listTriggeredLOIs(username, domain);
  }
  
  public static void getTriggeredLOI(String id, 
      final Callback<TriggeredLOI, Throwable> callback) {
    REST.withCallback(new MethodCallback<TriggeredLOI>() {
      @Override
      public void onSuccess(Method method, TriggeredLOI response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }
    }).call(getDiskService()).getTriggeredLOI(username, domain, id);
  }
  
  public static void deleteTriggeredLOI(String id,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).deleteTriggeredLOI(username, domain, id);
  }  
  
  /*
   * Assertions
   */
  
  public static void updateAssertions(Graph graph,
      final Callback<Void, Throwable> callback) {
    REST.withCallback(new MethodCallback<Void>() {
      @Override
      public void onSuccess(Method method, Void response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).updateAssertions(username, domain, graph);
  }
  
  public static void listAssertions(
      final Callback<Graph, Throwable> callback) {
    REST.withCallback(new MethodCallback<Graph>() {
      @Override
      public void onSuccess(Method method, Graph response) {
        callback.onSuccess(response);
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).listAssertions(username, domain);
  }
  
  /*
   * Workflows
   */
  
  public static void listWorkflows(
      final Callback<List<Workflow>, Throwable> callback) {
    if(workflows.size() > 0)
      callback.onSuccess(workflows);
    
    REST.withCallback(new MethodCallback<List<Workflow>>() {
      @Override
      public void onSuccess(Method method, List<Workflow> response) {
        if(response == null)
          callback.onFailure(new Throwable("No WINGS workflows found"));
        else {
          workflows = response;
          callback.onSuccess(response);
        }
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).listWorkflows(username, domain);
  }
  
  public static void getWorkflowVariables(final String id, 
      final Callback<List<Variable>, Throwable> callback) {
    if(workflow_variables.containsKey(id)) {
      callback.onSuccess(workflow_variables.get(id));
      return;
    }
    
    REST.withCallback(new MethodCallback<List<Variable>>() {
      @Override
      public void onSuccess(Method method, List<Variable> response) {
        if(response == null) {
          callback.onFailure(new Throwable("No WINGS workflow variables found"));
        }
        else {
          workflow_variables.put(id, response);
          callback.onSuccess(response);
        }
      }
      @Override
      public void onFailure(Method method, Throwable exception) {
        callback.onFailure(exception);
      }      
    }).call(getDiskService()).getWorkflowVariables(username, domain, id);
  }  
}
