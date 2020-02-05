package com.softwareco.intellij.plugin.music;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ListRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if(value instanceof JLabel)
        {
            this.setText(((JLabel)value).getText());
            this.setToolTipText(((JLabel)value).getToolTipText());
            this.setIcon(((JLabel)value).getIcon());
            this.setBorder(new EmptyBorder(2, 10, 2, 0));
        }
        return this;
    }
}
