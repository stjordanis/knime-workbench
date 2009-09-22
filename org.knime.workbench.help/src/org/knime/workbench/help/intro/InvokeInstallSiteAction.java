/*
 * ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2009
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * ---------------------------------------------------------------------
 *
 * History
 *   Dec 18, 2006 (sieb): created
 */
package org.knime.workbench.help.intro;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.equinox.internal.p2.ui.sdk.UpdateAndInstallDialog;
import org.eclipse.equinox.internal.provisional.p2.ui.operations.AddColocatedRepositoryOperation;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;

/**
 * Custom action to open the install wizard.
 *
 * @author Christoph, University of Konstanz
 */
public class InvokeInstallSiteAction extends Action {
    private static final String ID = "INVOKE_INSTALL_SITE_ACTION";

    /** P2 profile id. */
    public static final String KNIME_PROFILE_ID = "KNIMEProfile";

    /**
     * Constructor.
     */
    public InvokeInstallSiteAction() {
        // TODO: define KNIME Update Site URL somewhere statically
        // FIXME: update this hardcoded update site!!!
        // FIXME: as of Eclipse 3.5 the update sites defined in the features
        // should be added automatically to the p2 update/install dialog
        // then this hack becomes obsolete
        try {
            AddColocatedRepositoryOperation op
                = new AddColocatedRepositoryOperation("KNIME",
                        new URL("http://www.knime.org/update_2.x/"));
            op.runInBackground();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        UpdateAndInstallDialog dialog = new UpdateAndInstallDialog(
                Display.getDefault().getActiveShell(), KNIME_PROFILE_ID);
        dialog.open();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Opens the KNIME update site to install "
                + "additional KNIME features.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getText() {
        return "Update KNIME...";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return ID;
    }
}
