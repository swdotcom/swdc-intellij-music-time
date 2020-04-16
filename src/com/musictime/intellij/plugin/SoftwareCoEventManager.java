package com.musictime.intellij.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Stream;


public class SoftwareCoEventManager {

    public static final Logger LOG = Logger.getLogger("SoftwareCoEventManager");

    private static SoftwareCoEventManager instance = null;

    private KeystrokeManager keystrokeMgr = KeystrokeManager.getInstance();
    private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();
    private boolean appIsReady = false;
    AtomicBoolean flag = new AtomicBoolean(true);

    public static SoftwareCoEventManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoEventManager();
        }
        return instance;
    }

    public void setAppIsReady(boolean appIsReady) {
        this.appIsReady = appIsReady;
    }

    private KeystrokeCount getCurrentKeystrokeCount(String projectName, String fileName, String projectDir) {
        KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
        if (keystrokeCount == null) {
            initializeKeystrokeCount(projectName, fileName, projectDir);
            keystrokeCount = keystrokeMgr.getKeystrokeCount();
        }
        return keystrokeCount;
    }

    protected int getLineCount(String fileName) {
        try {
            Path path = Paths.get(fileName);
            Stream<String> stream = Files.lines(path);
            int count = (int) stream.count();
            try {
                stream.close();
            } catch (Exception e) {
                //
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    public void handleFileOpenedEvents(String fileName, Project project) {
        if (project == null || SoftwareCoUtils.isCodeTimeInstalled()) {
            return;
        }
        KeystrokeCount keystrokeCount =
                getCurrentKeystrokeCount(project.getName(), fileName, project.getProjectFilePath());
        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        fileInfo.setOpen(fileInfo.getOpen() + 1);
        int documentLineCount = getLineCount(fileName);
        fileInfo.setLines(documentLineCount);
        LOG.info("Music Time: file opened: " + fileName);
    }

    public void handleFileClosedEvents(String fileName, Project project) {
        if (project == null || SoftwareCoUtils.isCodeTimeInstalled()) {
            return;
        }
        KeystrokeCount keystrokeCount =
                getCurrentKeystrokeCount(project.getName(), fileName, project.getProjectFilePath());
        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        fileInfo.setClose(fileInfo.getClose() + 1);
        LOG.info("Music Time: file closed: " + fileName);
    }

    /**
     * Handles character change events in a file
     * @param document
     * @param documentEvent
     */
    public void handleChangeEvents(Document document, DocumentEvent documentEvent) {

        if (document == null || SoftwareCoUtils.isCodeTimeInstalled()) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            FileDocumentManager instance = FileDocumentManager.getInstance();
            if (instance != null) {
                VirtualFile file = instance.getFile(document);
                if (file != null && !file.isDirectory()) {
                    Editor[] editors = EditorFactory.getInstance().getEditors(document);
                    if (editors != null && editors.length > 0) {
                        String fileName = file.getPath();
                        Project project = editors[0].getProject();

                        if (project != null) {
                            KeystrokeCount keystrokeCount =
                                    getCurrentKeystrokeCount(project.getName(), fileName, project.getProjectFilePath());

                            keystrokeMgr.addKeystrokeWrapperIfNoneExists(project);

                            // check whether it's a code time file or not
                            // .*\.software.*(data\.json|session\.json|latestKeystrokes\.json|ProjectContributorCodeSummary\.txt|CodeTime\.txt|SummaryInfo\.txt|events\.json|fileChangeSummary\.json)
                            boolean skip = (file == null || file.equals("") || fileName.matches(".*\\.software.*(data\\.json|session\\.json|latestKeystrokes\\.json|ProjectContributorCodeSummary\\.txt|CodeTime\\.txt|SummaryInfo\\.txt|events\\.json|fileChangeSummary\\.json)")) ? true : false;

                            if (!skip && keystrokeCount != null) {

                                KeystrokeManager.KeystrokeCountWrapper wrapper = keystrokeMgr.getKeystrokeWrapper();

                                // Set the current text length and the current file and the current project
                                //
                                int currLen = document.getTextLength();
                                wrapper.setCurrentFileName(fileName);
                                wrapper.setCurrentTextLength(currLen);

                                KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
                                String syntax = fileInfo.getSyntax();
                                if (syntax == null || syntax.equals("")) {
                                    // get the grammar
                                    try {
                                        String fileType = file.getFileType().getName();
                                        if (fileType != null && !fileType.equals("")) {
                                            fileInfo.setSyntax(fileType);
                                        }
                                    } catch (Exception e) {
                                        //
                                    }
                                }
                                if (documentEvent.getOldLength() > 0) {
                                    //it's a delete
                                    fileInfo.setDelete(fileInfo.getDelete() + 1);
                                    fileInfo.setNetkeys(fileInfo.getAdd() - fileInfo.getDelete());
                                    LOG.info("Music Time: delete incremented");
                                } else {
                                    // it's an add
                                    if (documentEvent.getNewLength() > 1) {
                                        // it's a paste
                                        fileInfo.setPaste(fileInfo.getPaste() + 1);
                                    } else {
                                        fileInfo.setAdd(fileInfo.getAdd() + 1);
                                        fileInfo.setNetkeys(fileInfo.getAdd() - fileInfo.getDelete());
                                        LOG.info("Music Time: add incremented");
                                    }
                                }

                                keystrokeCount.setKeystrokes(keystrokeCount.getKeystrokes() + 1);

                                int documentLineCount = document.getLineCount();
                                int savedLines = fileInfo.getLines();
                                if (savedLines > 0) {
                                    int diff = documentLineCount - savedLines;
                                    if (diff < 0) {
                                        fileInfo.setLinesRemoved(fileInfo.getLinesRemoved() + Math.abs(diff));
                                        LOG.info("Music Time: lines removed incremented");
                                    } else if (diff > 0) {
                                        fileInfo.setLinesAdded(fileInfo.getLinesAdded() + diff);
                                        LOG.info("Music Time: lines added incremented");
                                    }
                                }
                                fileInfo.setLines(documentLineCount);
                                fileInfo.setLength(document.getTextLength());

                                // update the latest payload
                                keystrokeCount.updateLatestPayloadLazily();
                            }
                        }
                    }

                }
            }
        });
    }

    public void initializeKeystrokeObjectGraph(String fileName, String projectName, String projectFilepath) {
        // initialize it in case it's not initialized yet
        initializeKeystrokeCount(projectName, fileName, projectFilepath);

        KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();

        //
        // Make sure we have the project name and directory info
        if(flag.get()) {
            new Thread(() -> {
                try {
                    Thread.sleep(60000);
                    flag.set(true);
                    LOG.info("Music Time: Reset Flag for project check");
                } catch (Exception e) {
                    System.err.println(e);
                }
            }).start();

            updateKeystrokeProject(projectName, fileName, keystrokeCount);
            flag.set(false);
            LOG.info("Music Time: Update project name & directory");
        }
    }

    private void initializeKeystrokeCount(String projectName, String fileName, String projectFilepath) {
        KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
        if ( keystrokeCount == null || keystrokeCount.getProject() == null ) {
            createKeystrokeCountWrapper(projectName, projectFilepath);
        } else if (!keystrokeCount.getProject().getName().equals(projectName)) {
            final KeystrokeManager.KeystrokeCountWrapper current = keystrokeMgr.getKeystrokeWrapper();

            // send the current wrapper and create a new one
            current.getKeystrokeCount().processKeystrokes();

            createKeystrokeCountWrapper(projectName, projectFilepath);
        } else {
            //
            // update the end time for files that don't match the incoming fileName
            //
            keystrokeCount.endPreviousModifiedFiles(fileName);
        }
    }

    private void createKeystrokeCountWrapper(String projectName, String projectFilepath) {
        //
        // Create one since it hasn't been created yet
        // and set the start time (in seconds)
        //
        KeystrokeCount keystrokeCount = new KeystrokeCount();

        KeystrokeProject keystrokeProject = new KeystrokeProject( projectName, projectFilepath );
        keystrokeCount.setProject( keystrokeProject );

        //
        // Update the manager with the newly created KeystrokeCount object
        //
        keystrokeMgr.setKeystrokeCount(projectName, keystrokeCount);
    }

    private void updateKeystrokeProject(String projectName, String fileName, KeystrokeCount keystrokeCount) {
        if (keystrokeCount == null) {
            return;
        }
        KeystrokeProject project = keystrokeCount.getProject();
        String projectDirectory = getProjectDirectory(projectName, fileName);

        if (project == null) {
            project = new KeystrokeProject( projectName, projectDirectory );
            keystrokeCount.setProject( project );
        } else if (project.getName() == null || project.getName() == "") {
            project.setDirectory(projectDirectory);
            project.setName(projectName);
        }
    }

    private String getProjectDirectory(String projectName, String fileName) {
        String projectDirectory = "";
        if ( projectName != null && projectName.length() > 0 &&
                fileName != null && fileName.length() > 0 &&
                fileName.indexOf(projectName) > 0 ) {
            projectDirectory = fileName.substring( 0, fileName.indexOf( projectName ) - 1 );
        }
        return projectDirectory;
    }
}
