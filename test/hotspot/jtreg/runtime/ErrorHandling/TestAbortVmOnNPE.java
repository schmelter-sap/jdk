/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestAbortVmOnNPE
 * @summary Test -XX:AbortVMOnException=MyAbortException with C1 compilation
 * @library /test/lib
 * @requires vm.flagless
 * @run driver TestAbortVmOnNPE implicit
 * @bug 8264899
 */

/*
 * @test TestAbortVmOnNPE
 * @summary Test -XX:AbortVMOnException=MyAbortException with C1 compilation
 * @library /test/lib
 * @requires vm.flagless
 * @run driver TestAbortVmOnNPE explicit
 * @bug 8264899
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;


public class TestAbortVmOnNPE {

    public static void main(String[] args) throws Exception {
        String abortMsg = "<invalid>";

        try {
            if (args[0].equals("implicit")) {
                (new Object[1])[0].hashCode();
            } else {
                throw new NullPointerException("Dummy");
            }
        } catch (NullPointerException e) {
            if (args.length == 2) {
                throw e;
            }

            abortMsg = "fatal error: Saw " + e.getClass().getName() + ": " + e.getMessage() + ", aborting";
        }

        String compileCmd = "-XX:CompileCommand=compileonly,TestAbortVmOnNPE::*";

        // Check if it works on all compiler levels.
        if (System.getProperty("jdk.debug").equals("release")) {
            runTest(args[0], new String[] {"-XX:AbortVMOnException=NullPointer", "-Xcomp", "-XX:TieredStopAtLevel=3",
                    "-XX:-CreateCoredumpOnCrash", compileCmd, "-XX:+ErrorFileToStderr"}, abortMsg);
        }

        runTest(args[0], new String[] {"-XX:AbortVMOnException=NullPointer", "-Xint"}, abortMsg);
        runTest(args[0], new String[] {"-XX:AbortVMOnException=NullPointer", "-Xcomp",
                "-XX:CompilationMode=high-only", compileCmd, "-XX:+ErrorFileToStderr"}, abortMsg);

        System.out.println("PASSED all");
    }

    private static void runTest(String command, String[] vmFlags, String... expected) throws Exception {
        Process proc = runProcess(command, vmFlags);
        parseOutput(proc, expected);
        System.out.println("PASSED " + java.util.Arrays.toString(vmFlags));
    }

    private static Process runProcess(String command, String... vmFlags) throws IOException {
        String[] defaultArgs = new String[] {
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-CreateCoredumpOnCrash"
        };

        String[] args = new String[defaultArgs.length + vmFlags.length + 3];
        System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
        System.arraycopy(vmFlags, 0, args, defaultArgs.length, vmFlags.length);
        args[args.length - 3] = TestAbortVmOnNPE.class.getName();
        args[args.length - 2] = command;
        args[args.length - 1] = "testee";

        return ProcessTools.createLimitedTestJavaProcessBuilder(args).start();
    }

    private static void parseOutput(Process process, String... expectedString) throws IOException {
        OutputAnalyzer output = new OutputAnalyzer(process);
        output.outputTo(System.err);
        output.errorTo(System.err);
        output.stdoutShouldNotBeEmpty();

        for (String expected: expectedString) {
            output.shouldContain(expected);
        }
    }
}
