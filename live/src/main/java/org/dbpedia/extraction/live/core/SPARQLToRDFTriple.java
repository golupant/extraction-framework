package org.dbpedia.extraction.live.core;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.rdf.model.impl.PropertyImpl;
import com.hp.hpl.jena.rdf.model.impl.ResourceImpl;
import org.apache.log4j.Logger;
import org.dbpedia.extraction.live.extraction.LiveExtractionConfigLoader;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;

import java.util.*;

/*import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.ntriples.NTriplesUtil;*/


/**
 * Created by IntelliJ IDEA.
 * User: Mohamed Morsey
 * Date: Jul 8, 2010
 * Time: 4:53:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class SPARQLToRDFTriple {
    //Initializing the Logger
    private Logger logger = Logger.getLogger(SPARQLToRDFTriple.class);
    
    private Resource subject;
	private String language;
	private SPARQLEndpoint sparqlEndpoint;
	private JDBC jdbc;
	private String use = null;

	public SPARQLToRDFTriple(Resource Subject, String Language){
        subject = Subject;
        language = Language;
        this.use = LiveOptions.options.get("Sparql.use");
        if(this.use.equals("jdbc")){
            this.jdbc = JDBC.getDefaultConnection();
        }else{
            this.sparqlEndpoint = SPARQLEndpoint.getDefaultEndpoint();
        }
    }

    public ArrayList getRDFTriples(String query, HashMap subject, HashMap predicate, HashMap object){
        return getRDFTriples(query, subject, predicate, object, true);
    }

    public ArrayList getRDFTriples(String query, HashMap subject, HashMap predicate, HashMap object, boolean filterlanguage){

        if(subject.get("action").toString().equals("fix")){
            Resource s = new ResourceImpl(subject.get("value").toString());
        }else if(subject.get("action").equals("classattribute")){
            Resource s = this.subject;
        }
        if(predicate.get("action").equals("fix")){
            Resource p = new ResourceImpl(predicate.get("value").toString());
        }
        if(object.get("action").equals("fix")){
            try {
                Resource o = new ResourceImpl(object.get("value").toString());
            }catch(Exception exp) {
                logger.warn("object = fix only for uris currently");
            return new ArrayList();
            }
        }

        ArrayList result = new ArrayList();
        HashMap jarr = new HashMap();

        boolean pass = false;
        String json = "";
        if(this.use.equals("jdbc")){
            //If the application is working in multithreading mode, we must attach the thread id to the timer name
            //to avoid the case that a thread stops the timer of another thread.
            String timerName = this.getClass() + ":jdbc_request" +
                    (LiveExtractionConfigLoader.isMultithreading()? Thread.currentThread().getId():"");
            Timer.start(timerName);

             jarr = this.jdbc.execAsJson(query, "SPARQLToRDFTriple");
            Timer.stop(timerName);
        }else{
            //If the application is working in multithreading mode, we must attach the thread id to the timer name
            //to avoid the case that a thread stops the timer of another thread.
            String timerName = this.getClass() + ":http_request" +
                    (LiveExtractionConfigLoader.isMultithreading()? Thread.currentThread().getId():"");

            Timer.start(timerName);
            json = this.sparqlEndpoint.executeQuery(query, this.getClass());

            JSONParser parser = new JSONParser();

            //This class is object is created to force the JSON decoder to return a HashMap, as hashesFromStore is a HashMap
            ContainerFactory containerFactory = new ContainerFactory(){
                public List creatArrayContainer() {
                  return new LinkedList();
                }

                public Map createObjectContainer() {
                  return new HashMap();
                }

              };

            try{
                jarr = (HashMap)parser.parse(json);
            }
            catch(Exception exp){
                this.logger.warn("Unable to parse JSON: " + exp.getMessage());
                return new ArrayList();
            }

            if(jarr.get("results") != null){
                jarr = (HashMap)jarr.get("results");
                }

            Timer.stop(timerName);
        }

        Resource s = null;
        Property p = null;
        RDFNode o = null;

        if(jarr.get("bindings")!=null ){
            ArrayList bindings = (ArrayList)jarr.get("bindings");
            for(Object objBinding:bindings ){
                HashMap one = (HashMap)objBinding;
                try{
                    if(!Util.isStringNullOrEmpty(subject.get("action").toString())){
                        if(subject.get("action").toString().equals("fix")){

                        }
                        else if(subject.get("action").toString().equals("variable")){
                            HashMap subHashmap = (HashMap)one.get(subject.get("value"));
                            s = new ResourceImpl(subHashmap.get("value").toString());
                            }
                        else if(subject.get("action").toString().equals("classattribute")){
                            }
                    }
                    if(!Util.isStringNullOrEmpty(predicate.get("action").toString())){
                        if(predicate.get("action").toString().equals("fix")){
                            }
                        else if(predicate.get("action").toString().equals("variable")){
                            HashMap predHashmap = (HashMap)one.get(predicate.get("value"));
                            p = new PropertyImpl(predHashmap.get("value").toString());
                            }
                    }
                    if(!Util.isStringNullOrEmpty(object.get("action").toString())){
                         if(object.get("action").toString().equals("fix")){
                            }
                        else if(object.get("action").toString().equals("variable")){
                            HashMap unknown = (HashMap)one.get(object.get("value"));
                            o = this.toObject(unknown, filterlanguage);
                            if(o == null) {continue;	}
                            }
                    }

                    this.logger.info(s);
                    this.logger.info(p);
                    this.logger.info(o);
                    result.add(new RDFTriple(s, p, o));
                }catch(Exception exp){
                    this.logger.warn("Found invalid URI: " + exp.getMessage());
                    }
                }
         }else{
            this.logger.warn(json);
             }
        return result;
   }

    public  ArrayList getRDFTriples(String query){
        Resource s = this.subject;
        ArrayList result = new ArrayList();

        //If the application is working in multithreading mode, we must attach the thread id to the timer name
        //to avoid the case that a thread stops the timer of another thread.
        String timerName = this.getClass() + ":http_request" +
                (LiveExtractionConfigLoader.isMultithreading()? Thread.currentThread().getId():"");

        Timer.start(timerName);
        String json = this.sparqlEndpoint.executeQuery(query, this.getClass());
        Timer.stop(timerName);

        JSONParser parser = new JSONParser();
        HashMap jarr = new HashMap(); 

        //This class is object is created to force the JSON decoder to return a HashMap, as hashesFromStore is a HashMap
        ContainerFactory containerFactory = new ContainerFactory(){
            public List creatArrayContainer() {
              return new LinkedList();
            }

            public Map createObjectContainer() {
              return new HashMap();
            }

          };

        try{
            jarr = (HashMap)parser.parse(json);
        }
        catch(Exception exp){
            this.logger.warn("Unable to parse JSON: " + exp.getMessage());
            return new ArrayList();
        }


        if((jarr.get("results")!=null) && (((HashMap)jarr.get("results")).get("bindings") != null)){
            ArrayList bindings = (ArrayList)((HashMap)jarr.get("results")).get("bindings");
            for(Object objBinding : bindings){
                HashMap one = (HashMap)objBinding;
                try{
                Property p = new PropertyImpl(((HashMap)one.get("p")).get("value").toString());
                HashMap unknown = (HashMap)one.get("o");
                Object o = this.toObject(unknown);
                if(o == null) {
                    continue;
                }

                this.logger.info(s);
                this.logger.info(p);
                this.logger.info(o);
                result.add(new RDFTriple(s, p, new ResourceImpl(o.toString())));
                }catch(Exception ex){
                    this.logger.warn("found invalid URI: " + ex.getMessage());
                    }
            }
         }
        return result;
    }


    public RDFNode toObject(HashMap unknown){
        
        return toObject(unknown, true);
    }

    public RDFNode toObject(HashMap unknown, boolean filterlanguage){
        Model tmpModel = ModelFactory.createDefaultModel();

        if(unknown.get("type").equals("uri")){
            return new ResourceImpl(unknown.get("value").toString());
        }else if (unknown.get("type").equals("literal")){
            if(unknown.get("xml:lang") != null){
                if(unknown.get("xml:lang").equals(this.language)){
                    return tmpModel.createLiteral(unknown.get("value").toString(), unknown.get("xml:lang").toString());

                }else if(!filterlanguage) {
                    return tmpModel.createLiteral(unknown.get("value").toString(), unknown.get("xml:lang").toString());
                }
            }else {
                return tmpModel.createLiteral(unknown.get("value").toString());
            }
        }else if (unknown.get("type").equals("typed-literal")){
            return tmpModel.createLiteral(unknown.get("value").toString(), unknown.get("datatype").toString());
        }else{
            System.out.println("tail in SPARQLToRDFTriple.toObject ");
            System.exit(1);
            }
        return null;
    }

}
