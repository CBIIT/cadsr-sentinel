/*L
 * Copyright ScenPro Inc, SAIC-F
 *
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See http://ncip.github.com/cadsr-sentinal/LICENSE.txt for details.
 */

// Copyright (c) 2007 ScenPro, Inc.

// $Header: /share/content/gforge/sentinel/sentinel/src/gov/nih/nci/cadsr/sentinel/ui/HeartbeatForm.java,v 1.2 2007-07-19 15:26:45 hebell Exp $
// $Name: not supported by cvs2svn $

package gov.nih.nci.cadsr.sentinel.ui;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * @author lhebel
 *
 */
public class HeartbeatForm extends ActionForm
{
    private static final long serialVersionUID = 8909417350041490262L;

    /**
     * 
     */
    public HeartbeatForm()
    {
        super();
    }

    /**
     * Validate the content of the Edit Screen.
     * 
     * @param mapping_
     *        The action map defined for Edit.
     * @param request_
     *        The servlet request object.
     * @return Any errors found.
     */
    public ActionErrors validate(ActionMapping mapping_, HttpServletRequest request_)
    {
        ActionErrors errors = new ActionErrors();
        
        return errors;
    }
}
