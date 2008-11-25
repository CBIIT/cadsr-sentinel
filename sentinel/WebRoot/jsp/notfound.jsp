<!-- Copyright ScenPro, Inc. 2005
     $Header: /share/content/gforge/sentinel/sentinel/WebRoot/jsp/notfound.jsp,v 1.2 2008-11-25 18:26:26 hebell Exp $
     $Name: not supported by cvs2svn $
-->
<%@ taglib uri="/tags/struts-bean" prefix="bean" %>
<%@ taglib uri="/tags/struts-html" prefix="html" %>
<%@ taglib uri="/tags/dsralert" prefix="dtags" %>

<%@ page import="gov.nih.nci.cadsr.sentinel.ui.AlertPlugIn" %>
<%@ page import="gov.nih.nci.cadsr.sentinel.database.DBAlert" %>

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

    <script language="javascript">
        function cmdLogon()
        {
            msg.innerText = <bean:message key="logon.msg" />;
        }
        function cmdHelp()
        {
        <%
            AlertPlugIn api = (AlertPlugIn) pageContext.getServletContext().getAttribute(DBAlert._DATASOURCE);
        %>
            window.open("<%=api.getHelpUrl()%>", "_blank");
        }
    </script>

    <table class="secttable"><colgroup></colgroup><tbody class="secttbody" /><tr><td align="center">

        <dtags:head key="logon.title" />
        <bean:message key="notfound" />
        <html:button styleClass="but2" property="help" onclick="cmdHelp();"><bean:message key="all.help" /></html:button>
        
        <dtags:foot />
    </td></tr></table>

</body>
    <head>
        <meta HTTP-EQUIV="Pragma" CONTENT="no-cache">
        <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache">
    </head>
</html>
