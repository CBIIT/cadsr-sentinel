<%--L
  Copyright ScenPro Inc, SAIC-F

  Distributed under the OSI-approved BSD 3-Clause License.
  See http://ncip.github.com/cadsr-sentinal/LICENSE.txt for details.
L--%>

<!-- Copyright ScenPro, Inc. 2005
     $Header: /share/content/gforge/sentinel/sentinel/WebRoot/jsp/logon.jsp,v 1.9 2009-04-08 17:56:18 hebell Exp $
     $Name: not supported by cvs2svn $
-->
<%@ taglib uri="/tags/struts-bean" prefix="bean" %>
<%@ taglib uri="/tags/struts-html" prefix="html" %>
<%@ taglib uri="/tags/dsralert" prefix="dtags" %>

<%@ page import="gov.nih.nci.cadsr.sentinel.ui.AlertPlugIn" %>
<%@ page import="gov.nih.nci.cadsr.sentinel.database.DBAlert" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
    <head>
        <title><bean:message key="logon.title" /></title>       		
        <html:base />
        <meta http-equiv="Content-Language" content="en-us">
        <meta HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=WINDOWS-1252">
        <meta HTTP-EQUIV="Pragma" CONTENT="no-cache">
        <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache">
        <LINK href="/cadsrsentinel/css/sentinel.css" rel="stylesheet" type="text/css">
    </head>

<body>

    <div style="position:absolute;">
		<a href="#skip">
		<img src="/cadsrsentinel/images/skipnav.gif" border="0" height="1" width="1" alt="Skip Navigation" title="Skip Navigation" />
 		</a>
	</div>

    <script type="text/javascript">
        function cmdLogon()
        {
            var msgTxt = document.getElementById("msg");
            msgTxt.innerText = <bean:message key="logon.msg" />;
            msgTxt.textContent = <bean:message key="logon.msg" />;
        }
        function cmdHelp()
        {
        <%
            AlertPlugIn api = (AlertPlugIn) pageContext.getServletContext().getAttribute(DBAlert._DATASOURCE);
        %>
            window.open("<%=api.getHelpUrl()%>", "_blank");
        }
    </script>
	
    <html:form method="post" action="/logon" focus="userid">

    <table class="secttable"><colgroup></colgroup><tbody class="secttbody" /><tr><td align="center">
        <dtags:head key="logon.title" />
        <bean:message key="logon.back" />
        <table summary=<bean:message key="logon.credits" />
            <tr>
                <td valign="middle"><label for="logUserId"><bean:message key="logon.userid" /></label></td>
                <td valign="middle"><input type="text" name="userid" value="" id="logUserId" style="width: 3.75in" class="std" autocomplete="off"></td>
            </tr><tr>
            <tr>
                <td valign="middle"><label for="logPassword"><bean:message key="logon.pswd" /></label></td>
                <td valign="middle"><input type="password" name="pswd" value="" id="logPassword" style="width: 3.75in" class="std" autocomplete="off"></td>
            </tr><tr>
                <td colspan="2" valign="middle"><html:errors /></td>
            </tr><tr>
                <td valign="bottom"><html:submit property="logon" styleClass="but2" onclick="cmdLogon();"><bean:message key="logon.logon" /></html:submit></td>
                <td valign="bottom" align="right"><html:button styleClass="but2" property="help" onclick="cmdHelp();"><bean:message key="all.help" /></html:button></td>
            </tr>
        </table>
        
        <dtags:foot />
    </td></tr></table>
    </html:form>
</body>
    <head>
        <meta HTTP-EQUIV="Pragma" CONTENT="no-cache">
        <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache">
    </head>
</html>
