package org.data.wrangler.excel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Excel's sheet tabs: a strip along the bottom, active tab white with green
 * text and a green underline, inactive tabs grey. Sheets live in a CardLayout above.
 */
public final class SheetTabStrip extends JPanel {

 private final JPanel cards = new JPanel(new CardLayout());
 private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
 private final List<JLabel> tabs = new ArrayList<>();
 private final List<String> names = new ArrayList<>();
 private int active = -1;

 public SheetTabStrip() {
 super(new BorderLayout());
 strip.setBackground(SpreadsheetTable.HEADER_BG);
 strip.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SpreadsheetTable.HEADER_BORDER));
 add(cards, BorderLayout.CENTER);
 add(strip, BorderLayout.SOUTH);
    }

 public void addSheet(String name, JComponent sheet) {
 cards.add(sheet, name);
        JLabel tab = new JLabel(name);
 tab.setOpaque(true);
 tab.setFont(SpreadsheetTable.CELL_FONT);
 tab.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
 int index = tabs.size();
 tab.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { select(index); }
        });
 tabs.add(tab);
 names.add(name);
 strip.add(tab);
 if (active < 0) select(0); else style();
 strip.revalidate();
    }

 public void select(int index) {
 active = index;
        ((CardLayout) cards.getLayout()).show(cards, names.get(index));
 style();
    }

 public String activeSheet() { return active < 0 ? null : names.get(active); }

 public void clear() {
 cards.removeAll(); strip.removeAll(); tabs.clear(); names.clear(); active = -1;
 revalidate(); repaint();
    }

 private void style() {
 for (int i = 0; i < tabs.size(); i++) {
            JLabel t = tabs.get(i);
 boolean a = i == active;
 t.setBackground(a ? Color.WHITE : SpreadsheetTable.HEADER_BG);
 t.setForeground(a ? SpreadsheetTable.GREEN : SpreadsheetTable.HEADER_FG);
 t.setFont(a ? SpreadsheetTable.BOLD_FONT : SpreadsheetTable.CELL_FONT);
 t.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, a ? 2 : 0, 1, a ? SpreadsheetTable.GREEN : SpreadsheetTable.HEADER_BORDER),
                    BorderFactory.createEmptyBorder(4, 14, a ? 2 : 4, 14)));
        }
 strip.repaint();
    }

 public Component currentSheet() {
 for (Component c : cards.getComponents()) if (c.isVisible()) return c;
 return null;
    }
}
