package com.softwareco.intellij.plugin.music;

import com.softwareco.intellij.plugin.SoftwareCoSessionManager;

import javax.swing.tree.*;

public class PlaylistTreeNode extends DefaultMutableTreeNode {

    protected DefaultTreeModel model;
    private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();

    private String id;
    private String toolTip;

    public PlaylistTreeNode(String nodeName, String id) {
        super(nodeName);
        this.model = null;
        this.id = id;
        this.toolTip = nodeName;
    }

    public void setModel(DefaultTreeModel model) {
        this.model = model;
    }

    public void add(MutableTreeNode node) {
        super.add(node);
        nodeWasAdded(this, getChildCount() - 1);
    }

    protected void nodeWasAdded(TreeNode node, int index) {
        if (model == null) {
            ((PlaylistTreeNode)node.getParent()).nodeWasAdded(node, index);
        }
        else {
            int[] childIndices = new int[1];
            childIndices[0] = index;
            model.nodesWereInserted(node, childIndices);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToolTip() {
        return toolTip;
    }

    public void setToolTip(String toolTip) {
        this.toolTip = toolTip;
    }
}
