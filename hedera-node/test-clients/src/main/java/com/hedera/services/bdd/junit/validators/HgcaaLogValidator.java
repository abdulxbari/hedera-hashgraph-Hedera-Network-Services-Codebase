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

package com.hedera.services.bdd.junit.validators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class HgcaaLogValidator {
    private static final String WARN = "WARN";
    private static final String ERROR = "ERROR";
    private static final String POSSIBLY_CATASTROPHIC = "ERROR";
    private final String logFileLocation;

    public HgcaaLogValidator(final String logFileLocation) {
        this.logFileLocation = logFileLocation;
    }

    public void validate() throws IOException {
        final List<String> problemLines = new ArrayList<>();
        final var problemTracker = new ProblemTracker();
        try (final var stream = Files.lines(Paths.get(logFileLocation))) {
            stream.filter(problemTracker::isProblem)
                    .map(problemTracker::indented)
                    .forEach(problemLines::add);
        }
        if (!problemLines.isEmpty()) {
            Assertions.fail(
                    "Found problems in log file '" + logFileLocation + "':\n" + String.join("\n", problemLines));
        }
    }

    private static class ProblemTracker {
        private static final int LINES_AFTER_NON_CATASTROPHIC_PROBLEM_TO_REPORT = 10;
        private static final int LINES_AFTER_CATASTROPHIC_PROBLEM_TO_REPORT = 30;
        private static final String PROBLEM_DELIMITER = "\n========================================\n";

        private static final List<List<String>> PROBLEM_PATTERNS_TO_IGNORE = List.of(
                List.of("active throttles, but", "Not performing a reset!"),
                List.of(
                        "Could not start Helidon gRPC with TLS support on port",
                        "Resource on path: /opt/hedera/services/hedera.crt does not exist"),
                List.of("Specified TLS cert 'hedera.crt' doesn't exist!"),
                List.of("Could not start Netty with TLS support on port 50212"),
                List.of("CryptoTransfer throughput congestion has no throttle buckets"),
                // (UNDESIRABLE) Remove when precompiles all return null on invalid input
                List.of("Internal precompile failure"),
                List.of("payerReqSig not expected to be null"));

        private int linesSinceInitialProblem = -1;
        private int linesToReportAfterInitialProblem = -1;

        boolean isProblem(final String line) {
            if (linesSinceInitialProblem >= 0) {
                linesSinceInitialProblem++;
                if (linesSinceInitialProblem > linesToReportAfterInitialProblem) {
                    linesSinceInitialProblem = -1;
                    linesToReportAfterInitialProblem = -1;
                    return false;
                } else {
                    return true;
                }
            } else if (isInitialProblem(line)) {
                for (final var patterns : PROBLEM_PATTERNS_TO_IGNORE) {
                    if (patterns.stream().allMatch(line::contains)) {
                        return false;
                    }
                }
                linesSinceInitialProblem = 0;
                linesToReportAfterInitialProblem = isPossiblyCatastrophicProblem(line)
                        ? LINES_AFTER_CATASTROPHIC_PROBLEM_TO_REPORT
                        : LINES_AFTER_NON_CATASTROPHIC_PROBLEM_TO_REPORT;
                return true;
            } else {
                return false;
            }
        }

        String indented(final String line) {
            return linesSinceInitialProblem == 0 ? (PROBLEM_DELIMITER + line) : "  " + line;
        }

        private boolean isInitialProblem(final String line) {
            return line.contains(WARN) || line.contains(ERROR);
        }

        private boolean isPossiblyCatastrophicProblem(final String line) {
            return line.contains(POSSIBLY_CATASTROPHIC);
        }
    }
}
