package org.diskproject.client;

import java.util.Arrays;
import java.util.Map;

import javax.annotation.Nonnull;

import org.diskproject.client.authentication.SessionStorage;
import org.diskproject.client.rest.DiskREST;
import org.realityforge.gwt.keycloak.Keycloak;
import org.realityforge.gwt.keycloak.KeycloakListenerAdapter;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.shared.GWT;
import com.gwtplatform.mvp.client.ApplicationController;
import com.vaadin.polymer.Polymer;
import com.vaadin.polymer.elemental.Function;
import com.vaadin.polymer.iron.IronIconElement;
import com.vaadin.polymer.iron.IronListElement;
import com.vaadin.polymer.paper.*;
import com.vaadin.polymer.vaadin.VaadinComboBoxElement;
import org.diskproject.client.authentication.AuthUser;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class DiskClient implements EntryPoint {
  public final ApplicationController controller = GWT.create(ApplicationController.class);
  
  public void onModuleLoad() {
	//Login with keycloak 
	final Keycloak keycloak = new Keycloak("DiskClient", "/disk-portal/customise/keycloak.json");
	keycloak.getInitOptions().setCheckLoginIframe(false);
	keycloak.addKeycloakListener(new KeycloakListenerAdapter()
		{
		  @Override
		  public void onReady( @Nonnull final Keycloak keycloak, final boolean authenticated )
		  {
			if( authenticated ) {
				AuthUser.init(keycloak);

    DiskREST.getServerConfig(new Callback<Map<String, String>, Throwable>() {
      @Override
      public void onSuccess(Map<String, String> config) {
        GWT.log("Loading Module");
        Polymer.startLoading();
        
        SessionStorage.loadSession();
        
        Polymer.importHref(Arrays.asList(
            "iron-icons/iron-icons.html",
            "paper-styles/color.html",
            "neon-animation/animations/scale-up-animation.html",
            "neon-animation/animations/scale-down-animation.html",
            //IronAjaxElement.SRC,
            IronListElement.SRC,
            IronIconElement.SRC,
            //NeonAnimatableElement.SRC,
            //NeonAnimatedPagesElement.SRC,
            PaperIconButtonElement.SRC,
            PaperItemElement.SRC,
            PaperToastElement.SRC,
            //PaperCardElement.SRC,
            PaperDrawerPanelElement.SRC,
            PaperScrollHeaderPanelElement.SRC,
            PaperHeaderPanelElement.SRC,
            PaperToolbarElement.SRC,
            PaperDialogElement.SRC,
            PaperTextareaElement.SRC,
            PaperInputElement.SRC,
            PaperListboxElement.SRC,
            //PaperDropdownMenuElement.SRC,
            PaperMenuElement.SRC,
            PaperIconButtonElement.SRC,
            PaperButtonElement.SRC,
            PaperCheckboxElement.SRC,
            VaadinComboBoxElement.SRC));
        
        Polymer.whenReady(new Function<Object, Object>() {
          public Object call(Object arg) {
            GWT.log("Initializing controller");
            controller.init();
            return null;
          }
        });
      }      
      @Override
      public void onFailure(Throwable reason) {
      }
    });
			  
			} else {
			  keycloak.login();
			}
		  }
		} 
	);

	keycloak.init();
	  
  }
}

