package org.diskproject.client.application;

import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;

import org.diskproject.client.authentication.SessionStorage;
import org.diskproject.client.rest.AppNotification;
import org.diskproject.client.rest.UserREST;
import org.diskproject.shared.classes.users.UserCredentials;
import org.diskproject.shared.classes.users.UserSession;

import com.google.gwt.core.client.Callback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import com.vaadin.polymer.iron.widget.event.IronOverlayOpenedEvent;
import com.vaadin.polymer.paper.PaperDrawerPanelElement;
import com.vaadin.polymer.paper.PaperToastElement;
import com.vaadin.polymer.paper.widget.PaperDialog;
import com.vaadin.polymer.paper.widget.PaperInput;

public class ApplicationView extends ViewImpl implements
    ApplicationPresenter.MyView {
  interface Binder extends UiBinder<Widget, ApplicationView> { }

  @UiField PaperDrawerPanelElement drawer;
  
  @UiField public static SimplePanel contentContainer;
  @UiField public static PaperToastElement toast;
  
  @UiField SimplePanel sidebar;
  @UiField SimplePanel toolbar;
  
  @UiField Anchor loginanchor, logoutanchor, registeranchor;
  
  @UiField PaperDialog loginwindow;
  @UiField PaperInput username;
  @UiField PaperInput password;

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
  
  @UiHandler("loginwindow")
  void onShowWindow(IronOverlayOpenedEvent event) {
    username.setFocused(true);
  }
  
  @UiHandler("loginbutton")
  public void onLogin(ClickEvent event) {
    submitLoginForm();
    event.stopPropagation();
  }
  
  /*@UiHandler({"username", "password"})
  void onSoftwareEnter(KeyPressEvent event) {
    if(event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
      submitLoginForm();
    }
  }*/
  
  @UiHandler("logoutanchor")
  public void onLogout(ClickEvent event) {
    UserREST.logout(new Callback<Void, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }
      @Override
      public void onSuccess(Void session) {
        toggleLoginLogoutButtons();
      }
    });
  }
  
  @UiHandler("loginanchor")
  public void onLoginClick(ClickEvent event) {
    loginwindow.open();
  }
  
  private void submitLoginForm() {
    if(!username.getInvalid() && !password.getInvalid()) {
      UserCredentials credentials = new UserCredentials();
      credentials.setName(username.getValue());
      credentials.setPassword(password.getValue());
      UserREST.login(credentials, new Callback<UserSession, Throwable>() {
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure(reason.getMessage());
        }
        @Override
        public void onSuccess(UserSession session) {
          toggleLoginLogoutButtons();
          username.setValue(null);
          password.setValue(null);
          loginwindow.close();
        }
      });
    }
  }

  @Override
  public void initializeParameters(String userid, String domain, String[] params, boolean edit, 
      SimplePanel sidebar, SimplePanel toolbar) {
    toolbar.clear();
    toolbar.add(new HTML("<h3>DISK Home</h3>"));
    //drawer.closeDrawer();
    toggleLoginLogoutButtons();
  }
  
  private void toggleLoginLogoutButtons() {
    UserSession session = SessionStorage.getSession();
    if(session == null) {
      loginanchor.setVisible(true);
      registeranchor.setText("Register");
      logoutanchor.setVisible(false);
    }
    else {
      loginanchor.setVisible(false);
      registeranchor.setText("Change user details");
      logoutanchor.setVisible(true);
      logoutanchor.setText("Logout " + session.getUsername());
    }
  }

}
