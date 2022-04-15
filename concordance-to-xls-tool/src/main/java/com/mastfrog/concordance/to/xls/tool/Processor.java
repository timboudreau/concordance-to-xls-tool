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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastfrog.concordance.parser.DatFileParser;
import com.mastfrog.concordance.parser.DatParserConsumer;
import com.mastfrog.concordance.parser.OptConsumer;
import com.mastfrog.concordance.parser.OptFileParser;
import com.mastfrog.concordance.to.xls.tool.EntryKey;
import com.mastfrog.concordance.to.xls.tool.ItemKey;
import com.mastfrog.concordance.to.xls.tool.ProgressConsumer.ProgressTask;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import static java.lang.Integer.min;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
public class Processor {

    private static final String SYNTH_FILE_KEY_SUFFIX = ".File";
    private static final int THREADS = Runtime.getRuntime().availableProcessors() * 2;
    static final ExecutorService svc = Executors.newFixedThreadPool(THREADS, new TF());
    private final AtomicLinkedQueue<FilePair> pendingOptFiles;
    private final AtomicLinkedQueue<FilePair> pendingDatFiles;
    private final Map<EntryKey, OptFileEntry> optDict = new ConcurrentHashMap<>();
    private volatile boolean ok = true;
    private final ObjectMapper mapper = new ObjectMapper();
    private int totalItems;
    private final Set<String> allHeadings = ConcurrentHashMap.newKeySet();
    private final Set<String> syntheticHeadings = ConcurrentHashMap.newKeySet();
    private final ConversionSettings settings;

    public Processor(Set<FilePair> pairs, ConversionSettings settings) {
        this.pendingOptFiles = new AtomicLinkedQueue<>(pairs);
        this.pendingDatFiles = new AtomicLinkedQueue<>(pairs);
        this.settings = settings;
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public void go(ProgressConsumer consumer) {
        int total = pendingDatFiles.size();
        AtomicInteger running = new AtomicInteger();
        int th = Math.min(THREADS, total);
        for (int thread = 0; thread < th; thread++) {
            running.getAndIncrement();
            svc.submit(processOptFiles(thread, total, running, consumer));
        }
    }

    private Runnable processOptFiles(int thread, int total, AtomicInteger remaining, ProgressConsumer c) {
        return () -> {
            String msg = "Building .opt dictionary";
            ProgressConsumer.ProgressTask task = c.task(thread, msg, Phase.PROCESSING);
            boolean aborted = false;
            try {
                for (FilePair pair = pendingOptFiles.pop(); pair != null; pair = pendingOptFiles.pop()) {
                    try {
                        processOptFile(pair.optFile(), c, task);
                    } catch (IOException ex) {
                        c.onError(ex.getMessage(), ex, false);
                        Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        int curr = pendingOptFiles.size();
                        task.progress(total - (curr + 1), total);
                    }
                }
            } catch (Exception ex) {
                aborted = true;
                msg = ex.getMessage() + " - failure";
                task.problem(ex.getClass().getSimpleName() + " '" + ex.getMessage() + "': "
                        + Strings.toString(ex));
            } finally {
                task.done(aborted, msg);
                if (remaining.decrementAndGet() == 0) {
                    launchDatFiles(c);
                }
            }
        };
    }

    void processOptFile(Path optFile, ProgressConsumer c, ProgressConsumer.ProgressTask task) throws IOException {
        OptFileParser parser = new OptFileParser(optFile);
        OptConsumer oc = new OptConsumer() {
            @Override
            public boolean onEntry(String itemName, String volumeName, Path relativePath, String... parts) {
                OptFileEntry ofe = new OptFileEntry(itemName, volumeName, relativePath, parts);
//            synchronized (optDict) {
                optDict.put(ofe.key(), ofe);
//            }
                return ok;
            }

            @Override
            public boolean onError(String line, int index, String problem) {
                task.problem(optFile.getFileName() + " line " + line + ": " + problem);
                return false;
            }
        };
        parser.parse(oc);
    }

    private void launchDatFiles(ProgressConsumer consumer) {
        int total = pendingDatFiles.size();
        AtomicInteger running = new AtomicInteger();
        int th = min(THREADS, total);
        for (int thread = 0; thread < th; thread++) {
            running.getAndIncrement();
            svc.submit(processDatFiles(thread, total, running, consumer));
        }
    }

    private Runnable processDatFiles(int thread, int total, AtomicInteger remaining, ProgressConsumer c) {
        return () -> {
            ObjectMapper map = mapper.copy();
            String msg = "Processing .dat files";
            ProgressConsumer.ProgressTask task = c.task(thread, msg, Phase.PROCESSING);
            boolean aborted = false;
            try {
                for (FilePair pair = pendingDatFiles.pop(); pair != null; pair = pendingDatFiles.pop()) {
                    try {
                        processDatFile(pair, map, c, task);
                    } catch (IOException ex) {
                        c.onError(ex.getMessage(), ex, false);
                        Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        int curr = pendingOptFiles.size();
                        task.progress(total - (curr + 1), total);
                    }
                }
            } catch (Exception | Error ex) {
                aborted = true;
                msg = ex.getMessage() + " - failure";
                ex.printStackTrace();
                task.problem(ex.getClass().getSimpleName() + " '" + ex.getMessage() + "': "
                        + Strings.toString(ex));
            } finally {
                task.done(aborted, msg);
                if (remaining.decrementAndGet() == 0) {
                    try {
                        if (!aborted) {
                            launchGeneration(c);
                        }
                    } catch (IOException ex) {
                        ok = false;
                        c.onError(ex.getMessage() + "", ex, true);
                        Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        };
    }

    void processDatFile(FilePair pair, ObjectMapper mapper, ProgressConsumer c,
            ProgressConsumer.ProgressTask task) throws IOException {
        ItemKey volume = pair.volumeKey();
        Path datFile = pair.datFile();
        DatFileParser parser = new DatFileParser(datFile).withFilter(settings.filters());
        List<Map<String, String>> all = new ArrayList<>();
        String[] headings = parser.parse(new DatParserConsumer() {
            @Override
            public boolean onError(String line, int index, String problem) {
                Exception e = new Exception(datFile + ":" + line + " " + problem);
                task.problem(pair.datFile().getFileName() + " line " + line + ": " + problem);
                Logger.getLogger(Processor.class.getName()).log(Level.INFO, "Problem parsing " + datFile, e);
                return false;
            }

            @Override
            public boolean accept(int item, Map<String, String> entry) {
                Map<String, String> toAdd = new HashMap<>();
                toAdd.put("Volume", pair.volumeName());
                entry.forEach((k, v) -> {
                    // MD5Hash is a big source of false positives, and we don't need the NPEs
                    if (!"MD5Hash".equals(k) && !"Author".equals(k)) {
                        Optional<EntryKey> ek = volume.possibleKey(v);
                        if (ek.isPresent()) {
                            EntryKey target = ek.get();
                            OptFileEntry opt = optDict.get(target);
                            boolean found = opt != null;
                            if (opt != null) {
                                toAdd.put(k + SYNTH_FILE_KEY_SUFFIX, opt.path());
                            } else {
                                // Not sure what the off-by-one thing going on here is, but
                                // we have references to files that do exist, but in the .opt
                                // file, they are in some other volume, not the one the opt file
                                // is part of.  So scan a bit before and after.
                                Optional<EntryKey> altKey = target.withNextVolume();
                                if (altKey.isPresent()) {
                                    opt = optDict.get(altKey.get());
                                    if (opt != null) {
                                        found = true;
                                        toAdd.put(k + SYNTH_FILE_KEY_SUFFIX, opt.path());
                                    }
                                } else {
                                    altKey = target.withPrevVolume();
                                    if (altKey.isPresent()) {
                                        opt = optDict.get(altKey.get());
                                        if (opt != null) {
                                            found = true;
                                            toAdd.put(k + SYNTH_FILE_KEY_SUFFIX, opt.path());
                                        }
                                    }
                                }
                                if (!found) {
                                    task.problem(datFile.getFileName() + " item " + item
                                            + " refers to " + v + " for column '" + k
                                            + "' which looks like it could "
                                            + " be an document ID, but which is not present in "
                                            + pair.optFile().getFileName());
                                    System.out.println("No opt for " + target);
                                }
                            }
                        }
                    }
                });
                if (!toAdd.isEmpty()) {
                    Set<String> synthesized = toAdd.keySet();
                    allHeadings.addAll(synthesized);
                    syntheticHeadings.addAll(synthesized);
                }
                entry.putAll(toAdd);
                all.add(entry);
                if (all.size() % 200 == 0) {
                    try {
                        writeToTempFile(all);
                    } catch (IOException ex) {
                        ok = false;
                        c.onError(ex.getMessage() + "", ex, true);
                    }
                }
                return ok;
            }
        });
        this.allHeadings.addAll(Arrays.asList(headings));
        if (ok && !all.isEmpty()) {
            writeToTempFile(all);
        }
    }

    private TempItems tempFile;

    synchronized TempItems tempFile() throws IOException {
        if (tempFile == null) {
            tempFile = new TempItems(FileUtils.newTempFile("ccd-temp"), mapper);
        }
        return tempFile;
    }

    private synchronized void writeToTempFile(List<Map<String, String>> items) throws IOException {
        try {
            TempItems ti = tempFile();
            for (Map<String, String> m : items) {
                ti.write(m);
            }
        } finally {
            items.clear();
        }
    }

    void launchGeneration(ProgressConsumer c) throws IOException {
        TempItems ti;
        synchronized (this) {
            ti = tempFile;
        }
        if (!ok || ti == null) {
            ok = false;
            c.onError("Aborted? Not ok or tempfile is null, or no items at all found.",
                    new Exception(), true);
            return;
        }
        ProgressTask task = c.task(0, "Generate " + settings.format(), Phase.GENERATING);
        boolean aborted = false;
        String msg = "Finished generation";
        try {
            new XLSGenerator(mapper, ti, settings).generate(allHeadings, syntheticHeadings, task);
        } catch (Exception | Error ex) {
            Logger.getLogger(Processor.class.getName()).log(Level.INFO, "Failed generation", ex);
            task.problem(ex.getClass().getSimpleName() + " '" + ex.getMessage() + "': "
                    + Strings.toString(ex));
            msg = ex.getMessage() + "";
            aborted = true;
        } finally {
            task.done(aborted, msg);
            ti.delete();
        }
    }

    static class TF implements ThreadFactory {

        private final AtomicInteger ids = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Worker-" + ids.getAndIncrement());
            t.setDaemon(true);
            // Ensure processing threads don't block the UI
            t.setPriority(Thread.NORM_PRIORITY - 2);
            return t;
        }
    }
}
