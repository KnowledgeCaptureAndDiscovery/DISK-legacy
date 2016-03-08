package org.diskproject.client.components.list.events;

import org.diskproject.client.components.list.ListNode;
import com.google.gwt.event.shared.GwtEvent;

public class ListItemDeletionEvent extends GwtEvent<ListItemDeletionHandler> {

  public static Type<ListItemDeletionHandler> TYPE = new Type<ListItemDeletionHandler>();

  private final ListNode node;

  public ListItemDeletionEvent(ListNode node) {
    this.node = node;
  }

  @Override
  public Type<ListItemDeletionHandler> getAssociatedType() {
    return TYPE;
  }

  @Override
  protected void dispatch(ListItemDeletionHandler handler) {
    handler.onDeletion(this);
  }

  public ListNode getItem() {
    return this.node;
  }
}
