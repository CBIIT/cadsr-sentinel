// Copyright (c) 2008 ScenPro, Inc.

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/daily/CleanStrings.java,v 1.3 2008-11-10 18:54:35 hebell Exp $
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
 * This class is responsible for cleaning the strings in the caDSR and removing all ASCII characters
 * in the 0 - 31 range. This can happen when pasting values into fields or inserting values programmatically
 * into the database. Because the presence of the character may or may not be visible depending on the
 * font it is changed to a simple space, ASCII 32.
 * 
 * The CR, LF and TAB are valid and not translated by this process.
 * 
 * @author lhebel
 *
 */
public class CleanStrings
{
    private static Logger _logger = Logger.getLogger(CleanStrings.class);
    private String _dsurl;
    private String _user;
    private String _pswd;
    private Properties _propList;
    private Connection _conn;
    private DoWork _action;
    
    private static final String _propTable = "table.";
    private static final String _propAction = "action";
    private static final String _propUpdate = "update";

    private static final String _sqlRegexp = "[^[:alnum:][:space:][:punct:]]";
    private static final String _javaRegexp = "[^\\p{Alnum}\\p{Space}\\p{Punct}]";
    
    private static final String _sqlUpdate = "update $table$ "
        + "set $sets$ "
        + "where REGEXP_LIKE($col$, '" + _sqlRegexp + "')";
    
    private static final String _sqlSet = "$col$ = REGEXP_REPLACE($col$, '" + _sqlRegexp + "', '')";
    
    private static final String _sqlSelect = "select * from $table$ "
        + "where REGEXP_LIKE($col$, '" + _sqlRegexp + "') ";
    
    /**
     * @param args_
     */
    public static void main(String[] args_)
    {
        if (args_.length != 2)
        {
            System.err.println(CleanStrings.class.getName() + " log4j.xml config.xml");
            return;
        }

        DOMConfigurator.configure(args_[0]);
        
        CleanStrings cs = new CleanStrings();
        
        try
        {
            _logger.info("");
            _logger.info(CleanStrings.class.getClass().getName() + " begins");
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
         * @see gov.nih.nci.cadsr.sentinel.daily.CleanStrings.DoWork#apply(java.lang.String, java.lang.String)
         */
        @Override
        public void apply(String[] list_, String prop_) throws Exception
        {
            // Build the SQL UPDATE
            String sql = _sqlUpdate.replace("$table$", list_[0]);
            String sets = "";
            for (int cnt = 1; cnt < list_.length; ++cnt)
            {
                sets += "," + _sqlSet.replace("$col$", list_[cnt]);
            }
            sql = sql.replace("$sets$", sets.substring(1));
            _logger.debug(sql);

            // Execute the UPDATE
            PreparedStatement stmt = _conn.prepareStatement(sql);
            int updates = stmt.executeUpdate();

            // Report results
            if (updates > 0)
                _logger.info("Fixed [" + prop_ + "] " + updates + " records.");
            else
                _logger.debug("Fixed [" + prop_ + "] " + updates + " records.");
            
            // Get ready for next one.
            stmt.close();
            _conn.commit();
        }
        
    }
    
    /**
     * @author lhebel
     *
     */
    public class DoSelect extends DoWork
    {

        /* (non-Javadoc)
         * @see gov.nih.nci.cadsr.sentinel.daily.CleanStrings.DoWork#apply(java.lang.String, java.lang.String)
         */
        @Override
        public void apply(String[] list_, String prop_) throws Exception
        {
            // Build the SQL SELECT
            String sql = _sqlSelect.replace("$table$", list_[0]);
            String union = "";
            for (int cnt = 1; cnt < list_.length; ++cnt)
            {
                union += "union " + sql.replace("$col$", list_[cnt]);
            }
            sql = union.substring("union ".length());
            _logger.debug(sql);

            // Execute the SELECT
            PreparedStatement stmt = _conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            
            // Build the columns header
            List<String> cols = Arrays.asList(list_);
            ResultSetMetaData rsmd = rs.getMetaData();
            String header = "";
            boolean[] targetCol = new boolean[rsmd.getColumnCount()];
            for (int cnt = 0; cnt < rsmd.getColumnCount(); ++cnt)
            {
                if (cols.contains(rsmd.getColumnName(cnt + 1).toLowerCase()))
                {
                    targetCol[cnt] = true;
                }
                header = header + "\t" + rsmd.getColumnLabel(cnt + 1);
            }

            // Show each matching record
            HashMap<Integer, String> badMap = new HashMap<Integer, String>();
            boolean showHead = true;
            int rowCnt = 0;
            _logger.info("");
            while (rs.next())
            {
                String line = "";
                for (int cnt = 0; cnt < rsmd.getColumnCount(); ++cnt)
                {
                    String value = rs.getString(cnt + 1);
                    if (targetCol[cnt])
                    {
                        for (int ndx = 0; ndx < value.length(); ++ndx)
                        {
                            String subvalue =value.substring(ndx, ndx + 1); 
                            if (subvalue.matches(_javaRegexp))
                            {
                                badMap.put(Integer.valueOf(subvalue.codePointAt(0)), String.valueOf(subvalue.codePointAt(0)));
                            }
                        }
                    }
                    line = line + "\t" + value;
                }
                if (showHead)
                {
                    _logger.info(prop_ + " contains translatable character(s)");
                    _logger.info(header.substring(1));
                    showHead = false;
                }
                _logger.info(line.substring(1));
                ++rowCnt;
            }
            if (showHead)
            {
                _logger.info(prop_ + " contains no translatable characters.");
            }
            else
            {
                String bad = "";
                for (String code : badMap.values())
                {
                    bad += " " + code;
                }
                _logger.info(rowCnt + " results contain a combination of the translatable character(s)" + bad);
            }
            
            // Get ready for next one.
            rs.close();
            stmt.close();
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
            
            DoWork select = new DoSelect();

            // Go through all the property values.
            Enumeration props = _propList.propertyNames();
            while (props.hasMoreElements())
            {
                String prop = (String) props.nextElement();
                if (prop.startsWith(_propTable))
                {
                    // For every table/column set do the cleanup.
                    String tabCol = _propList.getProperty(prop);
                    _logger.debug("Processing [" + prop + "] [" + tabCol + "]");
                    String[] parts = tabCol.split(" ");
                    if (parts.length < 2)
                        throw new RuntimeException("Table properties must be {table name, column name [, column name]} sets.");

                    // Always report on the offenders
                    select.apply(parts, prop + ": " + tabCol);
                    
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

        _logger.info("\n\nLoading properties...\n\n");
        
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
