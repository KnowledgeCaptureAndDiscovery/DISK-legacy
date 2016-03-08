package org.diskproject.client.components.list.events;

import com.google.gwt.event.shared.EventHandler;

public interface ListItemDeletionHandler extends EventHandler {
  void onDeletion(ListItemDeletionEvent event);
}
