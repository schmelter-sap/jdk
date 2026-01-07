/*
 * Copyright (c) 2026 SAP SE. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.management.ManagementFactory;
import java.io.File;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import com.sun.management.HotSpotDiagnosticMXBean;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @summary Verifies a heap dump does not contain filler arrays
 * @requires vm.gc.G1
 * @modules jdk.attach/sun.tools.attach
 * @library /test/lib
 * @run driver NoFillerArraysTest
 */

class FillerArraysApp {
    public static void main(String[] args) throws Exception {
        NoFillerArraysTest.createFillers();
    }
}

public class NoFillerArraysTest {
    private static final int nrOfFillers = 10;
    private static final int regionSize = 2 * 1024 * 1024;
    private static final int allocSize = regionSize / 2 + 1700;  // slightly larger than half a region
    private static final Object[] allocatedObjs = new Object[nrOfFillers];
    private static final File dumpFile = new File("heapdump.hprof");

    public static void main(String[] args) throws Exception {
        Process p = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UseG1GC",
            "-XX:G1HeapRegionSize=" + regionSize, FillerArraysApp.class.getName()).start();
        OutputAnalyzer oa = new OutputAnalyzer(p);
        System.out.println(oa.getOutput());
        oa.shouldHaveExitValue(0);

        Asserts.assertTrue(dumpFile.exists(), "Heap dump file not found.");

        try (Snapshot snapshot = Reader.readFile(dumpFile.getPath(), false, 0)) {
            snapshot.resolve(true);

            long maxSize = regionSize - allocSize;
            long minSize = maxSize - 128;
            int foundFillers = 0;

            Enumeration<JavaHeapObject> it = snapshot.getThings();

            while (it.hasMoreElements()) {
                JavaHeapObject obj = it.nextElement();
                long size = obj.getSize();

                if ((size < minSize) || (size > maxSize)) {
                    continue;
                }

                if ("[I".equals(obj.getClazz().getName())) {
                    foundFillers++;
                }
            }

             // Allow 3 random arrays to have about the same size as the fillers.
            Asserts.assertLTE(foundFillers, 3);
        }
    }

    public static void createFillers() throws Exception {
        for (int i = 0; i < nrOfFillers; ++i) {
            // Allocate a humungous object. Cover a little more than half of the region.
            // The rest will be filled with a filler array.
            allocatedObjs[i] = new byte[allocSize];
        }

        // Dump the heap.
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
            ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean.class);
        mxBean.dumpHeap(dumpFile.getAbsolutePath(), true);
    }
}
