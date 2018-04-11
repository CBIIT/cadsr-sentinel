/*L
 * Copyright ScenPro Inc, SAIC-F
 *
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See http://ncip.github.com/cadsr-sentinal/LICENSE.txt for details.
 */

// Copyright (c) 2008 ScenPro, Inc.

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/daily/CleanStrings.java,v 1.6 2008-11-18 15:16:58 hebell Exp $
// $Name: not supported by cvs2svn $

package gov.nih.nci.cadsr.sentinel.daily;

import gov.nih.nci.cadsr.sentinel.tool.Constants;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import oracle.jdbc.pool.OracleDataSource;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

/**
 * This class is responsible for cleaning the corrupted special characters in the database.  
 * Corruption can happen when pasting values into fields or inserting values programmatically
 * into the database. Because the presence of the character may or may not be visible depending on the
 * font it is changed to a simple space, ASCII 32.
 * 
 * The CR, LF and TAB are valid and not translated by this process.
 * 
 *
 */
public class CleanSpecialCharacters
{
    private static Logger _logger = Logger.getLogger(CleanSpecialCharacters.class);
    private String _dsurl;
    private String _user;
    private String _pswd;
    private Properties _propList;
    private Connection _conn;
    private DoWork _action;
    
    private static final String _propTable = "table.";
    private static final String _propAction = "action";
    private static final String _propUpdate = "update";
    private static final String _specialChar = "C2A0";

    private static final String _pvidseq = "PV_IDSEQ = '4C45F711-8657-7A81-E053-246C850A9856' and ";
    private static final String _rdidseq = "RD_IDSEQ='34B83083-BDF2-4398-E050-BB89AD431614' and ";
    
    private static final String _sqlUpdate = "update $table$ "
        + "set $sets$ "
        + "where "
        + "$rowLimit$ "
        + "$wheres$";
    
    
    private static final String _sqlSet = "$col$ = substr($col$, 1, length($col$) -1)";//"$col$ = REGEXP_REPLACE($col$, '" + _sqlRegexp + "', chr(32))";
    private static final String _sqlWhere = "instr ($col$, UTL_RAW.CAST_TO_VARCHAR2('"+_specialChar+"'), 1) = length($col$) ";//"REGEXP_LIKE($col$, '" + _sqlRegexp + "')";
    
    /**
     * @param args_
     */
    public static void main(String[] args_)
    {
        if (args_.length != 2)
        {
            System.err.println(CleanSpecialCharacters.class.getName() + " log4j.xml config.xml");
            return;
        }

        DOMConfigurator.configure(args_[0]);
        
        CleanSpecialCharacters cs = new CleanSpecialCharacters();
        
        try
        {
            _logger.info("CleanSpecialCharacters begins");
            _logger.info("Template: "+args_[1]);   
            cs.doClean(args_[1]);
        }
        catch (Exception ex)
        {
            _logger.error(ex.toString(), ex);
        }
    }
    
    /**
     * @author lhebel
     *
     */
    public class DoWork
    {
        DoWork()
        {
        }
        
        /**
         * @param list_ 
         * @param Prop_ 
         * @throws Exception 
         */
        public void apply(String[] list_, String Prop_) throws Exception
        {
        }
    }
    
    /**
     * @author lhebel
     *
     */
    public class DoUpdate extends DoWork
    {

        /* (non-Javadoc)
         * @see gov.nih.nci.cadsr.sentinel.daily.CleanSpecialCharacters.DoWork#apply(java.lang.String, java.lang.String)
         */
        @Override
        public void apply(String[] list_, String prop_) throws Exception
        {
            // Build the SQL UPDATE
            final String comma = ", ";
            final String or = " OR ";
            String sets = "";
            String wheres = "";

            for (int cnt = 1; cnt < list_.length; ++cnt)
            {
                sets += comma + _sqlSet.replace("$col$", list_[cnt]);
                wheres += or + _sqlWhere.replace("$col$", list_[cnt]);
            }
            String sql = _sqlUpdate.replace("$table$", list_[0]);
            sql = sql.replace("$sets$", sets.substring(comma.length()));
            sql = sql.replace("$wheres$", wheres.substring(or.length()));
            if (list_[0].equals("sbr.PERMISSIBLE_VALUES_VIEW"))
            	sql = sql.replace("$rowLimit$", _pvidseq);
            else if (list_[0].equals("sbr.REFERENCE_DOCUMENTS_VIEW"))
            	sql = sql.replace("$rowLimit$", _rdidseq);            
            
            _logger.info("Table name : "+list_[0]);
            
            _logger.info("do update sql : "+sql);
            _logger.debug(sql);

            // Execute the UPDATE
            PreparedStatement stmt = _conn.prepareStatement(sql);
            int updates = stmt.executeUpdate();

            // Report results
            if (updates > 0)
                _logger.info("Fixed [" + prop_ + "] " + updates + " record(s).");
            else
                _logger.info("No records updated.");
            
            // Get ready for next one.
            stmt.close();
            _conn.commit();
        }
        
    }    
    
    /**
     * Run the clean up.
     * @param propFile The property file
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
    public void doClean(String propFile) throws Exception
    {
        loadProp(propFile);
        
        try
        {
            open();

            // Go through all the property values.
            Enumeration props = _propList.propertyNames();
            while (props.hasMoreElements())
            {
                String prop = (String) props.nextElement();
                if (prop.startsWith(_propTable))
                {
                    // For every table/column set do the cleanup.
                    String tabCol = _propList.getProperty(prop);
                    _logger.info("Processing [" + prop + "] [" + tabCol + "]");
                    _logger.debug("Processing [" + prop + "] [" + tabCol + "]");
                    String[] parts = tabCol.split(" ");
                    if (parts.length < 2)
                        throw new RuntimeException("Table properties must be {table name, column name [, column name]} sets.");

                    // Fix the data if appropriate
                    _action.apply(parts, prop + ": " + tabCol);
                }
            }
        }
        catch (Exception ex)
        {
            if (_conn != null)
            {
                try { _conn.rollback(); } catch (Exception e) { }
            }
            throw ex;
        }
        finally
        {
            if (_conn != null)
            {
                try { _conn.close(); } catch (Exception e) { }
                _conn = null;
            }
        }
    }

    /**
     * Load the properties from the XML file specified.
     *
     * @param propFile_ the properties file.
     */
    private void loadProp(String propFile_) throws Exception
    {
        _propList = new Properties();

        _logger.info("\n\nLoading properties " + gov.nih.nci.cadsr.common.Constants.BUILD_TAG + " ...\n\n");
        
        try
        {
            FileInputStream in = new FileInputStream(propFile_);
            _propList.loadFromXML(in);
            in.close();
        }
        catch (Exception ex)
        {
            throw ex;
        }

        _dsurl = _propList.getProperty(Constants._DSURL);
        if (_dsurl == null)
            _logger.error("Missing " + Constants._DSURL + " connection string in " + propFile_);

        _user = _propList.getProperty(Constants._DSUSER);
        if (_user == null)
            _logger.error("Missing " + Constants._DSUSER + " in " + propFile_);

        _pswd = _propList.getProperty(Constants._DSPSWD);
        if (_pswd == null)
            _logger.error("Missing " + Constants._DSPSWD + " in " + propFile_);
        
        if (_propList.getProperty(_propAction).equals(_propUpdate))
            _action = new DoUpdate();
        else
            _action = new DoWork();
    }

    /**
     * Open a single simple connection to the database. No pooling is necessary.
     *
     * @param _dsurl
     *        The Oracle TNSNAME entry describing the database location.
     * @param user_
     *        The ORACLE user id.
     * @param pswd_
     *        The password which must match 'user_'.
     * @return The database error code.
     */
    private int open() throws Exception
    {
        // If we already have a connection, don't bother.
        if (_conn != null)
            return 0;

        try
        {
            OracleDataSource ods = new OracleDataSource();

            String parts[] = _dsurl.split("[:]");
            ods.setDriverType("thin");
            ods.setServerName(parts[0]);
            ods.setPortNumber(Integer.parseInt(parts[1]));
            ods.setServiceName(parts[2]);

            _conn = ods.getConnection(_user, _pswd);
            _conn.setAutoCommit(false);
            return 0;
        }
        catch (SQLException ex)
        {
            throw ex;
        }
    }
}
