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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 *
 * @author Tim Boudreau
 */
public class ConversionSettings {

    private Path root;
    private Path dest = destFile("dest", "ConcordanceConversion.xlsx");
    private boolean scan;
    private OutputFormat format = OutputFormat.XLSX;
    private Filters filters = Filters.load();

    public ConversionSettings() {
        Optional<String> oldRoot = getFromPrefs("root");
        if (oldRoot.isPresent()) {
            Path p = Paths.get(oldRoot.get());
            if (Files.exists(p) && Files.isReadable(p)) {
                root = p;
            } else {
                root = Paths.get(System.getProperty("user.home"));
            }
        } else {
            root = Paths.get(System.getProperty("user.home"));
        }
        Optional<String> fmt = getFromPrefs("fmt");
        if (fmt.isPresent()) {
            try {
                OutputFormat f = OutputFormat.valueOf(fmt.get());
                format = f;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public Filters filters() {
        return filters;
    }

    public ConversionSettings format(OutputFormat fmt) {
        this.format = fmt;
        Preferences.userNodeForPackage(ConversionSettings.class)
                .put("fmt", fmt.name());
        return this;
    }

    public OutputFormat format() {
        return format;
    }

    public String ext() {
        return format.ext();
    }

    private String extOf(Path p) {
        String s = p.getFileName().toString();
        int ix = s.lastIndexOf('.');
        if (ix >= 0 && ix < s.length() - 1) {
            return s.substring(ix + 1);
        }
        return "";
    }

    private String rawName(String s) {
        int ix = s.lastIndexOf('.');
        if (ix >= 0) {
            return s.substring(0, ix);
        }
        return s;
    }

    public Path output() {
        Path p = dest;
        String targetExt = ext();
        String myExt = extOf(p);
        if (!targetExt.equals(myExt)) {
            p = p.getParent().resolve(rawName(p.getFileName().toString() + "." + myExt));
        }
        return p;
    }

    public ConversionSettings dest(Path dest) {
        this.dest = dest;
        Preferences.userNodeForPackage(ConversionSettings.class)
                .put("dest", dest.toString());
        return this;
    }

    public ConversionSettings root(Path root) {
        this.root = root;
        Preferences prefs = Preferences.userNodeForPackage(ConversionSettings.class);
        prefs.put("root", root.toString());
        return this;
    }

    public ConversionSettings scan(boolean val) {
        this.scan = val;
        return this;
    }

    public boolean scan() {
        return scan;
    }

    public Path root() {
        return root;
    }

    private Optional<String> getFromPrefs(String key) {
        Preferences prefs = Preferences.userNodeForPackage(ConversionSettings.class);
        String result = prefs.get(key, "");
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private static Path destFile(String key, String name) {
        Preferences prefs = Preferences.userNodeForPackage(ConversionSettings.class);
        String result = prefs.get(key, "");
        if (result.isEmpty()) {
            return defaultFile(name);
        } else {
            Path p = Paths.get(result);
            if (!Files.exists(p.getParent())) {
                return defaultFile(name);
            }
            return p;
        }
    }

    private static Path defaultFile(String name) {
        Path dir = Paths.get(System.getProperty("user.home"));
        return dir.resolve(name);
    }
}
