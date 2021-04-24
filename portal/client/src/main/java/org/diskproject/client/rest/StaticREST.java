package org.diskproject.client.rest;

import org.diskproject.client.Config;
import org.diskproject.shared.api.StaticService;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.REST;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;

public class StaticREST {
	public static void getJSObject(String url, final Callback<JavaScriptObject, Throwable> callback) {
		GWT.log("trying to download: " + url);
		RequestBuilder builder =  new RequestBuilder(RequestBuilder.GET, url);
		try {
			builder.sendRequest(null, new RequestCallback() {
				@Override
				public void onResponseReceived(Request request, Response response) {
					if (response.getStatusCode() == 200) {
						JavaScriptObject json = JsonUtils.safeEval(response.getText());
						callback.onSuccess(json);
					} else {
						GWT.log("error3");
					}
				}
				@Override
				public void onError(Request request, Throwable exception) {
					GWT.log("error2");
					// TODO Auto-generated method stub
				}
			});
		} catch (Exception e) {
			GWT.log("some error");
			// TODO: handle exception
		}
	}

	public static void getAsString(String url, final Callback<String, Throwable> callback) {
		GWT.log("trying to download: " + url);
		RequestBuilder builder =  new RequestBuilder(RequestBuilder.GET, url);
		try {
			builder.sendRequest(null, new RequestCallback() {
				@Override
				public void onResponseReceived(Request request, Response response) {
					if (response.getStatusCode() == 200) {
						callback.onSuccess(response.getText());
					} else {
						GWT.log("error3");
					}
				}
				@Override
				public void onError(Request request, Throwable exception) {
					GWT.log("error2");
					// TODO Auto-generated method stub
				}
			});
		} catch (Exception e) {
			GWT.log("some error");
			// TODO: handle exception
		}
	}

  //public static StaticService staticService;

  /*public static StaticService getStaticService() {
	  if (staticService == null) {
		  GWT.log("creating static rest");
		  Defaults.setServiceRoot(Config.getServerURL());
		  staticService = GWT.create(StaticService.class);
	  }
	  return staticService;
  }

  public static void getFile(String path,
		  final Callback<Void, Throwable> callback) {
	  REST.withCallback(new MethodCallback<Void>() {
		  @Override
		  public void onFailure(Method method, Throwable exception) {
			  callback.onFailure(exception);
		  }
		  @Override
		  public void onSuccess(Method method, Void response) {
			  GWT.log(response.toString());
			  callback.onSuccess(null);
		  }
	  }).call(getStaticService()).staticResources(path);
  }*/
}
