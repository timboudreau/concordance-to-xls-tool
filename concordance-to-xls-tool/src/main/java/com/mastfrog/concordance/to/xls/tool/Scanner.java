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

import com.mastfrog.concordance.to.xls.tool.ProgressConsumer.ProgressTask;
import com.mastfrog.function.state.Int;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public class Scanner {

    private final ConversionSettings settings;

    public Scanner(ConversionSettings settings) {
        this.settings = settings;
    }

    public void scan(Set<? super FilePair> pairs, ProgressConsumer c) throws IOException {
        AtomicInteger acount = new AtomicInteger();
        ProgressTask task = c.task(0, "Scanning for .opt/.dat file pairs", Phase.SCANNING);
        try {
            if (settings.root() == null) {
                c.onError("No folder to scan provided", new IOException(), true);
            } else if (settings.scan()) {
                try ( Stream<Path> str = Files.walk(settings.root(), 200).unordered().parallel().filter(Files::isDirectory)) {
                    str.forEach(dir -> {
                        try {
                            Int ct = Int.create();
                            checkOneDir(dir, pth -> {
                                pairs.add(pth);
                                ct.increment();
                            });
                            if (ct.ifNotEqual(0, () -> {
                                int total = acount.addAndGet(ct.getAsInt());
                                task.status(ct.getAsInt() + " files found");
                            }));
                        } catch (IOException ex) {
                            c.onError("Exception in " + dir, ex, false);
                            Logger.getLogger(Scanner.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                }
            } else {
                checkOneDir(settings.root(), pairs::add);
                if (pairs.isEmpty()) {
                    c.onError("No opt/dat file pairs found in " + settings.root().getFileName(),
                            new IOException(), true);
                } else {
                    task.status("Found " + pairs.size() + " files");
                }
            }
        } finally {
            task.done(false, "Finished scan with " + pairs.size() + " .opt/.dat file pairs");
        }
    }

    private void checkOneDir(Path dir, Consumer<FilePair> c) throws IOException {
        try ( Stream<Path> files = Files.list(dir).parallel().filter(p -> {
            return Files.exists(p) && Files.isReadable(p) && (p.getFileName().toString().endsWith(".dat")
                    || p.getFileName().toString().endsWith(".DAT"));
        })) {
            files.forEach(path -> {
                String nm = path.getFileName().toString();
                if (nm.length() > 4) {
                    nm = nm.substring(0, nm.length() - 4);
                }
                Path exp = path.getParent().resolve(nm + ".opt");
                if (Files.exists(exp) && Files.isReadable(exp)) {
                    FilePair nue = new FilePair(exp, path);
                    c.accept(nue);
                } else {
                    exp = path.getParent().resolve(nm + ".OPT");
                    if (Files.exists(exp) && Files.isReadable(exp)) {
                        FilePair nue = new FilePair(exp, path);
                        c.accept(nue);
                    }
                }
            });
        }
    }
}
