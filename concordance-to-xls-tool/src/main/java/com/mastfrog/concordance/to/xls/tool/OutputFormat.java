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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JRadioButton;

/**
 *
 * @author Tim Boudreau
 */
public enum OutputFormat {

    XLSX,
    CSV,
    JSON;
    String ext;

    @Override
    public String toString() {
        return description() + " (" + ext() + ")";
    }

    public JRadioButton createButton() {
        JRadioButton button = new JRadioButton(shortDescription());
        button.setDisplayedMnemonicIndex(this == XLSX ? 1 : 0);
        button.setMnemonic(button.getText().charAt(this == XLSX ? 1 : 0));
        button.putClientProperty(OutputFormat.class, this);
        button.setToolTipText(toString());
        return button;
    }

    static List<JRadioButton> buttons(OutputFormat selected) {
        List<JRadioButton> result = Arrays.asList(XLSX.createButton(), CSV.createButton(), JSON.createButton());
        for (JRadioButton b : result) {
            if (selected == match(b)) {
                b.setSelected(true);
            } else {
                b.setSelected(false);
            }
        }
        return result;
    }

    public static OutputFormat match(JComponent comp) {
        return (OutputFormat) comp.getClientProperty(OutputFormat.class);
    }

    public boolean is(Path path) {
        return path != null && path.getFileName().toString().endsWith("." + ext());
    }

    public Path withExtension(Path path) {
        String fn = path.getFileName().toString();
        String ext = "." + ext();
        if (fn.endsWith(ext)) {
            return path;
        }
        int ix = fn.lastIndexOf('.');
        if (ix > 0 && ix < fn.length() - 1) {
            fn = fn.substring(0, ix);
        }
        return path.getParent().resolve(fn + ext);
    }

    public String shortDescription() {
        switch (this) {
            case XLSX:
                return "Spreadsheet";
            case CSV:
                return "CSV";
            case JSON:
                return "JSON";
            default:
                throw new AssertionError(this);
        }
    }

    public String description() {
        switch (this) {
            case XLSX:
                return "Excel Workbook";
            case CSV:
                return "Comma-Separated-Values";
            case JSON:
                return "Javascript Object Notation";
            default:
                throw new AssertionError(this);
        }
    }

    public String ext() {
        if (ext == null) {
            ext = name().toLowerCase();
        }
        return ext;
    }
}
