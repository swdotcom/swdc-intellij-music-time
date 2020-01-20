package com.softwareco.intellij.plugin.music;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.logging.Logger;

public class TreeScanner extends MouseMotionAdapter {

    public static final Logger LOG = Logger.getLogger("TreeScanner");

    @Override
    public void mouseMoved(MouseEvent e){
        super.mouseMoved(e);
        JTree tree=(JTree) e.getSource();
        int selRow=tree.getRowForLocation(e.getX(), e.getY());
        tree.setSelectionRow(selRow);
        tree.requestFocusInWindow();
    }

}
