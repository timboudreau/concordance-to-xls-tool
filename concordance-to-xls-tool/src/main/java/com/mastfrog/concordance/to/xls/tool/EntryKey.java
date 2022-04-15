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

import java.util.Objects;
import java.util.Optional;

/**
 * A pair of keys for volume (file name) plus a reference matching the volume's
 * prefix found in a .dat file.
 *
 * @author Tim Boudreau
 */
public final class EntryKey {

    private final ItemKey volume;
    private final ItemKey entry;

    public EntryKey(ItemKey volume, ItemKey entry) {
        this.volume = volume;
        this.entry = entry;
    }

    public Optional<EntryKey> withPrevVolume() {
        return volume.prev().map(pk -> new EntryKey(pk, entry));
    }

    public Optional<EntryKey> withNextVolume() {
        return volume.next().map(nk -> new EntryKey(nk, entry));
    }

    @Override
    public String toString() {
        return volume + ":" + entry;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + Objects.hashCode(this.volume);
        hash = 19 * hash + Objects.hashCode(this.entry);
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
        final EntryKey other = (EntryKey) obj;
        if (!Objects.equals(this.volume, other.volume)) {
            return false;
        }
        return Objects.equals(this.entry, other.entry);
    }

}
