package com.softwareco.intellij.plugin;

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
        KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
        if (keystrokeCount == null) {
            initializeKeystrokeObjectGraph(fileName, project.getName(), project.getProjectFilePath());
            keystrokeCount = keystrokeMgr.getKeystrokeCount();
        }
        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        fileInfo.setOpen(fileInfo.getOpen() + 1);
        int documentLineCount = getLineCount(fileName);
        fileInfo.setLines(documentLineCount);
        LOG.info("Code Time: file opened: " + fileName);
    }

    public void handleFileClosedEvents(String fileName, Project project) {
        KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
        if (keystrokeCount == null) {
            initializeKeystrokeObjectGraph(fileName, project.getName(), project.getProjectFilePath());
            keystrokeCount = keystrokeMgr.getKeystrokeCount();
        }
        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        fileInfo.setClose(fileInfo.getClose() + 1);
        LOG.info("Code Time: file closed: " + fileName);
    }

    /**
     * Handles character change events in a file
     * @param document
     * @param documentEvent
     */
    public void handleChangeEvents(Document document, DocumentEvent documentEvent) {

        if (document == null) {
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
                        String projectName = null;
                        String projectFilepath = null;
                        if (project != null) {
                            projectName = project.getName();
                            projectFilepath = project.getBasePath();

                            keystrokeMgr.addKeystrokeWrapperIfNoneExists(project);

                            initializeKeystrokeObjectGraph(fileName, projectName, projectFilepath);

                            KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
                            if (keystrokeCount != null) {

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
                                    LOG.info("Code Time: delete incremented");
                                } else {
                                    // it's an add
                                    if (documentEvent.getNewLength() > 1) {
                                        // it's a paste
                                        fileInfo.setPaste(fileInfo.getPaste() + 1);
                                    } else {
                                        fileInfo.setAdd(fileInfo.getAdd() + 1);
                                        fileInfo.setNetkeys(fileInfo.getAdd() - fileInfo.getDelete());
                                        LOG.info("Code Time: add incremented");
                                    }
                                }

                                int incrementedCount = Integer.parseInt(keystrokeCount.getKeystrokes()) + 1;
                                keystrokeCount.setKeystrokes(String.valueOf(incrementedCount));

                                int documentLineCount = document.getLineCount();
                                int savedLines = fileInfo.getLines();
                                if (savedLines > 0) {
                                    int diff = documentLineCount - savedLines;
                                    if (diff < 0) {
                                        fileInfo.setLinesRemoved(fileInfo.getLinesRemoved() + Math.abs(diff));
                                        LOG.info("Code Time: lines removed incremented");
                                    } else if (diff > 0) {
                                        fileInfo.setLinesAdded(fileInfo.getLinesAdded() + diff);
                                        LOG.info("Code Time: lines added incremented");
                                    }
                                }
                                fileInfo.setLines(documentLineCount);
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
                    LOG.info("Code Time: Reset Flag for project check");
                } catch (Exception e) {
                    System.err.println(e);
                }
            }).start();

            updateKeystrokeProject(projectName, fileName, keystrokeCount);
            flag.set(false);
            LOG.info("Code Time: Update project name & directory");
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
