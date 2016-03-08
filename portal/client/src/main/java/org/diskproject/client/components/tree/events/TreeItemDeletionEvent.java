package org.diskproject.client.components.tree.events;

import org.diskproject.client.components.tree.TreeNode;
import com.google.gwt.event.shared.GwtEvent;

public class TreeItemDeletionEvent extends GwtEvent<TreeItemDeletionHandler> {

  public static Type<TreeItemDeletionHandler> TYPE = new Type<TreeItemDeletionHandler>();

  private final TreeNode node;

  public TreeItemDeletionEvent(TreeNode node) {
    this.node = node;
  }

  @Override
  public Type<TreeItemDeletionHandler> getAssociatedType() {
    return TYPE;
  }

  @Override
  protected void dispatch(TreeItemDeletionHandler handler) {
    handler.onDeletion(this);
  }

  public TreeNode getItem() {
    return this.node;
  }
}
