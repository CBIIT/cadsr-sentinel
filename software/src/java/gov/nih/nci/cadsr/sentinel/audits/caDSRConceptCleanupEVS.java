/*L
 * Copyright ScenPro Inc, SAIC-F
 *
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See http://ncip.github.com/cadsr-sentinal/LICENSE.txt for details.
 */

// Copyright (c) 2006 ScenPro, Inc.

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/audits/AuditConceptToEVS.java,v 1.11 2008-05-15 17:35:48 hebell Exp $
// $Name: not supported by cvs2svn $

package gov.nih.nci.cadsr.sentinel.audits;

import gov.nih.nci.cadsr.sentinel.database.DBAlertOracleMetadata;
import gov.nih.nci.cadsr.sentinel.database.DBProperty;
import gov.nih.nci.cadsr.sentinel.tool.ConceptItem;
import gov.nih.nci.system.applicationservice.ApplicationException;
import gov.nih.nci.system.client.ApplicationServiceProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.LexGrid.LexBIG.DataModel.Core.CodingSchemeVersionOrTag;
import org.LexGrid.LexBIG.DataModel.Collections.ConceptReferenceList;
import org.LexGrid.LexBIG.DataModel.Collections.LocalNameList;
import org.LexGrid.LexBIG.DataModel.Collections.NCIChangeEventList;
import org.LexGrid.LexBIG.DataModel.Collections.ResolvedConceptReferenceList;
import org.LexGrid.LexBIG.DataModel.Collections.SortOptionList;
import org.LexGrid.LexBIG.DataModel.Core.ConceptReference;
import org.LexGrid.LexBIG.DataModel.Core.ResolvedConceptReference;
import org.LexGrid.LexBIG.DataModel.NCIHistory.NCIChangeEvent;
import org.LexGrid.LexBIG.Exceptions.LBException;
import org.LexGrid.LexBIG.Exceptions.LBInvocationException;
import org.LexGrid.LexBIG.Exceptions.LBParameterException;
import org.LexGrid.LexBIG.History.HistoryService;
import org.LexGrid.LexBIG.LexBIGService.CodedNodeSet;
import org.LexGrid.LexBIG.LexBIGService.LexBIGService;
import org.LexGrid.LexBIG.Utility.Constructors;
import org.LexGrid.LexBIG.Utility.ConvenienceMethods;
import org.LexGrid.LexBIG.Utility.Iterators.ResolvedConceptReferencesIterator;
import org.LexGrid.LexBIG.Utility.LBConstants.MatchAlgorithms;
import org.LexGrid.LexBIG.caCore.interfaces.LexEVSService;
import org.LexGrid.commonTypes.Property;
import org.LexGrid.concepts.Definition;
import org.LexGrid.concepts.Entity;
import org.LexGrid.concepts.Presentation;
import org.apache.log4j.Logger;


/**
 * This class compares the caDSR Concepts table to the referenced EVS Concepts. If the concept code or name is not
 * valid an appropriate message is returned. Concepts which match EVS are not reported.
 * 
 * @author Archana Sahu
 *
 */
public class caDSRConceptCleanupEVS extends AuditReport
{

    /* (non-Javadoc)
     * @see gov.nih.nci.cadsr.sentinel.tool.AuditReport#getTitle()
     */
    @Override
    public String getTitle()
    {
        return "NCI Thesaurus Concept cleanup";
    }

    /* (non-Javadoc)
     * @see gov.nih.nci.cadsr.sentinel.tool.AuditReport#getReportRows()
     */
    @Override
    public String[] getReportRows()
    {
        Vector<String> msgs = validate();
        String[] rows = new String[msgs.size()];
        for (int i = 0; i < rows.length; ++i)
        {
            rows[i] = msgs.get(i);
        }
       
        return rows;
    }

    /* (non-Javadoc)
     * @see gov.nih.nci.cadsr.sentinel.tool.AuditReport#okToDisplayCount()
     */
    @Override
    public boolean okToDisplayCount()
    {
        return true;
    }

    /* (non-Javadoc)
     * @see gov.nih.nci.cadsr.sentinel.tool.AuditReport#rightJustifyLastColumn()
     */
    @Override
    public boolean rightJustifyLastColumn()
    {
        return false;
    }

    /**
     * Parse the DBProperties and create appropriate EVSVocab objects.
     * 
     * @param props_ the property (key/value pairs)
     * @return the equivalent vocab objects
     */
    private EVSVocab[] parseProperties(DBProperty[] props_)
    {
        Vector<EVSVocab> vocabs = new Vector<EVSVocab>();
        String defProp = "";

        // Get defaults
        for (int i = 0; i < props_.length; ++i)
        {
            String[] text = props_[i]._key.split("[.]");
            if (text[2].equals("ALL"))
//            if (text != null && text.length == 2 && text[2].equals("ALL"))            
            {
                if (text[3].equals("PROPERTY") && text[4].equals("DEFINITION"))
                    defProp = props_[i]._value;
                break;
            }
        }

        // Process vocab data
        EVSVocab vocab = null;
        
        vocab = new EVSVocab();
        vocab._display = "MetaThesaurus";
        vocab._vocab = "MetaThesaurus";
        vocab._source = "NCI_META_CUI";
        vocab._source2 = "UMLS_CUI";
        vocab._ed = new MetaTh(vocab);
        vocabs.add(vocab);

        String vDisplay = null;
        String vAccess = null;
        String vName = null;
        String vDefProp = null;
        String vSearch = null;
        String vSource = null;
        String last = null;
        for (int i = 0; i < props_.length; ++i)
        {
            String[] text = props_[i]._key.split("[.]");
            if (!text[2].equals("ALL"))
//            if (text != null && text.length == 2 && text[2].equals("ALL"))            
            {
                if (last == null)
                    last = text[2];

                if (!last.equals(text[2]))
                {
                    vocab = new EVSVocab();
                    vocab._display = vDisplay;
                    vocab._vocab = vName;
                    vocab._access = vAccess;
                    vocab._preferredDefinitionProp = (vDefProp == null) ? defProp : vDefProp;
                    vocab._preferredNameProp = (vSearch == null) ? vDisplay : vSearch;
                    vocab._ed = new NonMetaTh(vocab);
                    vocab._source = vSource;
                    vocabs.add(vocab);
                    vDisplay = null;
                    vName = null;
                    vAccess = null;
                    vDefProp = null;
                    vSearch = null;
                    vSource = null;
                    last = text[2];
                }

                if (text.length == 4)
                {
                    if (text[3].equals("DISPLAY"))
                        vDisplay = props_[i]._value;
                    else if (text[3].equals("EVSNAME"))
                        vName = props_[i]._value;
                    else if (text[3].equals("ACCESSREQUIRED"))
                        vAccess = props_[i]._value;
                    else if (text[3].equals("VOCABCODETYPE"))
                        vSource = props_[i]._value;
                }
                else if (text.length == 5)
                {
                    if (text[3].equals("PROPERTY"))
                    {
                        if (text[4].equals("DEFINITION"))
                        {
                            vDefProp = props_[i]._value;
                        }
                        else if (text[4].equals("NAMESEARCH"))
                        {
                            vSearch = props_[i]._value;
                        }
                    }
                }
            }
        }

        vocab = new EVSVocab();
        vocab._display = vDisplay;
        vocab._vocab = vName;
        vocab._access = vAccess;
        vocab._preferredDefinitionProp = (vDefProp == null) ? defProp : vDefProp;
        vocab._preferredNameProp = (vSearch == null) ? vDisplay : vSearch;
        vocab._ed = new NonMetaTh(vocab);
        vocab._source = vSource;
        vocabs.add(vocab);

        EVSVocab[] rs = new EVSVocab[vocabs.size()];
        for (int i = 0; i < rs.length; ++i)
        {
            rs[i] = vocabs.get(i);
        }
        return rs;
    }
    
    private class EVSVocab
    {
        /**
         * Constructor
         */
        public EVSVocab()
        {
        }
        
        /**
         */
        public String _vocab;
        
        /**
         */
        public String _display;

        /**
         */
        public String _preferredNameProp;

        /**
         */
        public String _preferredDefinitionProp;

        /**
         */
        public String _source;

        /**
         */
        public String _source2;
        
        /**
         */
        public EVSData _ed;
        
        /**
         * 
         */
        public String _access;
    }
    
    private abstract class EVSData
    {
        /**
         * Constructor
         * 
         * @param vocab_ the vocabulary description 
         */
        public EVSData(EVSVocab vocab_)
        {
            reset();
            _vocab = vocab_;
        }
        
        /**
         * Reset the data elements to empty.
         *
         */
        public void reset()
        {
            _msg = "";
            _flag = true;
        }

        /**
         * Determine the recommended concept name when it is missing.
         * 
         * @return the recommended concept name
         */
        abstract public String recommendName();

        /**
         * Search EVS for the Concept Code.
         * 
         * @param query_ the EVSQuery defined by the caCORE API
         */
        abstract public CodedNodeSet search(LexBIGService service_);

        /**
         * Validate the Concept Code, Concept Name and Concept Definition.
         */
        abstract public void validate();

        /**
         * The messages from the validate() method.
         */
        public String _msg;

        /**
         * The recommended name for a Concept.
         */
        public String _name;

        /**
         * The caDSR Concept record to validate.
         */
        public ConceptItem _rec;

        /**
         * The validation flag, true indicates the Name does not match.
         */
        public boolean _flag;

        /**
         * The concept list returned by the caCORE API EVS Query.
         */
        public List _cons;

        /**
         * The name property list use to validate the concept name.
         */
        public EVSVocab _vocab;
    }
    
    private class MetaTh extends EVSData
    {
        /**
         * Constructor
         * 
         * @param vocab_ the EVS vocab description 
         */
        public MetaTh(EVSVocab vocab_)
        {
            super(vocab_);
        }

        @Override
        public CodedNodeSet search(LexBIGService service_)
        {
        	CodedNodeSet cns = null;
        	try {
        		CodingSchemeVersionOrTag cvt = new CodingSchemeVersionOrTag();
				cvt.setTag("PRODUCTION");
				cns = service_.getNodeSet("NCI Metathesaurus", cvt, null);
				/*cns = cns.restrictToMatchingProperties(
								Constructors.createLocalNameList("conceptCode"), 
								null, 
								_rec._preferredName, 
								MatchAlgorithms.exactMatch.name(), 
								null
							);*/
				ConceptReferenceList crefs = ConvenienceMethods.createConceptReferenceList(new String[]{_rec._preferredName},"NCI Metathesaurus");
                cns.restrictToCodes(crefs);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return cns;
        }

        @Override
        public String recommendName()
        {
        	EVSConcept obj = (EVSConcept) _cons.get(0);
        	if (obj != null) {
        		return obj.preferredName;
        	}
            return "";
        }

        @Override
        public void validate()
        {
        	// The search returns a list of results so process all.
            for (int i = 0; i < _cons.size(); ++i)
            {
                // The objects are Meta Thesaurus
            	EVSConcept temp = (EVSConcept) _cons.get(i);
                
                // Check the default name.
                if (_rec._longName.compareToIgnoreCase(temp.preferredName) == 0)
                {
                    _flag = false;
                    break;
                }
                
                // Check the synonyms if the default name didn't match.
                List value = temp.synonyms;
                if (value.indexOf(_rec._longName) > -1)
                {
                    _flag = false;
                    break;
                }
            }
            // We didn't find a name so recommend one.
            if (_flag)
                _name = recommendName();

            // Must have a definition source to proceed.
            if (_rec._definitionSource == null || _rec._definitionSource.length() == 0)
            {
                boolean defFlag = false;
                for (int i = 0; i < _cons.size(); ++i)
                {
                    // Need definitions.
                    EVSConcept temp = (EVSConcept) _cons.get(i);
                    Definition[] defs = temp.definitions;
                    if (defs != null && defs.length > 0)
                    {
                        defFlag = true;
                        break;
                    }
                }
            }
            else
            {
                // Process the search results again only this time for the definition.
                boolean srcFlag = true;
                boolean defFlag = true;
                boolean defCol = true;
                String defSource = null;
                int full = 0;
                for (int i = 0; i < _cons.size() && full < 3 && srcFlag && defFlag; ++i)
                {
                    // Need definitions.
                    EVSConcept temp = (EVSConcept) _cons.get(i);
                    Definition[] defs = temp.definitions;
                    if (defs != null && defs.length > 0)
                    {
                        defCol = false;

                        // Check the definition source and definition text.
                        for (Definition def : defs)
                        {
                            full = 0;
                            org.LexGrid.commonTypes.Source[] defsors = def.getSource();
                            for (org.LexGrid.commonTypes.Source defsor: defsors) {
                            	if (defsor != null && defsor.getContent().equals(_rec._definitionSource))
                                {
                                    srcFlag = false;
                                    full += 1;
                                }
                            	
                            	if (def.getValue().getContent().equals(_rec._preferredDefinition))
                                {
                                    if (defsor != null)
                                        defSource = defsor.getContent();
                                    defFlag = false;
                                    full += 2;
                                }
                                if (full == 3)
                                    break;
                            }
                        }
                    }
                }
                
                // Did we find everything?
                if (full == 3)
                    return;
            }
        }
    }
    
    private class NonMetaTh extends EVSData
    {
        /**
         * Constructor
         * 
         * @param vocab_ the EVS vocab description 
         */
        public NonMetaTh(EVSVocab vocab_)
        {
            super(vocab_);
        }

        @Override
        public CodedNodeSet search(LexBIGService service_)
        {
        	CodedNodeSet cns = null;
            try {
            	CodingSchemeVersionOrTag cvt = new CodingSchemeVersionOrTag();
				cvt.setTag("PRODUCTION");
				cns = service_.getNodeSet(_rec._origin, cvt, null);
				/*cns = cns.restrictToMatchingProperties(
								Constructors.createLocalNameList("conceptCode"), 
								null, 
								_rec._preferredName, 
								MatchAlgorithms.exactMatch.name(), 
								null
							);*/
				ConceptReferenceList crefs = ConvenienceMethods.createConceptReferenceList(new String[]{_rec._preferredName},_vocab._vocab);
				cns.restrictToCodes(crefs);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return cns;
        }

        @Override
        public String recommendName()
        {
            EVSConcept obj = (EVSConcept) _cons.get(0);

            if (obj.preferredName != null)
                return obj.preferredName;
            
            return "no recommendations available";
        }

        @Override
        public void validate()
        {
            try
            {
                for (int i = 0; i < _cons.size(); ++i)
                {
                    List collection = null;
                    EVSConcept temp = (EVSConcept) _cons.get(i);
                    if (temp.preferredName.compareToIgnoreCase(_rec._longName) == 0)
                    {
                        _flag = false;
                        break;
                    }
                    
                    collection = temp.properties;
                    for (int n = 0; n < collection.size(); ++n)
                    {
                        org.LexGrid.commonTypes.Property prop = (org.LexGrid.commonTypes.Property) collection.get(n);
                        if (_rec._longName.compareToIgnoreCase(prop.getValue().getContent()) == 0)
                        {
                            // The collection.contains() test above doesn't always catch matching property names because of case.
                            if (preferredName.equals(prop.getPropertyName()) || _vocab._preferredNameProp.compareToIgnoreCase(prop.getPropertyName()) == 0)
                            {
                                _flag = false;
                                break;
                            }

                            _flag = false;
                            break;
                        }
                    }
                }
                if (_flag)
                    _name = recommendName();
            }
            catch (ClassCastException ex)
            {
                _flag = false;
                _logger.warn(ex.toString());
                _logger.debug("Error in Concept cleanup validate - "+ex.getStackTrace());                
                // Can't continue if this exception occurs.
                return;
            }
        }
    }
    
    private class EVSConcept  {
    	  public String preferredName;
    	  public String code;
    	  public List synonyms;
    	  public String status;
    	  public List<org.LexGrid.commonTypes.Property> properties;
    	  public Definition[] definitions;
    }
    
    /**
     * Validate the caDSR Concepts against EVS
     * 
     * @return exception, error and information messages
     */
    private Vector<String> validate()
    {
        // Seed message list with column headings.
        Vector<String> msgs = new Vector<String>();
        msgs.add(formatTitleMsg());

        DBAlertOracleMetadata meta = new DBAlertOracleMetadata();
        Vector<ConceptItem> concepts = meta.selectEVSConcepts("NCI Thesaurus", _db.getConnection());
        EVSVocab[] vocabs = parseProperties(_db.selectEVSVocabs());
        
        //Vector<ConceptItem> concepts = selectConcepts(); //Used Just for TESTING
        //System.out.println("No of Concepts (validate): " + concepts.size());
        
        // Get the EVS URL and establish the application service.
        String evsURL = _db.selectEvsUrl();
        _logger.debug("caDSRConceptCleanupEVS API URL " + evsURL);
        
        int numConceptsUpdate = meta.getMaxNumMsgs(_maxMsgs, concepts.size()); //check property
        //System.out.println("Maximum limit on concepts to update through metadata clenup: " + numConceptsUpdate);
        
        //LexEVSService service;
        LexBIGService service;
        try
        {
        	service = (LexBIGService) ApplicationServiceProvider.getApplicationServiceFromUrl(evsURL, "EvsServiceInfo");
			
        }
        catch (Exception ex)
        {
        	_logger.debug(" caDSRConceptCleanupEVS validate - " + ex.getStackTrace());
            StackTraceElement[] list = ex.getStackTrace();
            for (int i = 0; i < list.length; ++i)
                msgs.add(list[i].toString());
            return msgs;
        }
        
        // Check each concept with EVS.
        String name = null;
        EVSConcept evsconcept = null;
        int count = 0;
        for (ConceptItem rec : concepts)
        {
            // Reset loop variables.
            evsconcept = null;
            
            //if (rec._preferredDefinition.toLowerCase().startsWith("no value exists"))
            //    rec._preferredDefinition = "";

            ++count;

            EVSVocab vocab = null;
            while (true)
            {
                // Missing EVS Source
                if (rec._evsSource == null || rec._evsSource.length() == 0)
                {
                    break;
                }

                // Determine the desired vocabulary using the EVS source value in caDSR.
                for (int i = 0; i < vocabs.length; ++i)
                {
                    if (rec._evsSource.equals(vocabs[i]._source))
                    {
                        vocab = vocabs[i];
                        break;
                    }
                }
                
                if (vocab == null)
                {
                    if (rec._evsSource.equals(vocabs[0]._source2))
                        vocab = vocabs[0];
                }
                
                // Unknown EVS Source {0}
                if (vocab == null)
                {
                    break;
                }
                
                // Missing Concept Code
                if (rec._preferredName== null || rec._preferredName.length() == 0)
                {
                    break;
                }

                EVSData ed = vocab._ed;
                ed.reset();
                ed._rec = rec;
                //System.out.println(rec._preferredName + " : " + vocab._display + " : " + vocab._source + " : " + vocab._vocab);
                
                CodedNodeSet cns = ed.search(service);
                
                try
                {
                    // Get the attributes for the concept code. 
                    ed._cons = resolveNodeSet(cns, true);
                    if (ed._cons != null && ed._cons.size() > 0)
                    	evsconcept = (EVSConcept) ed._cons.get(0);
                }
                catch (ApplicationException ex)
                {
                    // Invalid concept code
                    if (ex.toString().indexOf("Invalid concept code") > -1)
                    {
                        _logger.warn(ex.toString());
                        break;
                    }
                    
                    // Invalid concept ID
                    else if (ex.toString().indexOf("Invalid conceptID") > -1)
                    {
                        _logger.warn(ex.toString());
                        break;
                    }
                    
                    // An unexpected exception occurred so record it and terminate the validation.
                    else
                    {
                        break;
                    }
                }
                catch (Exception ex)
                {
                   ex.printStackTrace();
                   msgs.add(ex.toString());
                   StackTraceElement[] list = ex.getStackTrace();
                   for (int i = 0; i < list.length; ++i)
                     msgs.add(list[i].toString());
                   return msgs;
                }

                // Failed to retrieve EVS concept
                if (ed._cons.size() == 0)
                {
                    _logger.debug("Failed to retrieve concept");                	
                    break;
                }
                 
                // Missing Concept Long Name, recommend using [{0}]
                if (rec._longName == null || rec._longName.length() == 0)
                {
                    break;
                }

                // Assume we will not match the concept name.
                boolean flag = true;
                name = null;

                // Validate data.
                ed.validate();
                flag = ed._flag;
                name = ed._name;
                
                break;
            }
            
            //need to compare 4 fields with EVS here and display message accordingly
            String cleanup_msg = compareconceptWithEVS(rec, evsconcept, meta, service);
            if (cleanup_msg.length() > 0) {
            	msgs.add(cleanup_msg);
	            if (msgs.size() >= numConceptsUpdate) //_maxMsgs)
	            {
	            	msgs.add(formatMaxMsg(numConceptsUpdate));
	            	break;
	            }
            }
        }

        // Return all the messages, the validation processing is complete.
        return msgs;
    }
    
    public List<EVSConcept> resolveNodeSet(CodedNodeSet cns, boolean includeRetiredConcepts) throws Exception {
		
		if (!includeRetiredConcepts) {
			cns.restrictToStatus(CodedNodeSet.ActiveOption.ACTIVE_ONLY, null);
		}
		CodedNodeSet.PropertyType propTypes[] = new CodedNodeSet.PropertyType[2];
		propTypes[0] = CodedNodeSet.PropertyType.PRESENTATION;
		propTypes[1] = CodedNodeSet.PropertyType.DEFINITION;
		
		SortOptionList sortCriteria = Constructors.createSortOptionList(new String[]{"matchToQuery"});
		
		ResolvedConceptReferencesIterator results = cns.resolve(sortCriteria, null,new LocalNameList(), propTypes, true);
		
		return getEVSConcepts(results);
	}
    
    private List<EVSConcept> getEVSConcepts(ResolvedConceptReferencesIterator rcRefIter) throws Exception {
    	List<EVSConcept> evsConcepts = new ArrayList<EVSConcept>();
    	if (rcRefIter != null) {
    		while (rcRefIter.hasNext()) {
    			ResolvedConceptReference next = rcRefIter.next();
    			if(next != null) {
    				evsConcepts.add(getEVSConcept(next));
    			}
    		}
    	}
    	return evsConcepts;
    }
    
    private EVSConcept getEVSConcept(ResolvedConceptReference rcRef) {
		EVSConcept evsConcept = new EVSConcept();
		evsConcept.code = rcRef.getCode();
		
		Entity entity = rcRef.getEntity();
		evsConcept.preferredName = rcRef.getEntityDescription().getContent();
		evsConcept.definitions = entity.getDefinition();
		
		if(entity.getIsActive())
			evsConcept.status = "ACTIVE";
		else
			evsConcept.status = "RETIRED";
		
		setPropsAndSyns(evsConcept, entity);
		
		return evsConcept;
	}
	
	private void setPropsAndSyns(EVSConcept evsConcept, Entity entity) {
		List<Property> properties = new ArrayList<Property>();
		List<String> synonyms = new ArrayList<String>();
		
		if (entity != null) {
			org.LexGrid.commonTypes.Property[] entityProps = entity.getAllProperties();
			for (org.LexGrid.commonTypes.Property entityProp: entityProps) {
				
				if (entityProp instanceof Presentation) {
					properties.add(entityProp);
				}
				else {
					String propName = entityProp.getPropertyName();
					String propValue = entityProp.getValue().getContent();
					
					if (propName.equalsIgnoreCase("FULL_SYN") || propName.equalsIgnoreCase("Synonym")) {
						synonyms.add(propValue);
					}
				}
			}
		}
		
		evsConcept.properties = properties;
		evsConcept.synonyms = synonyms;
	}
    
    private static String formatMsg(String msg_, String ... subs_)
    {
        String text = msg_;
        for (int i = 0; i < subs_.length; ++i)
        {
            String temp = "{" + i +  "}";
            text = text.replace(temp, subs_[i]);
        }
        return "\n" + text;
    }
    
    private String formatMsg(ConceptItem rec_, EVSVocab vocab_, String msg_)
    {
        return rec_._longName + AuditReport._ColSeparator
        + rec_._publicID + AuditReport._ColSeparator 
        + rec_._version + AuditReport._ColSeparator
        + ((vocab_ == null) ? "" : vocab_._display) + AuditReport._ColSeparator 
        + rec_._preferredName + AuditReport._ColSeparator
            + ((msg_.charAt(0) == '\n') ? msg_.substring(1) : msg_);
    }
    
    private String formatCeanupMsg(ConceptItem rec_, ConceptItem evsConcept, ConceptItem updatedrec, String msg_)
    {
    	return  rec_._preferredName + AuditReport._ColSeparator 
    			+ "Workflow Status: " + rec_._workflow_status 
    			+ "<br>Long Name: " + rec_._longName  
    			+ "<br>Definition Source: " + rec_._definitionSource
    			+ "<br>Preferred Definition: " + rec_._preferredDefinition
    			+ AuditReport._ColSeparator 
    			+ "Retirement Status: " + evsConcept._workflow_status 
    			+ "<br>Preferred Name: " + evsConcept._longName  
    			+ "<br>Definition Source: " + evsConcept._definitionSource
    			+ "<br>EVS Definition: " + evsConcept._preferredDefinition
    			+ AuditReport._ColSeparator 
    			+ "Workflow Status: " + updatedrec._workflow_status 
    			+ "<br>Long Name: " + updatedrec._longName  
    			+ "<br>Definition Source: " + updatedrec._definitionSource
    			+ "<br>Preferred Definition: " + updatedrec._preferredDefinition
    			+ AuditReport._ColSeparator 
    	        + ((msg_.charAt(0) == '\n') ? msg_.substring(1) : msg_);
    	        
    }
    
    private String formatMaxMsg(int limit)
    {
        return "*** Maximum Messages ***" + AuditReport._ColSeparator
        + "***" + AuditReport._ColSeparator
        + "***" + AuditReport._ColSeparator
        + "***" + AuditReport._ColSeparator
        + "***" + AuditReport._ColSeparator
        + "The Message maximum limit [" + limit + "] has been reached, report truncated.";
    }
    
    private String formatTitleMsg()
    {
    	return  "Concept Code" + AuditReport._ColSeparator
    	        + "caDSR Details (Before)" + AuditReport._ColSeparator
    	        + "EVS Details" + AuditReport._ColSeparator
    	        + "caDSR Details (After)" + AuditReport._ColSeparator
    	        + "Message";
    }
    
    private static final String preferredName = "Preferred_Name";
    private static final int _maxMsgs = 200;
    private static final Logger _logger = Logger.getLogger(caDSRConceptCleanupEVS.class.getName());
    
	public String compareconceptWithEVS(ConceptItem rec, EVSConcept evsconcept, DBAlertOracleMetadata meta, LexBIGService _service) {
		_logger.debug(" Begin - compareconceptWithEVS ");
	    boolean neededDisplay = false;
	    boolean updateLongName = false, updateDefn = false, updateDefnSrc = false, updateStatus = false;
	    String evsDefn = "";
    	String evsDefnSrc = "";
	    
    	//System.out.println("=======" + rec._preferredName + "=======");
    	
	    if (evsconcept != null) {
	    	Definition[] defs = evsconcept.definitions;
	    	_logger.debug("No. of EVS definitions: " + defs.length);
	    	
	        if (rec._longName != null && !rec._longName.isEmpty() && !rec._longName.equalsIgnoreCase(evsconcept.preferredName)) {
	        	neededDisplay = true;
	        	updateLongName = true;
	        }	
	      
	        if (rec._definitionSource != null && !rec._definitionSource.isEmpty() ) { //There is some definition src
	        	evsDefn = "";
	        	evsDefnSrc = "";
	        	boolean srcMatches = false;
	        	//find matching definition for this definition source in EVS
                for (Definition def : defs) {
                	try {
	                	evsDefn = def.getValue().getContent();
	                    //Each definition in NCIt will only have once source.
	                	_logger.debug("evs defn: "+evsDefn);
	                	org.LexGrid.commonTypes.Source[] sources = def.getSource();
	                	_logger.debug("defn source array length: "+sources.length);
	                	if (sources.length == 0) {
	                		_logger.info("Source missing in EVS for evs defn: "+evsDefn);
	                	} else {
	                    	org.LexGrid.commonTypes.Source defSource = sources[0];
	                    	evsDefnSrc = defSource.getContent();
	                        _logger.debug("EVS Definition and source (with caDSR having source):" + evsDefn + ": "+ evsDefnSrc);
	                        if (evsDefnSrc.equals(rec._definitionSource)) {
	        	           		srcMatches = true;
	        	           		break;
	        	           	}
	                	}
		        	} catch (Exception ex) {
		        		_logger.error("Error in getting the source for evs definition"+evsDefn);
		        		ex.printStackTrace();
		        	}
                }
                if (srcMatches) { //found matching definition source in EVS
                	_logger.debug("Matched Definition and source :" + evsDefn + ": "+ evsDefnSrc);
                	//if preferred definition doesn't match with EVS, update definition
                	if (!rec._preferredDefinition.equalsIgnoreCase(evsDefn) ) { 
	            		neededDisplay = true;
	            		updateDefn = true;
	            	} 
                }
	        } else { //no definition src for this concept in caDSR
	        	//Find the NCI definition and source (NCI) and update both in caDSR
	        	evsDefn = "";
	        	evsDefnSrc = "";
	        	boolean isNCISrc = false;
	        	for (Definition def : defs) {
		        	try {
		        		evsDefn = def.getValue().getContent();
		        		_logger.debug("evs defn: "+evsDefn);	        		
	                    //Each definition in NCIt will only have once source.
	                	org.LexGrid.commonTypes.Source[] sources = def.getSource();
	                	_logger.debug("defn source array length: "+sources.length);
	                	if (sources.length == 0) {
	                		_logger.info("Source missing in EVS for evs defn: "+evsDefn);
	                	}  else {
	                		org.LexGrid.commonTypes.Source defSource = sources[0];
		                	evsDefnSrc = defSource.getContent();
		                	_logger.debug("EVS Definition and source (with caDSR having NO source):" + evsDefn + ": "+ evsDefnSrc);
		                    if (evsDefnSrc.equals("NCI")) {
		                    	isNCISrc = true;
		    	           		break;
		    	           	}	                		
	                	}
		        	} catch (Exception ex) {
		        		_logger.error("Error in getting the source for evs definition"+evsDefn);
		        		ex.printStackTrace();
		        	}
	        	}
	        	if (isNCISrc) { //found NCI definition in EVS, update both definition and source in caDSR
	        		_logger.debug("NCI Definition and source :" + evsDefn + ": "+ evsDefnSrc);
                	neededDisplay = true;
	            	updateDefn = true;
	            	updateDefnSrc = true;
                }
	        }
	        
	        if (evsconcept.status.equalsIgnoreCase("RETIRED")) {
	        	neededDisplay = true;
	        	updateStatus = true;
	        }      
	    } 
	    
	    //System.out.println("ALL BOOLEANS: " + neededDisplay +  " : " + updateLongName + " : " + updateDefn + " : " + updateStatus + "  : " + updateDefnSrc);
	    
	    String cleanup_msg = "";
	    // Continue with the next concept if there is no change between EVS and caDSR concepts
	    if (neededDisplay && (updateLongName || updateDefn ||  updateStatus || updateDefnSrc))
	    {
	    	if (updateDefn)
	    		cleanup_msg = "Conflict with caDSR and EVS Definition";
	    	if (updateDefnSrc)
	    		cleanup_msg += ", Conflict with caDSR and EVS Definition Source";
	    	if (updateLongName)
	    		cleanup_msg += "\nConflict with caDSR and EVS Concept Name";
	    	if (updateStatus)
	    		cleanup_msg += "\nConflict with caDSR and EVS Retirement Status";
	    	
	    	_logger.debug("DSR Definition:" + rec._preferredDefinition + ": Definition Source:" + rec._definitionSource+ ": Long Name:" + rec._longName +  " : Workflow Status : RELEASED");
	    	_logger.debug("EVS Definition:" + evsDefn + ": Definition Source:" + evsDefnSrc + ": Preferred Name:" + evsconcept.preferredName + " : Status : " + evsconcept.status);
	    	_logger.debug("=======" + cleanup_msg + "=======");
	    	
	    	
	    	String[] ret_info = null;
	    	ConceptItem evsrec = new ConceptItem();
	        evsrec._preferredName = evsconcept.code;
	        evsrec._longName = evsconcept.preferredName;
	        evsrec._preferredDefinition = evsDefn;
	        evsrec._definitionSource = evsDefnSrc;
	        if (evsconcept.status.equalsIgnoreCase("RETIRED"))  {//if the concept has been retired in EVS, set the status to 'RETIRED ARCHIVED' in caDSR
	        	evsrec._workflow_status = "RETIRED ARCHIVED";
		        //get the retirement date and replacement concept here
		        ret_info = getRetirementDate(rec._preferredName, rec._origin, _service);
	        }
	        else evsrec._workflow_status = rec._workflow_status;
	        
	        ConceptItem updatedrec = new ConceptItem();
	        boolean updated = false;
	        try
	        {
	        	updated = meta.updateCADSRConcept(rec, evsrec, updateLongName, updateDefn, updateStatus, updateDefnSrc, ret_info, _db.getConnection()); 
	        	if (updated) {
	        		cleanup_msg += " - caDSR database updated successfully";
	        		updatedrec = meta.findConceptDetails(rec._preferredName, rec._origin, _db.getConnection());
	        	}
	        	else {
	        		cleanup_msg += " - caDSR database update failed"; 
	        	}
	        }
	        catch (Exception ex)
	        {
	        	_logger.debug("Exception in compareconceptWithEVS");
	        	ex.printStackTrace();
	        	cleanup_msg += ex.toString();
	        	_logger.error(ex.toString());
	        }
	        
	        //System.out.println("Update or not: " + updated);
	        cleanup_msg = formatCeanupMsg(rec, evsrec, updatedrec, cleanup_msg);
	    }
	    _logger.debug(" End - compareconceptWithEVS - "+cleanup_msg);	    
        return cleanup_msg;
    }
	 
	public static Vector<ConceptItem> selectConcepts(){
		Vector<ConceptItem> conItems = new Vector<ConceptItem>();
	
		//conItems.add(parseConcept("CON_IDSEQ,CONTE_IDSEQ,CON_ID,VERSION,EVS_SOURCE,PREFERRED_NAME,LONG_NAME,DEFINITION_SOURCE,ORIGIN,ASL_NAME,PREFERRED_DEFINITION"));
			
		//Diff in definition -- insert a row in definition table -- TESTED just updating concept - works fine
		//conItems.add(parseConcept("F37D0428-B53C-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202277:1:NCI_CONCEPT_CODE:C20754:ACRBP Gene:NCI:NCI Thesaurus:RELEASED:The protein encoded by this gene is similar to proacrosin binding protein sp32 precursor found in mouse, guinea pig, and pig. This protein is located in the sperm acrosome and is thought to function as a binding protein to proacrosin for packaging and condensation of the acrosin zymogen in the acrosomal matrix. This protein is a member of the cancer/testis family of antigens and it is found to be immunogenic. In normal tissues, this mRNA is expressed only in testis, whereas it is detected in a range of different tumor types such as bladder, breast, lung, liver, and colon. (LocusLink)"));

		//Diff in long name -- insert a row in designation table -- WORKED long name inserted into DESIGNATIONS table
		//conItems.add(parseConcept("F37D0428-B538-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202276:1:NCI_CONCEPT_CODE:C20985:Ablation:NCI:NCI Thesaurus:RELEASED:Removal, separation, detachment, extirpation, or eradication of a body part, pathway, or function by surgery, chemical destruction, morbid process, or noxious substance."));
		
		//Diff in long name -- insert a row in designation table -- WORKED long name inserted into DESIGNATIONS table
		//conItems.add(parseConcept("F37D0428-B5C4-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202311:1:NCI_CONCEPT_CODE:C241:Analgesics:NCI-GLOSS:NCI Thesaurus:RELEASED:Drugs that reduce pain. These drugs include aspirin, acetaminophen, and ibuprofen."));

		//Diff in definition -- insert a row in definition table -- WORKED definition inserted into DEFINITIONS table
		//conItems.add(parseConcept("F37D0428-B5D0-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202314:1:NCI_CONCEPT_CODE:C3167:Acute Lymphoblastic Leukemia:NCI:NCI Thesaurus:RELEASED:Leukemia with an acute onset, characterized by the presence of lymphoblasts in the bone marrow and the peripheral blood. It includes the acute B lymphoblastic leukemia and acute T lymphoblastic leukemia."));
		
		//Diff in both definition and definition source -- insert a row in definition table--database update failed because of single quote in definition -- WORKED fine
		//conItems.add(parseConcept("F37D0428-B610-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202330:1:NCI_CONCEPT_CODE:C29878:Family Cancer History::NCI Thesaurus:RELEASED:A chronological record of cancer and cancer-related events and problems of family members and ancestors."));
		
		//Diff in both definition and definition source  - worked fine
		//conItems.add(parseConcept("F37D0428-B60C-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202329:1:NCI_CONCEPT_CODE:C28745:Subsegmental Lymph Node::NCI Thesaurus:RELEASED:The lymph nodes around the subsegmental bronchi."));

		//Diff in both definition and long name -- insert a row in both designation and definition table - worked fine
		//conItems.add(parseConcept("F37D0428-B5EC-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202321:1:NCI_CONCEPT_CODE:C900:ATRA:NCI:NCI Thesaurus:RELEASED:A naturally-occurring acid of retinol.  Vitamin A acid binds to and activates retinoic acid receptors (RARs), thereby inducing changes in gene expression that lead to cell differentiation, decreased cell proliferation, and inhibition of carcinogenesis.  This agent also inhibits telomerase, resulting in telomere shortening and eventual apoptosis of some cancer cell types.  The oral form of vitamin A acid has teratogenic and embryotoxic properties.(NCI04)"));
		
		//definition with No Value Exists and definition source is NCI == Tested == no updates = #of defn = 0 in evs 
		//conItems.add(parseConcept("6FB37056-1FF8-9C64-E040-BB89AD431A26:D9344734-8CAF-4378-E034-0003BA12F5E7:2922847:1:NCI_CONCEPT_CODE:C56075:Face Pain Adverse Event:NCI:NCI Thesaurus:RELEASED: ")); //No Value Exists

		//definition with "No Value Exists" and definition source is null == Tested == sent "" to definitions table
		//conItems.add(parseConcept("C6EA955C-6A0F-D52A-E040-BB89AD435890:D9344734-8CAF-4378-E034-0003BA12F5E7:3553583:1:NCI_CONCEPT_CODE:C97273:Afatinib Dimaleate::NCI Thesaurus:RELEASED: ")); //No Value Exists

		//definition with "No Value Exists" and definition source is null 
		//conItems.add(parseConcept("3E5C4DDC-D3CC-3094-E044-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2693379:1:NCI_CONCEPT_CODE:C39271:West Nile Virus Pathway::NCI Thesaurus:RELEASED::")); //No Value Exists

		//Diff in both definition and long name -- insert a row in both designation and definition table -- Tested worked fine -- 
		//conItems.add(parseConcept("F37D0428-BBD4-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202699:1:NCI_CONCEPT_CODE:C15195:Brachytherapy:NCI-GLOSS:NCI Thesaurus:RELEASED:(ray-dee-AY-shun) A procedure in which radioactive material sealed in needles, seeds, wires, or catheters is placed directly into or near a tumor. Also called brachytherapy, internal radiation, or interstitial radiation."));

		//Diff in both definition and long name -- insert a row in both designation and definition table -- Tested ---
		//conItems.add(parseConcept("F37D0428-B5CC-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202313:1:NCI_CONCEPT_CODE:C225:Alpha Interferon:NCI:NCI Thesaurus:RELEASED:A class of naturally-isolated or recombinant therapeutic peptides used as antiviral and anti-tumour agents.  Alpha interferons are cytokines produced by nucleated cells (predominantly natural killer (NK) leukocytes) upon exposure to live or inactivated virus, double-stranded RNA or bacterial products.  These agents bind to specific cell-surface receptors, resulting in the transcription and translation of genes containing an interferon-specific response element.  The proteins so produced mediate many complex effects, including antiviral effects (viral protein synthesis), antiproliferative effects (cellular growth inhibition and alteration of cellular differentiation), anticancer effects (interference with oncogene expression), and immune-modulating effects (natural killer cell activation, alteration of cell surface antigen expression, and augmentation of lymphocyte and macrophage cytotoxicity). (NCI04)"));
				
		//Diff in both definition and definition source -- insert a row in definition table
		//conItems.add(parseConcept("F37D0428-B5B8-6787-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2202308:1:NCI_CONCEPT_CODE:C16342:Biomarker::NCI Thesaurus:RELEASED:Measurable and quantifiable biological parameters (e.g., specific enzyme concentration, specific hormone concentration, specific gene phenotype distribution in a population, presence of biological substances) which serve as indices for health- and physiology-related assessments, such as disease risk, psychiatric disorders, environmental exposure and its effects, disease diagnosis, metabolic processes, substance abuse, pregnancy, cell line development, epidemiologic studies, etc"));

		//EVS Defn: blank or null; both definition and long name need modification
		//conItems.add(parseConcept("F37D0428-B67C-6787-E034-0003BA3F9857:2202357:D9344734-8CAF-4378-E034-0003BA12F5E7:1:NCI_CONCEPT_CODE:C15393:Limb Perfusion:NCI-GLOSS:NCI Thesaurus:RELEASED:(per-FYOO-zhun) A technique that may be used to deliver anticancer drugs directly to an arm or leg. The flow of blood to and from the limb is temporarily stopped with a tourniquet, and anticancer drugs are put directly into the blood of the limb. This allows the person to receive a high dose of drugs in the area where the cancer occurred.  Also called isolated limb perfusion."));
		
		//EVS Retirement status changed -- Tested
		//conItems.add(parseConcept("290D9A3A-4378-66FC-E044-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2594262:1:NCI_CONCEPT_CODE:C28365:Irradiated:NCI-GLOSS:NCI Thesaurus:RELEASED:Treated with radiation"));
		conItems.add(parseConcept("F62111B6-56C9-4D8B-E034-0003BA3F9857:D9344734-8CAF-4378-E034-0003BA12F5E7:2222832:1:NCI_CONCEPT_CODE:C43713:Synonym:NCI:NCI Thesaurus:RELEASED:Synonyms are ways of referring to a concept that are valid alternatives to the preferred name that NCI uses to refer to the concept."));

		return conItems;
	}
	 
	private static ConceptItem parseConcept(String conceptText){

		String[] conceptArray = conceptText.split(":");
		//System.out.println("#of attributes for this concept: " + conceptArray.length);
		//System.out.println(conceptArray[0] + "  " + conceptArray[1] + "  " + conceptArray[4] + " " + conceptArray[5]+ "  " + conceptArray[6] + "  " + conceptArray[7] + "  " + conceptArray[10]);
		
		ConceptItem rec = new ConceptItem();
		rec._idseq = conceptArray[0];
		rec._conteidseq = conceptArray[1];
		rec._publicID = conceptArray[2];
		rec._version = conceptArray[3];
		rec._evsSource = conceptArray[4];
		rec._preferredName = conceptArray[5];
		rec._longName = conceptArray[6];
		rec._definitionSource = conceptArray[7];
		rec._origin = conceptArray[8];
		rec._workflow_status = conceptArray[9];
		rec._preferredDefinition = conceptArray[10];
		
		return rec;
	}
	
	private String[] getRetirementDate(String conCode, String vocabName, LexBIGService lbSvc){
		
		String[] ret_info = new String[2];
		
		try {
			HistoryService hs = lbSvc.getHistoryService(vocabName);

			Date startDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse("2005-01-01");
			Date endDate = new Date();
			NCIChangeEventList cel = hs.getEditActionList(Constructors.createConceptReference(conCode, null),startDate,endDate);
			cel.getEntryCount();
			Iterator<NCIChangeEvent> celIter = (Iterator<NCIChangeEvent>) cel.iterateEntry();
			while (celIter.hasNext()){
				NCIChangeEvent ce = celIter.next();
				if (ce.getEditaction().name().equals("RETIRE")){
					ret_info[0] = ce.getEditDate().toLocaleString();
					ret_info[1] = ce.getReferencecode();
					//System.out.println("Retirement date "+ ret_info[0]);
					//System.out.println("Reference concept " + ret_info[1]);
				}
			}
		} catch (Exception ex) {
			_logger.error("getRetirementDate for " + conCode + "  throws Exception = " + ex.toString());
			//System.out.println("getRetirementDate for " + conCode + "  throws Exception = " + ex);
		}
		
		return ret_info;
    }
			
}
