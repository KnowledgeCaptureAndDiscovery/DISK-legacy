package org.diskproject.client.application;

import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;

import java.util.Date;

import org.diskproject.client.place.NameTokens;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.DiskREST;
import org.diskproject.shared.classes.firebase.User;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;
import com.vaadin.polymer.paper.PaperDrawerPanelElement;
import com.vaadin.polymer.paper.PaperToastElement;
import com.vaadin.polymer.paper.PaperInputElement;

public class ApplicationView extends ViewImpl implements
    ApplicationPresenter.MyView {
  interface Binder extends UiBinder<Widget, ApplicationView> { }

  @UiField public static PaperDrawerPanelElement drawer;
  @UiField public static SimplePanel contentContainer;
  @UiField public static PaperToastElement toast;
  
  @UiField DialogBox loginDialog;
  @UiField PaperInputElement username, password;
  @UiField DivElement loginMsg;

  @UiField public static DivElement 
    hypothesesMenu, loisMenu, assertionsMenu, terminologyMenu;
  
  @UiField SimplePanel sidebar;
  @UiField SimplePanel toolbar;
  @UiField DivElement fog, userDiv, logoutDiv;
  
  User user = null;

  @Inject
  public ApplicationView(Binder binder) {
    initWidget(binder.createAndBindUi(this));
  }

  @Override
  public void setInSlot(Object slot, IsWidget content) {
    if (slot == ApplicationPresenter.CONTENT_SLOT)
      contentContainer.setWidget(content);
    else
      super.setInSlot(slot, content);
  }
  
  /*@UiHandler({"username", "password"})
  void onSoftwareEnter(KeyPressEvent event) {
    if(event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
      submitLoginForm();
    }
  }*/

  public void initializeParameters(String userid, String domain, 
      String[] params, boolean edit, 
      SimplePanel sidebar, SimplePanel toolbar) {
    this.initializeParameters(userid, domain, null, params, 
        edit, sidebar, toolbar);
  }

  public void initializeParameters(String userid, String domain, 
      final String nametoken, final String[] params, boolean edit, 
      SimplePanel sidebar, SimplePanel toolbar) {
    toolbar.clear();
    toolbar.add(new HTML("<h3>Neuro DISK Home</h3>"));
     
    Polymer.ready(drawer, new Function<Object, Object>() {
      @Override
      public Object call(Object arg) {
        drawer.closeDrawer();
        hypothesesMenu.removeClassName("activeMenu");
        loisMenu.removeClassName("activeMenu");
        //tloisMenu.removeClassName("activeMenu");
        assertionsMenu.removeClassName("activeMenu");
        terminologyMenu.removeClassName("activeMenu");
        
        DivElement menu = null;
        if(nametoken.equals(NameTokens.hypotheses))
          menu = hypothesesMenu;
        else if(nametoken.equals(NameTokens.lois))
          menu = loisMenu;
        else if(nametoken.equals(NameTokens.tlois))
          //menu = tloisMenu;
          menu = hypothesesMenu;
        else if(nametoken.equals(NameTokens.assertions))
          menu = assertionsMenu;
        else if(nametoken.equals(NameTokens.terminology))
          menu = terminologyMenu;
        
        clearMenuClasses(hypothesesMenu);
        clearMenuClasses(loisMenu);
        //clearMenuClasses(tloisMenu);
        clearMenuClasses(assertionsMenu);
        clearMenuClasses(terminologyMenu);
        
        if(menu != null) {
          menu.addClassName("activeMenu");
          if(params.length > 0) {
            addClassToMenus("hiddenMenu");
            menu.addClassName("activeItemMenu");
            menu.removeClassName("hiddenMenu");
          }
        }
        
        // LOGIN STUFF 
        checkLogin();
        return null;
      }
    });
  }
  
  public void checkLogin () {
	if (user == null) {
	 String sessionID = Cookies.getCookie("sid");
	 if ( sessionID != null ) {
		 String name = Cookies.getCookie("sname");
		 GWT.log("Checking cookies: " + sessionID + " | "+ name);
		 setUser(name);
	 } else {
		showLoginDialog();
	 }
	}
  }
  
  public void showLoginDialog () {
	fog.getStyle().setVisibility(Visibility.VISIBLE);
	loginMsg.getStyle().setVisibility(Visibility.HIDDEN);
	loginDialog.center();
  }

  @UiHandler("submitButton")
  void onSubmitButtonClicked(ClickEvent event) {   
	  String email = username.getValue();
	  String pass = password.getValue();

	  if (email.length() > 0 && pass.length() > 0) {
		  loginMsg.setInnerText("Loading...");
		  loginMsg.getStyle().setVisibility(Visibility.VISIBLE);
		  DiskREST.login(email, pass, new Callback<Boolean, Throwable>() {
			  @Override
			  public void onSuccess(Boolean login) {
				  if (login) {
					  loginDialog.hide();
					  fog.getStyle().setVisibility(Visibility.HIDDEN);
					  onLogin(email);
				  } else {
					  loginMsg.setInnerText("Incorrect email or password.");
					  loginMsg.getStyle().setVisibility(Visibility.VISIBLE);
				  }
			  }
			  @Override
			  public void onFailure(Throwable reason) {
				  GWT.log("Error on login!");
				  loginMsg.getStyle().setVisibility(Visibility.VISIBLE);
			  }
		  });
	  } else {
		  loginMsg.setInnerText("You must write and email and password.");
		  loginMsg.getStyle().setVisibility(Visibility.VISIBLE);
	  }
  } 
  

  private void onLogin (String username) {
	setUser(username);
    String sessionID = "12"; /*(Get sessionID from server's response to your login request.)*/;
    final long DURATION = 1000 * 60 * 60 * 24 * 14; //duration remembering login. 2 weeks in this example.
    Date expires = new Date(System.currentTimeMillis() + DURATION);
    Cookies.setCookie("sid", sessionID, expires, null, "/", false); 
    Cookies.setCookie("sname", username, expires, null, "/", false);
  }

  private void setUser (String username) {
	  user = new User(username, null);
	  userDiv.setInnerText("Login as " + user.getEmail());
	  logoutDiv.getStyle().setVisibility(Visibility.VISIBLE);
  }

  @UiHandler("logoutButton")
  void onLogoutButtonClicked(ClickEvent event) {
	  onLogout();
  }
  
  private void onLogout () {
	  userDiv.setInnerText("");
	  logoutDiv.getStyle().setVisibility(Visibility.HIDDEN);
	  user = null;
	  Cookies.removeCookie("sid");
	  Cookies.removeCookie("sname");
	  showLoginDialog();
  }

  private void clearMenuClasses(DivElement menu) {
    menu.removeClassName("activeMenu");
    menu.removeClassName("hiddenMenu");
    menu.removeClassName("activeItemMenu");    
  }

  private void addClassToMenus(String cls) {
    hypothesesMenu.addClassName(cls);
    loisMenu.addClassName(cls);
    //tloisMenu.addClassName(cls);
    assertionsMenu.addClassName(cls);
    terminologyMenu.addClassName(cls);
  }

}
