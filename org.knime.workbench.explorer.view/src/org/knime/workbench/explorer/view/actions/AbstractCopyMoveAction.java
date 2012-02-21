/* ------------------------------------------------------------------
  * This source code, its documentation and all appendant files
  * are protected by copyright law. All rights reserved.
  *
  * Copyright, 2008 - 2011
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
  *
  * History
  *   Oct 31, 2011 (morent): created
  */

package org.knime.workbench.explorer.view.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.PlatformUI;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.dialogs.SpaceResourceSelectionDialog;
import org.knime.workbench.explorer.dialogs.SpaceResourceSelectionDialog.SelectionValidator;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileInfo;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.filesystem.ExplorerFileSystemUtils;
import org.knime.workbench.explorer.filesystem.LocalExplorerFileStore;
import org.knime.workbench.explorer.filesystem.MessageFileStore;
import org.knime.workbench.explorer.filesystem.RemoteExplorerFileStore;
import org.knime.workbench.explorer.view.AbstractContentProvider;
import org.knime.workbench.explorer.view.ContentDelegator;
import org.knime.workbench.explorer.view.ContentObject;
import org.knime.workbench.explorer.view.DestinationChecker;
import org.knime.workbench.explorer.view.ExplorerView;

/**
 * Abstract base class for copy and move actions for the Explorer. It contains
 * basically all necessary functionality. Derived classes only have to
 * override the name and set the move flag.
 * @author Dominik Morent, KNIME.com, Zurich, Switzerland
 *
 */
public abstract class AbstractCopyMoveAction extends ExplorerAction {
    private static final NodeLogger LOGGER = NodeLogger.getLogger(
            AbstractCopyMoveAction.class);
    private AbstractExplorerFileStore m_target;
    private boolean m_performMove;
    /** The textual representation of the performed command. */
    private final String m_cmd;
    private List<AbstractExplorerFileStore> m_sources;
    private boolean m_success;

    /**
     * Creates a new copy/move action which displays a dialog to select the
     * target file store.
     *
     * @param viewer viewer of the space
     * @param menuText the text to be displayed in the menu
     * @param performMove true to move the files, false to copy them
     */
    public AbstractCopyMoveAction(final ExplorerView viewer,
            final String menuText, final boolean performMove) {
        this(viewer, menuText, null, null, performMove);
    }

    /**
     * Creates a new copy/move action that copies/moves the source files to
     * the target file store.
     *
     * @param viewer viewer of the space
     * @param menuText the text to be displayed in the menu
     * @param sources the file stores to copy
     * @param target the file store to copy/move the files to
     * @param performMove true to move the files, false to copy them
     */
    public AbstractCopyMoveAction(final ExplorerView viewer,
            final String menuText,
            final List<AbstractExplorerFileStore> sources,
            final AbstractExplorerFileStore target,
            final boolean performMove) {
        super(viewer, menuText);
        m_sources = sources;
        setTarget(target);
        m_performMove = performMove;
        m_cmd = m_performMove ? "Move" : "Copy";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        if (m_sources == null) {
            // retrieve the selected file stores
            m_sources = removeSelectedChildren(getAllSelectedFiles());
            String message = ExplorerFileSystemUtils.isLockable(m_sources,
                    !m_performMove);
            if (m_sources == null || m_sources.isEmpty()
                    || message != null) {
                MessageBox mb =
                    new MessageBox(getParentShell(), SWT.ICON_ERROR | SWT.OK);
                String action = m_performMove ? "Move" : "Copy";
                mb.setText("Can't " + action + " All Selected Items");
                mb.setMessage(message);
                mb.open();
                return;
            }
        }

        // open browse dialog for target selection if necessary
        if (m_target == null) {
            openTargetSelectionDialog();
        }

        if (m_target == null) {
            // user cancelled target selection
            return;
        }

        m_success = copyOrMove(m_sources);
        if (!m_success) {
            LOGGER.error(m_performMove ? "Moving" : "Copying" + " to \""
                    + m_target.getFullName() + "\" failed.");
        } else {
            LOGGER.debug("Successfully "
                    + (m_performMove ? "moved " : "copied ")
                    + m_sources.size() + " item(s) to \""
                    + m_target.getFullName() + "\".");
        }
    }

    private void openTargetSelectionDialog() {
        boolean showServer = !isMultipleSelection();
        List<String> mountIDs = new ArrayList<String>();
        for (String mountID : getView().getMountedIds()) {
            AbstractContentProvider acp
            = ExplorerMountTable.getMountPoint(mountID).getProvider();
            /* Add servers only if a single file is selected and if the user
             * can write to it (e.g. is authenticated and the server is not
             * read-only. */
            if ((showServer && acp.isWritable())
                    || !acp.isRemote()) {
                mountIDs.add(mountID);
            }
        }
        ContentObject initialSelection = null;
        if (m_sources.size() == 1) {
            Object selection = ContentDelegator.getTreeObjectFor(
                    m_sources.get(0).getParent());
            if (selection instanceof ContentObject) {
                initialSelection = (ContentObject)selection;
            }
        }

        String[] shownMountIds = mountIDs.toArray(new String[0]);
        SpaceResourceSelectionDialog dialog =
            new SpaceResourceSelectionDialog(Display
                    .getDefault().getActiveShell(),
                    shownMountIds, initialSelection);
        dialog.setTitle("Target workflow group selection");
        dialog.setDescription(
                "Please select the location to "
                + (m_performMove ? "move" : "copy")
                + " the selected files to.");
        dialog.setValidator(new SelectionValidator() {
            @Override
            public String isValid(
                    final AbstractExplorerFileStore selection) {
                boolean isWFG = selection.fetchInfo().isWorkflowGroup();
                return isWFG ? null
                        : "Only workflow groups can be selected as target.";
            }
        });
        dialog.scaleDialogSize(1, 2);
        if (Window.OK == dialog.open()) {
            setTarget(dialog.getSelection());
        }
    }

    /**
     * @return true if the copy/move operation was successful, false otherwise
     */
    public boolean isSuccessful() {
        return m_success;
    }

    /**
     * @param srcFileStores the file stores to copy/move
     * @return true if the operation was successful, false otherwise
     */
    protected boolean copyOrMove(final List<AbstractExplorerFileStore>
            srcFileStores) {
        /* Make sure that the target is not a child of any item in the
         * selection. */
        if (isChildOf(m_target,
                new LinkedHashSet<AbstractExplorerFileStore>(srcFileStores))) {
            String msg = "Cannot " + m_cmd + " the selected files into "
            + m_target + " because it is a child of the selection.";
            MessageDialog.openError(Display.getDefault()
                    .getActiveShell(), m_cmd + " Workflow", msg);
            LOGGER.info(msg);
            return false;
        }

        // collect the necessary information
        final DestinationChecker <AbstractExplorerFileStore,
                AbstractExplorerFileStore> destChecker = new DestinationChecker
                <AbstractExplorerFileStore, AbstractExplorerFileStore>(
                getParentShell(), m_cmd, srcFileStores.size() > 1, true);
        for (final AbstractExplorerFileStore srcFS : srcFileStores) {
            final AbstractExplorerFileStore destFS =
                    destChecker.getAndCheckDestinationFlow(srcFS, m_target);
            if (destChecker.isAbort()) {
                LOGGER.info(m_cmd + " operation was aborted.");
                return false;
            }
            if (destFS == null) {
                // the user skipped the operation or it is not allowed
                LOGGER.info(m_cmd + " operation of "
                        + srcFS.getMountIDWithFullPath() + " was skipped.");
            }
        }

        final List<String> result = new LinkedList<String>();
        final AtomicBoolean success = new AtomicBoolean(true);
        try {
            // perform the copy/move operations en-bloc in the background
            PlatformUI.getWorkbench().getProgressService()
                    .busyCursorWhile(new IRunnableWithProgress() {
                @Override
                public void run(final IProgressMonitor monitor)
                        throws InvocationTargetException,
                        InterruptedException {

                    int numFiles = srcFileStores.size();
                    monitor.beginTask(m_cmd + " " + numFiles
                            + " files to " + m_target.getFullName(),
                            numFiles);

                    for (final Map.Entry<AbstractExplorerFileStore,
                            AbstractExplorerFileStore> entry
                            : destChecker.getMappings().entrySet()) {
                        AbstractExplorerFileStore srcFS = entry.getKey();
                        AbstractExplorerFileStore destFS = entry.getValue();
                        if (destFS == null) {
                            // skip operations that have been marked to
                            // be skipped
                            continue;
                        }
                        String operation = m_cmd + " "
                                + srcFS.getMountIDWithFullPath()
                                + " to " + destFS.getFullName();
                        monitor.subTask(operation);
                        LOGGER.debug(operation);
                        try {
                            int options = destChecker.getOverwriteFS()
                                    .contains(destFS) ?
                                            EFS.OVERWRITE : EFS.NONE;
                            boolean isSrcRemote
                                = srcFS instanceof RemoteExplorerFileStore;
                            boolean isDstRemote
                                = destFS instanceof RemoteExplorerFileStore;
                            if (!isSrcRemote && isDstRemote) { // upload
                                destFS.getContentProvider().performUpload(
                                        (LocalExplorerFileStore)srcFS,
                                        (RemoteExplorerFileStore)destFS,
                                        monitor);
                                if (m_performMove) {
                                    srcFS.delete(options, monitor);
                                }
                            } else if (isSrcRemote && !isDstRemote) {
                                // dowload
                                destFS.getContentProvider().performDownload(
                                        (RemoteExplorerFileStore)srcFS,
                                        (LocalExplorerFileStore)destFS,
                                        monitor);
                                if (m_performMove) {
                                    srcFS.delete(options, monitor);
                                }
                            } else { // regular copy
                                if (m_performMove) {
                                    srcFS.move(destFS, options, monitor);
                                } else {
                                    srcFS.copy(destFS, options, monitor);
                                }
                            }
                        } catch (CoreException e) {
                            LOGGER.debug(m_cmd + " failed: "
                                    + e.getStatus().getMessage(), e);
                            result.add("ERROR");
                            result.add(e.getStatus().getMessage());
                            success.set(false);
                        } catch (Exception e) {
                            LOGGER.debug(m_cmd + " failed: "
                                    + e.getMessage(), e);
                            result.add("ERROR");
                            result.add(e.getMessage());
                            success.set(false);
                        }
                        monitor.worked(1);
                    }
                }
            });
            List<Object> treeObjects = ContentDelegator.getTreeObjectList(
                    destChecker.getMappings().values());
            if (treeObjects != null && treeObjects.size() > 0) {
                getViewer().setSelection(new StructuredSelection(treeObjects),
                        true);
            }
        } catch (InvocationTargetException e) {
            LOGGER.debug("Invocation exception, " + e.getMessage(), e);
            result.add("ERROR");
            result.add("invocation error: " + e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.debug(m_cmd + " failed: interrupted, " + e.getMessage(),
                    e);
            result.add("ERROR");
            result.add("interrupted: " + e.getMessage());
        }
        if (result.size() > 0) {
            boolean openDlg = false;
            int kind = MessageDialog.INFORMATION;
            String msg = null;
            if ("ERROR".equals(result.get(0))) {
                kind = MessageDialog.ERROR;
                openDlg = true; // always display an error
            }
            if (result.size() > 1) {
                // we have a message
                msg = result.get(1);
                openDlg = true; // always show that message
            } else {
                msg = "<no details available>";
            }
            if (openDlg) {
                if (kind == MessageDialog.ERROR) {
                    msg = m_cmd + " failed: " + msg;
                }
                if (kind == MessageDialog.ERROR) {
                    MessageDialog.openError(Display.getDefault()
                            .getActiveShell(), m_cmd + " Workflow", msg);
                } else if (kind == MessageDialog.INFORMATION) {
                    MessageDialog.openInformation(Display.getDefault()
                            .getActiveShell(), m_cmd + " Workflow", msg);
                }
            }
        }
        return success.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
        return isCopyOrMovePossible(getSelectedFiles(), m_performMove);
    }

    /**
     * Determines if a copy/move operation is possible based on the selection.
     *
     * @param selProviders the selected content providers
     * @param performMove true if a move operation should be checked
     * @return true if the operation is possible, false otherwise
     */
    public static boolean isCopyOrMovePossible(
            final Map<AbstractContentProvider, List<AbstractExplorerFileStore>>
            selProviders, final boolean performMove) {
        if (selProviders.size() != 1) {
            // can only copy/move from one source content provider
            return false;
        }
        if (!selProviders.keySet().iterator().next().isWritable()
                && performMove) {
            return false;
        }
        List<AbstractExplorerFileStore> selections =
                selProviders.values().iterator().next();
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        AbstractExplorerFileStore fileStore = selections.get(0);
        if (fileStore instanceof MessageFileStore) {
            return false;
        }
        if (fileStore instanceof RemoteExplorerFileStore) {
            // currently we can only download one workflow or metanode template
            if (selections.size() > 1) {
                return false;
            }

            AbstractExplorerFileInfo info = fileStore.fetchInfo();
            if (!(info.isWorkflow() || info.isWorkflowTemplate())) {
                return false;
            }
        }
        if (performMove) {
            return fileStore.canMove();
        } else {
            return fileStore.canCopy();
        }
    }

    protected boolean isPerformMove() {
        return m_performMove;
    }

    protected AbstractExplorerFileStore getTarget() {
        return m_target;
    }

    protected void setTarget(final AbstractExplorerFileStore target) {
        if (target == null || target.fetchInfo().isWorkflowGroup()) {
            m_target = target;
        } else {
            m_target = target.getParent();
        }
    }

    protected void setPerformMove(final boolean performMove) {
        m_performMove = performMove;
    }

    protected void setSuccess(final boolean success) {
        m_success = success;
    }
 }
