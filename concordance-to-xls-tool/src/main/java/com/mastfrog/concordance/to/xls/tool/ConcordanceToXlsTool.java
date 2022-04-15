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

import java.awt.EventQueue;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author Tim Boudreau
 */
public class ConcordanceToXlsTool {

    private final ConversionSettings settings;

    public ConcordanceToXlsTool(ConversionSettings settings) {
        this.settings = settings;
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "lcd_hrgb");
        try {
            EventQueue.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                    Logger.getLogger(UI.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (Exception ex) {
            Logger.getLogger(UI.class.getName()).log(Level.SEVERE, null, ex);
        }

        UI ui = new UI(new ConversionSettings());
        ui.show();
    }

    public void launch(ProgressConsumer consumer) throws IOException {
        Scanner scan = new Scanner(settings);
        Set<FilePair> pairs = ConcurrentHashMap.newKeySet();
        scan.scan(pairs, consumer);
        System.out.println("Scan done");
        if (!pairs.isEmpty()) {
            Processor proc = new Processor(pairs, settings);
            System.out.println("Process " + pairs.size());
            proc.go(consumer);
        } else {
            consumer.onError("No files found", new Error(), true);
        }
    }

}
