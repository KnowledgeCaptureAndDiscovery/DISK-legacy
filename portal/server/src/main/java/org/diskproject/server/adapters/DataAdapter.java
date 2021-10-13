package org.diskproject.server.adapters;

import java.util.List;
import java.util.Map;

import org.diskproject.shared.classes.loi.LineOfInquiry;

public abstract class DataAdapter {
    private String URI, name, username, password;

    public DataAdapter (String URI, String name, String username, String password) {
        this.URI = URI;
        this.name = name;
        this.username = username;
        this.password = password;
    }
    
    public String getURI () {
        return this.URI;
    }
    
    public String getName ( ) {
        return this.name;
    }
    
    public String getUsername () {
        return this.username;
    }
    
    protected String getPassword () {
        return this.password;
    }
    
    public String toString () {
        return "[" + this.name + "] " + this.username + "@" + this.URI;
    }

    public abstract List<DataResult> query (String queryString);

    //This data query must return two variable names:
    static public String VARURI = "uri";
    static public String VARLABEL = "label";
    public abstract List<DataResult> queryOptions (String varname, String constraintQuery);

    // file -> hash
    public abstract Map<String, String> getFileHashes (List<String> dsurls);

    // Check that a LOI is correctly configured for this adapter
    public abstract boolean validateLOI (LineOfInquiry loi, Map<String, String> values);
}