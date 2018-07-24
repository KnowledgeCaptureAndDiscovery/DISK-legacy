package org.diskproject.server.repository;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.diskproject.server.util.Config;
import org.diskproject.shared.classes.util.KBConstants;
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

import edu.isi.wings.ontapi.KBAPI;
import edu.isi.wings.ontapi.KBObject;
import edu.isi.wings.ontapi.KBTriple;
import edu.isi.wings.ontapi.OntFactory;
import edu.isi.wings.ontapi.OntSpec;

public class WingsAdapter {
	static WingsAdapter singleton = null;

	private Gson json;
	private String server;
	private Map<String, String> sessions;
	private String wflowns = "http://www.wings-workflows.org/ontology/workflow.owl#";
	private String execns = "http://www.wings-workflows.org/ontology/execution.owl#";

	public static WingsAdapter get() {
		if (singleton == null)
			singleton = new WingsAdapter();
		return singleton;
	}

	public WingsAdapter() {
		this.sessions = new HashMap<String, String>();
		System.out.println("Config.get().getProperties()"
				+ Config.get().getProperties());
		System.out
				.println("Config.get().getProperties().getString(wings.server)"
						+ Config.get().getProperties()
								.getString("wings.server"));
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

	public String RUNURI(String username, String domain, String id) {
		return this.DOMURI(username, domain) + "/executions/" + id + ".owl";
	}

	public String RUNID(String username, String domain, String id) {
		return this.RUNURI(username, domain, id) + "#" + id;
	}

	public List<Workflow> getWorkflowList(String username, String domain) {
		String liburi = this.WFLOWURI(username, domain) + "/library.owl";
		try {
			List<Workflow> list = new ArrayList<Workflow>();
			OntFactory fac = new OntFactory(OntFactory.JENA);
			KBAPI kb = fac.getKB(liburi, OntSpec.PLAIN);
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			KBObject templatecls = kb.getResource(this.wflowns
					+ "WorkflowTemplate");
			for (KBTriple triple : kb.genericTripleQuery(null, typeprop,
					templatecls)) {
				KBObject tobj = triple.getSubject();
				Workflow wflow = new Workflow();
				wflow.setName(tobj.getName());
				wflow.setLink(this.getWorkflowLink(username, domain,
						wflow.getName()));
				list.add(wflow);
			}
			return list;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<Variable> getWorkflowVariables(String username, String domain,
			String id) {
		String wflowuri = this.WFLOWURI(username, domain, id);
		try {
			List<Variable> list = new ArrayList<Variable>();
			Map<String, Boolean> varmap = new HashMap<String, Boolean>();
			OntFactory fac = new OntFactory(OntFactory.JENA);
			KBAPI kb = fac.getKB(wflowuri, OntSpec.PLAIN);
			KBObject linkprop = kb.getProperty(this.wflowns + "hasLink");
			KBObject origprop = kb.getProperty(this.wflowns + "hasOriginNode");
			KBObject destprop = kb.getProperty(this.wflowns
					+ "hasDestinationNode");
			KBObject varprop = kb.getProperty(this.wflowns + "hasVariable");
			KBObject paramcls = kb.getConcept(this.wflowns
					+ "ParameterVariable");

			for (KBTriple triple : kb.genericTripleQuery(null, linkprop, null)) {
				KBObject linkobj = triple.getObject();
				KBObject orignode = kb.getPropertyValue(linkobj, origprop);
				KBObject destnode = kb.getPropertyValue(linkobj, destprop);

				// Only return Input and Output variables
				if (orignode != null && destnode != null)
					continue;

				KBObject varobj = kb.getPropertyValue(linkobj, varprop);

				if (varmap.containsKey(varobj.getID()))
					continue;
				varmap.put(varobj.getID(), true);

				Variable var = new Variable();
				var.setName(varobj.getName());
				if (orignode == null)
					var.setInput(true);

				if (kb.isA(varobj, paramcls))
					var.setParam(true);

				list.add(var);
			}
			return list;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public Map<String, String> getRunVariableBindings(String username,
			String domain, String runid) {
		String runuri = runid.replace("#.*", "");
		try {
			OntFactory fac = new OntFactory(OntFactory.JENA);
			KBAPI kb = fac.getKB(runuri, OntSpec.PLAIN);
			KBObject execobj = kb.getIndividual(runid);
			KBObject prop = kb.getProperty(execns + "hasExpandedTemplate");
			KBObject xtpl = kb.getPropertyValue(execobj, prop);
			String xtpluri = xtpl.getID().replaceAll("#.*", "");

			Map<String, String> varmap = new HashMap<String, String>();
			KBAPI xkb = fac.getKB(xtpluri, OntSpec.PLAIN);
			KBObject bindprop = xkb
					.getProperty(this.wflowns + "hasDataBinding");

			for (KBTriple triple : xkb.genericTripleQuery(null, bindprop, null)) {
				KBObject varobj = triple.getSubject();
				KBObject bindobj = triple.getObject();
				varmap.put(varobj.getName(), bindobj.getID());
			}
			return varmap;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public Map<String, Variable> getWorkflowInputs(String username,
			String domain, String id) {
		String pageid = "users/" + username + "/" + domain
				+ "/workflows/getInputsJSON";

		String wflowid = this.WFLOWID(username, domain, id);
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("template_id", wflowid));

		String inputsjson = this.get(username, pageid, data);

		Type type = new TypeToken<List<Map<String, Object>>>() {
		}.getType();
		List<Map<String, Object>> list = json.fromJson(inputsjson, type);

		Map<String, Variable> inputs = new HashMap<String, Variable>();
		for (Map<String, Object> inputitem : list) {
			Variable var = new Variable();
			String varid = (String) inputitem.get("id");
			var.setName(varid.replaceAll("^.*#", ""));
			if (inputitem.containsKey("dim"))
				var.setDimensionality(((Double) inputitem.get("dim"))
						.intValue());
			if (inputitem.containsKey("dtype"))
				var.setType((String) inputitem.get("dtype"));
			String vartype = (String) inputitem.get("type");
			var.setParam(vartype.equals("param"));

			inputs.put(var.getName(), var);
		}
		return inputs;
	}

	private String login(String username) {
		String password = Config.get().getProperties()
				.getString("wings.passwords." + username);
		System.out.println("Logging in " + username + " with password "
				+ password);

		CloseableHttpClient client = HttpClients.createDefault();
		HttpClientContext context = HttpClientContext.create();
		try {
			HttpHead securedResource = new HttpHead(this.server + "/sparql");
			HttpResponse httpResponse = client
					.execute(securedResource, context);
			HttpEntity responseEntity = httpResponse.getEntity();
			System.out.println("httpResponse.getEntity(); :"
					+ httpResponse.getEntity());
			int statusCode = httpResponse.getStatusLine().getStatusCode();
			EntityUtils.consume(responseEntity);
			if (statusCode != 200)
				return null;

			// Login
			HttpPost authpost = new HttpPost(this.server + "/j_security_check");
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("j_username", username));
			nameValuePairs.add(new BasicNameValuePair("j_password", password));
			authpost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			System.out.println(nameValuePairs);
			for (int i = 1; i < 4; i++) {
				try {
					httpResponse = client.execute(authpost);
					responseEntity = httpResponse.getEntity();
					break;
				} catch (Exception e) {
					System.out.println("Tried " + i + " times");
					e.printStackTrace();
				}
			}
			statusCode = httpResponse.getStatusLine().getStatusCode();
			EntityUtils.consume(responseEntity);
			if (statusCode != 302)
				return null;

			httpResponse = client.execute(securedResource);
			responseEntity = httpResponse.getEntity();
			EntityUtils.consume(responseEntity);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			if (statusCode != 200)
				return null;

			String sessionId = null;
			for (Cookie cookie : context.getCookieStore().getCookies()) {
				if (cookie.getName().equalsIgnoreCase("JSESSIONID"))
					sessionId = cookie.getValue();
			}
			System.out.println("Got session id: " + sessionId);
			return sessionId;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	private CookieStore getCookieStore(String sessionId) {
		CookieStore cookieStore = new BasicCookieStore();
		BasicClientCookie cookie = new BasicClientCookie("JSESSIONID",
				sessionId);
		try {
			URL url = new URL(this.server);
			cookie.setDomain(url.getHost());
			cookie.setPath(url.getPath());
			cookieStore.addCookie(cookie);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cookieStore;
	}

	public WorkflowRun getWorkflowRunStatus(String username, String domain,
			String runid) {
		try {
			// Get data
			String execid = RUNID(username, domain, runid);
			List<NameValuePair> formdata = new ArrayList<NameValuePair>();
			formdata.add(new BasicNameValuePair("run_id", execid));
			String pageid = "users/" + username + "/" + domain
					+ "/executions/getRunDetails";
			String runjson = this.get(username, pageid, formdata);
			if (runjson == null)
				return null;

			WorkflowRun wflowstatus = new WorkflowRun();
			wflowstatus.setId(execid);

			JsonParser jsonParser = new JsonParser();
			JsonObject runobj = jsonParser.parse(runjson).getAsJsonObject();
			JsonObject expobj = runobj.get("execution").getAsJsonObject();
			String status = expobj.get("runtimeInfo").getAsJsonObject()
					.get("status").getAsString();
			wflowstatus.setStatus(status);

			String link = this.server + "/users/" + username + "/" + domain
					+ "/executions";
			link += "?run_id=" + URLEncoder.encode(execid, "UTF-8");
			wflowstatus.setLink(link);
			return wflowstatus;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// TODO: Hackish function. Fix it !!!! *IMPORTANT*
	private String getWorkflowRunWithSameBindings(String username,
			String domain, String wflowname, List<NameValuePair> vbindings) {

		String templateid = WFLOWID(username, domain, wflowname);
		// Get all successful runs for the template (and their variable
		// bindings)
		String query = "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX exec: <http://www.wings-workflows.org/ontology/execution.owl#>\n"
				+ "PREFIX wflow: <http://www.wings-workflows.org/ontology/workflow.owl#>\n"
				+ "\n"
				+ "SELECT ?run\n"
				+ "(group_concat(concat(strafter(str(?iv), \"#\"), \"=\", str(?b));separator=\"||\") as ?bindings)  \n"
				+ "WHERE {\n" + "  ?run a exec:Execution .\n"
				+ "  ?run exec:hasTemplate <" + templateid + "> .\n"
				+ "  ?run exec:hasExecutionStatus \"SUCCESS\"^^xsd:string .\n"
				+ "\n" + "  ?run exec:hasExpandedTemplate ?xt .\n"
				+ "  ?xt wflow:hasInputRole ?ir .\n"
				+ "  ?ir wflow:mapsToVariable ?iv .\n"
				+ "  OPTIONAL { ?iv wflow:hasDataBinding ?b } .\n"
				+ "  OPTIONAL { ?iv wflow:hasParameterValue ?b } .\n" + "}\n"
				+ "GROUP BY ?run";

		String pageid = "sparql";
		List<NameValuePair> formdata = new ArrayList<NameValuePair>();
		formdata.add(new BasicNameValuePair("query", query));
		formdata.add(new BasicNameValuePair("format", "json"));
		String resultjson = this.get(username, pageid, formdata);
		if (resultjson == null || resultjson.equals(""))
			return null;

		// Check the variable bindings to see if this matches the values that we
		// have
		JsonParser jsonParser = new JsonParser();
		JsonObject result = jsonParser.parse(resultjson).getAsJsonObject();
		JsonArray qbindings = result.get("results").getAsJsonObject()
				.get("bindings").getAsJsonArray();

		for (JsonElement qbinding : qbindings) {
			JsonObject qb = qbinding.getAsJsonObject();
			if (qb.get("run") == null)
				continue;
			String runid = qb.get("run").getAsJsonObject().get("value")
					.getAsString();
			String bindstrs = qb.get("bindings").getAsJsonObject().get("value")
					.getAsString();
			HashMap<String, String> keyvalues = new HashMap<String, String>();
			for (String bindstr : bindstrs.split("\\|\\|")) {
				String[] keyval = bindstr.split("=", 2);
				String varid = keyval[0];
				String value = keyval[1];
				keyvalues.put(varid, value);
			}

			boolean match = true;
			for (NameValuePair vbinding : vbindings) {
				if (vbinding.getName().startsWith("__"))
					continue; // Special variable .. Not to be matched
				String value = keyvalues.get(vbinding.getName());
				if (value == null || !value.equals(vbinding.getValue())) {
					match = false;
					break;
				}
			}

			if (match)
				return runid;
		}
		return null;
	}

	public String getWorkflowLink(String username, String domain, String id) {
		return this.server + "/users/" + username + "/" + domain
				+ "/workflows/" + id + ".owl";
	}

	public String runWorkflow(String username, String domain, String wflowname,
			List<VariableBinding> vbindings,
			Map<String, Variable> inputVariables) {
		try {
			String templateid = WFLOWID(username, domain, wflowname);
			List<NameValuePair> formdata = this.createFormData(username,
					domain, templateid, vbindings, inputVariables);

			// Get data (first set)
			String pageid = "users/" + username + "/" + domain
					+ "/plan/getData";
			String datajson = this.post(username, pageid, formdata);
			formdata = this.getBindings(datajson, vbindings, formdata);
			if (formdata == null)
				return null;

			// Get parameters (first set)
			pageid = "users/" + username + "/" + domain + "/plan/getParameters";
			String paramjson = this.post(username, pageid, formdata);
			formdata = this.getBindings(paramjson, vbindings, formdata);
			if (formdata == null)
				return null;

			// TODO: This should be called after getting expanded workflow.
			// - Create mapping data from expanded workflow, and then check.
			// - *NEEDED* to handle collections properly

			String runid = this.getWorkflowRunWithSameBindings(username,
					domain, wflowname, formdata);
			if (runid != null) {
				System.out.println("Found existing run : " + runid);
				return runid;
			}

			// Get first expanded workflow
			pageid = "users/" + username + "/" + domain + "/plan/getExpansions";
			String expjson = this.post(username, pageid, formdata);
			if (expjson == null)
				return null;

			JsonParser jsonParser = new JsonParser();
			JsonObject expobj = jsonParser.parse(expjson).getAsJsonObject();
			if (!expobj.get("success").getAsBoolean())
				return null;

			JsonObject dataobj = expobj.get("data").getAsJsonObject();

			JsonArray templatesobj = dataobj.get("templates").getAsJsonArray();
			if (templatesobj.size() == 0)
				return null;

			JsonObject templateobj = templatesobj.get(0).getAsJsonObject();
			JsonObject seedobj = dataobj.get("seed").getAsJsonObject();

			// Run the first Expanded workflow
			formdata = new ArrayList<NameValuePair>();
			formdata.add(new BasicNameValuePair("template_id", templateid));
			formdata.add(new BasicNameValuePair("json", templateobj.get(
					"template").toString()));
			formdata.add(new BasicNameValuePair("constraints_json", templateobj
					.get("constraints").toString()));
			formdata.add(new BasicNameValuePair("seed_json", seedobj.get(
					"template").toString()));
			formdata.add(new BasicNameValuePair("seed_constraints_json",
					seedobj.get("constraints").toString()));

			pageid = "users/" + username + "/" + domain
					+ "/executions/runWorkflow";
			runid = this.post(username, pageid, formdata);

			// Return the run id
			return runid;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String fetchDataFromWings(String username, String domain,
			String dataid) {
		String getpage = "users/" + username + "/" + domain + "/data/fetch";

		// Check for data already present on the server
		List<NameValuePair> formdata = new ArrayList<NameValuePair>();
		formdata.add(new BasicNameValuePair("data_id", dataid));
		return this.get(username, getpage, formdata);
	}

	public String addOrUpdateData(String username, String domain, String id,
			String type, String contents) {

		String getpage = "users/" + username + "/" + domain
				+ "/data/getDataJSON";
		String postpage = "users/" + username + "/" + domain
				+ "/data/addDataForType";
		String uploadpage = "users/" + username + "/" + domain + "/upload";

		// Add unique md5 hash to id based on contents
		String md5 = DigestUtils.md5Hex(contents.getBytes());
		Pattern extensionPattern = Pattern.compile("^(.*)(\\..+)$");
		Matcher mat = extensionPattern.matcher(id);
		if (mat.find()) {
			id = mat.group(1) + "-" + md5 + mat.group(2);
		} else
			id += "-" + md5;

		String dataid = this.DATAID(username, domain, id);
		List<NameValuePair> formdata = new ArrayList<NameValuePair>();
		formdata.add(new BasicNameValuePair("data_id", dataid));
		String datajson = this.get(username, getpage, formdata);
		String DataFromWings;
		if (datajson != null && !datajson.trim().equals("null")) {
			DataFromWings = fetchDataFromWings(username, domain, dataid);
			if (contents.equals(DataFromWings))
				return dataid;
		}

		try {
			System.out.println("creating data file");
			File dir = File.createTempFile("uploadQueryTmp", "");
			System.out.println("file name: "+ "/"+id);
			if (!dir.delete() || !dir.mkdirs()) {
				System.err.println("Could not create temporary directory "
						+ dir);
				return null;
			}
			System.out.println("absoloute path: "+dir.getAbsolutePath().replace("\\", "/") + "/" + id);
			File f = new File(dir.getAbsolutePath().replace("\\", "/") + "/" + id);
			FileUtils.write(f, contents);
			System.out.println("upload result: "+this.upload(username, uploadpage, "DataObject", f));

			List<NameValuePair> data = new ArrayList<NameValuePair>();
			data.add(new BasicNameValuePair("data_id", dataid));
			data.add(new BasicNameValuePair("data_type", type));
			String response = this.post(username, postpage, data);
			System.out.println("this.post(username, postpage, data) response: "
					+ response);
			if (response != null && response.equals("OK"))
				return dataid;
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("leeg me cuz it not saving");
		return null;
	}

	public String addDataToWings(String username, String domain, String id,
			String type, String contents) {

		String getpage = "users/" + username + "/" + domain
				+ "/data/getDataJSON";
		String postpage = "users/" + username + "/" + domain
				+ "/data/addDataForType";
		String uploadpage = "users/" + username + "/" + domain + "/upload";

		// Add unique md5 hash to id based on contents
		String md5 = DigestUtils.md5Hex(contents.getBytes());
		Pattern extensionPattern = Pattern.compile("^(.*)(\\..+)$");
		Matcher mat = extensionPattern.matcher(id);
		if (mat.find()) {
			id = mat.group(1) + "-" + md5 + mat.group(2);
		} else
			id += "-" + md5;

		// Check for data already present on the server
		String dataid = this.DATAID(username, domain, id);
		List<NameValuePair> formdata = new ArrayList<NameValuePair>();
		formdata.add(new BasicNameValuePair("data_id", dataid));
		String datajson = this.get(username, getpage, formdata);
		if (datajson != null && !datajson.trim().equals("null")) {
			// Already there
			return dataid;
		}

		System.out.println("Not found, upload " + id);
		// If not present, Create temporary file and upload
		try {
			File dir = File.createTempFile("tmp", "");
			if (!dir.delete() || !dir.mkdirs()) {
				System.err.println("Could not create temporary directory "
						+ dir);
				return null;
			}
			File f = new File(dir.getAbsolutePath() + "/" + id);
			FileUtils.write(f, contents);
			this.upload(username, uploadpage, "data", f);
			f.delete();

			List<NameValuePair> data = new ArrayList<NameValuePair>();
			data.add(new BasicNameValuePair("data_id", dataid));
			data.add(new BasicNameValuePair("data_type", type));
			String response = this.post(username, postpage, data);
			if (response != null && response.equals("OK"))
				return dataid;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private List<NameValuePair> getBindings(String json,
			List<VariableBinding> initbindings, List<NameValuePair> formdata) {

		Map<String, Boolean> isBound = new HashMap<String, Boolean>();
		for (VariableBinding vbinding : initbindings)
			isBound.put(vbinding.getVariable(), true);

		if (json == null)
			return null;

		JsonParser jsonParser = new JsonParser();
		JsonObject expobj = jsonParser.parse(json).getAsJsonObject();

		if (!expobj.get("success").getAsBoolean())
			return null;

		JsonObject dataobj = expobj.get("data").getAsJsonObject();
		JsonArray bindingsobj = dataobj.get("bindings").getAsJsonArray();
		if (bindingsobj.size() == 0)
			return formdata;

		JsonObject bindingobj = bindingsobj.get(0).getAsJsonObject();
		for (Entry<String, JsonElement> entry : bindingobj.entrySet()) {
			if (!isBound.containsKey(entry.getKey())) {
				if (entry.getValue().isJsonArray()) {
					for (JsonElement bindingEl : entry.getValue()
							.getAsJsonArray())
						formdata.add(new BasicNameValuePair(entry.getKey(),
								this.getJsonBindingValue(bindingEl)));
				} else {
					formdata.add(new BasicNameValuePair(entry.getKey(), this
							.getJsonBindingValue(entry.getValue())));
				}
			}
		}
		return formdata;
	}

	private String getJsonBindingValue(JsonElement el) {
		JsonObject obj = el.getAsJsonObject();
		if (obj.get("type").getAsString().equals("uri"))
			return obj.get("id").getAsString();
		else
			return obj.get("value").getAsString();
	}

	private List<NameValuePair> createFormData(String username, String domain,
			String templateid, List<VariableBinding> bindings,
			Map<String, Variable> inputs) {
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		HashMap<String, String> paramDTypes = new HashMap<String, String>();

		for (Variable var : inputs.values()) {
			if (var.isParam())
				paramDTypes.put(var.getName(), var.getType());
		}
		data.add(new BasicNameValuePair("__template_id", templateid));
		data.add(new BasicNameValuePair("__no_explanation", "true"));

		for (VariableBinding vbinding : bindings) {
			if (paramDTypes.containsKey(vbinding.getVariable())) {
				data.add(new BasicNameValuePair(vbinding.getVariable(),
						vbinding.getBinding()));
			} else {
				data.add(new BasicNameValuePair(vbinding.getVariable(), DATAID(
						username, domain, vbinding.getBinding())));
			}
		}
		data.add(new BasicNameValuePair("__paramdtypes", json
				.toJson(paramDTypes)));
		return data;
	}

	private String get(String username, String pageid, List<NameValuePair> data) {
		String sessionId = this.sessions.get(username);
		System.out.println("pageid: " + pageid);
		System.out.println("List<NameValuePair> data: " + data);
		if (sessionId == null) {
			sessionId = this.login(username);
			if (sessionId == null)
				return null;
			this.sessions.put(username, sessionId);
			return this.get(username, pageid, data);
		}
		for (int i = 0; i < 3; i++) {
			CloseableHttpClient client = HttpClientBuilder.create()
					.setDefaultCookieStore(this.getCookieStore(sessionId))
					.build();
			try {
				String url = this.server + "/" + pageid;
				if (data != null && data.size() > 0) {
					url += "?" + URLEncodedUtils.format(data, "UTF-8");
				}
				HttpGet securedResource = new HttpGet(url);
				CloseableHttpResponse httpResponse = client
						.execute(securedResource);

				try {
					HttpEntity responseEntity = httpResponse.getEntity();
					System.out.println("get/responseEntity" + responseEntity);
					String strResponse = EntityUtils.toString(responseEntity);
					EntityUtils.consume(responseEntity);
					httpResponse.close();

					if (strResponse.indexOf("j_security_check") > 0) {
						sessionId = this.login(username);
						if (sessionId == null)
							return null;
						this.sessions.put(username, sessionId);
						return this.get(username, pageid, data);
					}
					return strResponse;
				} finally {
					httpResponse.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					client.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}

	private String post(String username, String pageid, List<NameValuePair> data) {
		String sessionId = this.sessions.get(username);
		if (sessionId == null) {
			sessionId = this.login(username);
			if (sessionId == null)
				return null;
			this.sessions.put(username, sessionId);
			return this.post(username, pageid, data);
		}

		CloseableHttpClient client = HttpClientBuilder.create()
				.setDefaultCookieStore(this.getCookieStore(sessionId)).build();

		try {
			HttpPost securedResource = new HttpPost(this.server + "/" + pageid);
			securedResource.setEntity(new UrlEncodedFormEntity(data));
			CloseableHttpResponse httpResponse = client
					.execute(securedResource);
			try {
				HttpEntity responseEntity = httpResponse.getEntity();
				String strResponse = EntityUtils.toString(responseEntity);
				EntityUtils.consume(responseEntity);
				httpResponse.close();

				if (strResponse.indexOf("j_security_check") > 0) {
					sessionId = this.login(username);
					if (sessionId == null)
						return null;
					this.sessions.put(username, sessionId);
					return this.post(username, pageid, data);
				}
				return strResponse;
			} finally {
				httpResponse.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				client.close();
			} catch (IOException e) {
			}
		}
		return null;
	}

	private String upload(String username, String pageid, String type, File file) {

		String sessionId = this.sessions.get(username);
		if (sessionId == null) {
			sessionId = this.login(username);
			if (sessionId == null)
				return null;
			this.sessions.put(username, sessionId);
			return this.upload(username, pageid, type, file);
		}
 
		CloseableHttpClient client = HttpClientBuilder.create()
				.setDefaultCookieStore(this.getCookieStore(sessionId)).build();
		try {
			HttpPost post = new HttpPost(this.server + "/" + pageid);
			System.out.println("pageid: " + pageid);
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
			builder.addTextBody("name", file.getName());
			builder.addTextBody("type", type);
			builder.addBinaryBody("file", file);
			//
			HttpEntity entity = builder.build();
			post.setEntity(entity);
			System.out.println("post.getEntity: "+EntityUtils.toString(post.getEntity())+"end.getEntity");
			CloseableHttpResponse response = client.execute(post);
			try {
				HttpEntity responseEntity = response.getEntity();
				String strResponse = EntityUtils.toString(responseEntity);
				System.out.println("strresponse: "+strResponse);
				EntityUtils.consume(responseEntity);

				if (strResponse.indexOf("j_security_check") > 0) {
					sessionId = this.login(username);
					if (sessionId == null)
						return null;
					this.sessions.put(username, sessionId);
					return this.upload(username, pageid, type, file);
				}
				return strResponse;
			} finally {
				response.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				client.close();
			} catch (IOException e) {
			}
		}
		return null;
	}
}
