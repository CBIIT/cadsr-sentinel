/*L
 * Copyright ScenPro Inc, SAIC-F
 *
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See http://ncip.github.com/cadsr-sentinal/LICENSE.txt for details.
 */

// Copyright (c) 2004 ScenPro, Inc.

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/tool/ACData.java,v 1.16 2008-04-28 22:25:22 hebell Exp $
// $Name: not supported by cvs2svn $

package gov.nih.nci.cadsr.sentinel.tool;

import gov.nih.nci.cadsr.sentinel.database.DBAlert;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Stack;
import java.util.Vector;

/**
 * This is the working class for the collection, assimilation and processing of
 * caDSR data by the Sentinel Alert Auto Process server. This a collaborative
 * class with DBAlert as there is specialized SQL for an Alert.
 *
 * @author Larry Hebel
 * @version 1.0
 */

public class ACData
{
    /**
     * Constructor.
     */
    public ACData()
    {
        _debug = false;
        _resolveNames = true;
    }

    /**
     * Compare two records for logical equality. Ignore the category and context
     * name.
     *
     * @param rec_
     *        The other record.
     * @return true the record are essentially the same.
     */
    public boolean isEquivalent(ACData rec_)
    {
        if (!_tableCode.equals(rec_._tableCode))
            return false;
        if (!_idseq.equals(rec_._idseq))
            return false;
        if (!_contextID.equals(rec_._contextID))
            return false;
        if (!_relatedID.equals(rec_._relatedID))
            return false;
        if (_level != rec_._level)
            return false;
        if (_publicID != rec_._publicID)
            return false;
        if (!_name.equals(rec_._name))
            return false;
        if (!((_version == null && rec_._version == null) || (_version != null
            && rec_._version != null && _version.equals(rec_._version))))
            return false;

        // If one has a note and the other not, let's keep the text.
        if (_note == null)
        {
            if (rec_._note != null)
                _note = rec_._note;
        }
        else if (rec_._note == null)
        {
            rec_._note = _note;
        }

/*
        if (!_created.equals(rec_._created))
            return false;
        if (!_creator.equals(rec_._creator))
            return false;
        if (!((_modified == null && rec_._modified == null) || (_modified != null
            && rec_._modified != null && _modified.equals(rec_._modified))))
            return false;
        if (!((_modifier == null && rec_._modifier == null) || (_modifier != null
            && rec_._modifier != null && _modifier.equals(rec_._modifier))))
            return false;
        if (!((_note == null && rec_._note == null) || (_note != null
            && rec_._note != null && _note.equals(rec_._note))))
            return false;
*/
        return true;
    }

    /**
     * Return the byte representation for the level code.
     *
     * @param val_
     *        A 'p' or 's'.
     * @return The bytes for output.
     */
    static private String dumpConvertLevel(char val_)
    {
        if (val_ == 'p')
            return "Changes&nbsp;to";
        else
            return "Associated&nbsp;To";
    }

    /**
     * Return the string representation for a String with special values for
     * 'null' and 'empty'.
     *
     * @param val_
     *        A string.
     * @return The String for output.
     */
    static private String dumpConvertString(String val_)
    {
        if (val_ == null)
            return "<i>&lt;null&gt;</i>";
        else if (val_.length() == 0)
            return "<i>&lt;empty&gt;</i>";
        else
            return AlertRec.getHTMLString(val_);
    }

    /**
     * Return the byte representation for a String. Use special values for
     * 'null' and 'empty' and replace all blanks with "&nbsp;".
     *
     * @param val_
     *        A string.
     * @return The bytes for output.
     */
    static private String dumpConvertBlanks(String val_)
    {
        if (val_ == null)
            return "<i>&lt;null&gt;</i>";
        else if (val_.length() == 0)
            return "<i>&lt;empty&gt;</i>";
        else
            return AlertRec.getHTMLString(val_).replaceAll(" ", "&nbsp;");
    }

    /**
     * Return the byte representation for a String with special values for
     * 'null' and 'empty'.
     *
     * @param val_
     *        An array of strings to concatenate into a single String.
     * @return The bytes for output.
    static private byte[] dumpConvert(String val_[])
    {
        if (val_ == null)
            return "<i>&lt;null&gt;</i>".getBytes();
        else if (val_.length == 0)
            return "<i>&lt;empty&gt;</i>".getBytes();

        String result = val_[0];
        if (result == null)
            result = "<i>(null)</i>";
        for (int ndx = 1; ndx < val_.length; ++ndx)
        {
            result = result
                + "<br>"
                + ((val_[ndx] == null || val_[ndx].length() == 0) ? "&nbsp;"
                    : AlertRec.getHTMLString(val_[ndx]));
        }
        return result.getBytes();
    }
     */

    /**
     * Return the byte representation for a String with null and empty as
     * "&nbsp;".
     *
     * @param val_
     *        A string.
     * @return The byte representation.
     */
    static private String dumpConvertString2(String val_)
    {
        if (val_ == null)
            return "&nbsp;";
        else if (val_.length() == 0)
            return "&nbsp;";
        else
            return AlertRec.getHTMLString(val_);
    }

    /**
     * Return the byte representation for a String with null and empty as
     * "&nbsp;"
     *
     * @param val_
     *        A string.
     * @param prefix_
     *        A prefix to append to val_ when val_ is not null or empty.
     * @return The byte representation.
     */
    static private byte[] dumpConvert3(String val_, String prefix_)
    {
        if (val_ == null)
            return "&nbsp;".getBytes();
        else if (val_.length() == 0)
            return "&nbsp;".getBytes();

        String temp = prefix_ + AlertRec.getHTMLString(val_);
        return temp.getBytes();
    }

    /**
     * Return the byte representation for an int with 0 as "&nbsp;".
     *
     * @param val_
     *        An integer value.
     * @return The byte representation.
     */
    static private byte[] dumpConvert2(int val_)
    {
        if (val_ == -1)
            return "&nbsp;".getBytes();
        else
            return String.valueOf(val_).getBytes();
    }

    /**
     * Trim the report stack to contain only rows of interest.
     *
     * @param save_ The original full report content.
     * @param sectLimit_
     *
     * @return The trimmed report stack.
     */
    static public Stack<RepRows> dumpTrim(Stack<RepRows> save_, int sectLimit_)
    {
        // If the report is to contain everything then just return the original
        // stack.
        if (sectLimit_ == 9)
            return save_;

        Stack<RepRows> report = new Stack<RepRows>();

        // Don't waste time with an empty list.
        if (save_ == null || save_.empty())
            return report;

        // This represents the longest association chain that could be created.
        int indentLimit = (sectLimit_ == 9) ? 100 : sectLimit_;

        while (!save_.empty())
        {
            RepRows val = save_.pop();
            if (val._indent > indentLimit && (val._indent != 1 || !val._rec._tableCode.equals("conte")))
                continue;
            report.add(0, val);
        }

        return report;
    }

    /**
     * Test for a primary record type.
     *
     * @return true if a primary record.
     */
    public boolean isPrimary()
    {
        return (_level == 'p');
    }

    /**
     * Write the report introduction when multiple part report files are created.
     *
     * @param name_ The Alert Report name.
     * @param namePattern_ The name pattern of the part files.
     * @param threshold_ The configuration threshold.
     * @param parts_ The number of report parts.
     * @param fout_ The main output report file.
     * @throws IOException When an I/O problem occurs.
     */
    static public void dumpParts(String name_, String namePattern_, int threshold_, int parts_, FileOutputStream fout_) throws IOException
    {
        String intro =
            "<html>\n"
            + "<head>\n"
            + "<title>Multi-part Alert Report for " + name_ + "</title>\n"
            + "</title>\n"
            + "<style>\n"
            + "BODY { font-family: arial; font-size: 10pt }\n"
            + "P { font-family: arial; font-size: 10pt }\n"
            + "OL { font-family: arial; font-size: 10pt }\n"
            + "LI { font-family: arial; font-size: 10pt; margin-top: 0.1in; margin-bottom: 0.1in }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<p><b>Alert Definition: " + name_ + "</b></p>"
            + "<p>This report exceeds the configuration threshold of " + threshold_ + " rows. "
            + "Consequently the report has been split into " + parts_ + " parts, each of which "
            + "may be opened using the following links.</p>\n<ol>\n";
        fout_.write(intro.getBytes());
        for (int ndx = 0; ndx < parts_; ++ndx)
        {
            String url = namePattern_.replace("{0}", Integer.toString(ndx + 1));
            url = "<li><a href=\"" + url + "\" target=\"_blank\">" + url + "</a></li>\n";
            fout_.write(url.getBytes());
        }
        fout_.write("</ol>\n</body>\n</html>\n".getBytes());
    }

    /**
     * Be sure the Version number has a decimal point.
     *
     * @param ver_ The version from the database.
     * @return The version is ".0" appended if needed.
     */
    private static String fixVersion(String ver_)
    {
        if (ver_.indexOf('.') > -1)
            return ver_;
        return ver_ + ".0";
    }

    /**
     * Dump the results in HTML format to an output stream. The form is in a
     * complex spreadsheet with sub-tables and merged columns.
     *
     * @param db_
     *        A database object for an open connection to a caDSR.
     * @param save_
     *        A stack containing the report results. (Note this is a LIFO
     *        stack.)
     * @param rows_
     *        The initial row counter value. Use zero (0) to see "Row 1" on the
     *        output.
     * @param fout_
     *        The file output stream.
     * @return The number of rows output.
     * @throws java.io.IOException
     *         When a problem exists with the output file.
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpHeader2 dumpHeader2()
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpFooter2 dumpFooter2()
     */
    static public int dumpDetail2(DBAlert db_, Stack<RepRows> save_, int rows_,
                                  FileOutputStream fout_) throws IOException
    {
        // Don't waste time with an empty list.
        if (save_ == null || save_.empty())
            return 0;

        // This represents the longest association chain that could be created.
        int sections[] = new int[20];
        int count;
        for (count = 0; count < sections.length; ++count)
            sections[count] = 0;
        count = 0;
        int lastIndent = 0;

        // Dump the stack.
        CDEBrowserAPI browser = new CDEBrowserAPI(db_.getConnection());
        while (!save_.empty())
        {
            RepRows val = save_.pop();
            val._rec.resolveNames(db_);
            ++count;

            // Handle the group/section number column
            if (lastIndent == val._indent)
                ++(sections[val._indent]);
            else if (lastIndent > val._indent)
                ++(sections[val._indent]);
            else
                sections[val._indent] = 1;
            lastIndent = val._indent;

            // Output the section header
            fout_.write(formatSection(browser, ++rows_, val._rec._level, val._indent, sections, val._rec._tableCode, val._rec._idseq, val._rec._table, val._rec._name).getBytes());

            // Output basic record information.
            fout_.write("\t\t<tr><td colspan=\"2\">&nbsp;</td>".getBytes());
            fout_.write(formatBasicProperties(val._rec._publicID, val._rec._version, val._rec._modifier, val._rec._modified, val._rec._creator, val._rec._created).getBytes());
            fout_.write("</tr>\n".getBytes());

            // Output the comment / change note if it's present.
            if (val._rec._note != null && val._rec._note.length() > 0)
            {
                fout_.write(new String("\t\t<tr><td colspan=\"2\">&nbsp;</td><td colspan=\"6\">Comment/Change&nbsp;Note:&nbsp;"
                    + dumpConvertString2(val._rec._note)
                    + "</td></tr>\n").getBytes());
            }

            // Output change details if this is the primary record and not an "associated" record.
            if (val._rec._level == 'p')
            {
                fout_.write("\t\t<tr><td colspan=\"2\">&nbsp;</td><td colspan=\"6\">\n".getBytes());
                fout_.write(formatChangeHistory(val._rec._changes, val._rec._modified, val._rec._dates, val._rec._chgby, val._rec._old, val._rec._new).getBytes());
                fout_.write("</td></tr>\n".getBytes());
            }
        }

        return count;
    }

    /**
     * Format the ACData record into HTML output for display to the user. The resulting HTML uses
     * embedded styles to control the visual display and not classes. This ensures a consistent display
     * with the Alert Report output.
     *
     * @param db_ The database connection for alerts.
     *
     * @return the HTML string.
     */
    public String formatACHistoryHTML(DBAlert db_)
    {
        resolveNames(db_);

        String html = "";
        html = html + "<p>" + _table + ":&nbsp;" + _name + "</p>\n";
        html = html
            + "<table style=\"border-collapse: collapse; margin: 0.1in 0.1in 0.1in 0.1in\"><colgroup>"
            + "<col style=\"padding: 0.1in 0.1in 0.1in 0.1in\"/>"
            + "<col style=\"padding: 0.1in 0.1in 0.1in 0.1in\"/>"
            + "<col style=\"padding: 0.1in 0.1in 0.1in 0.1in\"/>"
            + "<col style=\"padding: 0.1in 0.1in 0.1in 0.1in\"/>"
            + "<col style=\"padding: 0.1in 0.1in 0.1in 0.1in\"/>"
            + "<col style=\"padding: 0.1in 0.1in 0.1in 0.1in\"/>"
            + "</colgroup><tbody />\n"
            + formatBasicProperties(_publicID, _version, _modifier, _modified, _creator, _created)
            + "</table>\n";
        html = html + formatChangeHistory(_changes, _modified, _dates, _chgby, _old, _new);
        return html;
    }

    private static String formatSection(
                    CDEBrowserAPI browser_, int row_, char level_, int indent_, int[] sections_, String tableCode_, String idseq_, String table_, String name_)
    {
        String display = (level_ == 'p') ? "pstripe" : "stripe";
        String sectrow = "\t\t<tr class=\"" + display + "\">";

        sectrow = sectrow
            + "<td class=\""
            + display
            + "\" title=\"Row Number\">"
            + String.valueOf(row_)
            + "</td><td class=\""
            + display
            + "\" title=\"Group Number\">";

        sectrow = sectrow + String.valueOf(sections_[0]);
        for (int ndx = 1; ndx <= indent_; ++ndx)
        {
            sectrow = sectrow + "." + String.valueOf(sections_[ndx]);
        }
        sectrow = sectrow + "</td>"
            + "</td><td class=\"" + display + "\" colspan=\"";

        String detailsURL = null;
        if (browser_.isPresent())
        {
            if (tableCode_.compareTo("de") == 0)
            {
                detailsURL = browser_.getLinkForDE(idseq_);
            }
            else if (tableCode_.compareTo("oc") == 0)
            {
                detailsURL = browser_.getLinkForOC(idseq_);
            }
            if (detailsURL != null)
            {
                detailsURL = "<td class=\""
                    + display
                    + "\" align=\"right\"><a target=\"_blank\" href=\""
                    + detailsURL
                    + "\">(Details)</a></td>";
                sectrow = sectrow + "5";
            }
            else
                sectrow = sectrow + "6";
        }
        else
            sectrow = sectrow + "6";

        sectrow = sectrow
            + "\">"
            + dumpConvertLevel(level_)
            + "&nbsp;"
            + table_
            + ":&nbsp;"
            + dumpConvertString(name_);

        if (detailsURL != null)
        {
            sectrow = sectrow + detailsURL;
        }
        sectrow = sectrow + "</td></tr>\n";

        return sectrow;
    }

    /**
     * Format the history information into an HTML string to be used in a &lt;table&gt; element. The
     * arrays must all be equal length and should be sorted ascending or descending on the "dates_"
     * and then the "changes_" to provide consistency to the output.
     *
     * @param changes_ The list of attribute names
     * @param modified_ The modified date from the record
     * @param dates_ The dates of each change
     * @param chgby_ The user that made the change
     * @param old_ The old value of the attribute
     * @param new_ The new value for the attribute
     * @return HTML formatted result
     */
    static public String formatChangeHistory(String[] changes_, Timestamp modified_, Timestamp[] dates_, String[] chgby_, String[] old_, String[] new_)
    {
        String html = "<table style=\"border-collapse: collapse\">\n\t<colgroup><col /><col /><col /></colgroup>"
            + "\n\t<tbody style=\"padding: 0.05in 0.1in 0.05in 0.1in\" />\n\t<tr>"
            + "\n\t\t<td style=\"border-bottom: 1px solid black; font-weight: bold\">Attribute&nbsp;Name</td>"
            + "\n\t\t<td style=\"border-bottom: 1px solid black; font-weight: bold\">Old&nbsp;Value</td>"
            + "\n\t\t<td style=\"border-bottom: 1px solid black; font-weight: bold\">New&nbsp;Value</td>\n\t</tr>";

        if (changes_ == null || changes_.length == 0)
        {
            html = html + "\n\t<tr>";
            if (modified_ == null)
                html = html + "\n\t\t<td colspan=\"3\">(New&nbsp;Record)";
            else
                html = html + "\n\t\t<td colspan=\"3\"><i>(Details&nbsp;not&nbsp;available.)</i>";
            html = html + "</td>\n\t</tr>";
        }
        else
        {
            Timestamp dchg = dates_[0];
            String chgby = chgby_[0];
            html = html + formatActivityMarker(dchg, chgby);
            for (int chgcnt = 0; chgcnt < changes_.length; ++chgcnt)
            {
                if (dchg.compareTo(dates_[chgcnt]) != 0)
                {
                    dchg = dates_[chgcnt];
                    chgby = chgby_[chgcnt];
                    html = html + formatActivityMarker(dchg, chgby);
                }
                html = html + "\n\t<tr>\n\t\t<td title=\"Attribute Name\">"
                    + dumpConvertBlanks(changes_[chgcnt])
                    + "</td>\n\t\t<td title=\"Old Value\" style=\"border-left: 1px solid black\">"
                    + dumpConvertString(old_[chgcnt])
                    + "</td>\n\t\t<td title=\"New Value\" style=\"border-left: 1px solid black\">"
                    + dumpConvertString(new_[chgcnt])
                    + "</td>\n\t</tr>";
            }
        }
        html = html + "\n</table>\n";
        return html;
    }

    private static String formatBasicProperties(int pid_, String vers_, String mby_, Timestamp mdate_, String cby_, Timestamp cdate_)
    {
        String html = "";
        if (pid_ > -1)
        {
            html = html + "<td>Public&nbsp;ID<br>" + pid_ + "</td>";
        }
        else
        {
            html = html + "<td><span style=\"color: #aaaaaa\">Public&nbsp;ID<br>N/A</span></td>";
        }
        if (vers_ != null && vers_.length() > 0)
        {
            html = html + "<td>Version<br>" + fixVersion(vers_) + "</td>";
        }
        else
        {
            html = html + "<td><span style=\"color: #aaaaaa\">Version<br>N/A</span></td>";
        }
        html = html + "<td>Modified&nbsp;By<br>"
            + ((mby_ == null) ? "<i>&lt;null&gt;</i>" : mby_)
            + "</td><td>Modified&nbsp;Date<br>"
            + AlertRec.dateToString(mdate_, true, true)
            + "</td><td>Created&nbsp;By<br>"
            + cby_
            + "</td><td>Created&nbsp;Date<br>"
            + AlertRec.dateToString(cdate_, true, true)
            + "</td>";

        return html;
    }

    /**
     * Output the activity marker separator in the Attribute/Old/New table.
     *
     * @param dchg_
     *            The date to display on the marker.
     * @param chgby_
     *            The user name to display on the marker.
     * @return The string separator marker.
     */
    private static String formatActivityMarker(Timestamp dchg_, String chgby_)
    {
        return "\n\t<tr>\n\t\t<td title=\"Activity\" colspan=\"3\" style=\"border-top: black solid 1px; border-bottom: black solid 1px; background-color: #eeeeee\">"
            + dumpConvertString(chgby_)
            + "&nbsp;made&nbsp;the&nbsp;following&nbsp;changes&nbsp;on&nbsp;"
            + AlertRec.dateToString(dchg_, true, true)
            + "</td>\n\t</tr>";
    }

    /**
     * Dump the results in HTML format to an output stream. The form is in a
     * simple spreadsheet row and column list.
     *
     * @param db_
     *        A database object for an open connection to a caDSR.
     * @param save_
     *        A stack containing the report results. (Note this is a LIFO
     *        stack.)
     * @param rows_
     *        The initial row counter value. Use zero (0) to see "Row 1" on the
     *        output.
     * @param fout_
     *        The file output stream.
     * @return The number of rows output.
     * @throws java.io.IOException
     *         When a problem exists with the output file.
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpHeader1 dumpHeader1()
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpFooter1 dumpFooter1()
     */
    static public int dumpDetail1(DBAlert db_, Stack save_, int rows_,
                                  FileOutputStream fout_) throws IOException
    {
        // Don't waste time with an empty list.
        if (save_ == null || save_.empty())
            return 0;

        // This represents the longest association chain that could be created.
        int maxSections = 20;
        int sections[] = new int[maxSections];
        int count;
        for (count = 0; count < sections.length; ++count)
            sections[count] = 0;
        count = 0;
        int lastIndent = 0;

        // Set this variable to zero (0) to see only the Primary changes. The
        // limit controls how "deep"
        // to show the associated records.
        int indentLimit = maxSections;

        // Dump the stack.
        while (!save_.empty())
        {
            RepRows val = (RepRows) save_.pop();
            if (val._indent > indentLimit)
                continue;
            val._rec.resolveNames(db_);
            ++count;
            if (val._rec._level == 'p' && indentLimit > 0)
                fout_.write("\t\t<tr class=\"pstripe\">".getBytes());
            else if ((rows_ % 2) == 1)
                fout_.write("\t\t<tr class=\"stripe\">".getBytes());
            else
                fout_.write("\t\t<tr>".getBytes());
            fout_.write("<td title=\"Row Number\">".getBytes());
            fout_.write(dumpConvertString(String.valueOf(++rows_)).getBytes());
            fout_.write("</td><td title=\"Group Number\">".getBytes());

            // Handle the group/section number column
            if (lastIndent == val._indent)
                ++(sections[val._indent]);
            else if (lastIndent > val._indent)
                ++(sections[val._indent]);
            else
                sections[val._indent] = 1;
            fout_.write(dumpConvertString(String.valueOf(sections[0])).getBytes());
            for (int ndx = 1; ndx <= val._indent; ++ndx)
            {
                fout_.write(".".getBytes());
                fout_.write(dumpConvertString(String.valueOf(sections[ndx])).getBytes());
            }
            lastIndent = val._indent;

            fout_.write("</td><td title=\"Title\">".getBytes());
            int colcnt;
            if (_debug)
            {
                colcnt = 20;
                fout_.write(dumpConvertString(String.valueOf(val._rec._level)).getBytes());
                fout_.write("</td><td>".getBytes());
                fout_.write(dumpConvertString(String.valueOf(val._rec._category)).getBytes());
                fout_.write("</td><td>".getBytes());
                fout_.write(dumpConvertString(val._rec._contextID).getBytes());
                fout_.write("</td><td>".getBytes());
                fout_.write(dumpConvertString(val._rec._relatedID).getBytes());
                fout_.write("</td><td>".getBytes());
                fout_.write(dumpConvertString(val._rec._idseq).getBytes());
                fout_.write("</td><td>".getBytes());
                fout_.write(dumpConvertString(val._rec._table).getBytes());
            }
            else
            {
                colcnt = 16;
                fout_.write(dumpConvertLevel(val._rec._level).getBytes());
                fout_.write("</td><td title=\"Type\">".getBytes());
                fout_.write(dumpConvertString(val._rec._table).getBytes());
            }
            String temp = "</td><td title=\"Name / Value\"><p class=\"i"
                + val._indent + "\">";
            fout_.write(temp.getBytes());
            fout_.write(dumpConvertString(val._rec._name).getBytes());
            fout_.write("</p></td><td title=\"Public ID\">".getBytes());
            fout_.write(dumpConvert2(val._rec._publicID));
            fout_.write("</td><td title=\"Version\">".getBytes());
            fout_.write(dumpConvertString2(val._rec._version).getBytes());
            fout_.write("</td><td title=\"Context\">".getBytes());
            if (_debug)
                fout_.write(dumpConvertString(val._rec._context).getBytes());
            else
                fout_.write(dumpConvert3(val._rec._context,
                    ((val._rec._category == 1) ? "Owned" : "Used")
                        + "&nbsp;By<br>"));
            fout_.write("</td><td title=\"Modified By\">".getBytes());
            fout_.write(dumpConvertString2(val._rec._modifier).getBytes());
            fout_.write("</td><td title=\"Modified Date\">".getBytes());
            fout_.write(dumpConvertString2(AlertRec.dateToString(val._rec._modified,
                true, true)).getBytes());
            fout_.write("</td><td title=\"Created By\">".getBytes());
            fout_.write(dumpConvertString(val._rec._creator).getBytes());
            fout_.write("</td><td title=\"Created Date\">".getBytes());
            fout_.write(dumpConvertString(AlertRec.dateToString(val._rec._created,
                true, true)).getBytes());
            fout_.write("</td><td title=\"Change Note / Comment\">".getBytes());
            fout_.write(dumpConvertString(val._rec._note).getBytes());
            fout_.write("</td><td title=\"Change\">".getBytes());
            if (val._rec._changes == null || val._rec._changes.length == 0)
            {
                if (val._rec._level == 'p')
                {
                    if (val._rec._modified == null)
                        fout_.write("(new record)</td>".getBytes());
                    else
                        fout_.write("<i>(unknown)</i></td>".getBytes());
                }
                else
                    fout_.write("&nbsp;</td>".getBytes());
                fout_
                    .write("<td title=\"Old Value\">&nbsp;</td><td title=\"New Value\">"
                        .getBytes());
                fout_.write("&nbsp;</td></tr>\n".getBytes());
            }
            else if (val._rec._changes.length == 1)
            {
                fout_.write(dumpConvertString(val._rec._changes[0]).getBytes());
                fout_.write("</td><td title=\"Old Value\">".getBytes());
                fout_.write(dumpConvertString(val._rec._old[0]).getBytes());
                fout_.write("</td><td title=\"New Value\">".getBytes());
                fout_.write(dumpConvertString(val._rec._new[0]).getBytes());
                fout_.write("</td></tr>\n".getBytes());
            }
            else
            {
                fout_.write("&nbsp;</td><td title=\"Old Value\">".getBytes());
                fout_.write("&nbsp;</td><td title=\"New Value\">".getBytes());
                fout_.write("&nbsp;</td></tr>\n".getBytes());
                for (int chgcnt = 0; chgcnt < val._rec._changes.length; ++chgcnt)
                {
                    fout_
                        .write(dumpConvertString("\t\t<tr class=\"pstripe\"><td colspan=\""
                            + (colcnt - 3)
                            + "\">&nbsp;</td><td title=\"Change\">").getBytes());
                    fout_.write(dumpConvertString(val._rec._changes[chgcnt]).getBytes());
                    fout_.write("</td><td title=\"Old Value\">".getBytes());
                    fout_.write(dumpConvertString(val._rec._old[chgcnt]).getBytes());
                    fout_.write("</td><td title=\"New Value\">".getBytes());
                    fout_.write(dumpConvertString(val._rec._new[chgcnt]).getBytes());
                    fout_.write("</td></tr>\n".getBytes());
                }
            }
        }
        return count;
    }

    /**
     * Write the footer for the dump file.
     *
     * @param empty_
     *        The report is empty.
     * @param msg_
     *        The addition text for the report footer.
     * @param rec_
     *        The sentinel being processed.
     * @param fout_
     *        The file output stream.
     * @throws java.io.IOException
     *         When there is a problem with the output file.
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpHeader2 dumpHeader2()
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpDetail2 dumpDetail2()
     */
    static public void dumpFooter2(boolean empty_, String msg_, AlertRec rec_, FileOutputStream fout_)
        throws IOException
    {
        dumpFooter1(empty_, msg_, rec_, fout_);
    }

    /**
     * Write the footer for the dump file.
     *
     * @param empty_
     *        The report is empty.
     * @param msg_
     *        The addition text for the report footer.
     * @param rec_
     *        The sentinel being processed.
     * @param fout_
     *        The file output stream.
     * @throws java.io.IOException
     *         When there is a problem with the output file.
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpHeader1 dumpHeader1()
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpDetail1 dumpDetail1()
     */
    static public void dumpFooter1(boolean empty_, String msg_, AlertRec rec_, FileOutputStream fout_)
        throws IOException
    {
        if (empty_)
        {
            fout_.write("\t\t<tr><td colspan=\"3\">".getBytes());
            fout_.write("<p>No activity to report.</p></td></tr>\n".getBytes());
        }
        fout_.write("\t\t<tr><td style=\"border-top: 1px solid black;\">&nbsp;</td>".getBytes());
        fout_.write("<td style=\"border-top: 1px solid black;\">&nbsp;</td>".getBytes());
        fout_.write("<td style=\"border-top: 1px solid black; text-align: right\" colspan=\"6\">".getBytes());
        if (msg_ != null && msg_.length() > 0)
        {
            String temp = "<p style=\"margin-top: 6pt\">" + msg_ + "</p>";
            fout_.write(temp.getBytes());
        }
        else
        {
            fout_.write("&nbsp;".getBytes());
        }
        fout_.write("</td></tr>\n\t</table>".getBytes());
        fout_.write("\n</body>\n</html>\n".getBytes());
    }

    /**
     * Dump the common header shared by all output formats
     *
     * @param db_
     *        The name of the source database used for the report.
     * @param style_
     *        The HTML Style block to appear in the document.
     * @param cemail_
     *        The email of the Alert creator.
     * @param recips_
     *        The list of recipients for this report.
     * @param rec_
     *        The sentinel alert definition being processed.
     * @param start_
     *        The start date for this report content.
     * @param end_
     *        The end date for this report content.
     * @param fout_
     *        The file output stream to which to write.
     * @param part_
     *        The report part number, zero (0) means a single complete file.
     * @param next_
     *        Will there be a next part to the report.
     * @param filePattern_
     *        The file name pattern for the next/prev links for multi-part reports.
     * @throws java.io.IOException
     *         When there is a problem with the output file.
     */
    static private void dumpCommonHeader(String db_, String style_,
                                         String cemail_, String recips_, AlertRec rec_,
                                         Timestamp start_, Timestamp end_, int part_, boolean next_, String filePattern_,
                                         FileOutputStream fout_) throws IOException
    {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        fout_.write("<html>\n\t<head>\n\t\t<title>".getBytes());
        fout_.write("Sentinel Report for ".getBytes());
        fout_.write(dumpConvertString(rec_.getName()).getBytes());
        String part = "";
        if (part_ > 0)
        {
            part = " (Part " + part_ + ")";
            fout_.write(part.getBytes());
            if (part_ > 1)
                part = part + " &nbsp;<a href=\"" + filePattern_.replace("{0}", Integer.toString(part_ - 1)) + "\">Prev</a>&nbsp;";
            else
                part = part + " &nbsp;Prev&nbsp;";
            if (next_)
                part = part + " &nbsp;<a href=\"" + filePattern_.replace("{0}", Integer.toString(part_ + 1)) + "\">Next</a>&nbsp;";
            else
                part = part + " &nbsp;Next&nbsp;";
        }
        fout_.write("</title>\n".getBytes());
        fout_.write("\t<style>\n".getBytes());
        fout_.write(style_.getBytes());
        fout_.write("\t</style>\n</head>\n<body>\n".getBytes());
        fout_.write("\t<hr><table class=\"t1prop\">\n".getBytes());
        fout_.write("\t<colgroup>\n".getBytes());
        fout_.write("\t<col class=\"c1prop\" />\n".getBytes());
        fout_.write("\t<col />\n".getBytes());
        fout_.write("\t</colgroup>\n".getBytes());
        fout_.write("\t<tbody class=\"t1body\" />\n".getBytes());

        fout_.write("\t\t<tr><td>Sentinel&nbsp;Name:</td><td>".getBytes());
        fout_.write(dumpConvertString(rec_.getName()).getBytes());
        fout_.write(part.getBytes());
        if (rec_.getIntro(false) != null)
        {
            fout_.write("</td></tr>\n\t\t<tr><td>Introduction:</td><td>"
                .getBytes());
            fout_.write(dumpConvertString(rec_.getIntro(false)).getBytes());
        }
        fout_.write("</td></tr>\n\t\t<tr><td>Created&nbsp;By:</td><td>".getBytes());
        if (cemail_ == null || cemail_.length() == 0)
            fout_.write(dumpConvertString(rec_.getCreatorName()).getBytes());
        else
        {
            String temp;
            temp = "<a href=\"mailto:" + cemail_
                + "?subject=Concerning%20caDSR%20Sentinel%20Alert%20Name%20"
                + rec_.getName().replaceAll(" ", "%20") + "\">";
            fout_.write(temp.getBytes());
            fout_.write(dumpConvertString(rec_.getCreatorName()).getBytes());
            fout_.write("</a>".getBytes());
        }
        fout_.write("</td></tr>\n\t\t<tr><td>Recipients:</td><td>".getBytes());
        fout_.write(recips_.getBytes());
        fout_.write("</td></tr>\n\t\t<tr><td>Summary:</td><td>".getBytes());
        fout_.write(dumpConvertString(rec_.getSummary(false)).getBytes());
        if (rec_.getIncPropSect())
        {
            fout_.write("</td></tr>\n\t\t<tr><td>Last&nbsp;Auto&nbsp;Run&nbsp;Date:</td><td>"
                .getBytes());
            fout_.write(dumpConvertString(rec_.getADate()).getBytes());
            fout_.write("</td></tr>\n\t\t<tr><td>Frequency:</td><td>"
                .getBytes());
            fout_.write(dumpConvertString(rec_.getFreq(false)).getBytes());
            fout_.write("</td></tr>\n\t\t<tr><td>Status:</td><td>".getBytes());
            fout_.write(dumpConvertString(rec_.getStatus()).getBytes());
            fout_.write("</td></tr>\n\t\t<tr><td>Reporting Level:</td><td>".getBytes());
            if (rec_.getIAssocLvl() == 0)
                fout_.write("0 - Do not show Associated To".getBytes());
            else if (rec_.getIAssocLvl() == 9)
                fout_.write("9 - Show all Associated To".getBytes());
            else
                fout_.write(Integer.toString(rec_.getIAssocLvl()).getBytes());
        }
        fout_.write("</td></tr>\n\t\t<tr><td>Reporting&nbsp;Dates:</td><td>"
            .getBytes());
        fout_.write(dumpConvertString(AlertRec.dateToString(start_, false)).getBytes());
        fout_.write("&nbsp;<b>To</b>&nbsp;".getBytes());
        fout_.write(dumpConvertString(AlertRec.dateToString(end_, false)).getBytes());
        fout_.write("</td></tr>\n\t\t<tr><td>Report&nbsp;Created&nbsp;On:</td><td>"
            .getBytes());
        fout_.write(dumpConvertString(AlertRec.dateToString(now, true)).getBytes());
        fout_.write("</td></tr>\n\t\t<tr><td>Source&nbsp;Database:</td><td>"
            .getBytes());
        fout_.write(dumpConvertString(db_).getBytes());
        fout_
            .write(new String("</td></tr>\n\t</table>"
                + "<p style=\"font-weight: normal; font-style: italic\">"
                + "Information in the report is displayed in blocks. "
                + "Change blocks are indicated by a bright blue color, a "
                + "whole number under the Group column, and the prefix: "
                + "\"Changes to the ...\" The change details appear under the "
                + "headings \"Attribute Name\", \"Old Value\" and \"New Value\".\n"
                + "<ul style=\"font-weight: normal; font-style: italic\">"
                + "<li>If no details are available, the text \"Details not available\" appears."
                + "</li><li>When \"Old Value\" is \"&lt;null&gt;\" the attribute "
                + "was either missing or not set and the \"New Value\" shows the added value."
                + "</li><li>When \"New Value\" is \"&lt;null&gt;\" the attribute has been removed or cleared."
                + "</li><li>When both the old and new values are not \"&lt;null&gt;\" the attribute value has been changed or replaced."
                + "</li></ul>\n"
                + "<p style=\"font-weight: normal; font-style: italic\">Associated blocks are indicated by a light blue "
                + "color, one or more decimal Group numbers, and the prefix "
                + "\"Associated To ...\". These blocks indicate information associated "
                + "to the preceding change block. The first number of the "
                + "Associated To block corresponds to the related change.<br><br>"
                + "Note all dates are month/day/year (mm/dd/yyyy).</p>\n")
                .getBytes());
    }

    /**
     * Output a header to describe the dump file content.
     *
     * @param db_
     *        The name of the source database used for the report.
     * @param style_
     *        The HTML Style block to appear in the document.
     * @param cemail_
     *        The email of the Alert creator.
     * @param recips_
     *        The list of recipients for this report.
     * @param rec_
     *        The sentinel alert definition being processed.
     * @param start_
     *        The start date for this report content.
     * @param end_
     *        The end date for this report content.
     * @param fout_
     *        The file output stream to which to write.
     * @param part_
     *        The part number of this report, zero (0) means a single complete file.
     * @param next_
     *        true if there is a next part to this report.
     * @param filePattern_
     *        The file name pattern for a multi-part report.
     * @throws java.io.IOException
     *         When there is a problem with the output file.
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpDetail2 dumpDetail2()
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpFooter2 dumpFooter2()
     */
    static public void dumpHeader2(String db_, String style_, String cemail_, String recips_,
                                   AlertRec rec_, Timestamp start_, Timestamp end_, int part_, boolean next_, String filePattern_, FileOutputStream fout_)
        throws IOException
    {
        dumpCommonHeader(db_, style_, cemail_, recips_, rec_, start_, end_, part_, next_, filePattern_, fout_);
        fout_.write("\t<table class=\"t2prop\">\n".getBytes());
        fout_.write("\t<colgroup>\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t</colgroup>\n".getBytes());
        fout_
            .write("\t<thead class=\"t2head\">\n\t\t<tr><th>Row</th><th>Group</th>"
                .getBytes());
        fout_.write("<th colspan=\"6\">&nbsp;</th></tr>\n".getBytes());
        fout_.write("\t<tbody class=\"t2body\">\n".getBytes());
    }

    /**
     * Output a header to describe the dump file content.
     *
     * @param db_
     *        The name of the source database used for the report.
     * @param style_
     *        The HTML Style block to appear in the document.
     * @param cemail_
     *        The email of the Alert creator.
     * @param recips_
     *        The list of recipients for this report.
     * @param rec_
     *        The sentinel alert definition being processed.
     * @param start_
     *        The start date for this report content.
     * @param end_
     *        The end date for this report content.
     * @param fout_
     *        The file output stream to which to write.
     * @throws java.io.IOException
     *         When there is a problem with the output file.
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpDetail1 dumpDetail1()
     * @see gov.nih.nci.cadsr.sentinel.tool.ACData#dumpFooter1 dumpFooter1()
     */
    static public void dumpHeader1(String db_, String style_, String cemail_, String recips_,
                                   AlertRec rec_, Timestamp start_, Timestamp end_, FileOutputStream fout_)
        throws IOException
    {
        dumpCommonHeader(db_, style_, cemail_, recips_, rec_, start_, end_, 0, false, null, fout_);

        fout_.write("\t<table class=\"t2prop\">\n".getBytes());
        fout_.write("\t<colgroup>\n".getBytes());
        fout_.write("\t<col class=\"c2propr\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        if (_debug)
        {
            fout_.write("\t<col class=\"c2propc\">\n".getBytes());
            fout_.write("\t<col class=\"c2propc\">\n".getBytes());
            fout_.write("\t<col class=\"c2propl\">\n".getBytes());
            fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        }
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propr\">\n".getBytes());
        fout_.write("\t<col class=\"c2propr\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t<col class=\"c2propl\">\n".getBytes());
        fout_.write("\t</colgroup>\n".getBytes());
        fout_.write("\t<thead class=\"t2head\">\n\t\t<tr><th>Row".getBytes());
        fout_.write("</th><th>Group".getBytes());
        if (_debug)
        {
            fout_.write("</th><th>Level".getBytes());
            fout_.write("</th><th>Category".getBytes());
            fout_.write("</th><th>Context&nbsp;Seq".getBytes());
            fout_.write("</th><th>Related&nbsp;Seq".getBytes());
            fout_.write("</th><th>Seq".getBytes());
            fout_.write("</th><th>Table".getBytes());
        }
        else
        {
            fout_.write("</th><th>&nbsp;".getBytes());
            fout_.write("</th><th>Type".getBytes());
        }
        fout_.write("</th><th>Name / Value".getBytes());
        fout_.write("</th><th>Public&nbsp;ID".getBytes());
        fout_.write("</th><th>Version".getBytes());
        fout_.write("</th><th>Context".getBytes());
        fout_.write("</th><th>Modified&nbsp;By".getBytes());
        fout_.write("</th><th>Modified&nbsp;Date".getBytes());
        fout_.write("</th><th>Created&nbsp;By".getBytes());
        fout_.write("</th><th>Created&nbsp;Date".getBytes());
        fout_.write("</th><th>Change&nbsp;Note / Comment".getBytes());
        fout_.write("</th><th>Change".getBytes());
        fout_.write("</th><th>Old&nbsp;Value".getBytes());
        fout_.write("</th><th>New&nbsp;Value".getBytes());
        fout_.write("</th></tr>\n".getBytes());
        fout_.write("\t<tbody class=\"t2body\">\n".getBytes());
    }

    /**
     * Merge 2 lists into one. When "duplicate" records are found always use the
     * entry from list1_. For the purposes of this merge a duplicate record is
     * when the category and sequence match.
     *
     * @param list1_
     *        The dominant list.
     * @param list2_
     *        The subordinate list.
     * @return ACData[] The merged list.
     */
    static public ACData[] merge(ACData list1_[], ACData list2_[])
    {
        // Create a buffer for the merged list. Assume the worst that all
        // records are
        // unique.
        int length1 = (list1_ == null) ? 0 : list1_.length;
        int length2 = (list2_ == null) ? 0 : list2_.length;
        if (length1 == 0)
            return list2_;
        if (length2 == 0)
            return list1_;

        ACData list3[] = new ACData[length1 + length2];
        int ndx3 = 0;

        // The arrays must be sorted in ascending order by _idseq for the
        // following merge logic to
        // work correctly. This is most easily done upstream in the SQL.
        int ndx1 = 0;
        int ndx2 = 0;
        while (ndx1 < list1_.length && ndx2 < list2_.length)
        {
            int compare = list1_[ndx1]._idseq.compareTo(list2_[ndx2]._idseq);
            if (compare < 0)
            {
                list3[ndx3++] = list1_[ndx1++];
            }
            else if (compare > 0)
            {
                list3[ndx3++] = list2_[ndx2++];
            }
            else
            {
                list3[ndx3++] = list1_[ndx1++];
                list3[ndx3++] = list2_[ndx2++];
            }
        }
        for (; ndx1 < list1_.length; ++ndx1)
            list3[ndx3++] = list1_[ndx1];
        for (; ndx2 < list2_.length; ++ndx2)
            list3[ndx3++] = list2_[ndx2];

        if (ndx3 < list3.length)
        {
            ACData temp[] = new ACData[ndx3];
            for (ndx3 = 0; ndx3 < temp.length; ++ndx3)
                temp[ndx3] = list3[ndx3];
            return temp;
        }
        return list3;
    }

    /**
     * Clean the list of data rows by removing anything that does not appear in
     * the id list provided. This logic requires the data rows are pre-sorted by
     * the idseq value.
     *
     * @param ids_
     *        The list of id values to compare to the _idseq of each row.
     * @param rows_
     *        The data rows to be cleaned.
     * @return The new list of data rows to keep.
     */
    static public ACData[] clean(String ids_[], ACData rows_[])
    {
        // Be sure we have something to do.
        if (ids_ == null || ids_.length == 0 || ids_[0].charAt(0) == '('
            || rows_ == null || rows_.length == 0)
            return rows_;

        // Keep a flag for each row in the data list.
        boolean keep[] = new boolean[rows_.length];
        for (int ndx = 0; ndx < keep.length; ++ndx)
            keep[ndx] = false;

        // Perform a binary search. This seems the most efficient given the
        // nature of the data.
        int max;
        int min;
        for (int check = 0; check < ids_.length; ++check)
        {
            max = rows_.length;
            min = 0;
            while (true)
            {
                int ndx = (max + min) / 2;
                int compare = ids_[check].compareTo(rows_[ndx]._idseq);
                if (compare == 0)
                {
                    // We want to keep these. But remember there could be
                    // multiple rows with the
                    // same idseq value.
                    for (--ndx; ndx >= 0
                        && ids_[check].equals(rows_[ndx]._idseq); --ndx)
                        ;
                    for (++ndx; ndx < rows_.length
                        && ids_[check].equals(rows_[ndx]._idseq); ++ndx)
                    {
                        keep[ndx] = true;
                    }
                    break;
                }

                // The id is greater than the value in the row so move 'min' up.
                else if (compare > 0)
                {
                    if (min == ndx)
                        break;
                    min = ndx;
                }

                // The id is less than the value in the row so move the 'max'
                // down.
                else
                {
                    if (max == ndx)
                        break;
                    max = ndx;
                }
            }
        }

        // Keep everything. Unexpected but possible.
        int keepLen = 0;
        for (int ndx = 0; ndx < keep.length; ++ndx)
        {
            if (keep[ndx])
                keepLen++;
        }
        if (keepLen == rows_.length)
            return rows_;

        // Keep the ones we want.
        ACData list[] = new ACData[keepLen];
        min = 0;
        for (int ndx = 0; ndx < keep.length; ++ndx)
        {
            if (keep[ndx])
            {
                list[min++] = rows_[ndx];
            }
        }
        return list;
    }

    /**
     * Sort the provided list by the _relatedID data element.
     *
     * @param list_
     *        The list to be sorted.
     */
    static public void sortRelated(ACData list_[])
    {
        // Be sure we need to be here.
        if (list_ == null || list_.length == 0)
            return;

        // As we want to keep the list in tact. We will use a little more
        // temporary
        // memory and perform a binary sort into a second array.

        ACData temp[] = new ACData[list_.length];

        // Seed the new list with the first one, it is empty after all.
        temp[0] = list_[0];
        int tmax = 1;

        for (int ndx = 1; ndx < list_.length; ++ndx)
        {
            int min = 0;
            int max = tmax;
            int check = 0;
            while (true)
            {
                check = (max + min) / 2;
                int compare = list_[ndx]._relatedID
                    .compareTo(temp[check]._relatedID);
                if (compare == 0)
                {
                    for (++check; check < tmax
                        && list_[ndx]._relatedID.equals(temp[check]._relatedID); ++check)
                        ;
                    break;
                }
                else if (compare > 0)
                {
                    if (min == check)
                    {
                        ++check;
                        break;
                    }
                    min = check;
                }
                else
                {
                    if (max == check)
                        break;
                    max = check;
                }
            }

            // Copy the record to the temporary list.
            for (int cnt = tmax; cnt > check; --cnt)
            {
                temp[cnt] = temp[cnt - 1];
            }
            temp[check] = list_[ndx];
            ++tmax;
        }

        // Send it back sorted.
        for (int ndx = 0; ndx < list_.length; ++ndx)
        {
            list_[ndx] = temp[ndx];
        }
    }

    /**
     * Find the records with a matching ID in the Related ID data element. The
     * records list must be sorted ascending as this method performs a binary
     * search.
     *
     * @param id_
     *        The record id of interest.
     * @param list_
     *        The records for the report.
     * @return An object which describes the index range of the matches. This
     *         provides for the possibility the list array may contain multiple
     *         records with the same Related ID value.
     */
    static public ACDataFindRange findRelated(String id_, ACData list_[])
    {
        if (list_.length == 0)
            return new ACDataFindRange(0, 0);

        int min = 0;
        int max = list_.length;
        while (true)
        {
            int ndx = (max + min) / 2;
            int compare = id_.compareTo(list_[ndx]._relatedID);
            if (compare == 0)
            {
                for (--ndx; ndx >= 0 && id_.equals(list_[ndx]._relatedID); --ndx)
                    ;
                min = ++ndx;
                for (++ndx; ndx < list_.length
                    && id_.equals(list_[ndx]._relatedID); ++ndx)
                    ;
                return new ACDataFindRange(min, ndx);
            }
            else if (compare > 0)
            {
                if (min == ndx)
                    return new ACDataFindRange(0, 0);
                min = ndx;
            }
            else
            {
                if (max == ndx)
                    return new ACDataFindRange(0, 0);
                max = ndx;
            }
        }
    }

    /**
     * Save the given records on the results stack.
     *
     * @param results_
     *        The results stack.
     * @param p_
     *        The caDSR records to report.
     */
    static public void remember(Stack<RepRows> results_, ACData p_[])
    {
        if (p_ == null)
            return;
        for (int ndx = 0; ndx < p_.length; ++ndx)
            results_.push(new RepRows(0, p_[ndx]));
    }

    /**
     * Set the data elements for this occurrence.
     *
     * @param level_
     * @param category_
     * @param table_
     * @param idseq_
     * @param version_
     * @param publicID_
     * @param name_
     * @param contextID_
     * @param modified_
     * @param created_
     * @param modifier_
     * @param creator_
     * @param note_
     * @param context_
     * @param relatedID_
     */
    public void set(char level_, int category_, String table_, String idseq_,
                    String version_, int publicID_, String name_, String contextID_,
                    Timestamp modified_, Timestamp created_, String modifier_,
                    String creator_, String note_, String context_, String relatedID_)
    {
        _level = level_;
        _category = category_;
        _table = table_;
        _tableCode = table_;
        _idseq = idseq_;
        _version = version_;
        _publicID = publicID_;
        _name = (name_ == null) ? "" : name_;
        _contextID = (contextID_ == null) ? "" : contextID_;
        _modified = modified_;
        _created = created_;
        _modifier = modifier_;
        _creator = creator_;
        _note = note_;
        _context = context_;
        _relatedID = (relatedID_ == null) ? "" : relatedID_;
        _changes = null;
        _old = null;
        _new = null;
        _chgtable = null;
    }

    /**
     * Set the change, old and new value lists for this caDSR record.
     *
     * @param changes_
     *        The change code list.
     * @param old_
     *        The old value prior to the change.
     * @param new_
     *        The new value which is not necessarily the current value in the
     *        database.
     * @param dates_
     *        The change timestamp for each record.
     * @param chgtable_
     *        The changed table name for each record.
     * @param chgby_
     *        The user id of each recorded change.
     */
    public void setChanges(String changes_[], String old_[], String new_[], Timestamp dates_[], String chgtable_[], String chgby_[])
    {
        _changes = changes_;
        // _changesKeyNames = changes_;
        _old = old_;
        _new = new_;
        _dates = dates_;
        _chgtable = chgtable_;
        _chgby = chgby_;
        _uChgby = chgby_;
    }




    /**
     * Get the primary database identifier for this occurrence.
     *
     * @return The value for *_idseq.
     */
    public String getIDseq()
    {
        return _idseq;
    }

    /**
     * Translate the internal codes used to track changes and resolve the id's
     * into readable names for the old and new values.
     *
     * @param db_
     *        The database connection.
     * @param list_
     *        The caDSR change records.
     */
    static public void resolveChanges(DBAlert db_, ACData list_[])
    {
        if (list_ == null)
            return;

        for (int ndx = 0; ndx < list_.length; ++ndx)
        {
            db_.selectNames(list_[ndx]._changes, list_[ndx]._old);
            db_.selectNames(list_[ndx]._changes, list_[ndx]._new);
            for (int ndx2 = 0; ndx2 < list_[ndx]._changes.length; ++ndx2)
            {
                list_[ndx]._changes[ndx2] = db_
                    .translateColumn(list_[ndx]._chgtable[ndx2], list_[ndx]._changes[ndx2]);
                list_[ndx]._chgby[ndx2] = db_.selectName(null, DBAlert._UNAME, list_[ndx]._chgby[ndx2]);
            }
        }
    }

    /**
     * Resolve the id's into readable names for the Created By and Modified By
     * columns.
     *
     * @param db_
     *        The database connection.
     */
    public void resolveNames(DBAlert db_)
    {
        if (_resolveNames)
        {
           //copy the names into local variables before they are resolved
            _uName = _modifier;
            _uCreator = _creator;
            _creator = dumpConvertBlanks(db_.selectName(null,
                DBAlert._UNAME, _creator));
            _modifier = db_.selectName(null, DBAlert._UNAME,
                _modifier);
            if (_modifier != null && _modifier.length() > 0)
                _modifier = dumpConvertBlanks(_modifier);
            _table = dumpConvertBlanks(db_
                .translateTable(_tableCode));
            _resolveNames = false;
        }
    }

    /**
     * Keep only the records which have a specific change and value combination.
     *
     * @param list_
     *        The database change records.
     * @param col_
     *        The column name identifying the data.
     * @param any_
     *        true if only the column is tested, false if the column/value pair
     *        must be tested.
     * @param vals_
     *        The acceptable values for the column.
     * @param flags_
     *        The composite array of keep/discard flags. Must be the same size
     *        as list_. All entries must be initialized to false.
     * @return The number of records to keep.
     */
    static private int filterSpecific(ACData list_[], String col_,
                                      boolean any_, String vals_[], boolean flags_[])
    {
        // If any change appears with the desire column change then keep the
        // whole record.
        int count = 0;
        if (any_)
        {
            for (int ndx = 0; ndx < list_.length; ++ndx)
            {
                for (int ndx2 = 0; ndx2 < list_[ndx]._changes.length
                    && flags_[ndx] == false; ++ndx2)
                {
                    if (list_[ndx]._changes[ndx2].equals(col_))
                    {
                        flags_[ndx] = true;
                        ++count;
                    }
                }
            }
            return count;
        }

        // We have to check for very specific values in the change array.
        for (int ndx = 0; ndx < list_.length; ++ndx)
        {
            for (int ndx2 = 0; ndx2 < list_[ndx]._changes.length
                && flags_[ndx] == false; ++ndx2)
            {
                for (int ndx3 = 0; ndx3 < vals_.length && flags_[ndx] == false; ++ndx3)
                {
                    if (list_[ndx]._changes[ndx2].equals(col_)
                        && list_[ndx]._new[ndx2].equals(vals_[ndx3]))
                    {
                        flags_[ndx] = true;
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Filter the records which do not have a Major Release change to the
     * Version Number.
     *
     * @param list_
     *        The list of records from the caDSR search.
     * @param flags_
     *        The composite array of keep/discard flags. Must be the same size
     *        as list_. All entries must be initialized to false.
     * @return The number of records to keep.
     */
    static private int filterVersionMajor(ACData list_[], boolean flags_[])
    {
        int count = 0;
        for (int ndx = 0; ndx < list_.length; ++ndx)
        {
            for (int ndx2 = 0; ndx2 < list_[ndx]._changes.length
                && flags_[ndx] == false; ++ndx2)
            {
                if (list_[ndx]._changes[ndx2].equals(DBAlert._VERSION))
                {
                    int ondx = list_[ndx]._old[ndx2].indexOf('.');
                    String oldmaj = (ondx < 0) ? list_[ndx]._old[ndx2]
                        : list_[ndx]._old[ndx2].substring(0, ondx);
                    int nndx = list_[ndx]._new[ndx2].indexOf('.');
                    String newmaj = (nndx < 0) ? list_[ndx]._new[ndx2]
                        : list_[ndx]._new[ndx2].substring(0, nndx);
                    if (!oldmaj.equals(newmaj))
                    {
                        flags_[ndx] = true;
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Filter the records which do not have a specific change to the Version
     * Number.
     *
     * @param list_
     *        The list of records from the caDSR search.
     * @param vers_
     *        The specific version number of interest.
     * @param flags_
     *        The composite array of keep/discard flags. Must be the same size
     *        as list_. All entries must be initialized to false.
     * @return The number of records to keep.
     */
    static private int filterVersionSpecific(ACData list_[], String vers_,
                                             boolean flags_[])
    {
        int count = 0;
        for (int ndx = 0; ndx < list_.length; ++ndx)
        {
            for (int ndx2 = 0; ndx2 < list_[ndx]._changes.length
                && flags_[ndx] == false; ++ndx2)
            {
                if (list_[ndx]._changes[ndx2].equals(DBAlert._VERSION)
                    && list_[ndx]._new[ndx2].equals(vers_))
                {
                    flags_[ndx] = true;
                    ++count;
                }
            }
        }
        return count;
    }

    /**
     * Filter the list for the monitors being watched.
     *
     * @param list_
     *        The data to filter.
     * @param rec_
     *        The alert definition.
     * @return The new data array.
     */
    static public ACData[] filter(ACData list_[], AlertRec rec_)
    {
        // If there are no monitors then return the list.
        if (list_ == null
            || list_.length == 0
            || (rec_.isAWorkflowANY() && rec_.isARegisANY() && rec_
                .isAVersionANY()))
            return list_;

        // Keep track of the records using a separate flag array. We assume that
        // all records are dropped and we flag those we wish to really keep.
        boolean flags[] = new boolean[list_.length];
        for (int ndx = 0; ndx < flags.length; ++ndx)
            flags[ndx] = false;

        // When we ignore changes, it means remove them from the list because
        // the
        // user doesn't care to see them. As we just initialized the flag array
        // to 'false' this is implicit in the logic.

        // Decide which rows to keep.
        int count = 0;
        if (!rec_.isAWorkflowIGNORE())
            count += filterSpecific(list_, DBAlert._WFS, rec_.isAWorkflowANY(),
                rec_.getAWorkflow(), flags);
        if (!rec_.isARegisIGNORE())
            count += filterSpecific(list_, DBAlert._RS, rec_.isARegisANY(),
                rec_.getARegis(), flags);
        if (!rec_.isAVersionIGNORE())
        {
            if (rec_.isAVersionANY())
                count += filterSpecific(list_, DBAlert._VERSION, true, null,
                    flags);
            else if (rec_.isAVersionMAJOR())
                count += filterVersionMajor(list_, flags);
            else if (rec_.isAVersionSPECIFIC())
                count += filterVersionSpecific(list_, rec_.getActVerNum(),
                    flags);
        }

        // Keep the good rows.
        ACData results[] = new ACData[count];
        count = 0;
        for (int ndx = 0; ndx < flags.length; ++ndx)
        {
            if (flags[ndx])
                results[count++] = list_[ndx];
        }

        return results;
    }

    /**
     * Compare two ACData instances using the record ID and context ID.  Record
     * ID takes precedence and when equal, determine the result from the context
     * ID.
     *
     * @param rec_ The other ACData being compared.
     * @return -1 when this < rec_, 0 when this == rec_ and 1 when this > rec_.
     */
    public int compareUsingIDS(ACData rec_)
    {
        int rc = _idseq.compareTo(rec_._idseq);
        if (rc == 0)
        {
            if (_contextID == null)
            {
                if (rec_._contextID == null)
                    rc = 0;
                else
                    rc = -1;
            }
            else if (rec_._contextID == null)
                rc = 1;
            else
                rc = _contextID.compareTo(rec_._contextID);
        }
        return rc;
    }

    /**
     * Convert a null string to an empty string.
     *
     * @param value the string to test
     * @return the empty string as appropriate
     */
    public static final String convertNullString(String value)
    {
        return (value == null) ? "" : value;
    }

    /**
     * Collect the change content converting the vectors to arrays for more efficient processing downstream.
     *
     * @param dchg_
     * @param chgby_
     * @param attrs_
     * @param oldValue_
     * @param newValue_
     * @return the new change record
     */
    private ACDataChangesXML collectData(Timestamp dchg_, String chgby_, String uchgby_, Vector<String> attrs_, Vector<String> oldValue_, Vector<String> newValue_)
    {
        ACDataChangesXML change = new ACDataChangesXML();
        change._time = dchg_.toString();
        change._modifiedByName = convertNullString(chgby_);
        change._modifiedByUser = convertNullString(uchgby_);
        change._attributes = new String[attrs_.size()];
        change._oldValues = new String[change._attributes.length];
        change._newValues = new String[change._attributes.length];
        for (int i = 0; i < change._attributes.length; ++i)
        {
            change._attributes[i] = attrs_.get(i);
            change._oldValues[i] = oldValue_.get(i);
            change._newValues[i] = newValue_.get(i);
        }
        return change;
    }

    /**
     * Return values appropriate to place in an XML document
     *
     * @return the XML String values.
     */
    public ACDataXML getXML()
    {
        ACDataXML xml = new ACDataXML();

        //verify the DTD with this code because the public id and version are optional as is the modified* attributes.
        // Set the basic common information
        xml._type = convertNullString(_tableCode);
        xml._name = convertNullString(_name);
        xml._id = convertNullString(_idseq);
        if (_publicID != -1)
            xml._publicId = String.valueOf(_publicID);
        if (_version != null)
            xml._version = convertNullString(_version);
        if (_uName != null)
            xml._modifiedByUser = convertNullString(_uName);
        if (_modifier != null)
             xml._modifiedByName = convertNullString(_modifier).replace("&nbsp;", " ");
        if (_modified != null)
            xml._modifiedTime = _modified.toString();
        xml._createdByUser = convertNullString(_uCreator);
        xml._createdByName = convertNullString(_creator).replace("&nbsp;", " ");
        xml._createdTime = _created.toString();
        xml._changeNote = convertNullString(_note);
        xml._relatedId = _relatedID;

        // If this is a 'primary' record with changes then convert it too.
        if (isPrimary() && _changes != null && _changes.length != 0)
        {
            // The grouping with the change records is by the timestamp. Have to keep the user with the timestamp because it
            // may or may not be the same person.
            Timestamp dchg = _dates[0];
            String chgby = _chgby[0];
            String uchgby = _uChgby[0];

            // Use vectors to collect the information because we don't know how the counts and change groups will fall out.
            Vector<String> attrs = new Vector<String>();
            Vector<String> newValue = new Vector<String>();
            Vector<String> oldValue = new Vector<String>();
            Vector<ACDataChangesXML> changes = new Vector<ACDataChangesXML>();

            // Look at every change
            for (int chgcnt = 0; chgcnt < _changes.length; ++chgcnt)
            {
                // If the timestamp changes this is a new change group - not to be confused with an association group appearing on the
                // report.
                if (dchg.compareTo(_dates[chgcnt]) != 0)
                {
                    // Save what we have so far.
                    changes.add(collectData(dchg, chgby, uchgby, attrs, oldValue, newValue));

                    // Reset local values for later
                    dchg = _dates[chgcnt];
                    chgby = _chgby[chgcnt];
                    uchgby = _uChgby[chgcnt];

                    //empty the previous values once we have saved what we have so far
                    attrs = new Vector<String>();
                    newValue = new Vector<String>();
                    oldValue = new Vector<String>();
                }
                // Collect the changes.
                attrs.add(convertNullString(_changes[chgcnt]));
                oldValue.add(convertNullString(_old[chgcnt]));
                newValue.add(convertNullString(_new[chgcnt]));
            }
            // There is always a change group to write after the loop because it is remembered after counting the records.
            changes.add(collectData(dchg, chgby, uchgby, attrs, oldValue, newValue));

            // Put everything in an array for more efficient processing downstream.
            xml._changes = new ACDataChangesXML[changes.size()];
            for (int i = 0; i < xml._changes.length; ++i)
            {
                xml._changes[i] = changes.get(i);
            }
        }

        return xml;
    }

    // -----------------------------------------------
    
    private static boolean _debug;       // *** development purposes only

    private boolean        _resolveNames; // Can not resolve the names more than
                                          // once or they become corrupted.

    private char           _level;       // 'p' for primary (a direct change
                                         // found), 's' for secondary
                                         // (indirectly changed).

    private int            _category;    // 1 from the primary tables, 2 from
                                         // the admin_components_view table

    private String         _table;       // A table identifier, for example 'de'
                                         // is sbr.data_elements.

    private String         _tableCode;   // A table identifier, for example 'de'
                                         // is sbr.data_elements.

    private String         _idseq;       // The id of the record in the table.

    private String         _version;     // The record version value.

    private int            _publicID;    // The record public id value.

    private String         _name;        // The appropriate "name" of the record
                                         // (most often long_name).

    private String         _contextID;   // The context associated with the
                                         // record if one exists.

    private Timestamp      _modified;    // The modified date.

    private Timestamp      _created;     // The created date.

    private String         _modifier;    // The last modifier.

    private String         _creator;     // The record creator.

    private String         _note;        // The change_note for the last change
                                         // to the record.

    private String         _context;     // The context associated with the
                                         // record if one exists.

    private String         _relatedID;   // The related row that caused this to
                                         // be pulled from the database. Only
                                         // valid when

    // _level is 's'.
    private String         _changes[];   // The list of changes to the record.
                                         // This is currently the database
                                         // column names

    // from the sbrext.ac_change_history_ext.changed_column. If we can't find
    // this (not
    // everything is written into this change table) then _old and _new will be
    // null.
    private String         _old[];       // The old value prior to the change if
                                         // we can get it. Each index matches
                                         // the identical

    // index in _changes and _new.
    private String         _new[];       // The value replacing the old value.
                                         // This is not necessarily the current
                                         // value in
    // the primary table of the database as multiple changes could have been
    // made since
    // the last Alert run.

    private Timestamp      _dates[];     // The corresponding date of the change as
                                         // recorded in the _old and _new arrays.

    // The list of tables actually changed related to the old, new and dates lists
    // above.
    private String         _chgtable[];

    private String         _chgby[];

    //This is used to store the key names for the correspondint values of the keys
    //used in generating the XML
    //TODO this is currently used but it seems a better alternative to doing another lookup after the changed item codes are translated to readable text.
    // private String  _changesKeyNames[];

    private String _uName;

    private String _uCreator;

    private String _uChgby[];
}