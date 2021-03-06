package de.unileipzig.bioinf.pdb2dllearner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.xml.sax.InputSource;

import com.dumontierlab.pdb2rdf.model.PdbRdfModel;
import com.dumontierlab.pdb2rdf.parser.PdbXmlParser;
import com.dumontierlab.pdb2rdf.util.Pdb2RdfInputIterator;
import com.dumontierlab.pdb2rdf.util.PdbsIterator;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

public class PDBIdRdfModel {

	private static Logger _logger = Logger.getLogger(HelixRDFCreator.class);
	
	private PdbRdfModel _pdbIdModel = new PdbRdfModel();
	private PdbRdfModel _removedFromModel = new PdbRdfModel();
	private PDBProtein _protein = null ;
	private ArrayList<Resource> _positives = null;
	private ArrayList<Resource> _negatives = null;
	private HashMap<Integer, Resource> _positionResource = null;
	
	public PDBIdRdfModel (PDBProtein protein) {
		
		this._protein = protein;
		this._pdbIdModel = this.getPdbRdfModel();
		this.getProtein().setSequence(extractSequence(_pdbIdModel));
		_logger.info("Sequence: " + this.getProtein().getSequence());
		this.getProtein().setSpecies(extractSpecies(_pdbIdModel));
		_logger.info("Species: " + this.getProtein().getSpecies());
		createPositivesAndNegatives();
		this._positionResource = createPositionResidueMap();
	}
	
	public PdbRdfModel getModel(){
		return _pdbIdModel;
	}
	
	public PDBProtein getProtein() {
		return _protein;
	}

	public ArrayList<Resource> getPositives(){
		return _positives;
	}
	
	public ArrayList<Resource> getNegatives(){
		return _negatives;
	}
	
	public HashMap<Integer, Resource> getPositionResource(){
		return _positionResource;
	}

	private PdbRdfModel getPdbRdfModel() {
		String[] pdbIDs = {_protein.getPdbID()}; 
	    Pdb2RdfInputIterator i = new PdbsIterator(pdbIDs);
	    PdbXmlParser parser = new PdbXmlParser();
        PdbRdfModel model = new PdbRdfModel();
        try {
        	while (i.hasNext())
        	{
        		final InputSource input = i.next();
        		model = parser.parse(input, new PdbRdfModel());
        		/*
        		 *  jedes Model muss gleich nach den relevanten Daten durchsucht werden,
        		 *  da ansonsten Probleme mit der Speichergröße auftreten können. 
        		 */

        		_pdbIdModel.add(extractDataForPdbAndChain(model, _protein.getPdbID(), _protein.getChainID()));
        	}
        	try {
				PrintStream test = new PrintStream (new File("data/" + this.getProtein().getPdbID() + ".rdf"));
				_pdbIdModel.write(test, "RDF/XML");
				test.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
        }
        catch (Exception e)
        {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    return _pdbIdModel;
	}
	
	private String extractSpecies(PdbRdfModel model) {
		String queryString ;
		queryString = 
			"PREFIX pdb: <http://bio2rdf.org/pdb:> " +
			"PREFIX dcterms: <http://purl.org/dc/terms/> " +
			"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
			"PREFIX fn: <http://www.w3.org/2005/xpath-functions#> " +
			"CONSTRUCT { <http://bio2rdf.org/pdb:" + this.getProtein().getPdbID() + "/extraction/source/gene/organism> rdfs:label ?species. }" +
    		"WHERE { ?x1 dcterms:isPartOf ?x2 ." +
	    		" ?x1 rdf:type ?x3 ." +
	    		" ?x1 pdb:isImmediatelyBefore ?x4 ." +
				" OPTIONAL { ?x5 rdfs:label ?species FILTER (str(?x5) = fn:concat(str(?x2), '/extraction/source/gene/organism')) . } . }";
		
		_logger.debug(queryString);
		
		PdbRdfModel construct = new PdbRdfModel();
		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
    	construct.add(qe.execConstruct());
		Resource organism = ResourceFactory.createResource("http://bio2rdf.org/pdb:" + this.getProtein().getPdbID() + "/extraction/source/gene/organism");
    	Property label = ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#", "label");
		String species = "";
		try
		{
			NodeIterator niter = construct.listObjectsOfProperty(organism, label);
			while ( niter.hasNext() )
			{
				RDFNode nextRes = niter.next();
				species = nextRes.toString();
				_logger.debug(species);
			}
		}
		finally 
		{
			qe.close() ;
		}
		return species;
	}

	private String extractSequence(PdbRdfModel model) {

		String sequence = null;
		
		Property type = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "type");
		Property hasValue = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "hasValue");
		Resource polymerSequence = ResourceFactory.createResource("http://bio2rdf.org/pdb:PolymerSequence");
		
		ResIterator riter = model.listResourcesWithProperty(type, polymerSequence);
		while (riter.hasNext()) {
			Resource nextRes = riter.nextResource();
			if (model.contains(nextRes, hasValue)){
				NodeIterator niter = model.listObjectsOfProperty(nextRes, hasValue);
				sequence = niter.next().toString();
				
				_logger.debug("Sequence: " + sequence);
			}
		} ;
    	return sequence;
	}

	private PdbRdfModel extractDataForPdbAndChain(PdbRdfModel model, String pdbID, String chainID) {
    	
		// Beispiel einer SELECT Abfrage
		/*	String selectQuery = 
		 *		"SELECT { ?x1 ?x2 ?x3 .} " +
		 *		"WHERE { ?x1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://bio2rdf.org/pdb:Helix> .}";
		 * 	Query query = QueryFactory.create(selectQuery);
		 * 	QueryExecution qe = QueryExecutionFactory.create(query, model);
		 * 	ResultSet select = qe.execSelect();
		 * 	ResultSetFormatter.out (System.out, select, query); 
		 * 
		 */
		
		// CONSTRUCT Abfrage
		 
		PdbRdfModel construct = new PdbRdfModel();
			/* 
			 * i do it kind of difficult, but i want to be certain that i only get the sequences of
			 * Polypeptides(L) which contain at least one Helix. Furthermore i collect the information
			 * about at which position helices begin and end.
			 * NOTE:	this information has to be removed before outputing the model. But i will use this
			 * 			to check for in-helix amino acids
			*/ 
		/*
		 * ich brauche noch die selektion der chain und die info über den genursprungsorganismus
		 * rdf:resource="http://bio2rdf.org/pdb:3LQH/chain_A"
		 * http://bio2rdf.org/pdb:3LQH/chain_A/position_1596
		 */
		
		 String queryString = 
				"PREFIX pdb: <http://bio2rdf.org/pdb:> " +
				"PREFIX dcterms: <http://purl.org/dc/terms/> " +
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
				"PREFIX fn: <http://www.w3.org/2005/xpath-functions#> " +
				"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " +
				"CONSTRUCT { " +
					" ?x1 pdb:beginsAt ?x2 ." +
					" ?x1 pdb:endsAt ?x3 ." +
					" ?x5 dcterms:isPartOf ?x4 ." +
					" ?x5 rdf:type ?x6 ." +
					" ?x5 pdb:isImmediatelyBefore ?x7 ." +
					" ?x5 pdb:hasChainPosition ?x8 ." +
					" ?x8 pdb:hasValue ?x9 ." +
					" ?organism rdfs:label ?organismName ." +
					" ?seq rdf:type pdb:PolymerSequence ." +
					" ?seq pdb:hasValue ?sequence . " +
				"} " +	    		
	    		"WHERE { " +
	    			" OPTIONAL { ?x1 rdf:type pdb:Helix ." +
	    				" ?x1 pdb:beginsAt ?x2 ." +
	    				" ?x1 pdb:endsAt ?x3 . " +
	    			"} . " +
	    			" ?x3 dcterms:isPartOf ?x4 ." +
	    			" ?x4 rdf:type <http://bio2rdf.org/pdb:Polypeptide(L)> ." +
	    		//" <http://bio2rdf.org/pdb:3A4R/chemicalComponent_A0> dcterms:isPartOf ?x4 ." +
	    		//" <http://bio2rdf.org/pdb:3A4R/chemicalComponent_A0> rdf:type ?x6 ." +
	    		//" OPTIONAL { <http://bio2rdf.org/pdb:3A4R/chemicalComponent_A0> pdb:isImmediatelyBefore ?x7 . } ." +
	    		//" <http://bio2rdf.org/pdb:3A4R/chemicalComponent_A0> pdb:hasChainPosition ?x8 ." +
	    			" ?x5 dcterms:isPartOf ?x4 . " +
	    			" ?x5 rdf:type ?x6 ." +
	    		// with the optional clause i get the information by which amino acid
	    		// a amino acid is followed
	    			" OPTIONAL { ?x5 pdb:isImmediatelyBefore ?x7 . } ." +
	    			" ?x5 pdb:hasChainPosition ?x8 ." +
	    			" ?x8 pdb:hasValue ?x9 Filter (datatype((?x9)) = xsd:integer) .";
		 if (chainID.length() == 1 && pdbID.length() == 4)
			{
				queryString +=
						" ?x8 dcterms:isPartOf <http://bio2rdf.org/pdb:" + 
								pdbID.toUpperCase() +
								"/chain_" + chainID.toUpperCase() + "> .";
			}
		 queryString +=
					" ?x4 pdb:hasPolymerSequence ?seq . " +
					" ?seq rdf:type pdb:PolymerSequence . " +
					" ?seq pdb:hasValue ?sequence . " +
					" OPTIONAL { ?organism rdfs:label ?organismName " +
	    				"FILTER (str(?organism) = fn:concat(str(?x4), '/extraction/source/gene/organism')) . " +
	    			"} . " +
	    		"}";
		
		_logger.debug(queryString);
		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
    	construct.add(qe.execConstruct()); 
    	qe.close();
    	return construct;
	}
	
	public ResIterator getFirstAA() {
		PdbRdfModel construct = new PdbRdfModel();
		/* i look for all amino acids (AA) that have a successor
		 * but do not have a predecessor -> it's the first AA of every
		 * polypeptide chain
		 */
		
		String queryString = 
			"PREFIX pdb: <http://bio2rdf.org/pdb:> " +
    		"CONSTRUCT { ?x1 pdb:isImmediatelyBefore ?x2 . } " +
    		"WHERE { ?x1 pdb:isImmediatelyBefore ?x2 . " +
    		// NOT EXISTS can be used with SPARQL 1.1
    		//"NOT EXISTS { ?x3 pdb:isImmediatelyBefore ?x1 . } }";
			" OPTIONAL { ?x3 pdb:isImmediatelyBefore ?x1 . } " +
			" FILTER ( !BOUND(?x3) ) }";
		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, _pdbIdModel);
    	construct.add(qe.execConstruct()); 
    	qe.close();
    	ResIterator niter = construct.listSubjects();
    	return niter;
	}
	
	private HashMap<Integer, Resource> createPositionResidueMap(){
		
		HashMap<Integer, Resource> posres = new HashMap<Integer, Resource>(150);
		Property iib = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "isImmediatelyBefore");

		ResIterator firstAAs = this.getFirstAA();
		while ( firstAAs.hasNext()) {
			Resource firstAA = firstAAs.next();
			Resource currentAA = firstAA;
			posres.put(new Integer(this.getResiduePosition(currentAA)), currentAA);
			while (currentAA.hasProperty(iib)) {
				currentAA = _pdbIdModel.getProperty(currentAA, iib).getResource();
				posres.put(new Integer(this.getResiduePosition(currentAA)), currentAA);
			}
		}
		
		return posres;
	}
	
	private int getResiduePosition(Resource res) {
		Property hasChainPosition = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "hasChainPosition");
		Property hasValue = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "hasValue");
		ResourceFactory.createResource();
		
		NodeIterator residuePosition = _pdbIdModel.listObjectsOfProperty(res, hasChainPosition );
		ArrayList<RDFNode> positionNodes = new ArrayList<RDFNode>();
		ArrayList<Integer> positionLabels = new ArrayList<Integer>();
		while ( residuePosition.hasNext() ) {
			RDFNode positionNode = residuePosition.next();
			positionNodes.add(positionNode);
			NodeIterator positionLabelNodes = _pdbIdModel.listObjectsOfProperty( positionNode.as(Resource.class), hasValue );
			while ( positionLabelNodes.hasNext() ) {
				positionLabels.add(positionLabelNodes.next().as(Literal.class).getInt());
			}
		} 
		
		
		Integer position = null;
		if ( positionNodes.size() == 1 && positionLabels.size() == 1 ) {
			position = positionLabels.get(0);
		} else {
			position = new Integer(0);
		}
		return position.intValue();
	}
	
	public void addDistanceInfo(){
		String queryString = 
			"PREFIX pdb: <http://bio2rdf.org/pdb:> " +
    		"CONSTRUCT { ?x1 pdb:isFourAminoAcidsBefore ?x5 . } " +
    		"WHERE { ?x1 pdb:isImmediatelyBefore ?x2 . " +
    		" ?x2 pdb:isImmediatelyBefore ?x3 . " +
    		" ?x3 pdb:isImmediatelyBefore ?x4 . " +
    		" ?x4 pdb:isImmediatelyBefore ?x5 . }";
		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, _pdbIdModel);
    	_pdbIdModel.add(qe.execConstruct()); 
    	qe.close();
	}
	
	public void addRemovedStatements(){
		_pdbIdModel.add(_removedFromModel);
		_removedFromModel.removeAll();
	}
	
	public void removeStatementsWithPoperty(Property prop){
		String queryString = 
			"PREFIX x:<" + prop.getNameSpace() + "> " +
    		"CONSTRUCT { ?x1 x:" + prop.getLocalName()+ " ?x2 . } " +
    		"WHERE { ?x1 x:" + prop.getLocalName() + " ?x2 . }";
		_logger.debug(queryString);
		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, _pdbIdModel);
    	StmtIterator stmtiter = qe.execConstruct().listStatements(); 
    	qe.close();
    	while(stmtiter.hasNext()){
    		Statement nextStmt = stmtiter.next();
    		_pdbIdModel.remove(nextStmt);
    		_removedFromModel.add(nextStmt);
    	}
    	
	}
	
	public void removeStatementsWithObject(Resource res){
		String queryString =
			"PREFIX x:<" + res.getNameSpace() + "> " +
    		"CONSTRUCT { ?x1 ?x2 x:" + res.getLocalName() + " . } " +
    		"WHERE { ?x1 ?x2 x:" + res.getLocalName() + " . }";
		_logger.debug(queryString);
		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, _pdbIdModel);
    	StmtIterator stmtiter = qe.execConstruct().listStatements(); 
    	qe.close();
    	while(stmtiter.hasNext()){
    		Statement nextStmt = stmtiter.next();
    		_pdbIdModel.remove(nextStmt);
    		_removedFromModel.add(nextStmt);
    	}
	}
	
	private void createPositivesAndNegatives() {
		
		ResIterator riter = this.getFirstAA();
		// Properties i have to check for while going through the AA-chain
		Property iib = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "isImmediatelyBefore");
		Property ba = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "beginsAt");
		Property ea = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "endsAt");
		ArrayList<Resource> pos = new ArrayList<Resource>();
		ArrayList<Resource> neg = new ArrayList<Resource>();
		
		// every element in riter stands for a AA-chain start
		// every first amino acid indicates a new AA-chain 
		while (riter.hasNext()) {
			// Initialization of variables needed
			Resource firstAA = riter.nextResource();
			_logger.debug("First AA: " + firstAA.getLocalName());
			Resource currentAA  = firstAA;
			Resource nextAA = firstAA;
			boolean inHelix = false;
						
			// look if there is a next AA
			do {
				// looks weird, but is needed to enter loop even for the last AA which does not have a iib-Property
				currentAA = nextAA;
				// die Guten ins Töpfchen ...
				// if we get an non-empty iterator for pdb:beginsAt the next AAs are within a AA-chain
				if(_pdbIdModel.listResourcesWithProperty(ba, currentAA).hasNext() && !inHelix ){
					inHelix = true;
				}
				// die Schlechten ins Kröpfchen
				// if we get an non-empty iterator for pdb:endsAt and are already within a AA-chain
				// the AAs AFTER the current ones aren't within a helix
				if (_pdbIdModel.listResourcesWithProperty(ea, currentAA).hasNext() && inHelix){
					inHelix = false;
				}
				// get next AA if there is one
				if (_pdbIdModel.contains(currentAA, iib)){
					nextAA = _pdbIdModel.getProperty(currentAA, iib).getResource();
				}
				
				// add current amino acid to positives or negatives set
				if (inHelix){
					pos.add(currentAA);
				} else {
					neg.add(currentAA);
				}
				
			} while (currentAA.hasProperty(iib)) ;
		}
		_positives = pos;
		_logger.debug("+++ Positive set +++");
		for (int i = 0; i < pos.size(); i++){
			_logger.debug("Das " + i + "te Element: " + pos.get(i).getLocalName());
		}
		
		_negatives = neg;
		_logger.debug("+++ Negatvie set +++");
		for (int i = 0; i < neg.size(); i++){
			_logger.debug("Das " + i + "te Element: " + neg.get(i).getLocalName());
		}
	}
	
	public void createFastaFile(String dir){
		try {
			String fastaFilePath = dir + this.getProtein().getFastaFileName();
			PrintStream out = new PrintStream (new File(fastaFilePath));
			out.println(">" + this.getProtein().getPdbID() + "." + this.getProtein().getChainID() + "." + this.getProtein().getSpecies());
			String sequence = this.getProtein().getSequence();
			int seqLength = sequence.length();
			
			if (seqLength > 80) {
				// write sequence in 80 character blocks into file
				int beginIndex = 0;
				int endIndex = 80;
				while ( endIndex <= seqLength ){
					out.println(sequence.substring(beginIndex, endIndex));
					if (seqLength - endIndex < 80){
						out.println(sequence.substring(endIndex, seqLength));
					}
					beginIndex = endIndex;
					endIndex += 80;
				}
				
			} else {
				out.println(sequence);
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void createFastaFile(String dir, String fastaFileName){
		this.getProtein().setFastaFileName(fastaFileName);
		this.createFastaFile(dir);
	}
}
