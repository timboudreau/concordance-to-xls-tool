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

import com.mastfrog.function.state.Int;
import com.mastfrog.util.strings.Strings;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

/**
 *
 * @author Tim Boudreau
 */
public class Filters implements Predicate<String> {

    public Set<String> exclude = new LinkedHashSet<>();
    private final Set<Int> counters = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    static Filters load() {
        Preferences p = Preferences.userNodeForPackage(Filters.class);
        Filters result = new Filters();
        String saved = p.get("exclude", "");
        if (!saved.isEmpty()) {
            result.setFilters(saved);
        }
        return result;
    }

    public boolean isEmpty() {
        return exclude.isEmpty();
    }

    public void save() {
        Preferences p = Preferences.userNodeForPackage(Filters.class);
        p.put("exclude", toString());
    }

    public Int counter() {
        Int result = Int.create();
        counters.add(result);
        return result;
    }

    public void setFilters(String filters) {
        exclude.clear();
        for (String s : filters.split(",")) {
            if ("".equals(s)) {
                continue;
            }
            exclude.add(s.trim());
        }
    }

    @Override
    public String toString() {
        return Strings.join(',', exclude);
    }

    @Override
    public boolean test(String t) {
        for (String e : exclude) {
            if (t.contains(e)) {
                for (Int i : counters) {
                    i.increment();
                }
                return false;
            }
        }
        return true;
    }
}
