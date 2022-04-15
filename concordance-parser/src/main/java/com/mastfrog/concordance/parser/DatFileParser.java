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

import static com.mastfrog.concordance.parser.OptFileParser.EMPTY;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public class DatFileParser {

    static final char DELIM = 'þ';
    private final Path path;
    private Predicate<String> filter = ignored -> true;

    public DatFileParser(Path path) {
        this.path = path;
    }

    public DatFileParser withFilter(Predicate<String> pred) {
        this.filter = pred;
        return this;
    }

    private String[] split(String line) {
        if (line.length() < 3) {
            return EMPTY;
        }
        if (line.charAt(0) == '\ufeff') {
            line = line.substring(1);
        }
        if (line.charAt(0) == '\u00fe') {
            line = line.substring(1);
        }
        if (line.charAt(0) == '\u00ff') {
            line = line.substring(1);
        }
        if (line.charAt(0) == '\u00fe') {
            line = line.substring(1);
        }
        if (line.length() < 3) {
            return EMPTY;
        }
        while (line.charAt(line.length() - 1) == '\u00fe') {
            line = line.substring(0, line.length() - 1);
        }
        if (line.length() < 3) {
            return EMPTY;
        }
        List<String> result = new ArrayList<>(Strings.countOccurrences('\u0014', line));
        String[] parts = line.split("\u00fe\u0014\u00fe");
        result.addAll(Arrays.asList(parts));

        return result.toArray(String[]::new);
    }

    public String[] parse(DatParserConsumer c) throws IOException {
        int count = 0;
        String[] headings = new String[0];
        try ( Stream<String> str = Files.lines(path)) {
            Iterator<String> it = str.iterator();
            while (it.hasNext()) {
                String line = it.next().trim();
                if (!line.isEmpty()) {
                    if (!filter.test(line)) {
                        continue;
                    }
                    String[] parts = split(line);
                    if (count == 0) {

                        String rr = Strings.escapeControlCharactersAndQuotes(line);
                        rr = Strings.escape(rr,
                                Escaper.escapeUnencodableAndControlCharacters(US_ASCII));

                        List<String> stuff = new ArrayList<>();
                        for (String p : parts) {
                            p = p.trim();
                            if (p.isEmpty() || p.contains("")) {
                                continue;
                            }
                            stuff.add(p);
                        }
                        headings = stuff.toArray(String[]::new);
                    } else {
                        Map<String, String> values = new TreeMap<>();
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].isEmpty() || parts[i].contains("")) {
                                continue;
                            }
                            if (i < headings.length) {
                                values.put(headings[i], parts[i]);
                            } else {
                                String raw = Strings.escapeControlCharactersAndQuotes(line);
                                raw = Strings.escape(raw,
                                        Escaper.escapeUnencodableAndControlCharacters(US_ASCII));
                                c.onError(line, i, "Data contains a heading "
                                        + i + " but the header says there are only " + headings.length
                                        + " headings");
                            }
                        }
                        if (!values.isEmpty()) {
                            if (!c.accept(count, values)) {
                                break;
                            }
                        }
                    }
                    count++;
                }
            }
        }
        return headings;
    }
}
