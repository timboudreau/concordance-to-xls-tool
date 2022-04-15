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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public final class OptFileEntry {

    private final String itemName;
    private final String volumeName;
    private final Path relativePath;
    private final String[] data;
    static final Pattern PAT = Pattern.compile("^([A-Za-z0-9_]*?[A-Za-z_])(\\d+)$");

    public OptFileEntry(String itemName, String volumeName, Path relativePath, String[] data) {
        this.itemName = itemName;
        this.volumeName = volumeName;
        this.relativePath = relativePath;
        this.data = data;
    }

    public EntryKey key() {
        return new EntryKey(ItemKey.from(volumeName), ItemKey.from(itemName));
    }

    public static Optional<EntryKey> possibleKey(ItemKey inVolume, String txt) {
        Optional<ItemKey> other = ItemKey.of(txt);
        if (other.isPresent()) {
            ItemKey ik = other.get();
            if (inVolume.prefixMatch(3, ik)) {
                return Optional.of(new EntryKey(inVolume, ik));
//            } else {
//                Optional<ItemKey> nextVolume = inVolume.next();
//                if (nextVolume.isPresent()) {
//                    if (nextVolume.get().prefixMatch(3, ik)) {
//                        return Optional.of(new EntryKey(inVolume, ik));
//                    }
//                }
            }
        }
        return Optional.empty();
    }

    public String path() {
        return relativePath.toString();
    }

    @Override
    public String toString() {
        return volumeName + ":" + itemName;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 43 * hash + Objects.hashCode(this.itemName);
        hash = 43 * hash + Objects.hashCode(this.volumeName);
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
        final OptFileEntry other = (OptFileEntry) obj;
        if (!Objects.equals(this.itemName, other.itemName)) {
            return false;
        }
        return Objects.equals(this.volumeName, other.volumeName);
    }
}
