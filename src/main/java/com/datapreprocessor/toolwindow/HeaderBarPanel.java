package com.datapreprocessor.toolwindow;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

/**
 * The header bar that spans the top of the tool window.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────┐
 * │ [Browse…]  [CSV] employees.csv       [Reload]    │
 * └──────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>All user-driven actions are surfaced as constructor callbacks so
 * this panel has no direct dependency on the coordinator:</p>
 * <ul>
 *   <li>{@code onBrowse} — invoked when the Browse button is clicked.</li>
 *   <li>{@code onReload} — invoked when the Reload button is clicked.</li>
 * </ul>
 */
class HeaderBarPanel {

    private final Runnable onBrowse;
    private final Runnable onReload;

    private final JLabel pathLabel   = new JLabel("No file loaded");
    private final JLabel formatBadge = new JLabel();
    private JButton reloadBtn;

    HeaderBarPanel(Runnable onBrowse, Runnable onReload) {
        this.onBrowse = onBrowse;
        this.onReload = onReload;
    }

    JComponent getContent() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // ── Browse button (left) ──────────────────────────────────────────────
        JButton browseBtn = new JButton("Browse…");
        browseBtn.addActionListener(e -> onBrowse.run());

        // ── Path label + format badge (centre) ───────────────────────────────
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.ITALIC, 11f));
        pathLabel.setForeground(JBColor.GRAY);

        formatBadge.setFont(formatBadge.getFont().deriveFont(Font.BOLD, 10f));
        formatBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));
        formatBadge.setVisible(false);

        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pathPanel.setOpaque(false);
        pathPanel.add(formatBadge);
        pathPanel.add(pathLabel);

        // ── Reload button (right) ────────────────────────────────────────────
        reloadBtn = new JButton("Reload");
        reloadBtn.setEnabled(false); // enabled once a file is loaded
        reloadBtn.setToolTipText("Re-read the file from disk (preserves your pipeline steps)");
        reloadBtn.addActionListener(e -> onReload.run());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(reloadBtn);

        bar.add(browseBtn, BorderLayout.WEST);
        bar.add(pathPanel, BorderLayout.CENTER);
        bar.add(right,     BorderLayout.EAST);
        return bar;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Updates the path label and format badge when a new file is loaded.
     *
     * @param fileName short display name (e.g. {@code employees.csv})
     * @param fullPath full absolute path shown in the tooltip
     * @param ext      uppercase extension used as the badge label (e.g. {@code "CSV"})
     */
    void setFileInfo(String fileName, String fullPath, String ext) {
        pathLabel.setText(fileName);
        pathLabel.setToolTipText(fullPath);
        pathLabel.setForeground(JBColor.foreground());
        formatBadge.setText(ext);
        formatBadge.setVisible(!ext.isEmpty());
    }

    /** Enables or disables the Reload button. */
    void setReloadEnabled(boolean enabled) {
        if (reloadBtn != null) reloadBtn.setEnabled(enabled);
    }
}
