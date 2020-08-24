/*L
 * Copyright ScenPro Inc, SAIC-F
 *
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See http://ncip.github.com/cadsr-sentinal/LICENSE.txt for details.
 */

// Copyright (c) 2004 ScenPro, Inc.

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/tool/AutoProcessAlerts.java,v 1.32 2009-01-05 20:52:41 hebell Exp $
// $Name: not supported by cvs2svn $

package gov.nih.nci.cadsr.sentinel.tool;

// import gov.nih.nci.cadsr.sentinel.*;

import gov.nih.nci.cadsr.sentinel.audits.AuditReport;
import gov.nih.nci.cadsr.sentinel.database.DBAlert;
import gov.nih.nci.cadsr.sentinel.database.DBAlertUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.Vector;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

/**
 * Encapsulate the logic to process an Alert definition. The process may be
 * started manually from the user interface and via command line script as an
 * "auto" run. It is intended the system administrator will create a scheduled
 * task to execute once every day using a command script - hence the reference
 * to the "auto" process as it runs unattended.
 * <p>
 * The differences in the logic between manual and auto runs is minimal. A
 * manual run executes the same methods as an auto run except that a single
 * Alert definition is used for manual and an array of definitions are used for
 * auto. Additionally the manual process starts a separate thread to process the
 * request which frees the main process to continue managing HTTP requests. The
 * auto process runs all Alerts sequentially in a single thread.
 * <p>
 * Because the manual run executes in a separate thread, any number may be
 * started by the same user or different users without fear of collision.
 * However, it is important to note at this time, the threads run on the same
 * application as the HTTP servlets which start them. Consequently, too many
 * manual requests can cause some performance impacts to responses. If this
 * becomes a problem a separate server will be created and all run requests,
 * auto and manual, will be passed to the server via an HTTP request which can
 * be configured to run anywhere.
 * <p>
 * Also important to note is that all output from the processes are stored in
 * files. There is no browser or other user interface to report progress. Once
 * the run is finished, the designated administrator receives an email of the
 * log and the recipients receive and email of the Alert output.
 *
 * @author Larry Hebel
 */

public class AutoProcessAlerts
{
    /**
     * Constructor
     */
    public AutoProcessAlerts()
    {
        // Clear the log files.
        _logSummary = null;
        _logAudits = null;
        _updateRunDate = true;

        // Set the default output format for the report.
        _outForm = 2;

        // Default database information
        _dsurl = null;

        // A location to store all log and report files.
        _work = "alerts_";

        // The active database connection.
        _db = null;

        // For the insert logic to work easily when recording recipients and
        // the reports they receive, we seed the lists with a 'blank' entry.
        _recipients = new String[1];
        _recipients[0] = "";
        _reports = new ReportItem[1][];
        _reports[0] = new ReportItem[0];

        // Get the current date and time.
        _today = Timemarker.timeNow();
        
        _urlMsgsErr = new Vector<String>();
        _urlMsgsInfo = new Vector<String>();
        _urlRunCnt = 0;
        _urlRunCalls = 0;
    }

    /**
     * This process is intended to be started by a system administrator
     * scheduled task or manually by an administrator when necessary. For
     * optimal performance this should run locally to the database server (i.e.
     * LAN proximity not WAN). The amount of database access is extremely high
     * even with the computed date ranges used in the SQL selects.
     *
     * @param /Users/ag/demo/sentinel.orig/lib/log4j.xml true /Users/ag/demo/sentinel.orig/lib/cadsrsentinel.xml
     * Notes: search for "DEV - comment this out for testing" and comment it out to test.
     * To run it, add sentinel.orig/deployment-artifacts/bin into classpath (for application-config-client.xml)
     * 
     */
    public static void main(String[] args_)
    {
        if (args_.length != 3)
        {
            System.out.println("usage: <program> log4j.xml [true | false] cadsrsentinel.xml");
            return;
        }
        DOMConfigurator.configure(args_[0]);

        // All logic is contained in the object so create a new instance and
        // run.
        AutoProcessAlerts apa = new AutoProcessAlerts();
        apa._updateRunDate = args_[1].equals("true");

        try
        {
            // Get the database connection properties.
            apa.loadProp(args_[2]);

            // Run the Alert reports.
            apa.autoRun();
        }
        catch (Exception ex)
        {
        	ex.printStackTrace();
            _logger.error(ex.toString(), ex);
        }
    }

    /**
     * Load the properties from the XML file specified.
     *
     * @param propFile_ the properties file.
     */
    private void loadProp(String propFile_) throws Exception
    {
        Properties prop = new Properties();

        _logger.info("\n\nLoading properties " + gov.nih.nci.cadsr.common.Constants.BUILD_TAG + " ...\n\n");

        FileInputStream in = new FileInputStream(propFile_);
        prop.loadFromXML(in);
        in.close();

        _dsurl = prop.getProperty(Constants._DSURL);
        if (_dsurl == null)
            _logger.error("Missing " + Constants._DSURL + " connection string in " + propFile_);

        _user = prop.getProperty(Constants._DSUSER);
        if (_user == null)
            _logger.error("Missing " + Constants._DSUSER + " in " + propFile_);

        _pswd = prop.getProperty(Constants._DSPSWD);
        if (_pswd == null)
            _logger.error("Missing " + Constants._DSPSWD + " in " + propFile_);

        int auditCnt = 0;
        while (prop.getProperty("audit." + auditCnt + ".class") != null)
        {
            ++auditCnt;
        }

        _audits = new String[auditCnt];
        for (int i = 0; i < _audits.length; ++i)
        {
            _audits[i] = prop.getProperty("audit." + i + ".class");
        }

        _auditTitles = new String[_audits.length];
        for (int i = 0; i < _auditTitles.length; ++i)
        {
            _auditTitles[i] = prop.getProperty("audit." + i + ".title");
        }
    }

    /**
     * This private class is used to track local process information about each
     * Alert. It seems best to have this wrapper for the extra information
     * rather than add it to the AlertRec class.
     */
    private class ProcessRec
    {
        /**
         * Constructor
         */
        public ProcessRec()
        {
            _alert = null;
            _reportFile = null;
            _parts = 1;
            _errors = false;
        }

        /**
         * The Alert Definition.
         */
        public AlertRec _alert;

        /**
         * The report file name.
         */
        public String _reportFile;

        /**
         * The number of parts the report is broken into.
         */
        public int _parts;

        /**
         * True if errors exist in the file.
         */
        public boolean _errors;
    }

    /**
     * Reset the Auto Run time stamp in the Alert Definition.
     *
     * @param rec_
     *        The Alert definition.
     */
    private void resetAutoRun(ProcessRec rec_)
    {
        if (_updateRunDate)
        {
            _db.updateRun(rec_._alert.getAlertRecNum(), _today, true, rec_._alert
                .isActiveOnce());
            _logSummary.writeError(_db.getError());
        }
    }

    /**
     * Reset the Manual Run time stamp in the Alert Definitions.
     *
     * @param rec_
     *        The Alert definition.
     */
    private void resetManualRun(ProcessRec rec_)
    {
        _db.updateRun(rec_._alert.getAlertRecNum(), _today, false, false);
        _logSummary.writeError(_db.getError());
    }

    /**
     * Keep track of the name of the Alert and the file link for the emails.
     */
    private class ReportItem
    {
        /**
         * Constructor
         */
        public ReportItem()
        {
            _file = "";
            _name = "";
            _rows = 0;
            _parts = 1;
        }

        /**
         * The file name.
         */
        public String _file;

        /**
         * The name of the Alert Report.
         */
        public String _name;

        /**
         * The number of parts for the report.
         */
        public int _parts;

        /**
         * The number of rows in the report.
         */
        public int _rows;
    }

    /**
     * Record a new report for distribution to a specific user. This is part of
     * general logic to consolidate all reports to a recipient into a single
     * email.
     *
     * @param pos_
     *        The position in the recipient list (who will receive the report).
     * @param file_
     *        The file containing the report output.
     * @param name_
     *        The name of the Alert definition.
     * @param rows_
     *        The number of rows in the report.
     * @param parts_
     *        The number of parts to the report.
     */
    private void appendReport(int pos_, String file_, String name_, int rows_,
        int parts_)
    {
        // Increase the array by 1 element.
        int len = _reports[pos_].length + 1;
        ReportItem temp[] = new ReportItem[len];

        // Copy the exiting report list for this recipient.
        int ndx;
        for (ndx = 0; ndx < _reports[pos_].length; ++ndx)
        {
            temp[ndx] = _reports[pos_][ndx];
        }

        // Add the new report to the end of the list.
        temp[ndx] = new ReportItem();
        temp[ndx]._file = file_;
        temp[ndx]._name = name_;
        temp[ndx]._rows = rows_;
        temp[ndx]._parts = parts_;

        // Discard the old list in preference to the new one.
        _reports[pos_] = temp;
    }

    /**
     * Insert a recipient into the queue. This is part of larger logic which
     * consolidates all reports for a single recipient into a single email. The
     * recipient list is sorted alphabetically ascending to allow for binary
     * searches.
     *
     * @param pos_
     *        The position in the recipient array to insert the new email
     *        address.
     * @param email_
     *        The email address to store.
     */
    private void insertRecipient(int pos_, String email_)
    {
        // Increase the array by 1 entry.
        String temp[] = new String[_recipients.length + 1];
        ReportItem trep[][] = new ReportItem[_reports.length + 1][];

        // Copy the existing entries.
        int ndx;
        for (ndx = 0; ndx < pos_; ++ndx)
        {
            temp[ndx] = _recipients[ndx];
            trep[ndx] = _reports[ndx];
        }

        // Insert the new entry in the desired location in the sorted list.
        temp[ndx] = email_;
        trep[ndx] = new ReportItem[0];

        // Finish the copy of existing entries.
        for (int ndx2 = ndx; ndx2 < _recipients.length; ++ndx2)
        {
            temp[++ndx] = _recipients[ndx2];
            trep[ndx] = _reports[ndx2];
        }

        // Discard the old list in preference for the new.
        _recipients = temp;
        _reports = trep;
    }

    /**
     * Save the report with the list of recipients for later.
     *
     * @param rec_
     *        The process record.
     */
    private void queueReport(ProcessRec rec_)
    {
        // Get the list of desired recipients.
        String list[] = rec_._alert.getRecipients();
        String file;
        String name = rec_._alert.getName();
        int parts = rec_._parts;

        int rowCnt;
        if (rec_._errors)
        {
            file = "";
            rowCnt = 0;
        }
        else
        {
            file = rec_._reportFile;
            rowCnt = _outRows;
        }

        // The recipients are coded such that anything starting with
        // a "/" is the conte_idseq for the groups of curators for a
        // specific context. If an "@" appears in the recipient string
        // it is a well formed email address and can be used asis.
        // Finally the recipient name must be a single user id known to
        // the caDSR.
        for (int item = 0; item < list.length; ++item)
        {
            // Resolve all recipients to email addresses. By doing this
            // we can guarantee that should a creator place an email
            // address in one Alert and use the user id in another, only
            // 1 email will be sent to the recipient.
            if (list[item].startsWith("http://") || list[item].startsWith("https://"))
            {
                queueReportForProcess(list[item]);
            }
            else
            {
                queueReportForEmail(list[item], rowCnt, file, name, parts);
            }
        }
    }

    /**
     * Queue the report for inclusion in an email distribution.
     *
     * @param email_ the user, email address, etc
     * @param rowCnt_ the report row count
     * @param file_ the report file name
     * @param name_ the Alert Definition name
     * @param parts_ the number of parts for this report
     */
    private void queueReportForEmail(String email_, int rowCnt_, String file_, String name_, int parts_)
    {
        String[] temp = null;

        // if it is not a process URL then add the emails to the queue
        if (email_.charAt(0) == '/')
        {
            temp = _db.selectEmailsFromConte(email_);
            _logSummary.writeError(_db.getError());
        }
        else if (email_.indexOf('@') < 0)
        {
            temp = new String[1];
            temp[0] = _db.selectEmailFromUser(email_);
            _logSummary.writeError(_db.getError());
        }
        else
        {
            temp = new String[1];
            temp[0] = email_;
        }

        // Perform a binary search on the recipient email list.
        for (int ndx = 0; ndx < temp.length; ++ndx)
        {
            // Yes it's possible that a user does not have an email
            // address.
            if (temp[ndx] == null || temp[ndx].length() == 0)
                continue;

            // Standard binary search implementation.
            int min = 0;
            int max = _recipients.length;
            while (true)
            {
                int pos = (max + min) / 2;
                int compare = temp[ndx].compareTo(_recipients[pos]);
                if (compare == 0)
                {
                    // When the recipient is already in the list we need
                    // only
                    // add the report name.
                    appendReport(pos, file_, name_, rowCnt_, parts_);
                    break;
                }
                else if (compare > 0)
                {
                    if (min == pos)
                    {
                        // When the recipient is not in the list be sure
                        // to position the name alphabetically and add
                        // the report also.
                        insertRecipient(++pos, temp[ndx]);
                        appendReport(pos, file_, name_, rowCnt_, parts_);
                        break;
                    }
                    min = pos;
                }
                else
                {
                    if (max == pos)
                    {
                        // When the recipient is not in the list be sure
                        // to position the name alphabetically and add
                        // the report also.
                        insertRecipient(pos, temp[ndx]);
                        appendReport(pos, file_, name_, rowCnt_, parts_);
                        break;
                    }
                    max = pos;
                }
            }
        }
    }

    /**
     * Perform processing on all the eligible Alerts.
     */
    private void autoRun2()
    {
        // Pull a list of all active alerts from the database that should be run
        // at this time.
        AlertRec alerts[] = getAlertList();
        if (alerts == null)
        {
            _logSummary.writeParagraph1("No Alerts available to run on " + _today.toString());
            return;
        }
        _logSummary.writeHeading("Alert Definitions to process: " + alerts.length);
        ProcessRec list[] = new ProcessRec[alerts.length];
        for (int ndx = 0; ndx < list.length; ++ndx)
        {
            list[ndx] = new ProcessRec();
            list[ndx]._alert = alerts[ndx];
        }

        // Calculate the ending date/time for the SQL queries in this run.
        _end = setToMidnight(_today);

        // Process each Alert definition and find the raw data.
        for (int ndx = 0; ndx < list.length && list[ndx] != null; ++ndx)
        {
            // The date range for each Alert will begin at midnight on the Last
            // Run Date in the
            // AlertRec and end at midnight of the date this process runs. If
            // the Last Run Date
            // is null it will be defaulted to the current date minus the
            // frequency unit.
            _start = list[ndx]._alert.getAdate();
            if (_start == null)
            {
                // Back up in time using the auto run frequency.
                _start = timeTravel(list[ndx]._alert, _end);
            }
            else
            {
                // Use midnight of the last run date as a start point.
                _start = setToMidnight(_start);
            }
            _logSummary.writeHeading("Processing Alert Definition: " + ndx + ": "
                            + list[ndx]._alert.getName() + " (" + _start.toString()
                            + " TO " + _end.toString() + ")");

            // Pull the caDSR changes given the Alert definition and the date
            // range.
            pullDBchanges(list[ndx]);

            // Force the garbage collection to avoid out of memory issues.
            System.gc();

            // If errors occurred, tell the user to contact the administrator.
            list[ndx]._errors = _logSummary.hasErrors();
            if (list[ndx]._errors)
            {
                queueReport(list[ndx]);
                _logSummary.resetErrors();
            }

            else if (_outRows > 0 || list[ndx]._alert.isSendEmptyReport())
            {
                // Build the recipient list for this report.
                queueReport(list[ndx]);
            }

            // Reset the last run time in the database. Always do this
            // update whether or not a report is created.
            resetAutoRun(list[ndx]);
        }
    }

    /**
     * Perform the Auto Run process for all eligible Alerts in this caDSR
     * database. And eligible Alert must be active for today's run. For example,
     * if it is Tuesday and an Alert is defined to run on Wednesday it is not
     * eligible.
     */
    private void autoRun()
    {
        // Identify this run.
        _id = "Auto";

        // Load resources. Must be done first as we need configuration
        // information from the
        // properties file.
        getResources();

        // Flag the start of the log.
        _logSummary.writeHeading("Auto Run Process Starts... ");
        _logSummary.writeParagraph1("Database = " + _dbname + " (" + _dsurl + ")");
        _logSummary.writeParagraph1("Working folder prefix = " + _work);

        _logger.info("\nExecuting autoRun2() ...\n\n");        
        // Process the Alerts.
        autoRun2();	//DEV - comment this out for testing (TBD)

        // Done with the database.
        if (_db != null)
            _db.close();
        _db = null;

        // Output Process URL messages in one place to make it easier to find.
        writeUrlMsgs();

        // Send all emails now.
        sendEmails();

        // Flag the end of the log.
        _logSummary.writeHeading("Auto Run Process Ends... ");
        sendLog();
        _logSummary.close();
    }

    /**
     * Send the reports to the recipients. Currently this is done by providing
     * URL links to the actual report files. A number of other options were
     * considered but all have advantages and disadvantages.
     * <p>
     * <ul>
     * <li>Send the reports as links in the body of the email. Advantages,
     * keeps the emails small. Disadvantages, the recipient must have access to
     * the server to use the link. Also requires some space maintenance on the
     * server by an administrator.</li>
     * <li>Send the reports as attachments to the email. Advantages, the
     * recipient has the file in his possesion and can manage his own email
     * list. Disadvantages, the files can be such a size that the email server
     * is overloaded for the distribution.</li>
     * <li>Zip all the reports and add as a file attachment to the eamil.
     * Advantages, keeps the email relative reasonable in size. Disadvantages,
     * must be able to extract the files from zip. Also some email servers strip
     * zip files to control spam and for security.</li>
     * <li>Embed the report into the body of the email. Advantages, none
     * really. Disadvantages, all those mentioned above relating to attaching
     * files additionally the email becomes difficult to read.</li>
     * </ul>
     * </p>
     */
    private void sendEmails()
    {
        _logSummary.writeHeading("Sending emails...");

        // Be sure we have something to send.
        if (_recipients.length < 2)
        {
            _logSummary.writeParagraph1("No activity to email.");
            return;
        }

        // The first location (index 0) in the _recipients list is an empty
        // place holder.
        // We send one email to each recipient.
        for (int ndx = 1; ndx < _recipients.length; ++ndx)
        {
            // send emails to only email recipients and ignore process recipients
            if (_recipients[ndx].startsWith("http://") || _recipients[ndx].startsWith("https://"))
                continue;

            // Form the body of the email.
            boolean hasErrors = false;
            String prefix = "";
            String body = "<colgroup><col /><col /></colgroup><tbody />";
            for (int ndx2 = 0; ndx2 < _reports[ndx].length; ++ndx2)
            {
                String link1 = _reports[ndx][ndx2]._file;
                String link2;
                if (link1.length() == 0)
                {
                    if (prefix.length() == 0)
                        prefix = "<p>" + _adminIntroError + "</p>";
                    link1 = "";
                    link2 = "*** Errors ***";
                    hasErrors = true;
                }
                else
                {
                    link1 = _http + link1.substring(_work.length());
                    link1 = link1.replaceAll(" ", "%20");
                    link2 = link1;
                }
                String parts = "";
                if (_reports[ndx][ndx2]._parts > 1)
                    parts = "&nbsp;(In&nbsp;" + _reports[ndx][ndx2]._parts
                        + "&nbsp;parts.)";
                body = body + "<tr><td>Alert Definition Name:</td><td>"
                    + _reports[ndx][ndx2]._name + "</td></tr>"
                    + "<tr><td>Link to Alert Report:</td><td><a href=\""
                    + link1 + "\">" + link2 + "</a></td></tr>"
                    + "<tr><td>Number of Rows in Report:</td><td>"
                    + _reports[ndx][ndx2]._rows + parts + "</td></tr>"
                    + "<tr><td colspan=\"2\">&nbsp;</td></tr>";
            }

            // Complete subject and body and send to recipient.
            String subject = _subject
                            + ((_id.charAt(0) == 'A') ? " Auto Run" : " Manual Run")
                            + " for " + _today.toString().substring(0, 10)
                            + ((hasErrors) ? " has Errors" : "");
            String message = "<html><body style=\"font-family: arial; font-size: 10pt\"><p>"
                            + _adminIntro
                            + "</p>"
                            + prefix
                            + "<table style=\"font-size: 10pt\">"
                            + body
                            + "</table></body></html>";
            sendEmail(hasErrors, _recipients[ndx], "", subject,
                message);
            _logSummary.writeHR();
            _logSummary.writeParagraph0("To: " + _recipients[ndx]);
            _logSummary.writeParagraph0("Subject: " + subject);
            _logSummary.writeTable(body);
        }
           
        _logSummary.writeHR();
        _logSummary.writeHeading("Completed emails...\n");
    }

    /**
     * Create a thread wrapper around the processing of a single Alert.
     */
    private class AlertThread extends Thread
    {
        /**
         * Constructor
         *
         * @param rec_
         *        The Alert Defition to process.
         */
        public AlertThread(AlertRec rec_)
        {
            _rec = rec_;
        }

        /**
         * Process the single Alert.
         */
        public void run()
        {
            // Test code.
            /*
             * DSRAlert tapi =
             * DSRAlertImpl.factory("http://cadsrsentinel-dev.nci.nih.gov");
             * int rc = tapi.createAlert("hebell",
             * "E2538C9F-E9E7-3303-E034-0003BA12F5E7"); tapi = null;
             */

            // Get configuration information.
            getResources();
            _logSummary.writeHeading("Manual Run Process Starts... ");
            _logSummary.writeParagraph1("Database = " + _dbname + " (" + _dsurl + ")");
            _logSummary.writeParagraph1("Working folder prefix = " + _work);

            _logSummary.writeHeading("Processing Alert Definition: " + _rec.getName() + " ("
                    + _start.toString() + " TO " + _end.toString() + ")");

            ProcessRec rec = new ProcessRec();
            rec._alert = _rec;

            // Get the caDSR changes.
            pullDBchanges(rec);

            // Record the report.
            rec._errors = _logSummary.hasErrors();
            queueReport(rec);

            // Reset the run date. We don't care if any activity was found
            // because
            // the recipients always get information about a manual run - error
            // or
            // real output.
            resetManualRun(rec);

            // Done with the database.
            if (_db != null)
                _db.close();
            _db = null;

            // Output Process URL messages in one place to make it easier to find.
            writeUrlMsgs();
            
            // Send to the recipients.
            sendEmails();

            // We're done.
            _logSummary.writeHeading("Manual Run for Sentinel Alert ends: " + _rec.getName());
            sendLog();
            _logSummary.close();
        }

        // Class data elements.
        private AlertRec _rec;
    }

    /**
     * Invoke a single alert run using the start and end dates provided. This
     * method will execute in a separate thread.
     *
     * @param api_
     *        The data source (if one exists) for the database connection.
     * @param rec_
     *        The alert definition to run. This does not have to be saved in the
     *        database.
     * @param start_
     *        The start of the date range.
     * @param end_
     *        The end of the date range.
     */
    public void manualRun(DataSource dsManual_, String userNameManual_, AlertRec rec_, Timestamp start_, Timestamp end_)
    {
        AlertThread at = new AlertThread(rec_);
        _dsManual = dsManual_;
        _userNameManual = userNameManual_;
        _start = start_;
        _end = end_;
        _id = "Manual " + rec_.getCreator();
        at.start();
    }

    /**
     * Generate a file name to hold the report.
     *
     * @param rec_
     *        The Alert definition.
     */
    private void getFileName(ProcessRec rec_)
    {
        String temp = rec_._alert.getName().replaceAll("\\W", "_");
        String ts = "_" + Timemarker.timeNow().toString().replaceAll("\\D", "")
                + ".html";
        rec_._reportFile = _work + temp + ts;
        temp = _http + temp + ts;
        _logSummary.writeParagraph1("Created by " + rec_._alert.getCreator());
        _logSummary.writeParagraph1("Output File is");
        _logSummary.writeParagraph2(rec_._reportFile);
        _logSummary.writeParagraph2("<a href=\"" + temp + "\" target=\"_blank\">" + temp + "</a>");
    }

    /**
     * Dump the output for use in Emails
     *
     * @param save_
     *        The report content.
     * @param rec_
     *        The processing record for the report.
     * @param cemail_
     *        The creator email address
     */
    private void dumpEmailRecipients(Stack<RepRows> save_, ProcessRec rec_, String cemail_)
    {
        try
        {
            // Open the report file.
            FileOutputStream fout = new FileOutputStream(rec_._reportFile,
                false);

            _outRows = 0;
            if (_outForm == 1)
            {
                // Header first.
                ACData.dumpHeader1(_dbname, _style, cemail_, _db
                    .selectRecipientNames(rec_._alert.getRecipients()),
                    rec_._alert, _start, _end, fout);

                // Body
                _outRows += ACData.dumpDetail1(_db, save_, _outRows, fout);
                _logSummary.writeError(_db.getError());

                // Footer
                ACData
                    .dumpFooter1((_outRows == 0), _version, rec_._alert, fout);
            }
            else if (_outForm == 2)
            {
                // Add calculation of parts and break save_ into multiple
                // pieces. Watch out for the
                // Associated To Limit - will want to break up the final report.
                Stack<RepRows> report = ACData.dumpTrim(save_, rec_._alert
                    .getIAssocLvl());
                if (_threshold < report.size())
                {
                    int parts = 0;
                    int total = report.size();
                    String namePattern = rec_._reportFile.substring(_work
                        .length());
                    namePattern = namePattern
                        .replace(".html", "_part_{0}.html");
                    while (total > 0)
                    {
                        Stack<RepRows> working = new Stack<RepRows>();
                        ++parts;

                        // Find the report break by jumping into the stack and
                        // working back to the nearest
                        // Primary record.
                        int start = (_threshold < report.size()) ? _threshold
                            : report.size();
                        for (int ndx = report.size() - start; ndx > 0; --ndx)
                        {
                            RepRows val = report.get(ndx);
                            if (val._rec.isPrimary())
                            {
                                --start;
                                break;
                            }
                            ++start;
                        }
                        total -= start;
                        for (; start > 0; --start)
                        {
                            working.add(0, report.pop());
                        }

                        // Open the report file.
                        fout.close();
                        String filename = _work
                            + namePattern.replace("{0}", Integer
                                .toString(parts));
                        fout = new FileOutputStream(filename, false);

                        // Header first.
                        ACData.dumpHeader2(_dbname, _style, cemail_, _db
                            .selectRecipientNames(rec_._alert.getRecipients()),
                            rec_._alert, _start, _end, parts, total > 0,
                            namePattern, fout);

                        // Body
                        _outRows += ACData.dumpDetail2(_db, working, _outRows,
                            fout);
                        _logSummary.writeError(_db.getError());

                        // Footer
                        ACData.dumpFooter2((_outRows == 0), _version,
                            rec_._alert, fout);
                    }
                    fout.close();
                    fout = new FileOutputStream(rec_._reportFile, false);
                    ACData.dumpParts(rec_._alert.getName(), namePattern,
                        _threshold, parts, fout);
                    rec_._parts = parts;
                }
                else
                {
                    // Header first.
                    ACData.dumpHeader2(_dbname, _style, cemail_, _db
                        .selectRecipientNames(rec_._alert.getRecipients()),
                        rec_._alert, _start, _end, 0, false, null, fout);

                    // Body
                    _outRows += ACData.dumpDetail2(_db, report, _outRows, fout);
                    _logSummary.writeError(_db.getError());

                    // Footer
                    ACData.dumpFooter2((_outRows == 0), _version, rec_._alert,
                        fout);
                }
            }

            // Close file.
            fout.close();
        }
        catch (FileNotFoundException ex)
        {
        	ex.printStackTrace();        	
            _logSummary.writeError("Error opening auto alert dump file: " + rec_._reportFile);
            _logger.error(ex.toString(), ex);
        }
        catch (IOException ex)
        {
        	ex.printStackTrace();        	
            _logSummary.writeError("Error writing to auto alert dump file: "
                + rec_._reportFile);
            _logger.error(ex.toString(), ex);
        }
    }

    /**
     * Filter the list based on monitors in the Alert definition.
     *
     * @param list_
     *        The current data set.
     * @param rec_
     *        The Alert details.
     * @return The new data set.
     */
    private ACData[] filter(ACData list_[], AlertRec rec_)
    {
        ACData temp[] = ACData.filter(list_, rec_);
        ACData.resolveChanges(_db, temp);
        _logSummary.writeError(_db.getError());
        return temp;
    }

    /**
     * Find the records that have actually changed in the database.
     *
     * @param rec_
     *        The Alert Definition.
     * @param acdList_
     *        The lists of record changes.
     */
    private void findChanges(AlertRec rec_, ACData acdList_[][])
    {
        // Check for changes to Valid Values from the Questions on
        // Forms/Templates.
        String creators[] = rec_.getCreators();
        String modifiers[] = rec_.getModifiers();
        int dates = rec_.getDateFilter();

        for (int ndx = 0; ndx < acdList_.length; ++ndx)
            acdList_[ndx] = new ACData[0];

        int length = (rec_.isACTYPEall()) ? DBAlert._ACTYPE_LENGTH : rec_
            .getACTypes().length;
        boolean isCWFSall = rec_.isCWFSall();
        String CWorkflow[] = (isCWFSall) ? null : rec_.getCWorkflow();
        boolean isCRSall = rec_.isCRSall();
        String CRegStatus[] = (isCRSall) ? null : rec_.getCRegStatus();

        Timemarker timer = new Timemarker(null);
        _logSummary.writeParagraph1("Find changes");
        String text = "";
        for (int ndx = 0; ndx < length; ++ndx)
        {
            int rc = rec_.isACTypeUsed(_db, ndx);
            if (rc == -1)
                continue;
            switch (rc)
            {
                case DBAlert._ACTYPE_PROTO:
                    if (isCRSall)
                        acdList_[rc] = _db.selectPROTO(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>PROTO</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_PROP:
                    if (isCRSall)
                        acdList_[rc] = _db.selectPROP(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>PROP</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_OC:
                    if (isCRSall)
                        acdList_[rc] = _db.selectOC(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>OC</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_CON:
                    if (isCRSall)
                        acdList_[rc] = _db.selectCON(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>CON</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_QCV:
                    if (isCRSall)
                        acdList_[rc] = _db.selectQCV(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>QCV</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_QCQ:
                    if (isCRSall)
                        acdList_[rc] = _db.selectQCQ(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>QCQ</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_QCM:
                    if (isCRSall)
                        acdList_[rc] = _db.selectQCM(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>QCM</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_QC:
                    if (isCRSall)
                        acdList_[rc] = _db.selectQC(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>QC</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_PV:
                    if (isCWFSall && isCRSall)
                        acdList_[rc] = _db.selectPV(dates, _start, _end,
                            creators, modifiers);
                    text = text + "<tr><td>PV</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_VM:
                    if (isCRSall)
                        acdList_[rc] = _db.selectVM(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>VM</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_VD:
                    if (isCRSall)
                        acdList_[rc] = _db.selectVD(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>VD</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_CD:
                    if (isCRSall)
                        acdList_[rc] = _db.selectCD(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>CD</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_DEC:
                    if (isCRSall)
                        acdList_[rc] = _db.selectDEC(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>DEC</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_DE:
                    acdList_[rc] = _db.selectDE(dates, _start, _end, creators,
                        modifiers, CWorkflow, CRegStatus);
                    text = text + "<tr><td>DE</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_CSI:
                    if (isCWFSall && isCRSall)
                        acdList_[rc] = _db.selectCSI(dates, _start, _end,
                            creators, modifiers);
                    text = text + "<tr><td>CSI</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_CS:
                    if (isCRSall)
                        acdList_[rc] = _db.selectCS(dates, _start, _end,
                            creators, modifiers, CWorkflow);
                    text = text + "<tr><td>CS</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
                case DBAlert._ACTYPE_CONTE:
                    if (isCWFSall && isCRSall)
                        acdList_[rc] = _db.selectCONTE(dates, _start, _end,
                            creators, modifiers);
                    text = text + "<tr><td>CONTE</td><td>" + timer.check()
                        + "</td></tr>";
                    break;
            }
            _logSummary.writeError(_db.getError());
            if (acdList_[rc] == null)
                acdList_[rc] = new ACData[0];
            else
                acdList_[rc] = filter(acdList_[rc], rec_);
            text = text + "<tr><td><i>&nbsp;&nbsp;&nbsp;filter</i></td><td>"
                + timer.check() + "</td></tr>";
        }
        text = text + "<tr><td>Total</td><td>" + timer.reset() + "</td></tr>";
        _logSummary.writeMatrix("", text, 2, true, "");
    }

    /**
     * Create/Run the Audit Reports
     */
    private void createAuditReports()
    {
        // During an Auto Run report the statistics.
        if (_id.charAt(0) != 'A')
            return;

        if (_audits == null || _audits.length == 0)
        {
            _logSummary.writeHeading("No Audit Reports are defined for this run.");
            return;
        }

        _logAudits = new AlertOutput(_work, _http, "Audits", _version);
        _logSummary.writeHeading("Audit Report is located at <a href=\""
            + _logAudits.getHttpLink() + "\" target=\"_blank\">"
            + _logAudits.getHttpLink() + "</a>");

        _logAudits.writeParagraph1("Database: " + _dbname + " (" + _dsurl + ")");

        String[] rows;
        int colcnt;
        String text;
        int index = 0;
        String errorPrefix = "<b>Error:</b> <i>";

        String splitPattern = AuditReport.getSplitPattern();

        for (index = 0; index < _audits.length; ++index)
        {
            try
            {
                AuditReport ar = (AuditReport) Class.forName(_audits[index]).newInstance();
                ar.setDB(_db);
                rows = ar.getReportRows();
                colcnt = rows[0].split(splitPattern).length;
                String prefix = AuditReport.formatSectionTop(_auditTitles[index], index);
                String suffix = AuditReport.formatSectionBottom();
                text = AuditReport.formatHeader(
                    _auditTitles[index],
                    (ar.okToDisplayCount()) ? rows.length : -1,
                    colcnt, index);
                text = text + AuditReport.formatRows(rows);
                _logAudits.writeMatrix(prefix, text, colcnt, ar.rightJustifyLastColumn(), suffix);
            }
            catch (InstantiationException ex)
            {
            	ex.printStackTrace();            	
                _logSummary.writeError(ex.toString());
                _logAudits.writeParagraph1(errorPrefix + _auditTitles[index] + ":</i> " + ex.toString());
                _logger.error(ex.toString(), ex);
            }
            catch (IllegalAccessException ex)
            {
            	ex.printStackTrace();            	
                _logSummary.writeError(ex.toString());
                _logAudits.writeParagraph1(errorPrefix + _auditTitles[index] + ":</i> " + ex.toString());
                _logger.error(ex.toString(), ex);
            }
            catch (ClassNotFoundException ex)
            {
            	ex.printStackTrace();            	
                _logSummary.writeError(ex.toString());
                _logAudits.writeParagraph1(errorPrefix + _auditTitles[index] + ":</i> " + ex.toString());
                _logger.error(ex.toString(), ex);
            }
            catch (ClassCastException ex)
            {
            	ex.printStackTrace();            	
                _logSummary.writeError("Class " + _audits[index] + " does not extend class AuditReport: " + ex.toString());
                _logAudits.writeParagraph1(errorPrefix + _auditTitles[index] + ":</i> " + ex.toString());
                _logger.error(ex.toString(), ex);
            }
            catch (Exception ex)
            {
            	ex.printStackTrace();            	
                _logSummary.writeError(ex.toString());
                _logAudits.writeParagraph1(errorPrefix + _auditTitles[index] + ":</i> " + ex.toString());
                _logger.error(ex.toString(), ex);
            }
        }

        _logAudits.close();

    }

    /**
     * Create a dataset of all changes from the caDSR.
     *
     * @param rec_
     *        The Alert definition.
     */
    private void pullDBchanges(ProcessRec rec_)
    {
        try
        {
            // Report section timings.
            Timemarker timer = new Timemarker();

            // Determine file name that will eventually hold the output.
            getFileName(rec_);
            _logSummary.writeParagraph1("Initialization");
            _logSummary.writeParagraph2(timer.check());

            // Get the changes from the database.
            ACData actypes[][] = new ACData[DBAlert._ACTYPE_LENGTH][];
            findChanges(rec_._alert, actypes);
            ACData proto[] = actypes[DBAlert._ACTYPE_PROTO];
            ACData con[] = actypes[DBAlert._ACTYPE_CON];
            ACData prop[] = actypes[DBAlert._ACTYPE_PROP];
            ACData oc[] = actypes[DBAlert._ACTYPE_OC];
            ACData qcv[] = actypes[DBAlert._ACTYPE_QCV];
            ACData qcq[] = actypes[DBAlert._ACTYPE_QCQ];
            ACData qcm[] = actypes[DBAlert._ACTYPE_QCM];
            ACData qc[] = actypes[DBAlert._ACTYPE_QC];
            ACData pv[] = actypes[DBAlert._ACTYPE_PV];
            ACData vm[] = actypes[DBAlert._ACTYPE_VM];
            ACData vd[] = actypes[DBAlert._ACTYPE_VD];
            ACData cd[] = actypes[DBAlert._ACTYPE_CD];
            ACData dec[] = actypes[DBAlert._ACTYPE_DEC];
            ACData de[] = actypes[DBAlert._ACTYPE_DE];
            ACData csi[] = actypes[DBAlert._ACTYPE_CSI];
            ACData cs[] = actypes[DBAlert._ACTYPE_CS];
            ACData conte[] = actypes[DBAlert._ACTYPE_CONTE];
            actypes = null;

            // Report on the raw change counts.
            _logSummary.writeParagraph1("Change count:"
                + " qcv " + qcv.length + ", qcq " + qcq.length
                + ", qcm " + qcm.length + ", qc " + qc.length
                + ", proto " + proto.length + ", pv " + pv.length
                + ", vm " + vm.length + ", vd " + vd.length
                + ", cd " + cd.length + ", con " + con.length
                + ", oc " + oc.length
                + ", prop " + prop.length + ", dec " + dec.length
                + ", de " + de.length + ", csi " + csi.length
                + ", cs " + cs.length + ", conte " + conte.length);
            _logSummary.writeParagraph2(timer.check());

            // Get the data associated to the changed data. This is done one
            // step at
            // a time using the following associations.
            // Object Class associate to Data Element Concepts
            // Permissible Values associate to Value Domains
            // Value Domains associate to Data Elements and Valid Values
            // Valid Values associate to Questions
            // Data Element Concepts associate to Data Elements.
            // Data Elements associate to Questions and Classification Scheme
            // Items
            // Questions associate to Modules and Forms/Templates
            // Forms/Templates associate to Contexts
            // Classification Scheme Items associate to Classification Schemes
            // Classification Schemes associate to Contexts.
            ACData pvm[] = ACData.merge(pv, _db.selectPVfromVM(vm));
            _logSummary.writeError(_db.getError());
            ACData vdm[] = ACData.merge(vd, _db.selectVDfromPV(pvm));
            _logSummary.writeError(_db.getError());
            ACData dem[] = ACData.merge(de, _db.selectDEfromVD(vdm));
            _logSummary.writeError(_db.getError());
            ACData ocm[] = ACData.merge(oc, _db.selectOCfromCON(con));
            _logSummary.writeError(_db.getError());
            ACData decm[] = ACData.merge(dec, _db.selectDECfromOC(ocm));
            _logSummary.writeError(_db.getError());
            ACData propm[] = ACData.merge(prop, _db.selectPROPfromCON(con));
            _logSummary.writeError(_db.getError());
            decm = ACData.merge(decm, _db.selectDECfromPROP(propm));
            _logSummary.writeError(_db.getError());
            dem = ACData.merge(dem, _db.selectDEfromDEC(decm));
            _logSummary.writeError(_db.getError());
            ACData qcvm[] = ACData.merge(qcv, _db.selectQCVfromVD(vdm));
            _logSummary.writeError(_db.getError());
            ACData qcqm[] = ACData.merge(qcq, _db.selectQCQfromQCV(qcvm));
            _logSummary.writeError(_db.getError());
            qcqm = ACData.merge(qcqm, _db.selectQCQfromVD(vdm));
            _logSummary.writeError(_db.getError());
            qcqm = ACData.merge(qcqm, _db.selectQCQfromDE(dem));
            _logSummary.writeError(_db.getError());
            ACData qcmm[] = ACData.merge(qcm, _db.selectQCMfromQCQ(qcqm));
            _logSummary.writeError(_db.getError());
            ACData qca[] = ACData.merge(qc, _db.selectQCfromQCM(qcmm));
            _logSummary.writeError(_db.getError());
            qca = ACData.merge(qca, _db.selectQCfromQCQ(qcqm));
            _logSummary.writeError(_db.getError());
            ACData protom[] = ACData.merge(proto, _db.selectPROTOfromQC(qcvm));
            _logSummary.writeError(_db.getError());
            protom = ACData.merge(protom, _db.selectPROTOfromQC(qcqm));
            _logSummary.writeError(_db.getError());
            protom = ACData.merge(protom, _db.selectPROTOfromQC(qcmm));
            _logSummary.writeError(_db.getError());
            protom = ACData.merge(protom, _db.selectPROTOfromQC(qca));
            _logSummary.writeError(_db.getError());
            ACData cdm[] = new ACData[0];
            _logSummary.writeError(_db.getError());
            ACData csim[] = ACData.merge(csi, _db.selectCSIfromDE(dem));
            _logSummary.writeError(_db.getError());
            csim = ACData.merge(csim, _db.selectCSIfromDEC(decm));
            _logSummary.writeError(_db.getError());
            csim = ACData.merge(csim, _db.selectCSIfromVD(vdm));
            _logSummary.writeError(_db.getError());
            ACData csm[] = ACData.merge(cs, _db.selectCSfromCSI(csim));
            _logSummary.writeError(_db.getError());
            ACData contem[] = ACData.merge(conte, _db.selectCONTEfromCS(csm));
            _logSummary.writeError(_db.getError());
            contem = ACData.merge(contem, _db.selectCONTEfromQC(qca));
            _logSummary.writeError(_db.getError());
            contem = ACData.merge(contem, _db.selectCONTEfromPROTO(protom));
            _logSummary.writeError(_db.getError());

            // If a CSI, CS or Form is specified, we can not get to a Context
            // directly from the Administered Components.
            if (rec_._alert.isCSIall() && rec_._alert.isCSall()
                            && rec_._alert.isFORMSall() && rec_._alert.isPROTOall())
            {
                cdm = ACData.merge(cd, _db.selectCDfromVD(vdm));
                _logSummary.writeError(_db.getError());
                cdm = ACData.merge(cdm, _db.selectCDfromDEC(decm));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromCD(cdm));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromVD(vdm));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromDE(dem));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromDEC(decm));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromCON(con));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromOC(ocm));
                _logSummary.writeError(_db.getError());
                contem = ACData.merge(contem, _db.selectCONTEfromPROP(propm));
                _logSummary.writeError(_db.getError());
            }

            // Turns out we don't want the Conceptual Domain right now.
            else
                cd = new ACData[0];

            // Report on the related counts.
            _logSummary.writeParagraph1("Related count:"
                + " qca " + ((qca == null) ? -1 : qca.length)
                + ", protom " + ((protom == null) ? -1 : protom.length)
                + ", pvm " + ((pvm == null) ? -1 : pvm.length)
                + ", vdm " + ((vdm == null) ? -1 : vdm.length)
                + ", cdm " + ((cdm == null) ? -1 : cdm.length)
                + ", propm " + ((propm == null) ? -1 : propm.length)
                + ", ocm " + ((ocm == null) ? -1 : ocm.length)
                + ", decm " + ((decm == null) ? -1 : decm.length)
                + ", dem " + ((dem == null) ? -1 : dem.length)
                + ", csim " + ((csim == null) ? -1 : csim.length)
                + ", csm " + ((csm == null) ? -1 : csm.length)
                + ", contem " + ((contem == null) ? -1 : contem.length));
            _logSummary.writeParagraph2(timer.check());

            // Scrub the lists and remove everything that is not part of the
            // Criteria.
            conte = ACData.clean(rec_._alert.getContexts(), conte);
            contem = ACData.clean(rec_._alert.getContexts(), contem);
            if (!rec_._alert.isCSIall())
            {
                csim = ACData.clean(rec_._alert.getSchemeItems(), csim);
                csi = ACData.clean(rec_._alert.getSchemeItems(), csi);
            }
            if (!rec_._alert.isCSall())
            {
                csm = ACData.clean(rec_._alert.getSchemes(), csm);
                cs = ACData.clean(rec_._alert.getSchemes(), cs);
            }
            if (!rec_._alert.isFORMSall())
            {
                qca = ACData.clean(rec_._alert.getForms(), qca);
                qc = ACData.clean(rec_._alert.getForms(), qc);
            }
            if (!rec_._alert.isPROTOall())
            {
                protom = ACData.clean(rec_._alert.getProtocols(), protom);
                proto = ACData.clean(rec_._alert.getProtocols(), proto);
            }

            // Report the scrubbed numbers.
            _logSummary.writeParagraph1("Clean count:" + " qca " + qca.length + ", protom "
                + protom.length + ", csim " + csim.length + ", csm "
                + csm.length + ", contem " + contem.length + ", conte "
                + conte.length);
            _logSummary.writeParagraph2(timer.check());

            // We are about to do a lot of searches using the related id for
            // each
            // record so take
            // a little time to resort everything.
            ACData.sortRelated(qca);
            ACData.sortRelated(protom);
            ACData.sortRelated(pvm);
            ACData.sortRelated(vdm);
            ACData.sortRelated(cdm);
            ACData.sortRelated(decm);
            ACData.sortRelated(dem);
            ACData.sortRelated(csim);
            ACData.sortRelated(csm);
            ACData.sortRelated(contem);
            ACData.sortRelated(propm);
            ACData.sortRelated(ocm);

            // Build the network for traversing possible related objects.
            ACDataLink lconte = new ACDataLink(contem, true);
            ACDataLink lvm = new ACDataLink(vm);
            ACDataLink lpv = new ACDataLink(pv);
            ACDataLink lvd = new ACDataLink(vd);
            ACDataLink lcon = new ACDataLink(con);
            ACDataLink lprop = new ACDataLink(prop);
            ACDataLink loc = new ACDataLink(oc);
            ACDataLink lde = new ACDataLink(de);
            ACDataLink ldec = new ACDataLink(dec);
            ACDataLink lcs = new ACDataLink(cs);
            ACDataLink lcsi = new ACDataLink(csi);
            ACDataLink lcd = new ACDataLink(cd);
            ACDataLink lqcv = new ACDataLink(qcv);
            ACDataLink lqcq = new ACDataLink(qcq);
            ACDataLink lqcm = new ACDataLink(qcm);
            ACDataLink lqc = new ACDataLink(qc);
            ACDataLink lproto = new ACDataLink(proto);
            ACDataLink chainPROP = new ACDataLink(propm);
            ACDataLink chainOC = new ACDataLink(ocm);
            ACDataLink chainPV = new ACDataLink(pvm);
            ACDataLink chainVD = new ACDataLink(vdm);
            ACDataLink chainDEC = new ACDataLink(decm);
            ACDataLink chainDE = new ACDataLink(dem);
            ACDataLink chainCS = new ACDataLink(csm);
            ACDataLink chainCSI = new ACDataLink(csim);
            ACDataLink chainCD = new ACDataLink(cdm);
            ACDataLink chainPROTO = new ACDataLink(protom);
            ACDataLink chainQC = new ACDataLink(qca);
            ACDataLink chainQCM = new ACDataLink(qcmm);
            ACDataLink chainQCQ = new ACDataLink(qcqm);
            ACDataLink chainQCV = new ACDataLink(qcvm);

            // In all cases we must be able to follow a principle change all the
            // way
            // to a Context.
            // If we can't it means the CSI, CS, FORM or Context was removed
            // from
            // the list in earlier
            // logic and the change should be ignored by the report.
            Stack<RepRows> results = new Stack<RepRows>();
            if (rec_._alert.isFORMSall() && rec_._alert.isPROTOall())
            {
                // Qualify results by CS and CSI (i.e. Forms/Templates is
                // "All").
                chainCS.add(lconte);
                chainCSI.add(chainCS);
                chainDE.add(chainCSI);
                chainDEC.add(chainDE);
                chainCD.add(lconte);
                chainDEC.add(chainCD);
                chainDEC.add(chainCSI);
                chainVD.add(chainDE);
                chainVD.add(chainCD);
                chainVD.add(chainCSI);
                chainPV.add(chainVD);
                chainPROP.add(chainDEC);
                chainOC.add(chainDEC);
                lcon.add(chainPROP);
                lcon.add(chainOC);
                lprop.add(chainDEC);
                loc.add(chainDEC);
                lvm.add(chainPV);
                lpv.add(chainVD);
                lvd.add(chainCD);
                lvd.add(chainDE);
                lvd.add(chainCSI);
                ldec.add(chainCD);
                ldec.add(chainDE);
                ldec.add(chainCSI);
                lde.add(chainCSI);
                lcsi.add(chainCS);
                lcs.add(lconte);
            }
            if (rec_._alert.isCSIall() && rec_._alert.isCSall())
            {
                // Qualify results by Forms/Templates (i.e. CS and CSI is
                // "All").
                chainPROTO.add(lconte);
                chainQC.add(lconte);
                chainQC.add(chainPROTO);
                chainQCM.add(chainPROTO);
                chainQCM.add(chainQC);
                chainQCQ.add(chainPROTO);
                chainQCQ.add(chainQCM);
                chainQCQ.add(chainQC);
                chainQCV.add(chainPROTO);
                chainQCV.add(chainQCQ);
                chainPV.add(chainVD);
                chainVD.add(chainQCV);
                chainVD.add(chainDE);
                chainDEC.add(chainDE);
                chainDE.add(chainQCQ);
                chainPROP.add(chainDEC);
                chainOC.add(chainDEC);
                lcon.add(chainPROP);
                lcon.add(chainOC);
                lprop.add(chainDEC);
                loc.add(chainDEC);
                lvd.add(chainQCV);
                lde.add(chainQCQ);
                lqcv.add(chainQCQ);
                lqcv.add(chainPROTO);
                lqcq.add(chainQCM);
                lqcq.add(chainQC);
                lqcq.add(chainPROTO);
                lqcm.add(chainQC);
                lqcm.add(chainPROTO);
                lqc.add(chainPROTO);
                lqc.add(lconte);
                lproto.add(lconte);
                lvm.add(chainPV);
                lpv.add(chainVD);
                lvd.add(chainDE);
                lvd.add(chainQCQ);
                ldec.add(chainDE);
            }
            if (rec_._alert.isCSIall() && rec_._alert.isCSall()
                            && rec_._alert.isFORMSall() && rec_._alert.isPROTOall())
            {
                chainDE.add(lconte);
                chainDEC.add(lconte);
                chainVD.add(lconte);
                chainPROP.add(lconte);
                chainOC.add(lconte);
                lcon.add(chainPROP);
                lcon.add(chainOC);
                lcon.add(lconte);
                lprop.add(chainDEC);
                lprop.add(lconte);
                loc.add(chainDEC);
                loc.add(lconte);
                lvd.add(lconte);
                ldec.add(lconte);
                lde.add(lconte);
                lcd.add(lconte);
            }
            chainPROP = null;
            chainOC = null;
            chainPV = null;
            chainVD = null;
            chainDE = null;
            chainDEC = null;
            chainCS = null;
            chainCSI = null;
            chainCD = null;
            chainPROTO = null;
            chainQC = null;
            chainQCM = null;
            chainQCQ = null;
            chainQCV = null;
            lconte = null;
            _logSummary.writeParagraph1("Linked changed and related records.");
            _logSummary.writeParagraph2(timer.check());

            lcon.follow(results, 0, lcon.getRange());
            lprop.follow(results, 0, lprop.getRange());
            loc.follow(results, 0, loc.getRange());
            lvm.follow(results, 0, lvm.getRange());
            lpv.follow(results, 0, lpv.getRange());
            lvd.follow(results, 0, lvd.getRange());
            lde.follow(results, 0, lde.getRange());
            ldec.follow(results, 0, ldec.getRange());
            lcs.follow(results, 0, lcs.getRange());
            lcsi.follow(results, 0, lcsi.getRange());
            lcd.follow(results, 0, lcd.getRange());
            lqcv.follow(results, 0, lqcv.getRange());
            lqcq.follow(results, 0, lqcq.getRange());
            lqcm.follow(results, 0, lqcm.getRange());
            lqc.follow(results, 0, lqc.getRange());
            lproto.follow(results, 0, lproto.getRange());
            ACData.remember(results, conte);
            _logSummary.writeParagraph1("Created data chains.");
            _logSummary.writeParagraph2(timer.check());

            // What ever is placed on the stack ("results") should be output to
            // a
            // file.
            dump(results, rec_);
            _logSummary.writeParagraph1("Created report.");
            _logSummary.writeParagraph2(timer.check());
            _logSummary.writeParagraph1("Processing for this Alert Definition.");
            _logSummary.writeParagraph2(timer.reset());

            // Did we do anything?
            if (_outRows == 0)
                _logSummary.writeParagraph1("No activity to report.");
            else
                _logSummary.writeParagraph1("Activity to report: " + _outRows + " rows.");

            // Force heap reclamation.
            proto = null;
            con = null;
            prop = null;
            oc = null;
            qcv = null;
            qcq = null;
            qcm = null;
            qc = null;
            pv = null;
            vm = null;
            vd = null;
            cd = null;
            dec = null;
            de = null;
            csi = null;
            cs = null;
            conte = null;
            qca = null;
            protom = null;
            pvm = null;
            vdm = null;
            cdm = null;
            propm = null;
            ocm = null;
            decm = null;
            dem = null;
            csim = null;
            csm = null;
            contem = null;
            lcon = null;
            lprop = null;
            loc = null;
            lvm = null;
            lpv = null;
            lvd = null;
            lde = null;
            ldec = null;
            lcs = null;
            lcsi = null;
            lcd = null;
            lqcv = null;
            lqcq = null;
            lqcm = null;
            lqc = null;
            lproto = null;
            results = null;
        }
        catch (Exception ex)
        {
        	ex.printStackTrace();        	
            _logSummary.writeError(ex.toString());
            _logger.error(ex.toString(), ex);
        }
        catch (OutOfMemoryError ex)
        {
        	ex.printStackTrace();        	
            _logSummary.writeError(ex.toString());
            _logger.error(ex.toString(), ex);
        }
    }

    /**
     * Using the Auto Run frequency and an anchor date, backup the appropriate
     * number of days to calculate a working start.
     *
     * @param rec_
     *        The Alert definition.
     * @param anchor_
     *        The anchor date (effectively becomes the end date for the query)
     * @return The new target date.
     */
    private Timestamp timeTravel(AlertRec rec_, Timestamp anchor_)
    {
        if (rec_.isFreqDay())
        {
            // Back up 24 hours.
            return new Timestamp(anchor_.getTime() - (1 * 24 * 60 * 60 * 1000));
        }
        if (rec_.isFreqWeek())
        {
            // Back up 7 days
            return new Timestamp(anchor_.getTime() - (7 * 24 * 60 * 60 * 1000));
        }
        if (rec_.isFreqMonth())
        {
            // Back up 1 month (31 days).
            return new Timestamp(anchor_.getTime() - (31 * 24 * 60 * 60 * 1000));
        }
        return null;
    }

    /**
     * Set the time portion of the date to 00:00:00.0 (midnight).
     *
     * @param time_
     *        The specified date and time.
     * @return The same date as provided with the time set to midnight.
     */
    private Timestamp setToMidnight(Timestamp time_)
    {
        String time = time_.toString().substring(0, 10);
        return Timestamp.valueOf(time + " 00:00:00.0");
    }

    /**
     * Get the list of Alert definitions that are active for the date specified.
     *
     * @return The list of alerts.
     */
    private AlertRec[] getAlertList()
    {    	
        AlertRec recs[] = null;
    	try {

        // Get the eligible Alerts.
        recs = _db.selectAlerts(_today);
        } catch (Exception ex) {
        	_logger.error("In AlertList Exception: "+_db+" today: "+_today);
        	ex.printStackTrace();
        }        
        // Did we get anything?
        if (recs != null && (recs.length == 0 || recs[0] == null))
            recs = null;
        return recs;

    }

    /**
     * Search for the DSRAlert.properties file on the current drive.
     *
     * @param path_
     *        private void findResources(String path_) { if (new File(path_ +
     *        File.separator + _RESOURCES).exists()) { _resourcePath = path_ +
     *        File.separator + _RESOURCES; return; } String list[] = new
     *        File(path_).list(); for (int ndx = 0; ndx < list.length; ++ndx) {
     *        String dir = path_ + File.separator + list[ndx]; File temp = new
     *        File(dir); if (temp.isDirectory()) {
     *        findResources(temp.getAbsolutePath()); if (_resourcePath.length() >
     *        0) return; } } }
     */

    /**
     * Load the configuration options from the resource/property file.
     *
     * @return 0 if successful, otherwise !0
     */
    private int getResources()
    {
        try
        {
            ResourceBundle prb = PropertyResourceBundle.getBundle(_RESOURCES);
            if (prb == null)
            {
                _logger.error("Can not find properties resource "
                    + _RESOURCES);
                return -1;
            }

            /* These values should have been read from the XML file during the Autorun Start

            if (_dsurl == null)
            {
                _dsurl = prb.getString(Constants._DBTNSNAME);
                _user = prb.getString(Constants._DBUSER);
                _pswd = prb.getString(Constants._DBPSWD);
            }
            */

            _version = prb.getString(Constants._APLVERS);
            _style = prb.getString(_STYLE).replaceAll("}", "}\n");
        }
        catch (MissingResourceException ex)
        {
            _logger.error("Can not find properties resource "
                + _RESOURCES, ex);
            _version = "unknown";
            _style = "";
        }
        
        _logger.info("Version " + _version);

        // Be sure we have a database connection.
        String errMsg = null;
        if (_db == null)
        {
            _db = DBAlertUtil.factory();
            int rc = -1;
            if ((_dsManual != null) && (_userNameManual != null))
            {
                DataSource ds = _dsManual;
                _dsurl = ds.getClass().getName() + " " + ds.toString();
                rc = _db.open(ds, _userNameManual);
            }
            else if (_dsurl != null)
                rc = _db.open(_dsurl, _user, _pswd);
            else
                _logger.error("Missing information required to created a database connection.");
            if (rc != 0)
            {
                errMsg = _db.getError();
                _db = null;
            }
            else
            {
                AutoProcessData apd = new AutoProcessData();
                apd.getOptions(_db);
                _adminEmail = apd._adminEmail;
                _statReportEmail = apd._statReportEmail;
                _threshold = apd._threshold;
                _adminIntro = apd._adminIntro;
                _adminIntroError = apd._adminIntroError;
                _adminName = apd._adminName;
                _emailAddr = apd._emailAddr;
                _emailHost = apd._emailHost;
                _emailUser = apd._emailUser;
                _emailPswd = apd._emailPswd;
                _http = apd._http;
                _dtd = apd._dtd;
                _subject = apd._subject;
                _work = apd._work;
                _dbname = apd._dbname;
            }
        }

        _logSummary = new AlertOutput(_work, _http, "RunLog_" + _id.replaceAll("[ .:\\-]", "_"), _version);
        if (errMsg != null) {
            _logSummary.writeError(errMsg);
        } else {
            _logger.info("\nExecuting createAuditReports() ...\n\n");        	
            createAuditReports();	//DEV - comment this out for testing (still has issue here)
        }

        return 0;
    }

    /**
     * Send the Log file to the system administrator.
     */
    private void sendLog()
    {
        String body = "<p>The following log was created using the Sentinel Alert Version "
            + _version + "</p>";

        String statBody = null;

        boolean errors = true;
        if (_logSummary == null)
        {
            // This is bad - a log file should ALWAYS be created.
            body += "<p><b>*** Alert Run Log file was not created.</b></p>";
            _logger.error(body);
        }
        else
        {
            errors = _logSummary.someErrors();

            // Send link to log file.
            String tempHTTP = _logSummary.getHttpLink();
            String tempPath = _logSummary.getPathLink();
            body += "<p>Please review the Summary Log from the caDSR Alert Run.</p>"
                + "<p><a href=\"" + tempPath + "\">" + tempPath + "</a></p>"
                + "<p><a href=\"" + tempHTTP + "\">" + tempHTTP + "</a></p>";

            // Send link to statistics report.
            if (_logAudits != null)
            {
                tempHTTP = _logAudits.getHttpLink();
                statBody = "<p>Following is the Audit Report link.</p>"
                    + "<p><a href=\"" + tempHTTP + "\">" + tempHTTP + "</a></p>";
                body += statBody;
            }
        }

        // Create subject.
        String subject;
        subject = _subject
            + ((_id.charAt(0) == 'A') ? " Auto Run" : " Manual Run") + " LOG"
            + ((errors) ? " with ERRORS" : "");

        body = "<html><body style=\"font-family: arial; font-size: 10pt\">" + body + "</body></html>";

        // Send email.
        for (int ndx = 0; ndx < _adminEmail.length; ++ndx)
        {
            sendEmail(errors, _adminEmail[ndx], _adminName, subject, body);
            _logger.info("Sent admin report to " + _adminEmail[ndx]);
        }

        if (statBody != null && _statReportEmail != null && _statReportEmail.length > 0)
        {
            statBody = "<html><body style=\"font-family: arial; font-size: 10pt\">" + statBody + "</body></html>";

            // Send email.
            for (int ndx = 0; ndx < _statReportEmail.length; ++ndx)
            {
                sendEmail(errors, _statReportEmail[ndx], _adminName, _subject + " Audits", statBody);
                _logger.info("Sent statistics report to " + _statReportEmail[ndx]);
            }
        }
    }

    /**
     * Send an email through the transport.
     *
     * @param hasErrors_
     *        True if errors occurred.
     * @param toEmail_
     *        The recipient email address.
     * @param toName_
     *        The recipient name.
     * @param subject_
     *        The email subject line.
     * @param body_
     *        The email body.
     */
    private void sendEmail(boolean hasErrors_, String toEmail_, String toName_,
        String subject_, String body_)
    {
        try
        {
            // Create mail Session.
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);

            // Create message.
            MimeMessage message = new MimeMessage(session);

            // Set priority, "1" is high, "3" is normal - this is where the
            // error flag comes in.
            message.addHeader("X-Priority", (hasErrors_) ? "1" : "3");

            // Set basics and give the administrator a quick clue about any
            // errors during the
            // run.
            message.setSubject(subject_);
            message.setFrom(new InternetAddress(_emailAddr, _adminName));
            message.setSentDate(new Timestamp(System.currentTimeMillis()));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(
                toEmail_, toName_));

            // Give the real location and the "http" location.
            if (body_.substring(0, 6).compareToIgnoreCase("<html>") == 0)
            {
                message.setContent(body_, "text/html");
            }
            else
            {
                message.setText(body_);
            }

            // Give to the mail server.
            message.saveChanges();
            Store store = null;
            Transport transport = session.getTransport("smtp");
            if (_emailUser == null || _emailUser.length() == 0)
            {
                transport.connect(_emailHost, null, null);
            }
            else
            {
                store = session.getStore("pop3");
                store.connect(_emailHost, _emailUser, _emailPswd);
                transport.connect(_emailHost, _emailUser, _emailPswd);
            }
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
            if (store != null)
                store.close();
        }

        catch (AuthenticationFailedException ex)
        {
        	ex.printStackTrace();        	
            String temp = ex.toString();
            _logSummary.writeError(temp);
            _logger.error(temp, ex);
        }
        catch (MessagingException ex)
        {
        	ex.printStackTrace();        	
            String temp = ex.toString();
            _logSummary.writeError(temp);
            _logger.error(temp, ex);
        }
        catch (UnsupportedEncodingException ex)
        {
        	ex.printStackTrace();        	
            String temp = ex.toString();
            _logSummary.writeError(temp);
            _logger.error(temp, ex);
        }
    }

    /**
     * Notify a process the report is ready.
     *
     * @param url_ the process URL
     */
    private void queueReportForProcess(String url_)
    {
        // recipient is a process, create new thread to process urls...
        if (url_ == null || url_.length() == 0)
        {
            _logAudits.writeError("A process notification is requested but the Process URL is missing.");
            return;
        }
        
        if (_http == null || _http.length() == 0)
        {
            _logAudits.writeError("A process notification to " + url_ + " is requested but the XML URL prefix is missing.");
            return;
        }
        
        if (_work == null || _work.length() == 0)
        {
            _logAudits.writeError("A process notification to " + url_ + " is requested but the Work Path is missing.");
            return;
        }
        
        if (_xmlFile == null || _xmlFile.length() == 0)
        {
            _logAudits.writeError("A process notification to " + url_ + " is requested but the XML File Name is missing.");
            return;
        }

        String xmlLink;
        xmlLink = _http + _xmlFile.substring(_work.length());
        xmlLink = xmlLink.replaceAll(" ", "%20");
        String processURL = renderProcessURL(url_) + xmlLink;
        AlertProcessThread processThread = new AlertProcessThread(processURL);
        processThread.start();
    }

    /**
     * Add the alert report URL to the process recipient. Must allow for the possibility the URL already contains
     * a variable list so we are appending to it OR this may be the only variable.
     *
     * @param processURL_
     * @return the modified URL
     */
    private static final String renderProcessURL(String processURL_)
    {
        return processURL_ + ((processURL_.indexOf("?") == -1) ? "?" : "&") + "alertreport=";
    }

    /**
     * Create a thread wrapper around the processing of a single process Alert.
     */
    private class AlertProcessThread extends Thread {
        private String _processURL;

        /**
         * Constructor
         *
         * @param url
         */
        public AlertProcessThread(String url)
        {
            _processURL = url;
            ++_urlRunCalls;
            incRunCnt();
        }

        public void run()
        {
            try
            {
                // open the process URL
                URL pURL = new URL(_processURL);
                HttpURLConnection http = (HttpURLConnection) pURL.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream()));
                String inputLine;
                String outputLine = _processURL + "<br/>\n";
                
                // Only an HTTP_OK is considered successful.
                Vector<String> msgs = (http.getResponseCode() == HttpURLConnection.HTTP_OK) ? _urlMsgsInfo : _urlMsgsErr;

                // read from the process URL and write the acknowledgement message to the log
                while ((inputLine = in.readLine()) != null)
                {
                    outputLine += inputLine.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "<br/>\n";
                }
                msgs.add("Notified Process URL : " + outputLine);

                in.close();
            }
            catch (MalformedURLException ex)
            {
            	ex.printStackTrace();            	
                _urlMsgsErr.add(ex.toString() + " " +  _processURL + "<br/>\n");
                _logger.error(ex.toString(), ex);
            }
            catch (IOException ex)
            {
            	ex.printStackTrace();	
                _urlMsgsErr.add(ex.toString() + " " + _processURL + "<br/>\n");
                _logger.error(ex.toString(), ex);
            }
            catch (Exception ex)
            {
            	ex.printStackTrace();            	
                _urlMsgsErr.add("Unexpected: " + ex.toString() + " " + _processURL + "<br/>\n");
                _logger.error(ex.toString(), ex);
            }
            finally
            {
                decRunCnt();
            }
        }
    }
    
    private synchronized void incRunCnt()
    {
        ++_urlRunCnt;
    }
    
    private synchronized void decRunCnt()
    {
        --_urlRunCnt;
    }
    
    private synchronized int getRunCnt()
    {
        return _urlRunCnt;
    }

    /**
     * Look for process recipients
     *
     * @param receipients
     * @return true if sending to a process
     */
    private boolean hasProcessRecipient(String[] receipients)
    {
        for (int i = 0; i < receipients.length; i++)
        {
            if (receipients[i].startsWith("http://") || (receipients[i].startsWith("https://")))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Look for email recipients
     *
     * @param receipients
     * @return true if sending an email
     */
    private boolean hasEmailRecipient(String[] receipients)
    {
        for (int i = 0; i < receipients.length; i++)
        {
            if (receipients[i].indexOf("@") >= 0)
                return true;

            if (receipients[i].startsWith("http://") || receipients[i].startsWith("http://"))
                continue;

            String email = _db.selectEmailFromUser(receipients[i]);
            if (email != null && email.indexOf("@") >= 0)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Dump the output for use by Process recipients
     *
     * @param xmlSave_
     *        A clone of the report content
     * @param rec_
     *        The processing record for the report.
     * @param cemail_
     *        The creator email address
     */
    private void dumpProcessRecipients(Stack<RepRows> xmlSave_, ProcessRec rec_, String cemail_)
    {
        // if we have a process then the fileneme is changed to end with xml
        _xmlFile = rec_._reportFile.replace(".html", ".xml");

        // Create an array of all the changeNames to keyNames

        try
        {
            FileOutputStream _fout = new FileOutputStream(_xmlFile, false);
            // Create an output factory

            ACXMLData xmlWriter = new ACXMLData(_fout, rec_._alert, _db, xmlSave_, _dbname, cemail_, _version, _start, _end);
            //xmlWriter.writeXMLWithJDOM();
            xmlWriter.writeXMLWithSTAX(_dtd);
            // Create an entry in the log file
            _logSummary.writeParagraph1("Output XML File is");
            // write the location of the file
            _logSummary.writeParagraph2(_xmlFile);
            String xmlLink = _http + _xmlFile.substring(_work.length());
            xmlLink = xmlLink.replaceAll(" ", "%20");
            // write the URL for the xml file
            _logSummary.writeParagraph2("<a href=\"" + xmlLink + "\" target=\"_blank\">" + xmlLink + "</a>");

        }
        catch (FileNotFoundException ex)
        {
        	ex.printStackTrace();
            _logSummary.writeError("Error opening auto alert dump xml file: " + _xmlFile);
            _logger.error(ex.toString(), ex);
        }
    }

    /**
     * Exists solely to avoid compiler warnings - Java has some issues.
     * @param x an object
     * @return a casted object
     */
    @SuppressWarnings("unchecked")
    private static Stack<RepRows> cStack(Object x)
    {
        return (Stack<RepRows>)x;
    }

    /**
     * Dump the report records to output.
     *
     * @param save_
     *        The report content.
     * @param rec_
     *        The processing record for the report.
     */
    private void dump(Stack<RepRows> save_, ProcessRec rec_)
    {
        boolean hasProcesses = hasProcessRecipient(rec_._alert.getRecipients());
        boolean hasEmails = hasEmailRecipient(rec_._alert.getRecipients());

        // Creators email address.
        String cemail = _db.selectEmailFromUser(rec_._alert.getCreator());

        Stack<RepRows> xmlSave = null;
        if (hasProcesses)
        {
            // Copy the stack to another stack as it is also required for xml generation
            xmlSave = cStack(save_.clone());
        }

        // Do all these only if the alert has a receipient which is an email
        if (hasEmails)
        {
            dumpEmailRecipients(save_, rec_, cemail);
        }

        /*
         * We check if there is a process URL then an XML file is generated. ACXMLData is used to generate the XML file. JDOM is used to generate the xml in
         * memory and then serialize to a file later.
         */
        if (hasProcesses)
        {
            dumpProcessRecipients(xmlSave, rec_, cemail);
        }
    }

    /**
     * Write Process messages to log file.
     *
     */
    private void writeUrlMsgs()
    {
        // Nice to know how many times we tried.
        if (_urlRunCalls > 0)
            _logSummary.writeHeading("Process URL Run invoked " + _urlRunCalls + " times.");

        // Must wait for all URL Process threads to finish
        while (getRunCnt() > 0)
        {
            try
            {
                Thread.sleep(10000);
                _logger.info("Waiting on Process URL threads to finish.");
            }
            catch (InterruptedException e)
            {
                break;
            }
        }

        // Move information messages to administrator log.
        if (_urlMsgsInfo.size() > 0)
        {
            _logSummary.writeHeading("Process URL Information Messages ...");
            for (int i = 0; i < _urlMsgsInfo.size(); ++i)
            {
                _logSummary.writeParagraph0(_urlMsgsInfo.get(i));
            }
            _urlMsgsInfo = new Vector<String>();
        }
        
        // Move error messages to administrator log.
        if (_urlMsgsErr.size() > 0)
        {
            _logSummary.writeHeading("Process URL Error Messages ...");
            for (int i = 0; i < _urlMsgsErr.size(); ++i)
            {
                _logSummary.writeError(_urlMsgsErr.get(i));
            }
            _urlMsgsErr = new Vector<String>();
        }
    }

    // Class data elements.
    private String              _dsurl;

    private String              _user;

    private String              _pswd;

    private String              _dbname;

    private String              _version;

    private int                 _outRows;

    private String              _work;

    private DBAlert             _db;

    private String[]            _recipients;

    private ReportItem[][]      _reports;

    private String              _subject;

    private String[]            _adminEmail;

    private String[]           _statReportEmail;

    private String              _adminName;

    private String              _adminIntro;

    private String              _adminIntroError;

    private String              _emailAddr;

    private String              _emailHost;

    private String              _emailUser;

    private String              _emailPswd;

    private String              _http;

    private String              _dtd;

    private String              _style;

    private Timestamp           _start;

    private Timestamp           _end;

    private Timestamp           _today;

    private String              _id;

    private int                 _outForm;

    private int                 _threshold;

    private AlertOutput         _logSummary;

    private AlertOutput         _logAudits;

    private Vector<String> _urlMsgsInfo;

    private Vector<String> _urlMsgsErr;
    
    private int _urlRunCnt;
    
    private int _urlRunCalls;

    private DataSource _dsManual;
    private String _userNameManual;

    private String[] _audits;

    private String[] _auditTitles;

    private boolean _updateRunDate;

    private String _xmlFile;

    private static final Logger _logger;	// = Logger.getLogger(AutoProcessAlerts.class.getName());

    static {
    	_logger = Logger.getLogger(AutoProcessAlerts.class.getName());
    }
    private static final String _RESOURCES       = "gov.nih.nci.cadsr.sentinel.DSRAlert";

    /**
     * The Auto Process CSS style definitions used for the Alert report coded in
     * DSRAlert.properties.
     */
    private static final String _STYLE           = "AutoProcess.style";
}