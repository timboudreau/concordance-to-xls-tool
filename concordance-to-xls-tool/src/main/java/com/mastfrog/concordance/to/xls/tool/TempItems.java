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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
class TempItems implements AutoCloseable {

    private final Path file;
    private final FileChannel channel;
    private final ObjectMapper mapper;
    volatile boolean open = true;
    int total;

    TempItems(Path file, ObjectMapper mapper) throws IOException {
        this.file = file;
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        this.mapper = mapper;
    }

    synchronized void write(Map<String, String> m) throws JsonProcessingException, IOException {
        byte[] bytes = (mapper.writeValueAsString(m) + "\n").getBytes(StandardCharsets.UTF_8);
        channel.write(ByteBuffer.wrap(bytes));
        total++;
    }

    synchronized int total() {
        return total;
    }

    @Override
    public synchronized void close() throws IOException {
        if (open) {
            open = false;
            channel.close();
        }
    }

    void read(Consumer<Map<String, String>> c) throws IOException {
        try (final Stream<String> str = Files.lines(file).filter(l -> !l.isEmpty())) {
            str.forEach(line -> {
                if (!line.isEmpty()) {
                    try {
                        Map<String, String> items = mapper.readValue(line, MAP_REF);
                        c.accept(items);
                    } catch (JsonProcessingException ex) {
                        Exceptions.chuck(ex);
                    }
                }
            });
        }
    }
    static final TypeReference<TreeMap<String, String>> MAP_REF = new TypeReference<TreeMap<String, String>>() {
    };

    public void delete() throws IOException {
        close();
        FileUtils.deleteIfExists(file);
    }
}
