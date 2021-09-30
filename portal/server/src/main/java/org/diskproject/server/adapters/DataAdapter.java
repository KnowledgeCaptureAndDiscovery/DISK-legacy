package org.diskproject.server.adapters;

public abstract class DataAdapter implements DataAdapterInterface {
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
}