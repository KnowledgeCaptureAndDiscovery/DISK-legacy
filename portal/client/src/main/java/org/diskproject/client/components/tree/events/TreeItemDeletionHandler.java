package org.diskproject.client.components.tree.events;

import com.google.gwt.event.shared.EventHandler;

public interface TreeItemDeletionHandler extends EventHandler {
  void onDeletion(TreeItemDeletionEvent event);
}
