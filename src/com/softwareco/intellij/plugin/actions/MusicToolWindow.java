package com.softwareco.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.treeStructure.Tree;
import com.softwareco.intellij.plugin.PlaylistTreeNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MusicToolWindow extends AnAction {

    private Wrapper wraper;
    private JPanel playlistWindowContent;
    private JBScrollPane scrollPane;
    private JButton refresh;
    private List<Tree> playlist;
    private JLabel label;

    //private JTree[] playlist;
    //private JTree tree1;

    public MusicToolWindow(ToolWindow toolWindow, Wrapper wraper) {
        this.wraper = wraper;
        //refresh.addActionListener(e -> refreshButton());
        this.currentPlayList();
        JComponent component = toolWindow.getComponent();
        component.removeAll();
        component.add(scrollPane);
        component.revalidate();
        component.updateUI();
        component.setVisible(true);
//        tree1.addMouseMotionListener(new MouseMotionAdapter() {
//            @Override
//            public void mouseDragged(MouseEvent e) {
//                super.mouseDragged(e);
//            }
//
//            @Override
//            public void mouseMoved(MouseEvent e) {
//
//            }
//        });


    }

    public void currentPlayList() {
        //String xmlFilePath = System.getProperty("user.dir") + "\\resources\\META-INF\\plugin.xml";

        //playlistWindowContent.setFocusable(true);
//        playlistWindowContent = new JPanel();
//        playlistWindowContent.setLayout(new BorderLayout());
        PlaylistTreeNode list = new PlaylistTreeNode();
        DefaultTreeModel model = new DefaultTreeModel(list);
        list.setModel(model);
        list.add(new DefaultMutableTreeNode("Song 1"));
        list.add(new DefaultMutableTreeNode("Song 2"));
        list.add(new DefaultMutableTreeNode("Song 3"));
        Tree topsongs = new Tree(list);
        topsongs.setName("Top 40 Songs");
        playlist = new ArrayList<>();
        playlist.add(topsongs);
        scrollPane = new JBScrollPane();
        scrollPane.add(playlist.get(0));
        scrollPane.revalidate();
        //scrollPane.repaint();
//        playlistWindowContent.add(scrollPane);
//        playlistWindowContent.revalidate();
//        playlistWindowContent.setVisible(true);
//        wraper.setContent(playlistWindowContent);
//        wraper.setVisible(true);
        //playlistWindowContent.repaint();
        //JScrollPane treePane = new JScrollPane(playlist[0]);

    }

    public void refreshButton() {
        label.setText("Refreshed");
        playlistWindowContent.revalidate();
        playlistWindowContent.repaint();
    }

    public JPanel getContent() {
        return playlistWindowContent;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        playlistWindowContent.removeAll();
        playlistWindowContent.add(scrollPane);
        playlistWindowContent.revalidate();
        playlistWindowContent.updateUI();
        playlistWindowContent.setVisible(true);
    }
}
