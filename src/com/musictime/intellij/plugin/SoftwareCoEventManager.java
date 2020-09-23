package com.musictime.intellij.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.musictime.intellij.plugin.managers.EventTrackerManager;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SoftwareCoEventManager {

    public static final Logger LOG = Logger.getLogger("SoftwareCoEventManager");

    private static SoftwareCoEventManager instance = null;

    private static final int FOCUS_STATE_INTERVAL_SECONDS = 5;
    private static final Pattern NEW_LINE_PATTERN = Pattern.compile("\n");
    private static final Pattern NEW_LINE_TAB_PATTERN = Pattern.compile("\n\t");
    private static final Pattern TAB_PATTERN = Pattern.compile("\t");

    private static boolean isCurrentlyActive = true;

    private EventTrackerManager tracker;
    private KeystrokeManager keystrokeMgr;

    public static SoftwareCoEventManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoEventManager();
        }
        return instance;
    }

    private SoftwareCoEventManager() {
        keystrokeMgr = KeystrokeManager.getInstance();

        final Runnable checkFocusStateTimer = () -> checkFocusState();
        AsyncManager.getInstance().scheduleService(
                checkFocusStateTimer, "checkFocusStateTimer", 0, FOCUS_STATE_INTERVAL_SECONDS);
    }

    private void checkFocusState() {
        ApplicationManager.getApplication().invokeLater(() -> {
            boolean isActive = ApplicationManager.getApplication().isActive();
            if (isActive != isCurrentlyActive) {
                if (!isActive) {
                    KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
                    if (keystrokeCount != null) {
                        // set the flag the "unfocusStateChangeHandler" will look for in order to process payloads early
                        keystrokeCount.triggered = false;
                        keystrokeCount.processKeystrokes();
                    }
                    EventTrackerManager.getInstance().trackEditorAction("editor", "unfocus");
                } else {
                    // just set the process keystrokes payload to false since we're focused again
                    EventTrackerManager.getInstance().trackEditorAction("editor", "focus");
                }

                // update the currently active flag
                isCurrentlyActive = isActive;
            }
        });
    }

    private int getNewlineCount(String text) {
        if (text == null) {
            return 0;
        }
        Matcher matcher = NEW_LINE_PATTERN.matcher(text);
        int count = 0;
        while(matcher.find()) {
            count++;
        }
        return count;
    }

    private KeystrokeCount getCurrentKeystrokeCount(String projectName, String projectDir) {
        KeystrokeCount keystrokeCount = keystrokeMgr.getKeystrokeCount();
        if (keystrokeCount == null) {
            // create one
            projectName = projectName != null && !projectName.equals("") ? projectName : "Unnamed";
            projectDir = projectDir != null && !projectDir.equals("") ? projectDir : "Untitled";
            // create the keysrtroke count wrapper
            createKeystrokeCountWrapper(projectName, projectDir);

            // now retrieve it from the mgr
            keystrokeCount = keystrokeMgr.getKeystrokeCount();
        }
        return keystrokeCount;
    }

    private void updateFileInfoMetrics(Document document, DocumentEvent documentEvent, KeystrokeCount.FileInfo fileInfo, KeystrokeCount keystrokeCount) {

        String text = documentEvent.getNewFragment() != null ? documentEvent.getNewFragment().toString() : "";
        String oldText = documentEvent.getOldFragment() != null ? documentEvent.getOldFragment().toString() : "";

        int new_line_count = document.getLineCount();
        fileInfo.length = document.getTextLength();

        // this will give us the positive char change length
        int numKeystrokes = documentEvent.getNewLength();
        // this will tell us delete chars
        int numDeleteKeystrokes = documentEvent.getOldLength();

        // count the newline chars
        int linesAdded = this.getNewlineCount(text);
        int linesRemoved = this.getNewlineCount(oldText);

        // check if its an auto indent
        boolean hasAutoIndent = text.matches("^\\s{2,4}$") || TAB_PATTERN.matcher(text).find();
        boolean newLineAutoIndent = text.matches("^\n\\s{2,4}$") || NEW_LINE_TAB_PATTERN.matcher(text).find();

        // update the deletion keystrokes if there are lines removed
        numDeleteKeystrokes = numDeleteKeystrokes >= linesRemoved ? numDeleteKeystrokes - linesRemoved : numDeleteKeystrokes;

        // event updates
        if (newLineAutoIndent) {
            // it's a new line with auto-indent
            fileInfo.auto_indents += 1;
            fileInfo.linesAdded += 1;
        } else if (hasAutoIndent) {
            // it's an auto indent action
            fileInfo.auto_indents += 1;
        } else if (linesAdded == 1) {
            // it's a single new line action (single_adds)
            fileInfo.single_adds += 1;
            fileInfo.linesAdded += 1;
        } else if (linesAdded > 1) {
            // it's a multi line paste action (multi_adds)
            fileInfo.linesAdded += linesAdded;
            fileInfo.paste += 1;
            fileInfo.multi_adds += 1;
            fileInfo.is_net_change = true;
            fileInfo.characters_added += Math.abs(numKeystrokes - linesAdded);
        } else if (numDeleteKeystrokes > 0 && numKeystrokes > 0) {
            // it's a replacement
            fileInfo.replacements += 1;
            fileInfo.characters_added += numKeystrokes;
            fileInfo.characters_deleted += numDeleteKeystrokes;
        } else if (numKeystrokes > 1) {
            // pasted characters (multi_adds)
            fileInfo.paste += 1;
            fileInfo.multi_adds += 1;
            fileInfo.is_net_change = true;
            fileInfo.characters_added += numKeystrokes;
        } else if (numKeystrokes == 1) {
            // it's a single keystroke action (single_adds)
            fileInfo.add += 1;
            fileInfo.single_adds += 1;
            fileInfo.characters_added += 1;
        } else if (linesRemoved == 1) {
            // it's a single line deletion
            fileInfo.linesRemoved += 1;
            fileInfo.single_deletes += 1;
            fileInfo.characters_deleted += numDeleteKeystrokes;
        } else if (linesRemoved > 1) {
            // it's a multi line deletion and may contain characters
            fileInfo.characters_deleted += numDeleteKeystrokes;
            fileInfo.multi_deletes += 1;
            fileInfo.is_net_change = true;
            fileInfo.linesRemoved += linesRemoved;
        } else if (numDeleteKeystrokes == 1) {
            // it's a single character deletion action
            fileInfo.delete += 1;
            fileInfo.single_deletes += 1;
            fileInfo.characters_deleted += 1;
        } else if (numDeleteKeystrokes > 1) {
            // it's a multi character deletion action
            fileInfo.multi_deletes += 1;
            fileInfo.is_net_change = true;
            fileInfo.characters_deleted += numDeleteKeystrokes;
        }

        fileInfo.lines = new_line_count;
        fileInfo.keystrokes += 1;
        keystrokeCount.keystrokes += 1;
    }

    // this is used to close unended files
    public void handleSelectionChangedEvents(String fileName, Project project) {
        KeystrokeCount keystrokeCount =
                getCurrentKeystrokeCount(project.getName(), project.getProjectFilePath());

        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        keystrokeCount.endPreviousModifiedFiles(fileName);
    }

    public void handleFileOpenedEvents(String fileName, Project project) {
        KeystrokeCount keystrokeCount =
                getCurrentKeystrokeCount(project.getName(), project.getProjectFilePath());

        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        keystrokeCount.endPreviousModifiedFiles(fileName);
        fileInfo.open = fileInfo.open + 1;
        int documentLineCount = SoftwareCoUtils.getLineCount(fileName);
        fileInfo.lines = documentLineCount;
        LOG.info("Code Time: file opened: " + fileName);
        tracker.trackEditorAction("file", "open", fileName);
    }

    public void handleFileClosedEvents(String fileName, Project project) {
        KeystrokeCount keystrokeCount =
                getCurrentKeystrokeCount(project.getName(), project.getProjectFilePath());
        KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
        if (fileInfo == null) {
            return;
        }
        fileInfo.close = fileInfo.close + 1;
        LOG.info("Code Time: file closed: " + fileName);
        tracker.trackEditorAction("file", "close", fileName);
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

                        if (project != null) {

                            // get the current keystroke count obj
                            KeystrokeCount keystrokeCount =
                                    getCurrentKeystrokeCount(project.getName(), project.getProjectFilePath());

                            // check whether it's a code time file or not
                            // .*\.software.*(data\.json|session\.json|latestKeystrokes\.json|ProjectContributorCodeSummary\.txt|CodeTime\.txt|SummaryInfo\.txt|events\.json|fileChangeSummary\.json)
                            boolean skip = (file == null || file.equals("") || fileName.matches(".*\\.software.*(data\\.json|session\\.json|latestKeystrokes\\.json|ProjectContributorCodeSummary\\.txt|CodeTime\\.txt|SummaryInfo\\.txt|events\\.json|fileChangeSummary\\.json)")) ? true : false;

                            if (!skip && keystrokeCount != null) {

                                KeystrokeCount.FileInfo fileInfo = keystrokeCount.getSourceByFileName(fileName);
                                if (StringUtils.isBlank(fileInfo.syntax)) {
                                    // get the grammar
                                    try {
                                        String fileType = file.getFileType().getName();
                                        if (fileType != null && !fileType.equals("")) {
                                            fileInfo.syntax = fileType;
                                        }
                                    } catch (Exception e) {}
                                }

                                updateFileInfoMetrics(document, documentEvent, fileInfo, keystrokeCount);

                                // update the latest payload
                                keystrokeCount.updateLatestPayloadLazily();
                            }
                        }
                    }

                }
            }
        });
    }

    public void createKeystrokeCountWrapper(String projectName, String projectFilepath) {
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
