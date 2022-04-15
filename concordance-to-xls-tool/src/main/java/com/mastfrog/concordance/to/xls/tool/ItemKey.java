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

import java.lang.StackWalker.Option;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Key composed of a file name prefix plus integer suffix such as FOO00003.
 */
public final class ItemKey {

    private final int index;
    private final String prefix;
    private final byte digits;
    private transient int hc;

    public ItemKey(int index, String prefix, byte digits) {
        this.index = index;
        this.prefix = prefix.intern();
        this.digits = digits;
    }

    /**
     * .dat files sometimes contain references to the right item with the wrong
     * volume, so do some range checks.
     *
     * @return
     */
    public Optional<ItemKey> next() {
        int result = index + 1;
        if (result > 0) {
            return Optional.of(new ItemKey(result, prefix, digits));
        }
        return Optional.empty();
    }

    public Optional<ItemKey> prev() {
        int result = index - 1;
        if (result > 0) {
            return Optional.of(new ItemKey(result, prefix, digits));
        }
        return Optional.empty();
    }

    public int digits() {
        return digits;
    }

    public boolean prefixMatch(int suffixChars, ItemKey other) {
        if (other.prefix == prefix) {
            return true;
        }
        return prefixMatch(suffixChars, other.prefix);
    }

    public Optional<EntryKey> possibleKey(String txt) {
        return OptFileEntry.possibleKey(this, txt);
    }

    public boolean prefixMatch(int suffixChars, String txt) {
        int ct = txt.length() - suffixChars;
        if (ct < 0 || prefix.length() < ct) {
            return false;
        }
        for (int i = 0; i < ct; i++) {
            if (prefix.charAt(i) != txt.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static ItemKey from(String txt) {
        Matcher m = OptFileEntry.PAT.matcher(txt);
        if (m.find()) {
            String pfx = m.group(1);
            String ints = m.group(2);
            int ix = Integer.parseInt(ints);
            byte len = (byte) ints.length();
            return new ItemKey(ix, pfx, len);
        } else {
            throw new IllegalArgumentException("Could not match " + txt + " with " + OptFileEntry.PAT.pattern());
        }
    }

    public static Optional<ItemKey> of(String txt) {
        Matcher m = OptFileEntry.PAT.matcher(txt);
        if (m.find()) {
            String pfx = m.group(1);
            String ints = m.group(2);
            try {
                int ix = Integer.parseInt(ints);
                byte len = (byte) ints.length();
                return Optional.of(new ItemKey(ix, pfx, len));
            } catch (NumberFormatException nfe) {
                // Something like a hash ending in more digits than will fit in an integer
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Integer.toString(index));
        while (sb.length() < digits) {
            sb.insert(0, '0');
        }
        return sb.insert(0, prefix).toString();
    }

    @Override
    public int hashCode() {
        if (hc != 0) {
            return hc;
        }
        int hash = 1;
        hash = 101_771 * hash + (this.index + 1);
        hash += Objects.hashCode(this.prefix) * 13;
        return hc = hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || obj.getClass() != ItemKey.class) {
            return false;
        }
        final ItemKey other = (ItemKey) obj;
        if (this.index != other.index) {
            return false;
        }
        return Objects.equals(this.prefix, other.prefix);
    }

}
