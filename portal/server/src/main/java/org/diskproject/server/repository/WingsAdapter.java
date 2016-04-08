package org.diskproject.server.repository;

import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.diskproject.server.util.Config;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.Workflow;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class WingsAdapter {
  static WingsAdapter singleton = null;
  
  private Gson json;
  private String server;
  private Map<String, String> sessions;
  
  public static WingsAdapter get() {
    if(singleton == null)
      singleton = new WingsAdapter();
    return singleton;
  }

  public WingsAdapter() {
    this.sessions = new HashMap<String, String>();
    this.server = Config.get().getProperties().getString("wings.server");
    this.json = new Gson();
  }
  
  public String DOMURI(String username, String domain) {
    return this.server + "/export/users/" + username + "/" + domain;
  }
  
  public String WFLOWURI(String username, String domain) {
    return this.DOMURI(username, domain) + "/workflows";
  }
  
  public String WFLOWURI(String username, String domain, String id) {
    return this.DOMURI(username, domain) + "/workflows/" + id + ".owl";
  }  
  
  public String WFLOWID(String username, String domain, String id) {
    return this.WFLOWURI(username, domain, id) + "#" + id;
  }
  
  public String DATAID(String username, String domain, String id) {
    return this.DOMURI(username, domain) + "/data/library.owl#" + id;
  }
  
  public String RUNID(String username, String domain, String id) {
    return this.DOMURI(username, domain) + "/executions/" + id + ".owl#" + id;
  }
  
  public List<Workflow> getWorkflowList(String username, String domain) {
    String pageid = "users/"+username+"/"+domain+"/workflows/getTemplatesListJSON";
    String listjson = this.get(username, pageid, null);
    if(listjson == null)
      return null;
    
    List<Workflow> workflowList = new ArrayList<Workflow>();
    Type type = new TypeToken<List<String>>(){}.getType();
    List<String> list = json.fromJson(listjson, type);
    for(String wflowid : list) {
      Workflow wflow = new Workflow();
      wflow.setName(wflowid.replaceAll("^.*#", ""));
      wflow.setLink(this.getWorkflowLink(username, domain, wflow.getName()));
      workflowList.add(wflow);
    }
    return workflowList;
  }
  
  public List<Variable> getWorkflowInputs(String username, String domain, String id) {
    String pageid = "users/"+username+"/"+domain+"/workflows/getInputsJSON";
    
    String wflowid = this.WFLOWID(username, domain, id);    
    List<NameValuePair> data = new ArrayList<NameValuePair>();
    data.add(new BasicNameValuePair("template_id", wflowid));
    
    String inputsjson = this.get(username, pageid, data);
    
    Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
    List<Map<String, Object>> list = json.fromJson(inputsjson, type);
    
    List<Variable> inputList = new ArrayList<Variable>();
    for(Map<String, Object> inputitem : list) {
      Variable var = new Variable();
      String varid = (String) inputitem.get("id");
      var.setName(varid.replaceAll("^.*#", ""));
      if(inputitem.containsKey("dim"))
        var.setDimensionality(((Double) inputitem.get("dim")).intValue());
      if(inputitem.containsKey("dtype"))
        var.setType((String) inputitem.get("dtype"));
      String vartype = (String) inputitem.get("type");
      var.setParam(vartype.equals("param"));
      
      inputList.add(var);
    }
    return inputList; 
  }
  
  
  private String login(String username) {
    String password = Config.get().getProperties().getString("wings.passwords."+username);

    System.out.println("Logging in "+username);
    
    DefaultHttpClient client = new DefaultHttpClient();
    CookieStore cookieStore = client.getCookieStore();
    try {
      HttpHead securedResource = new HttpHead(this.server + "/sparql");         
      HttpResponse httpResponse = client.execute(securedResource);
      HttpEntity responseEntity = httpResponse.getEntity();

      int statusCode = httpResponse.getStatusLine().getStatusCode();
      EntityUtils.consume(responseEntity);
      if(statusCode != 200)
        return null;

      // Login 
      HttpPost authpost = new HttpPost(this.server +  "/j_security_check");
      List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
      nameValuePairs.add(new BasicNameValuePair("j_username", username));
      nameValuePairs.add(new BasicNameValuePair("j_password", password));
      authpost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
      httpResponse = client.execute(authpost);
      responseEntity = httpResponse.getEntity();
      statusCode = httpResponse.getStatusLine().getStatusCode();
      EntityUtils.consume(responseEntity);
      if(statusCode != 302)
        return null;
      
      httpResponse = client.execute(securedResource);
      responseEntity = httpResponse.getEntity();
      EntityUtils.consume(responseEntity);
      statusCode = httpResponse.getStatusLine().getStatusCode();
      if(statusCode != 200)
        return null;
      
      String sessionId = null;
      for (Cookie cookie : cookieStore.getCookies()) {
         if (cookie.getName().equalsIgnoreCase("JSESSIONID"))
           sessionId = cookie.getValue();
      }
      System.out.println("Got session id: "+sessionId);
      return sessionId;
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }
  
  private CookieStore getCookieStore(String sessionId) 
      throws MalformedURLException {
    URL url = new URL(this.server);
    CookieStore cookieStore = new BasicCookieStore();
    BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", sessionId);
    cookie.setDomain(url.getHost());
    cookie.setPath(url.getPath());
    cookieStore.addCookie(cookie);
    return cookieStore;    
  }
  
  public WorkflowRun getWorkflowRunStatus(String username, String domain, String runid) {
    try {
      // Get data
      String execid = RUNID(username, domain, runid);
      List<NameValuePair> formdata = new ArrayList<NameValuePair>();
      formdata.add(new BasicNameValuePair("run_id", execid));    
      String pageid = "users/"+username+"/"+domain+"/executions/getRunDetails";
      String runjson = this.get(username, pageid, formdata);
      if(runjson == null)
        return null;
      
      WorkflowRun wflowstatus = new WorkflowRun();
      wflowstatus.setId(runid);
      
      JsonParser jsonParser = new JsonParser();
      JsonObject expobj = jsonParser.parse(runjson).getAsJsonObject();
      String status = 
          expobj.get("runtimeInfo").getAsJsonObject().get("status").getAsString();
      wflowstatus.setStatus(status);
      
      String link = this.server + "/users/"+username+"/"+domain+"/executions";
      link += "?run_id=" + URLEncoder.encode(execid, "UTF-8");
      wflowstatus.setLink(link);
      return wflowstatus;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  // TODO: Hackish function. Fix it !!!! *IMPORTANT*
  private String getWorkflowRunWithSameBindings(
      String username, String domain, String wflowname, List<VariableBinding> bindings) {
    String pageid = "users/"+username+"/"+domain+"/executions/getRunList";
    String listjson = this.get(username, pageid, null);
    if(listjson == null)
      return null;
    
    JsonParser jsonParser = new JsonParser();
    JsonArray runs = jsonParser.parse(listjson).getAsJsonArray();
    for(JsonElement runel : runs) {
      String runid = runel.getAsJsonObject().get("id").getAsString();
      String runpart = runid.replaceAll(".*#", "").replaceAll("\\-.*$", "");
      if(!wflowname.startsWith(runpart))
        continue;
      
      List<NameValuePair> formdata = new ArrayList<NameValuePair>();
      formdata.add(new BasicNameValuePair("run_id", runid));  
      pageid = "users/"+username+"/"+domain+"/executions/getRunDetails";
      String runjson = this.get(username, pageid, formdata);
      if(runjson == null)
        continue;
      
      boolean match = true;
      for(VariableBinding vbinding : bindings) {
        // FIXME: The hackish bit of searching for bindings
        if(runjson.indexOf(vbinding.getBinding()+ " ") < 0
          && runjson.indexOf(vbinding.getBinding()+ "\n") < 0)
          match = false;
      }
      
      if(match)
        return runid;
    }
    return null;
  }
  
  public String getWorkflowLink(String username, String domain, String id) {
    return this.server + "/users/" + username + "/" + domain +
        "/workflows/" + id + ".owl";
  }
  
  public String runWorkflow(String username, String domain, String id, 
      List<VariableBinding> bindings) {
    try {
      // TODO: Check all existing executions for an execution with the same 
      // form data. If found, then return that run id
      String runid = this.getWorkflowRunWithSameBindings(username, domain, id, bindings);
      if(runid != null) {
        System.out.println("Found existing run : "+runid);
        return runid;
      }
      
      // Get data
      String templateid = WFLOWID(username, domain, id);
      List<Variable> inputs = this.getWorkflowInputs(username, domain, id);
      List<NameValuePair> formdata = this.createFormData(username, domain, 
          templateid, bindings, inputs);
      
      // Get data  (first set)    
      String pageid = "users/"+username+"/"+domain+"/plan/getData";
      String datajson = this.post(username, pageid, formdata);
      formdata = this.getBindings(datajson, bindings, formdata);
      if(formdata == null)
        return null;
      
      // Get parameters (first set)
      pageid = "users/"+username+"/"+domain+"/plan/getParameters";
      String paramjson = this.post(username, pageid, formdata);
      formdata = this.getBindings(paramjson, bindings, formdata);
      if(formdata == null)
        return null;
      
      // Get first expanded workflow
      pageid = "users/"+username+"/"+domain+"/plan/getExpansions";
      String expjson = this.post(username, pageid, formdata);
      if(expjson == null)
        return null;
      
      JsonParser jsonParser = new JsonParser();
      JsonObject expobj = jsonParser.parse(expjson).getAsJsonObject();
      if(!expobj.get("success").getAsBoolean())
        return null;
      
      JsonObject dataobj = expobj.get("data").getAsJsonObject();
      
      JsonArray templatesobj = dataobj.get("templates").getAsJsonArray();
      if(templatesobj.size() == 0)
        return null;
      
      JsonObject templateobj = templatesobj.get(0).getAsJsonObject();
      JsonObject seedobj = dataobj.get("seed").getAsJsonObject();

      // Run the first Expanded workflow
      formdata = new ArrayList<NameValuePair>();
      formdata.add(new BasicNameValuePair("json", 
          templateobj.get("template").toString()));
      formdata.add(new BasicNameValuePair("constraints_json", 
          templateobj.get("constraints").toString()));
      formdata.add(new BasicNameValuePair("seed_json", 
          seedobj.get("template").toString()));
      formdata.add(new BasicNameValuePair("seed_constraints_json", 
          seedobj.get("constraints").toString()));
      
      pageid = "users/"+username+"/"+domain+"/executions/runWorkflow";
      runid = this.post(username, pageid, formdata);

      // Return the run id
      return runid;
    }
    catch(Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  private List<NameValuePair> getBindings(String json, 
      List<VariableBinding> initbindings, List<NameValuePair> formdata) {

    Map<String, Boolean> isBound = new HashMap<String, Boolean>();
    for(VariableBinding vbinding : initbindings)
      isBound.put(vbinding.getVariable(), true);
    
    if(json == null)
      return null;
    
    JsonParser jsonParser = new JsonParser();
    JsonObject expobj = jsonParser.parse(json).getAsJsonObject();
    
    if(!expobj.get("success").getAsBoolean())
      return null;
    
    JsonObject dataobj = expobj.get("data").getAsJsonObject();
    JsonArray bindingsobj = dataobj.get("bindings").getAsJsonArray();
    if(bindingsobj.size() == 0)
      return formdata;
    
    JsonObject bindingobj = bindingsobj.get(0).getAsJsonObject();
    for(Entry<String, JsonElement> entry : bindingobj.entrySet()) {
      if(!isBound.containsKey(entry.getKey())) {
        if(entry.getValue().isJsonArray()) {
          for(JsonElement bindingEl : entry.getValue().getAsJsonArray())
            formdata.add(new BasicNameValuePair(entry.getKey(), 
                this.getJsonBindingValue(bindingEl)));
        }
        else {
          formdata.add(new BasicNameValuePair(entry.getKey(), 
              this.getJsonBindingValue(entry.getValue())));
        }
      }
    }
    return formdata;
  }
  
  private String getJsonBindingValue(JsonElement el) {
    JsonObject obj = el.getAsJsonObject();
    if(obj.get("type").getAsString().equals("uri"))
      return obj.get("id").getAsString();
    else
      return obj.get("value").getAsString();
  }
  
  private List<NameValuePair> createFormData(String username, String domain, 
      String templateid, List<VariableBinding> bindings, List<Variable> inputs) {
    List<NameValuePair> data = new ArrayList<NameValuePair>();
    HashMap<String, String> paramDTypes = new HashMap<String, String>();
    
    for(Variable var : inputs) {
      if(var.isParam())
        paramDTypes.put(var.getName(), var.getType());
    }
    data.add(new BasicNameValuePair("__template_id", templateid));
    data.add(new BasicNameValuePair("__no_explanation", "true"));

    for(VariableBinding vbinding: bindings) {
      if(paramDTypes.containsKey(vbinding.getVariable())) {
        data.add(new BasicNameValuePair(vbinding.getVariable(), 
            vbinding.getBinding()));
      }
      else {
        data.add(new BasicNameValuePair(vbinding.getVariable(), 
            DATAID(username, domain, vbinding.getBinding()) ));
      }
    }
    data.add(new BasicNameValuePair("__paramdtypes", json.toJson(paramDTypes)));
    return data;
  }
  
  private String get(String username, String pageid, 
      List<NameValuePair> data) {
    String sessionId = this.sessions.get(username);
    if(sessionId == null) {
      sessionId = this.login(username);
      if(sessionId == null)
        return null;
      this.sessions.put(username, sessionId);
      return this.get(username, pageid, data);
    }
    
    DefaultHttpClient client = new DefaultHttpClient();
    try {  
      client.setCookieStore(this.getCookieStore(sessionId));
      
      String url = this.server + "/" + pageid;
      if(data != null && data.size() > 0) {
        url += "?" + URLEncodedUtils.format(data, "UTF-8");
      }      
      HttpGet securedResource = new HttpGet(url);
      HttpResponse httpResponse = client.execute(securedResource);
      HttpEntity responseEntity = httpResponse.getEntity();
      String strResponse = EntityUtils.toString(responseEntity);
      EntityUtils.consume(responseEntity);
      
      if(strResponse.indexOf("j_security_check") > 0) {
        sessionId = this.login(username);
        if(sessionId == null)
          return null;
        this.sessions.put(username, sessionId);
        return this.get(username, pageid, data);
      }
      else {
        return strResponse;
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  private String post(String username, String pageid, 
      List<NameValuePair> data) {
    String sessionId = this.sessions.get(username);
    if(sessionId == null) {
      sessionId = this.login(username);
      if(sessionId == null)
        return null;
      this.sessions.put(username, sessionId);
      return this.post(username, pageid, data);
    }
    
    DefaultHttpClient client = new DefaultHttpClient();
    try {  
      client.setCookieStore(this.getCookieStore(sessionId));
      
      HttpPost securedResource = new HttpPost(this.server + "/" + pageid);
      securedResource.setEntity(new UrlEncodedFormEntity(data));
      HttpResponse httpResponse = client.execute(securedResource);
      HttpEntity responseEntity = httpResponse.getEntity();
      String strResponse = EntityUtils.toString(responseEntity);
      EntityUtils.consume(responseEntity);
      
      if(strResponse.indexOf("j_security_check") > 0) {
        sessionId = this.login(username);
        if(sessionId == null)
          return null;
        this.sessions.put(username, sessionId);
        return this.post(username, pageid, data);
      }
      return strResponse;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }  
  
}
