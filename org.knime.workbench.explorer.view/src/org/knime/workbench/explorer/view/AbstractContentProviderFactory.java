/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2008 - 2013
 * KNIME.com, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 */
package org.knime.workbench.explorer.view;

import org.eclipse.swt.graphics.Image;
import org.knime.workbench.explorer.ExplorerMountTable;

/**
 *
 * @author ohl, University of Konstanz
 */
public abstract class AbstractContentProviderFactory {

    /**
     * @return a unique ID (e.g. "com.knime.explorer.filesystem", etc.)
     */
    public abstract String getID();

    /**
     * @return a readable name useful for the user (e.g. "Local Workspace",
     *         "Local File System", etc.)
     */
    @Override
    public abstract String toString();

    /**
     * @return an icon displayed in selection lists
     */
    public abstract Image getImage();

    /**
     *
     * @return true, if the factory can produce multiple content provider
     *         instances, false, if not more than one content provider must be
     *         created.
     */
    public abstract boolean multipleInstances();

    /**
     * @return a unique mount ID if this mount point should appear by default in
     *         the mount table and the explorer view. Or null, if it shouldn't
     *         be mounted by default. If an ID is returned the instantiation of
     *         the corresponding content provider must not open any dialog (or
     *         cause any other interaction).
     */
    public String getDefaultMountID() {
        return null;
    }

    /**
     * Not intended to be called. Rather go through the
     * {@link ExplorerMountTable}.
     *
     * <p>The caller needs to make sure the returned value is disposed when
     * no longer needed!
     *
     * @param mountID the mount ID the new content provider is mounted with
     *
     * @return a new, fully parameterized instance for a specific content
     *         provider or <code>null</code> if no provider can be created
     */
    public abstract AbstractContentProvider
        createContentProvider(final String mountID);

    /** Restore content provider. Caller needs to dispose returned value when
     * no longer needed!
     *
     * @param mountID the id of the mount to restore
     * @param content the string content representing the state of the content
     *            provider
     * @return a new instance with its state restored from the passed structure
     */
   public abstract AbstractContentProvider createContentProvider(
           final String mountID, final String content);

    /**
     * Indicates if additional information is needed by the factory for
     * creating a content provider. In general this information is gathered
     * by the factory in the {@link #createContentProvider(String)} by opening
     * a dialog.
     *
     * @return true, if additional information is needed by the factory for
     *      creating a content provider, false otherwise
     *
     * @since 3.0
     */
    public abstract boolean isAdditionalInformationNeeded();
}
