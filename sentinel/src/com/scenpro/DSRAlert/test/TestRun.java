/*
 * Created on Jan 27, 2005
 *
 * Copyright (c) 2004 ScenPro, Inc
 */
package com.scenpro.DSRAlert.test;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.scenpro.DSRAlert.AlertBean;
import com.scenpro.DSRAlert.AlertRec;
import com.scenpro.DSRAlert.Constants;
import com.scenpro.DSRAlert.DBAlert;
import com.scenpro.DSRAlert.RunForm;

/**
 * @author James McAndrew
 *
 */
public class TestRun extends DSRAlertTestCase
{
  public static void main(String[] args)
  {
    junit.textui.TestRunner.run(TestRun.class);
  }

  /**
   * Constructor
   */
  public TestRun(String testName)
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

    setRequestPathInfo(getPath(Constants._ACTRUN));
    setActionForm(new RunForm());
    AlertBean alertBean = (AlertBean)getSession().getAttribute(
        AlertBean._SESSIONNAME);
    alertBean.setWorking(new AlertRec(alertBean.getUser(), alertBean
        .getUserName()));
  }

  /**
   * The testSubmit method is the test for choosing the submit option.
   */
  public void testSubmit()
  {
    // Create the alert to run
    AlertBean alertBean = (AlertBean)getSession().getAttribute(
        AlertBean._SESSIONNAME);
    alertBean.setRunPrev(Constants._ACTLIST);
    AlertRec alertRec = alertBean.getWorking();
    alertRec.setName("struts_test_temp_alert");
    DBAlert dbAlert = new DBAlert();
    assertEquals(dbAlert.open(getSession().getServletContext(), alertBean
        .getUser(), alertBean.getPswd()), 0);
    assertEquals(dbAlert.insertAlert(alertRec), 0);
    assertEquals(dbAlert.close(), 0);
    alertBean.setWorking(alertRec);

    // Setup a valid run request
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    addRequestParameter("startDate", sdf.format(new Date()));
    addRequestParameter("endDate", sdf.format(new Date()));

    // Then perform the action
    actionPerform();

    // Delete the record (clean up)
    assertEquals(dbAlert.open(getSession().getServletContext(), alertBean
        .getUser(), alertBean.getPswd()), 0);
    assertEquals(dbAlert.deleteAlert(((AlertBean)getSession().getAttribute(
        AlertBean._SESSIONNAME)).getWorking().getAlertRecNum()), 0);

    assertEquals(dbAlert.close(), 0);

    // Verify there were no errors
    verifyForward(Constants._ACTLIST);
    verifyNoActionErrors();
  }

  /**
   * The testBack method is the test for selecting the back option.
   */
  public void testBack()
  {
    // Set the next screen to be the list page
    ((RunForm)getActionForm()).setNextScreen(Constants._ACTLIST);

    // Then perform the action
    actionPerform();

    // Verify there were no errors and the alert is not in the database
    verifyForward(Constants._ACTLIST);
    verifyNoActionErrors();
  }
}