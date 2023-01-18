/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.yahcli.commands.signedstate;

import static com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.UPPERCASE_HEX_FORMATTER;

import com.hedera.services.yahcli.commands.signedstate.HexToBytesConverter.Bytes;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Variant;
import com.hedera.services.yahcli.commands.signedstate.evminfo.CommentLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.DirectiveLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.DirectiveLine.Kind;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Editor;
import com.hedera.services.yahcli.commands.signedstate.evminfo.LabelLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.MacroLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.VTableEntryRecognizer;
import com.hedera.services.yahcli.commands.signedstate.evminfo.VTableEntryRecognizer.MethodEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "decompilecontract",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Decompiles contract bytecodes")
public class DecompileContractCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-b", "--bytecode"},
            required = true,
            arity = "1",
            converter = HexToBytesConverter.class,
            paramLabel = "HEX",
            description = "Contract bytecode as a hex string")
    Bytes theContract;

    @Option(
            names = {"-p", "--prefix"},
            description = "Prefix for each assembly line")
    String prefix = "";

    @Option(
            names = {"-i", "--id"},
            paramLabel = "CONTRACT_ID",
            description = "Contract Id (decimal, optional)")
    Optional<Integer> theContractId;

    @Option(names = "--with-code-offset", description = "Display code offsets")
    boolean withCodeOffset;

    @Option(names = "--with-opcode", description = "Display opcode (in hex")
    boolean withOpcode;

    @Option(names = "--with-metrics", description = "Record metrics in generated assembly")
    boolean withMetrics;

    @Option(
            names = "--with-contract-bytecode",
            description = "Put the contract bytecode in a comment")
    boolean withContractBytecode;

    @Option(names = "--do-not-decode-before-metadata")
    boolean withoutDecodeBeforeMetadata;

    @Option(names = "--recognize-sequences", description = "Recognize and analyze code sequences")
    boolean recognizeCodeSequences;

    @Option(
            names = "--with-selector-lookups",
            description =
                    "Fetch selector method names from internet (requires --recognize-sequences")
    boolean fetchSelectorNames;

    Set<Flag> flags = new HashSet<>();

    enum Flag {
        Macros,
        RawDisassembly,
        Selectors,
        Trace
    };

    @Option(
            names = {"-f", "--flag"},
            description = {
                "flags controlling different output options:",
                "m    list macros",
                "r    list raw disassembly (before analyzed disassembly",
                "s    list selectors",
                "t    dump trace of recognizers"
            })
    ShortFlag[] shortFlags;

    enum ShortFlag {
        m(Flag.Macros),
        r(Flag.RawDisassembly),
        s(Flag.Selectors),
        t(Flag.Trace);

        ShortFlag(@NonNull Flag flag) {
            this.flag = flag;
        }

        final Flag flag;
    }

    @Override
    public Integer call() throws Exception {
        for (var sf : shortFlags) flags.add(sf.flag);
        disassembleContract();
        return 0;
    }

    void disassembleContract() throws Exception {

        try {
            var options = new ArrayList<Variant>();
            if (withCodeOffset) options.add(Variant.DISPLAY_CODE_OFFSET);
            if (withOpcode) options.add(Variant.DISPLAY_OPCODE_HEX);
            if (withoutDecodeBeforeMetadata) options.add(Variant.WITHOUT_DECODE_BEFORE_METADATA);
            if (recognizeCodeSequences) options.add(Variant.RECOGNIZE_CODE_SEQUENCES);
            if (fetchSelectorNames) options.add(Variant.FETCH_SELECTOR_NAMES);

            var metrics = new HashMap</*@NonNull*/ String, /*@NonNull*/ Object>();
            metrics.put(START_TIMESTAMP, System.nanoTime());

            // Do the disassembly here ...
            final var asm = new Assembly(metrics, options.toArray(new Variant[0]));
            final var prefixLines = getPrefixLines();
            final var lines = asm.getInstructions(prefixLines, theContract.contents);

            // TODO: Should just be embedded in Assembly class (since there's an option for it)
            final var analysisResults =
                    Optional.ofNullable(recognizeCodeSequences ? asm.analyze(lines) : null);

            metrics.put(END_TIMESTAMP, System.nanoTime());

            if (withMetrics) {
                // replace existing `END` directive with one that has the metrics as a comment
                final var endDirective = new DirectiveLine(Kind.END, formatMetrics(metrics));
                if (lines.get(lines.size() - 1) instanceof DirectiveLine directive
                        && Kind.END.name().equals(directive.directive()))
                    lines.remove(lines.size() - 1);
                lines.add(endDirective);
            }

            analysisResults.ifPresentOrElse(
                    results -> {
                        // TODO: Each section needs a command line argument to enable, also raw +/-
                        // analyzed listing.  Preferably as "POSIX clustered short options".
                        if (results.properties()
                                .containsKey(VTableEntryRecognizer.METHODS_PROPERTY)) {
                            if (flags.contains(Flag.Trace)) {
                                @SuppressWarnings("unchecked")
                                final var traceLines =
                                        (List<String>)
                                                results.properties()
                                                        .get(VTableEntryRecognizer.METHODS_TRACE);
                                System.out.printf("Methods trace:%n");
                                for (final var t : traceLines) {
                                    System.out.printf("   %s%n", t);
                                }
                                System.out.printf("%n");
                            }

                            if (flags.contains(Flag.Selectors)) {
                                @SuppressWarnings("unchecked")
                                final var methodsTable =
                                        (List<VTableEntryRecognizer.MethodEntry>)
                                                results.properties()
                                                        .get(
                                                                VTableEntryRecognizer
                                                                        .METHODS_PROPERTY);
                                methodsTable.sort(Comparator.comparing(MethodEntry::methodOffset));
                                System.out.printf("Selectors from contract vtable:%n");
                                for (final var me : methodsTable) {
                                    System.out.printf(
                                            "%04X: %08X%n", me.methodOffset(), me.selector());
                                }
                                System.out.printf("%n");
                            }

                            if (flags.contains(Flag.Macros)) {
                                System.out.printf("Macros from VTableEntryRecognizer:%n");
                                for (final var macro : results.codeLineReplacements()) {
                                    if (macro instanceof MacroLine macroLine) {
                                        System.out.printf("   %s%n", macroLine.formatLine());
                                    }
                                }
                                System.out.printf("%n");
                            }
                        }

                        if (flags.contains(Flag.RawDisassembly)) {
                            System.out.printf("Raw disassembly:%n");
                            for (var line : lines)
                                System.out.printf("%s%s%n", prefix, line.formatLine());
                        }

                        {
                            // TODO: _This_ should _really_ be moved to the Assembly class
                            var editor = new Editor(lines);
                            results.codeLineReplacements().forEach(editor::add);
                            var analyzedLines = editor.merge();

                            System.out.printf("Analyzed disassembly:%n");
                            for (var line : analyzedLines)
                                System.out.printf("%s%s%n", prefix, line.formatLine());
                        }
                    },
                    (/*orElse*/ ) -> {
                        System.out.printf("Raw disassembly:%n");
                        for (var line : lines)
                            System.out.printf("%s%s%n", prefix, line.formatLine());
                    });
        } catch (Exception ex) {
            throw printFormattedException(theContractId, ex);
        }
    }

    /**
     * Create "prefix" lines to be put ahead of disassembly
     *
     * <p>Put some interesting data in the form of comments and directives to be placed as a prefix
     * in front of the disassembly.
     */
    List<Line> getPrefixLines() {
        var lines = new ArrayList<Line>();
        if (withContractBytecode) {
            final var comment =
                    "contract (%d bytes): %s"
                            .formatted(
                                    theContract.contents.length,
                                    UPPERCASE_HEX_FORMATTER.formatHex(theContract.contents));
            lines.add(new CommentLine(comment));
        }
        {
            final var comment = theContractId.map(i -> "contract id: " + i.toString()).orElse("");
            lines.add(new DirectiveLine(Kind.BEGIN, comment));
        }
        lines.add(new LabelLine(0, "ENTRY"));
        return lines;
    }

    // metrics keys:
    static final String START_TIMESTAMP = "START_TIMESTAMP"; // long
    static final String END_TIMESTAMP = "END_TIMESTAMP"; // long

    /** Format metrics into a string suitable for a comment on the `END` directive */
    String formatMetrics(@NonNull final Map</*@NonNull*/ String, /*@NonNull*/ Object> metrics) {
        var sb = new StringBuilder();

        // elapsed time computation
        final var nanosElapsed =
                (long) metrics.get(END_TIMESTAMP) - (long) metrics.get(START_TIMESTAMP);
        final float msElapsed = nanosElapsed / 1.e6f;
        sb.append("%.3f ms elapsed".formatted(msElapsed));

        return sb.toString();
    }

    /**
     * Dump the contract id + exception + stacktrace to stdout
     *
     * <p>Do it here so each line can be formatted with a known prefix so that you can easily grep
     * for problems in a directory full of disassembled contracts.
     */
    Exception printFormattedException(final Optional<Integer> theContractId, final Exception ex) {
        final var EXCEPTION_PREFIX = "***";

        var sw = new StringWriter(2000);
        var pw = new PrintWriter(sw, true /*auto-flush*/);
        ex.printStackTrace(pw);
        final var starredExceptionDump =
                sw.toString()
                        .lines()
                        .map(s -> EXCEPTION_PREFIX + s)
                        .collect(Collectors.joining("\n"));
        System.out.printf(
                "*** EXCEPTION CAUGHT (id %s): %s%n%s%n",
                theContractId.map(Object::toString).orElse("NOT-GIVEN"),
                ex.toString(),
                starredExceptionDump);
        return ex;
    }
}
