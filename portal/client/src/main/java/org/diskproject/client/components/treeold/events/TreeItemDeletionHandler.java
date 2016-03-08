package org.diskproject.client.components.treeold.events;

import com.google.gwt.event.shared.EventHandler;

public interface TreeItemDeletionHandler extends EventHandler {
  void onDeletion(TreeItemDeletionEvent event);
}
