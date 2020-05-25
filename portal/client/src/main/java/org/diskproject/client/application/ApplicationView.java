package org.diskproject.client.application;

import com.google.gwt.uibinder.client.UiField;
import org.diskproject.client.place.NameTokens;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.uibinder.client.UiBinder;
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

public class ApplicationView extends ViewImpl implements
    ApplicationPresenter.MyView {
  interface Binder extends UiBinder<Widget, ApplicationView> { }

  @UiField public static PaperDrawerPanelElement drawer;
  @UiField public static SimplePanel contentContainer;
  @UiField public static PaperToastElement toast;
  
  @UiField public static DivElement 
    hypothesesMenu, loisMenu, assertionsMenu;
  
  @UiField SimplePanel sidebar;
  @UiField SimplePanel toolbar;

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
        
        clearMenuClasses(hypothesesMenu);
        clearMenuClasses(loisMenu);
        //clearMenuClasses(tloisMenu);
        clearMenuClasses(assertionsMenu);
        
        if(menu != null) {
          menu.addClassName("activeMenu");
          if(params.length > 0) {
            addClassToMenus("hiddenMenu");
            menu.addClassName("activeItemMenu");
            menu.removeClassName("hiddenMenu");
          }
        }
        
        return null;
      }
    });
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
  }
  

}
