// Copyright (c) 2004 ScenPro, Inc

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/test/TestDatabase.java,v 1.11 2006-09-08 22:32:54 hebell Exp $
// $Name: not supported by cvs2svn $

package gov.nih.nci.cadsr.sentinel.test;

import gov.nih.nci.cadsr.sentinel.database.DBAlert;
import gov.nih.nci.cadsr.sentinel.database.DBAlertUtil;
import gov.nih.nci.cadsr.sentinel.ui.AlertBean;

/**
 * Database verification test.
 * 
 * @author Larry Hebel
 */

public class TestDatabase extends DSRAlertTestCase
{
    /**
     * The main entry to run the test case.
     * 
     * @param args Command line arguments - none at this time.
     */
    public static void main(String[] args)
    {
        junit.textui.TestRunner.run(TestCreate.class);
    }

    private AlertBean _alertBean;

    /**
     * Constructor
     * 
     * @param testName The name of the class to test.
     */
    public TestDatabase(String testName)
    {
        super(testName);
    }

    /**
     * The setUp method overrides the DSRAlertTestCase setUp to setup the
     * request path info and action form.
     * 
     * @throws Exception Any exceptions encountered during setup
     */
    public void setUp() throws Exception
    {
        super.setUp();
        setUpLoginSession();
        _alertBean = (AlertBean)getSession().getAttribute(AlertBean._SESSIONNAME);
    }
    
    /**
     * Test the database connection and table dependencies.
     */
    public void testDB()
    {
        DBAlert dbAlert = DBAlertUtil.factory();
        assertEquals(dbAlert.open(getSession().getServletContext(), _alertBean
            .getUser(), _alertBean.getPswd()), 0);
        String txt = dbAlert.testDBdependancies();
        assertNull(txt, txt);
        dbAlert.close();
    }
}
