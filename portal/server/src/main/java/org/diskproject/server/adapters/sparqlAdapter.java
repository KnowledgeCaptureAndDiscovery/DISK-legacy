package org.diskproject.server.adapters;

import java.util.ArrayList;
import java.util.List;

import edu.isi.kcap.ontapi.KBAPI;
import edu.isi.kcap.ontapi.KBObject;
import edu.isi.kcap.ontapi.OntSpec;
import edu.isi.kcap.ontapi.SparqlQuerySolution;
import edu.isi.kcap.ontapi.jena.KBAPIJena;

public class sparqlAdapter extends DataAdapter {
    private final KBAPI plainKb = new KBAPIJena(OntSpec.PLAIN);
    
    public sparqlAdapter (String URI, String name, String username, String password) {
        super(URI, name, username, password);
    }
    
    public List<DataResult> query (String queryString) {
        ArrayList<ArrayList<SparqlQuerySolution>> solutions = null;
        try {
            solutions = plainKb.sparqlQueryRemote(queryString, this.getURI(), this.getUsername(), this.getPassword());
        } catch (Exception e) {
            System.err.println(e);
        }
        List<DataResult> results = new ArrayList<DataResult>();

        if (solutions != null) {
            for (ArrayList<SparqlQuerySolution> row : solutions) {
                DataResult curResult = new DataResult();
                for (SparqlQuerySolution cell : row) {
                    String varName = cell.getVariable();
                    KBObject varValue = cell.getObject();
                    if (varValue != null) {
                        if (cell.getObject().isLiteral()) {
                            curResult.addValue(varName, cell.getObject().getValueAsString());
                        } else {
                            String name = cell.getObject().getName();
                            name = name.replaceAll("-", "%"); //Semantic media wiki changes % to - 
                            curResult.addValue(varName, cell.getObject().getID(), name);
                        }
                    } else {
                        curResult.addValue(varName, null);
                    }
                }
                results.add(curResult);
            }
        }
        return results;
    }
}
