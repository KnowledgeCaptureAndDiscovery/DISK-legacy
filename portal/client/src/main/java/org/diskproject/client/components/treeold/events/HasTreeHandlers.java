package org.diskproject.client.components.treeold.events;

import com.google.gwt.event.shared.HandlerRegistration;

public interface HasTreeHandlers {
  public HandlerRegistration addTreeItemSelectionHandler(
      TreeItemSelectionHandler handler);
  public HandlerRegistration addTreeItemDeletionHandler(
      TreeItemDeletionHandler handler);  
}
