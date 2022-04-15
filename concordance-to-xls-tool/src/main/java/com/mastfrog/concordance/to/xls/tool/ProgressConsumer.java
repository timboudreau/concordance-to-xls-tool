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

/**
 *
 * @author Tim Boudreau
 */
public interface ProgressConsumer {

    ProgressTask task(int thread, String task, Phase phase);

    default void onError(String error, Throwable thrown, boolean fatal) {

    }

    default ProgressConsumer replanning() {
        return new ProgressConsumer() {
            @Override
            public ProgressTask task(int thread, String task, Phase phase) {
                return ProgressConsumer.this.task(thread, task, phase).replanning();
            }

            @Override
            public void onError(String error, Throwable thrown, boolean fatal) {
                EventQueue.invokeLater(() -> ProgressConsumer.this.onError(error, thrown, fatal));
            }
        };
    }

    interface ProgressTask {

        void progress(int step, int of);

        void done(boolean aborted, String msg);

        void status(String status);

        void problem(String problem);

        default ProgressTask replanning() {
            return new ProgressTask() {
                @Override
                public void progress(int step, int of) {
                    EventQueue.invokeLater(() -> ProgressTask.this.progress(step, of));
                }

                @Override
                public void done(boolean aborted, String msg) {
                    EventQueue.invokeLater(() -> ProgressTask.this.done(aborted, msg));
                }

                @Override
                public void status(String status) {
                    EventQueue.invokeLater(() -> ProgressTask.this.status(status));
                }

                @Override
                public void problem(String problem) {
                    EventQueue.invokeLater(() -> ProgressTask.this.problem(problem));
                }
            };
        }
    }
}
