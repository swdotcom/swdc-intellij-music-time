/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;

public class SoftwareCoDocumentListener implements DocumentListener {

    private SoftwareCoEventManager eventMgr = SoftwareCoEventManager.getInstance();

    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        Document document = documentEvent.getDocument();

        eventMgr.handleChangeEvents(document, documentEvent);
    }
}