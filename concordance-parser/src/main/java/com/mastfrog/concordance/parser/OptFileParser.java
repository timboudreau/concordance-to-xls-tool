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
package com.mastfrog.concordance.parser;

import com.mastfrog.util.collections.ArrayUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public class OptFileParser {

    private final Path file;

    public OptFileParser(Path file) {
        this.file = file;
    }

    public static final String[] EMPTY = new String[0];

    public int parse(OptConsumer c) throws IOException {
        int count = 0;
        try ( Stream<String> str = Files.lines(file)) {
            Iterator<String> it = str.iterator();
            while (it.hasNext()) {
                String line = it.next().trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String itemName = parts[0];
                        String volumeName = parts[1];
                        String relPath = parts[2].replaceAll("\\\\", "/");
                        if (relPath.startsWith("/")) {
                            relPath = relPath.substring(1);
                        }
                        Path p = Paths.get(relPath);
                        String[] remainder;
                        if (parts.length > 0) {
                            remainder = ArrayUtils.extract(parts, 3, parts.length - 3);
                        } else {
                            remainder = EMPTY;
                        }
                        count++;
                        if (!c.onEntry(itemName, volumeName, p, remainder)) {
                            break;
                        }
                    } else {
                        c.onError(line, count, "Bad line");
                    }
                }
            }
        }
        return count;
    }
}
