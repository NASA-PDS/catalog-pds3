// Copyright 2009, by the California Institute of Technology.
// ALL RIGHTS RESERVED. United States Government sponsorship acknowledged.
// Any commercial use must be negotiated with the Office of Technology Transfer
// at the California Institute of Technology.
//
// This software is subject to U. S. export control laws and regulations
// (22 C.F.R. 120-130 and 15 C.F.R. 730-774). To the extent that the software
// is subject to U.S. export control laws and regulations, the recipient has
// the responsibility to obtain export licenses or other export authority as
// may be required before exporting such information to foreign countries or
// providing access to foreign nationals.
//
// $Id$
package gov.nasa.pds.citool.registry.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;

import gov.nasa.pds.citool.registry.model.FileInfo;
import gov.nasa.pds.citool.registry.model.RegistryObject;


public class RegistryClientSolr implements RegistryClient 
{
	private static final String PDS_COLLECTION = "pds";
	private static final String FILE_COLLECTION = ".system";
	private static final String BLOB_EP = "/.system/blob/";

	private Logger log;
	private SolrClient solrClient;

	
	RegistryClientSolr(String solrUrl)
	{
		log = Logger.getLogger(this.getClass().getName());
		solrClient = new HttpSolrClient.Builder(solrUrl).build();
	}
	
	
	public String publishObject(RegistryObject obj) throws Exception
	{
		return obj.getLid();
	}


	public boolean publishFile(FileInfo fi) throws Exception
	{
		// A file with this MD5 hash already exists
		if(md5Exists(fi))
		{
			return false;
		}
		
		File file = new File(fi.path);
		String blobName = fi.lid.replaceAll(":", ".");
        String endPoint = BLOB_EP + blobName;
        
        ContentStreamUpdateRequest req = new ContentStreamUpdateRequest(endPoint);
        req.addFile(file, "application/octet-stream");
        req.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        NamedList<Object> resp = solrClient.request(req);
        
        Object error = resp.get("error"); 
        if(error != null)
        {
        	log.warning("Blob: " + blobName + ": " + error.toString());
        }
        
        return true;
	}


	public List<String> getResourceIds(String dataSetId) throws Exception
	{
		List<String> ids = new ArrayList<String>();
		if(dataSetId == null) return ids;
		
		String qStr = createGetResourceIdsQuery(dataSetId);
		
		SolrQuery query = new SolrQuery();
		query.add("q", qStr);
		query.add("fl", "identifier");
		query.add("rows", "100");
		
		QueryResponse resp = solrClient.query(PDS_COLLECTION, query);
		SolrDocumentList res = resp.getResults();		
		
		for(SolrDocument doc: res)
		{
			Object obj = doc.getFirstValue("identifier");
			if(obj != null)
			{
				ids.add(obj.toString());
			}
		}
		
		return ids;
	}
	
	
	private boolean md5Exists(FileInfo fi) throws Exception
	{
		// If md5 starts with '0' Solr 7.7.x strips it out. 
		// Is it a Solr bug?
		String md5 = fi.md5.startsWith("0") ? fi.md5.substring(1) : fi.md5;
				
		SolrQuery query = new SolrQuery("md5:\"" + md5 + "\"");
		QueryResponse resp = solrClient.query(FILE_COLLECTION, query);
		SolrDocumentList res = resp.getResults();
		return res.getNumFound() > 0;
	}
	
	
	private static String createGetResourceIdsQuery(String dataSetId)
	{
		return "identifier:urn\\:nasa\\:pds\\:context_pds3\\:resource\\:resource."
				+ dataSetId.toLowerCase() + "__* AND data_class:Resource";
	}

}
