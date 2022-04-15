/*
 * Copyright (c) 2022 Tim Boudreau
 *
 * This file is part of the concordance-to-xls tool.
 *
 * The concordance-to-xls tool is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.mastfrog.concordance.to.xls.tool;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.mastfrog.function.state.Int;
import com.mastfrog.swing.FlexEmptyBorder;
import com.mastfrog.swing.layout.VerticalFlowLayout;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.strings.Strings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import javax.swing.text.StyledDocument;

/**
 *
 * @author Tim Boudreau
 */
public class UI {

    private final ConversionSettings settings;

    UI(ConversionSettings settings) {
        this.settings = settings;
    }

    void show() {
        EventQueue.invokeLater(this::_show);
    }

    private void _show() {
        JPanel mainUI = new JPanel(new BorderLayout());
//        mainUI.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        mainUI.setBorder(new FlexEmptyBorder());
        JPanel inner = new JPanel(new GridBagLayout());
        JPanel innerHolder = new JPanel(new BorderLayout());
        innerHolder.add(inner, BorderLayout.CENTER);

//        JEditorPane problems = new JEditorPane();
        Doc problemsDoc = new Doc();
        JTextArea problems = new JTextArea(problemsDoc);
        problems.setEditable(false);
        problems.setMaximumSize(new Dimension(1000, 450));
        problems.setLineWrap(true);
//        problems.setWrapStyleWord(true);
        problems.setColumns(120);
        problems.setRows(10);
        JScrollPane scrollProblems = new JScrollPane(problems);
//        scrollProblems.setBorder(BorderFactory.createEmptyBorder());
        scrollProblems.setBorder(new TitledBorder("Problems"));
//        mainUI.add(scrollProblems, BorderLayout.SOUTH);
        scrollProblems.setVisible(false);

        Runnable ensureProblemsVisible = new Runnable() {
            boolean isVisible = false;

            @Override
            public void run() {
                if (!EventQueue.isDispatchThread()) {
                    if (isVisible) {
                        return;
                    }
                    EventQueue.invokeLater(this);
                } else {
                    isVisible = true;
                    scrollProblems.setVisible(true);
                    if (scrollProblems.getParent() != null) {
                        JComponent par = (JComponent) scrollProblems.getParent();
                        par.invalidate();
                        par.revalidate();
                        par.repaint();
                    }
                }
            }
        };
        Consumer<String> problemsConsumer = new Consumer<String>() {
            Consumer<String> realConsumer = new ProblemsConsumer(problemsDoc);

            @Override
            public void accept(String t) {
                realConsumer.accept(t);
                ensureProblemsVisible.run();
            }
        };

        mainUI.add(innerHolder, BorderLayout.CENTER);
        JLabel status = new JLabel("<html>&nbsp;");
        mainUI.add(status, BorderLayout.SOUTH);
        GridBagConstraints con = new GridBagConstraints();
        JButton actionButton = new JButton("Go");
        actionButton.setEnabled(false);

        // The real main panel
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.TRAILING));

        JPanel bottomOuter = new JPanel(new BorderLayout());
        bottomOuter.add(bottom, BorderLayout.SOUTH);
        bottomOuter.add(scrollProblems, BorderLayout.NORTH);

        outer.add(bottomOuter, BorderLayout.SOUTH);
        outer.add(mainUI, BorderLayout.CENTER);
        mainUI.setMinimumSize(new Dimension(600, 600));
        bottom.add(actionButton);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(ae -> {
            System.exit(0);
        });
        bottom.add(cancelButton);

        List<BooleanSupplier> validators = new ArrayList<>();
        Runnable onChange = () -> {
            boolean result = true;
            for (BooleanSupplier s : validators) {
                result &= s.getAsBoolean();
                if (!result) {
                    break;
                }
            }
            if (result) {
                status.setText("<html>&nbsp;");
            }
            actionButton.setEnabled(result);
        };

        con.fill = GridBagConstraints.HORIZONTAL;
        con.anchor = GridBagConstraints.ABOVE_BASELINE_LEADING;
        con.gridheight = 1;
        con.gridwidth = 1;
        con.weightx = 1;
        con.weightx = 1;
//        con.
//        con.weighty = 1;
        con.weighty = 1D / 5;

        con.insets = new Insets(0, 5, 5, 5);
        con.gridx = con.gridy = 0;
        int totalW = 5;
        JPanel top = new JPanel(new BorderLayout());
        JLabel head = new JLabel("Convert Concordance File");
        Color bl = UIManager.getColor("textText");
        Font bold = head.getFont().deriveFont(Font.BOLD);
        head.setFont(bold.deriveFont(head.getFont().getSize2D() + 2));
        head.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, bl));
        top.add(head, BorderLayout.NORTH);
        JLabel instructions = new JLabel("Choose directories to scan for .opt/.dat file pairs, choose settings, then click Go to generate.");
        instructions.setFont(instructions.getFont().deriveFont(Font.PLAIN).deriveFont(instructions.getFont().getSize() - 1));
        instructions.setBorder(BorderFactory.createEmptyBorder(12, 0, 5, 12));
        top.add(instructions, BorderLayout.CENTER);

        mainUI.add(top, BorderLayout.NORTH);

//        con.gridwidth = totalW;
        JLabel folderLabel = new JLabel("Folder to Scan");
        JTextField scanFolderField = focusSelectAll(new JTextField());
        Path root = settings.root();
        if (root != null) {
            scanFolderField.setText(root.toString());
        }
        folderLabel.setLabelFor(scanFolderField);
        scanFolderField.setColumns(100);

        scanFolderField.getDocument().addDocumentListener(docRun(onChange));

        JButton browse = new JButton("Browse");

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Folders";
            }
        });
        if (root != null && Files.exists(root)) {
            chooser.setSelectedFile(root.toFile());
        }
        browse.addActionListener(ae -> {
            switch (chooser.showDialog(mainUI, "OK")) {
                case JFileChooser.APPROVE_OPTION:
                    Path pp = chooser.getSelectedFile().toPath();
                    if (Files.exists(pp)) {
                        scanFolderField.setText(pp.toString());
                    }
            }
        });
        inner.add(folderLabel, con);
        con.gridx++;
        con.gridwidth = 3;

        inner.add(scanFolderField, con);
        con.gridwidth = 1;
        con.gridx += 3;
        con.fill = GridBagConstraints.NONE;
        con.anchor = GridBagConstraints.ABOVE_BASELINE_TRAILING;
        inner.add(browse, con);
        con.fill = GridBagConstraints.HORIZONTAL;
        con.anchor = GridBagConstraints.ABOVE_BASELINE_LEADING;
        con.gridx = 0;
        con.gridy++;

        JLabel scanLabel = new JLabel("Scan For .dat/.opt files in");
        JRadioButton recur = new JRadioButton("All subfolders recursively");
        recur.setToolTipText("<html>Scan the selected folder <b>and all of its subfolders</b> for .dat/.opt files<br>(can take a while)");
        recur.setSelected(settings.scan());
        JRadioButton dirOnly = new JRadioButton("Just this folder");
        dirOnly.setToolTipText("Only scan the selected folder for .dat/.opt files");
        dirOnly.setSelected(!settings.scan());
        ButtonGroup bg = new ButtonGroup();
        scanLabel.setLabelFor(recur);
        bg.add(recur);
        bg.add(dirOnly);
        inner.add(scanLabel, con);
        con.weightx = 0;
        con.gridx++;
        inner.add(recur, con);
        con.gridx++;
        inner.add(dirOnly, con);
        con.weightx = 1;
        con.gridx = 0;
        con.gridy++;

        JTextField outputField = focusSelectAll(new JTextField());

        JLabel outLabel = new JLabel("Output File Type");
        List<JRadioButton> formatButtons = OutputFormat.buttons(settings.format());
        Supplier<OutputFormat> currFormat = () -> {
            for (JRadioButton b : formatButtons) {
                OutputFormat result = OutputFormat.match(b);
                if (result != null) {
                    if (b.isSelected()) {
                        return result;
                    }
                }
            }
            return OutputFormat.XLSX;
        };
        for (JRadioButton b : formatButtons) {
            b.addActionListener(ae -> {
                OutputFormat result = OutputFormat.match(b);
                String txt = outputField.getText();
                if (!txt.isEmpty()) {
                    Path p = Paths.get(txt);
                    p = result.withExtension(p);
                    outputField.setText(p.toString());
                }
            });
        }
        ButtonGroup group = new ButtonGroup();
        inner.add(outLabel, con);
        con.gridx++;
        for (int i = 0; i < formatButtons.size(); i++) {
            JRadioButton b = formatButtons.get(i);
            if (i == 0) {
                outLabel.setLabelFor(b);
            }
            inner.add(b, con);
            con.gridx++;
            group.add(b);
        }

        con.gridy++;
        con.gridx = 0;

        Path output = settings.output();
        JLabel outputFileLabel = new JLabel("Output File");
        outputFileLabel.setDisplayedMnemonic('F');
        outputFileLabel.setDisplayedMnemonicIndex(7);
        outputFileLabel.setDisplayedMnemonicIndex(0);

        Path of = settings.output();
        if (of != null) {
            outputField.setText(of.toString());
        }

        outputField.setColumns(100);
        outputField.getDocument().addDocumentListener(docRun(onChange));
        JButton browse2 = new JButton("Browse");
        outputFileLabel.setLabelFor(browse2);

        browse2.addActionListener(ae -> {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Output File (." + (settings.ext() + ")"));
            ch.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return true;
                }

                @Override
                public String getDescription() {
                    return "." + settings.ext() + " Files";
                }
            });
            if (ch.showSaveDialog(mainUI) == JFileChooser.APPROVE_OPTION) {
                outputField.setText(ch.getSelectedFile().toPath().toString());
            }
        });

        inner.add(outputFileLabel, con);
        con.gridx++;
        con.gridwidth = 3;
        inner.add(outputField, con);
        con.gridwidth = 1;
        con.gridx += 3;
        con.fill = GridBagConstraints.NONE;
        con.anchor = GridBagConstraints.ABOVE_BASELINE_TRAILING;
        inner.add(browse2, con);
        con.fill = GridBagConstraints.HORIZONTAL;
        con.anchor = GridBagConstraints.ABOVE_BASELINE_LEADING;

        JTextArea filtersArea = new JTextArea(settings.filters().toString());
        filtersArea.setColumns(120);
        filtersArea.setRows(4);
        filtersArea.setLineWrap(true);
        filtersArea.setWrapStyleWord(true);
        JScrollPane filtersScroll = new JScrollPane(filtersArea);
//        filtersScroll.setBorder(BorderFactory.createEmptyBorder());
        filtersScroll.setViewportBorder(BorderFactory.createEmptyBorder());

        con.gridx = 0;
        con.gridy++;
        JLabel filtersLabel = new JLabel("Exclude");
        filtersLabel.setDisplayedMnemonic('E');
        filtersLabel.setDisplayedMnemonicIndex(0);
        filtersLabel.setLabelFor(filtersArea);
        inner.add(filtersLabel, con);
        con.gridx++;
        con.gridwidth = totalW - 1;
        con.fill = GridBagConstraints.BOTH;
        inner.add(filtersScroll, con);
        con.fill = GridBagConstraints.HORIZONTAL;
        double oldwy = con.weighty;
        con.weighty = 0;
        JLabel intrs = new JLabel("Case-sensitive, comma-separated list of words or phrases that cause a record to be ignored.");
        intrs.setFont(intrs.getFont().deriveFont(Font.PLAIN).deriveFont(intrs.getFont().getSize() - 1));
        con.gridy++;
        inner.add(intrs, con);
        con.gridy++;
        JLabel intrs2 = new JLabel("Leading and trailing whitespace is ignored.");
        intrs2.setFont(intrs.getFont());
        con.gridy++;
        inner.add(intrs2, con);
        con.gridy++;
        con.gridx = 0;
        con.gridwidth = 1;
        intrs.setLabelFor(filtersArea);
        intrs2.setLabelFor(filtersArea);
        con.weighty = oldwy;

        validators.add(() -> {
            String out = outputField.getText();
            if (out.isBlank()) {
                status.setText("Destination not set");
                return false;
            }
            Path p = Paths.get(out);
            if (p == null || p.getParent() == null) {
                status.setText("Output file is unset");
                return false;
            }
            if (!Files.exists(p.getParent())) {
                status.setText("Output file parent folder does not exist.");
                return false;
            }
            return true;
        });
        validators.add(() -> {
            String out = outputField.getText();
            if (out.isBlank()) {
                status.setText("Source folder not set");
                return false;
            }
            Path p = Paths.get(out);
            if (p == null || p.getParent() == null) {
                status.setText("Source folder unset");
                return false;
            }
            if (!Files.exists(p.getParent())) {
                status.setText("Source folder does not exist.");
                return false;
            }
            if (!Files.isDirectory(p.getParent())) {
                status.setText("Source folder is not a directory.");
                return false;
            }
            return true;
        });
        actionButton.addActionListener(ae -> {
            if (actionButton.isEnabled()) { // doClick ignores
                actionButton.setEnabled(false);

                settings.dest(Paths.get(outputField.getText()));
                if (Files.exists(settings.output())) {
                    String msg = "File exists.  Replace " + settings.output().getFileName() + "?";
                    String ttl = "Confirm Replacing " + settings.format().shortDescription();
                    int res = JOptionPane.showConfirmDialog(mainUI, msg, ttl, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (res != JOptionPane.YES_OPTION) {
                        actionButton.setEnabled(true);
                        return;
                    }
                }
                instructions.setVisible(false);
                settings.filters().setFilters(filtersArea.getText().trim());
                settings.filters().save();

                settings.format(currFormat.get());
                settings.scan(recur.isSelected());
                settings.root(Paths.get(scanFolderField.getText()));

                Int counter = settings.filters().counter();

                mainUI.remove(inner);
                JPanel progressContainer = new JPanel(new VerticalFlowLayout(5));

                JScrollPane scroll = new JScrollPane();
                mainUI.remove(innerHolder);
                mainUI.add(scroll, BorderLayout.CENTER);
                scroll.setViewportView(progressContainer);

                mainUI.invalidate();
                mainUI.revalidate();
                mainUI.repaint();

                BiConsumer<Boolean, String> onLastDone = (Boolean aborted, String msg) -> {
                    cancelButton.setText("Exit");
                    cancelButton.setMnemonic('x');
                    cancelButton.setDisplayedMnemonicIndex(1);
                    if (!aborted) {
                        JTextArea jta = focusSelectAll(new JTextArea());
                        jta.setLineWrap(true);
                        jta.setWrapStyleWord(true);
                        jta.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                        jta.setBackground(mainUI.getBackground());
                        String txt = "Wrote " + settings.output();
                        if (!settings.filters().isEmpty()) {
                            txt += "\n\n";
                            txt += counter.getAsInt() + " records excluded by the filters " + settings.filters();
                        }
                        jta.setText(txt);
                        jta.setEditable(false);
                        scroll.setViewportView(jta);

                        scroll.invalidate();
                        scroll.revalidate();
                        scroll.repaint();
                        JButton openButton = new JButton("Open In System Viewer");
                        openButton.setMnemonic('o');
                        openButton.setDisplayedMnemonicIndex(0);
                        JFrame jf = (JFrame) mainUI.getTopLevelAncestor();
                        cancelButton.getParent().remove(cancelButton);
                        jf.getRootPane().setDefaultButton(openButton);
                        openButton.addActionListener(ax -> {
                            try {
                                Desktop.getDesktop().open(settings.output().toFile());
                                System.exit(0);
                            } catch (IOException ex) {
                                JOptionPane.showMessageDialog(mainUI, "Open failed: " + ex.getMessage());
                                Logger.getLogger(UI.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });

                        JButton another = new JButton("Do Another");
                        another.setDisplayedMnemonicIndex(3);
                        another.setMnemonic('A');
                        another.addActionListener(ae2 -> {
                            this.show();
                            jf.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                            jf.setVisible(false);
                        });

                        bottom.remove(actionButton);
                        bottom.add(another);
                        bottom.add(openButton);
                        bottom.add(cancelButton);
                        bottom.invalidate();
                        bottom.revalidate();
                        bottom.repaint();
                    }
                };

                class PC implements ProgressConsumer {

                    final Consumer<String> problems;

                    public PC(Consumer<String> problems) {
                        this.problems = problems;
                    }

                    @Override
                    public ProgressTask task(int thread, String task, Phase phase) {
                        System.out.println("new Task " + thread + " " + phase + " " + task);
                        ProgressPanel pnl = new ProgressPanel(problems, thread, task, phase, onLastDone);
                        progressContainer.add(pnl, 0);
                        progressContainer.invalidate();
                        progressContainer.revalidate();
                        progressContainer.repaint();
                        return pnl;
                    }

                    @Override
                    public void onError(String error, Throwable thrown, boolean fatal) {
                        if (fatal) {
                            JOptionPane.showMessageDialog(mainUI, error);
                            System.exit(0);
                        } else {
                            problems.accept(error);
                            problems.accept(Strings.toString(thrown));
                            System.out.println(thrown + ": " + error);
                        }
                    }
                }
                ProgressConsumer prog = new PC(problemsConsumer).replanning();
                ConcordanceToXlsTool tool = new ConcordanceToXlsTool(settings);
                try {
                    tool.launch(prog);
                } catch (IOException ex) {
                    Logger.getLogger(UI.class.getName()).log(Level.SEVERE, null, ex);
                    prog.onError(ex.getMessage() + "", ex, true);
                }
            }
        });

        onChange.run();
        JFrame frame = new JFrame("Concordance Load File to XLS / CSV");
        frame.setLocationByPlatform(true);
        frame.setContentPane(outer);
        frame.setMinimumSize(new Dimension(600, 800));
        frame.pack();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getRootPane().setDefaultButton(actionButton);
        frame.setVisible(true);
    }

    static class ProblemsConsumer implements Consumer<String>, Runnable {

        private final ExecutorService svc = Executors.newSingleThreadExecutor();
        private final AtomicBoolean enqueued = new AtomicBoolean();
        private final ConcurrentLinkedList<String> pending = ConcurrentLinkedList.lifo();
        private final Doc doc;

        public ProblemsConsumer(Doc doc) {
            this.doc = doc;
        }

        @Override
        public void accept(String t) {
            pending.push(t);
            if (enqueued.compareAndSet(false, true)) {
                svc.submit(this);
            }
        }

        @Override
        public void run() {
            int loops = 0;
            try {
                while (!pending.isEmpty()) {
                    List<String> l = new LinkedList<>();
                    pending.drain(l::add);
                    if (!l.isEmpty()) {
                        Collections.reverse(l);
                        doc.writeLocked(() -> {
                            for (String s : l) {
                                try {
                                    doc.insertString(0, s + "\n\n", null);
                                } catch (BadLocationException ex) {
                                    Logger.getLogger(UI.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        });
                    }
                }
            } finally {
                enqueued.set(false);
            }
        }
    }

    static class ProgressPanel extends JPanel implements ProgressConsumer.ProgressTask {

        final JProgressBar progress = new JProgressBar();
        final JLabel status = new JLabel("Running...");
        final JLabel title = new JLabel();
        private final Phase phase;
        private final BiConsumer<Boolean, String> onLastDone;
        private final Consumer<String> problems;

        ProgressPanel(Consumer<String> problems, int thread, String task, Phase phase, BiConsumer<Boolean, String> onLastDone) {
            this.phase = phase;
            this.problems = problems;
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
            setLayout(new BorderLayout());
            title.setBorder(BorderFactory.createEmptyBorder(12, 5, 0, 5));
            status.setBorder(BorderFactory.createEmptyBorder(5, 5, 12, 5));
            progress.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            title.setText(task + " " + thread + " (" + phase + ")");
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            add(title, BorderLayout.NORTH);
            add(progress, BorderLayout.CENTER);
            add(status, BorderLayout.SOUTH);
            progress.setIndeterminate(true);
            this.onLastDone = onLastDone;
        }

        @Override
        public void progress(int step, int of) {
            if (progress.isIndeterminate()) {
                progress.setIndeterminate(false);
            }
            BoundedRangeModel mdl = progress.getModel();
            mdl.setRangeProperties(step, of, 0, of, false);
        }

        @Override
        public void done(boolean aborted, String msg) {
            System.out.println("done " + (aborted ? " aborted " : " ok ") + msg);
            progress.setIndeterminate(false);
            if (!aborted) {
                BoundedRangeModel mdl = progress.getModel();
                mdl.setRangeProperties(10, 10, 0, 10, false);
            }
            status.setText("Done. " + msg);
            if (phase == Phase.GENERATING) {
                onLastDone.accept(aborted, msg);
            }
        }

        @Override
        public void status(String status) {
//            System.out.println("  " + title.getText() + " -> " + status);
            this.status.setText(status);
        }

        @Override
        public void problem(String problem) {
            problems.accept(problem);
        }
    }

    DocumentListener docRun(Runnable r) {
        return new DocRun(r);
    }

    private static class DocRun implements DocumentListener {

        private final Runnable run;

        DocRun(Runnable run) {
            this.run = run;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            EventQueue.invokeLater(run);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            EventQueue.invokeLater(run);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            // do nothings
        }
    }

    static <T extends JTextComponent> T focusSelectAll(T comp) {
        comp.addFocusListener(Focuser.INSTANCE);
        return comp;
    }

    static class Focuser extends FocusAdapter {

        static Focuser INSTANCE = new Focuser();

        @Override
        public void focusGained(FocusEvent e) {
            if (e.getComponent() instanceof JTextComponent) {
                ((JTextComponent) e.getComponent()).selectAll();
            }
        }
    }

    static class Doc extends PlainDocument {

        public void writeLocked(Runnable run) {
            writeLock();
            try {
                run.run();
            } finally {
                writeUnlock();
            }
        }
    }
}
