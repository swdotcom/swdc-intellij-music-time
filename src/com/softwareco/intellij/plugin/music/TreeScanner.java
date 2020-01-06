package com.softwareco.intellij.plugin.music;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.logging.Logger;

public class TreeScanner extends MouseMotionAdapter {

    public static final Logger LOG = Logger.getLogger("TreeScanner");

    static int lastSelected=-1;

//    @Override
//    public void mouseExited(MouseEvent e){
//        JTree tree=(JTree) e.getSource();
//        lastSelected=-1;
//        tree.clearSelection();
//    }

    @Override
    public void mouseMoved(MouseEvent e){
        JTree tree=(JTree) e.getSource();
        int selRow=tree.getRowForLocation(e.getX(), e.getY());
        tree.setSelectionRow(selRow);
        tree.setFocusable(true);
    }

}
