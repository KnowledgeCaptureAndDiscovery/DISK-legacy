package org.diskproject.server.adapters;

import java.util.List;

public interface DataAdapterInterface {
    public List<DataResult> query (String queryString);
}
