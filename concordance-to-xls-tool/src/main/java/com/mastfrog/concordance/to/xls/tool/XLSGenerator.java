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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastfrog.concordance.to.xls.tool.ProgressConsumer.ProgressTask;
import com.mastfrog.function.state.Int;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * @author Tim Boudreau
 */
public class XLSGenerator {

    private final ObjectMapper mapper;
    private final TempItems items;
    private final ConversionSettings settings;

    XLSGenerator(ObjectMapper mapper, TempItems items, ConversionSettings settings) {
        this.mapper = mapper;
        this.items = items;
        this.settings = settings;
    }

    private static class HeadingLookup {

        final String[] headings;
        final Map<String, Integer> forIndex;
        private final Set<String> syntheticHeadings;

        HeadingLookup(Set<String> headings, Set<String> syntheticHeadings) {
            this.headings = new TreeSet<>(headings).toArray(new String[headings.size()]);
            this.forIndex = new HashMap<>();
            for (int i = 0; i < this.headings.length; i++) {
                forIndex.put(this.headings[i], i);
            }
            this.syntheticHeadings = syntheticHeadings;
        }

        Integer indexOf(String head) {
            return forIndex.get(head);
        }

        int size() {
            return headings.length;
        }

        String heading(int ix) {
            return headings[ix];
        }

        boolean isSynthetic(int ix) {
            String s = headings[ix];
            return syntheticHeadings.contains(s);
        }

        void eachHeading(HC str) {
            for (int i = 0; i < headings.length; i++) {
                str.withHeading(i, headings[i]);
            }
        }
    }

    interface HC {

        void withHeading(int ix, String heading);
    }

    public void generate(Set<String> headings, Set<String> syntheticHeadings,
            ProgressTask task) throws IOException {
        switch (settings.format()) {
            case XLSX:
                generateXlsx(headings, syntheticHeadings, task);
                break;
            case CSV:
                generateCsv(headings, syntheticHeadings, task);
                break;
            case JSON:
                generateJson(headings, syntheticHeadings, task);
                break;
        }
    }

    static class CSVEscaper implements Escaper {

        @Override
        public CharSequence escape(char c) {
            switch (c) {
                case '"':
                    return "\"\"";
                default:
                    return Strings.singleChar(c);
            }
        }
    }

    public void generateJson(Set<String> headings, Set<String> syntheticHeadings,
            ProgressTask task) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        task.status("Saving JSON file to disk...");
        Path file = settings.output();
        HeadingLookup hl = new HeadingLookup(headings, syntheticHeadings);
        int total = items.total;
        Int read = Int.create();
        int synthTotal = total + 1;
        try ( OutputStream out = Files.newOutputStream(file, CREATE, WRITE, TRUNCATE_EXISTING)) {
            try ( PrintStream print = new PrintStream(out, true, UTF_8)) {
                print.print('[');
                items.read(map -> {
                    if (map.isEmpty()) {
                        return;
                    }

                    int index = read.increment();
                    task.progress(index, synthTotal);
                    if (index != 0) {
                        print.print(",");
                    }
                    try {
                        String m = mapper.writeValueAsString(map);
                        print.println(m);
                    } catch (JsonProcessingException ex) {
                        Logger.getLogger(XLSGenerator.class.getName()).log(Level.SEVERE, null, ex);
                        task.done(true, ex.getMessage());
                    }

                    if (index % 100 == 0) {
                        task.status("Generated " + index + " / " + total + " rows.");
                    }
                });
                print.println(']');
            }
        }
        task.status("Saved " + file);
    }

    public void generateCsv(Set<String> headings, Set<String> syntheticHeadings,
            ProgressTask task) throws IOException {
        task.status("Saving CSV file to disk...");
        Path file = settings.output();
        HeadingLookup hl = new HeadingLookup(headings, syntheticHeadings);
        int total = items.total;
        Int read = Int.create();
        CSVEscaper csvE = new CSVEscaper();

        Function<String, String> escape = in -> {
            String result = Strings.escape(in, csvE);
            if (Strings.contains(',', result) || Strings.contains('"', result)) {
                result = '"' + result + '"';
            }
            return result;
        };

        try ( OutputStream out = Files.newOutputStream(file, CREATE, WRITE, TRUNCATE_EXISTING)) {
            try ( PrintStream print = new PrintStream(out, true, UTF_8)) {
                // Write all the headings
                StringBuilder sb = new StringBuilder(2_048);
                hl.eachHeading((ix, h) -> {
                    if (ix > 0) {
                        sb.append(',');
                    }
                    sb.append(escape.apply(h));
                });
                print.println(sb);
                int synthTotal = total + 1;
                items.read(map -> {
                    if (map.isEmpty()) {
                        return;
                    }
                    sb.setLength(0);
                    int index = read.increment();
                    task.progress(index, synthTotal);
                    hl.eachHeading((ix, h) -> {
                        if (ix > 0) {
                            sb.append(',');
                        }
                        String val = map.get(h);
                        if (val != null) {
                            sb.append(escape.apply(val));
                        }
                    });
                    print.println(sb);
                    if (index % 100 == 0) {
                        task.status("Generated " + index + " / " + total + " rows.");
                    }
                });
            }
        }
        task.status("Saved " + file);
    }

    public void generateXlsx(Set<String> headings, Set<String> syntheticHeadings, ProgressTask task) throws IOException {
        task.status("Saving XLSX file to disk...");
        HeadingLookup hl = new HeadingLookup(headings, syntheticHeadings);
        int total = items.total;
        Int read = Int.create();
        Workbook workbook = new XSSFWorkbook();
        CreationHelper createHelper = workbook.getCreationHelper();
        Sheet sheet = workbook.createSheet("Signups");

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 14);
        headerFont.setColor(IndexedColors.DARK_BLUE.getIndex());

        // Create a CellStyle with the font
        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setWrapText(true);
        headerCellStyle.setFont(headerFont);

        CellStyle synthHeaderCellStyle = workbook.createCellStyle();
        synthHeaderCellStyle.setWrapText(true);
        Font synthFont = workbook.createFont();
        synthFont.setItalic(true);
        synthFont.setBold(true);
        synthFont.setColor(IndexedColors.BLUE.getIndex());
        synthHeaderCellStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        Int currRow = Int.of(1);
        for (int i = 0; i < hl.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(hl.heading(i));
            cell.setCellStyle(hl.isSynthetic(i) ? synthHeaderCellStyle : headerCellStyle);
        }
        int synthTotal = total + 1;
        items.read(map -> {
            int index = read.increment();
            task.progress(index, synthTotal);
            Row row = sheet.createRow(currRow.increment());
            map.forEach((name, value) -> {
                Integer ix = hl.indexOf(name);
                if (ix != null) {
                    Cell cell = row.createCell(ix, CellType.STRING);
                    if (value.length() > 32_767) {
                        task.problem("Value too long for spreadsheet cell (max 32,767 characters): '"
                                + value.substring(100) + "'");
                        value = value.substring(0, 32_767);
                    }
                    cell.setCellValue(value);
                } else {
                    task.problem("Have a value for a column named '" + name
                            + "' with value '" + value + "' in " + map.get("Volume") + " but no such column exists.");
                    System.err.println("No header " + name);
                }
            });
            if (index % 100 == 0) {
                task.status("Generated " + index + " / " + total + " rows.");
            }
        });
        task.progress(total, synthTotal);
        task.status("Saving output file to disk...");
        Path file = settings.output();
        try ( OutputStream out = Files.newOutputStream(file, CREATE, WRITE, TRUNCATE_EXISTING)) {
            workbook.write(out);
        }
        task.status("Saved " + file);
    }
}
