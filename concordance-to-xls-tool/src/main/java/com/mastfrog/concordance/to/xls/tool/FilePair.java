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

import com.mastfrog.concordance.to.xls.tool.ItemKey;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public final class FilePair {

    private final Path optFile;
    private final Path datFile;
    private String vname;
    private ItemKey ikey;

    public FilePair(Path optFile, Path datFile) {
        this.optFile = optFile;
        this.datFile = datFile;
    }

    public String volumeName() {
        if (vname != null) {
            return vname;
        }
        String s = optFile.getFileName().toString();
        int ix = s.lastIndexOf('.');
        if (ix > 0) {
            s = s.substring(0, ix);
        }
        return vname = s;
    }

    public ItemKey volumeKey() {
        return ikey != null ? ikey : (ikey = ItemKey.from(volumeName()));
    }

    static final Pattern PAT = Pattern.compile("^([a-zA-Z0-9]*?[A-Za-z])(\\d+)$");

    public String volumePrefix() {
        Matcher m = PAT.matcher(volumeName());
        if (m.find()) {
            String result = m.group(1);
//            if (result.endsWith("VOL") && result.length() > 3) {
//                result = result.substring(0, result.length() - 3);
//            }
            return result;
        }
        return volumeName();
    }

    private Pattern vp;

    public Pattern volumePattern() {
        if (vp != null) {
            return vp;
        }
        Pattern result = Pattern.compile("^(" + volumePrefix() + ")(\\d+)$");
        return vp = result;
    }

    public Path optFile() {
        return optFile;
    }

    public Path datFile() {
        return datFile;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.optFile);
        hash = 83 * hash + Objects.hashCode(this.datFile);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FilePair other = (FilePair) obj;
        if (!Objects.equals(this.optFile, other.optFile)) {
            return false;
        }
        return Objects.equals(this.datFile, other.datFile);
    }
}
