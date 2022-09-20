/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static java.util.stream.Collectors.filtering;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.verification.NodeSignatureVerifier;
import com.hedera.services.bdd.spec.verification.RecordFileParser;
import com.hedera.services.bdd.spec.verification.TransactionAssertions;
import com.hedera.services.bdd.spec.verification.TransactionLogger;
import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.swirlds.common.crypto.Cryptography;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class RecordStreamVerification extends UtilOp {
    private static final Logger log = LogManager.getLogger(RecordStreamVerification.class);

    private static final byte[] EMPTY_HASH = new byte[48];
    private static final Pattern RECORD_FILE_REGEX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z.rcd.gz");
    // FUTURE WORK: remove this once constants from other PRs are in
    private static final String SIDECAR_FILE_EXTENSION = ".rcd.gz";

    private final TransactionLogger transactionLogger;
    private final Supplier<String> baseDir;
    private static final MessageDigest messageDigest;

    private boolean allGood = true;

    static {
        try {
            messageDigest =
                    MessageDigest.getInstance(Cryptography.DEFAULT_DIGEST_TYPE.algorithmName());
        } catch (Exception fatal) {
            throw new IllegalStateException("Cannot initialize digest!", fatal);
        }
    }

    public RecordStreamVerification(
            final Supplier<String> baseDir, final TransactionLogger transactionLogger) {
        this.transactionLogger = transactionLogger;
        this.baseDir = baseDir;
    }

    @Override
    protected boolean submitOp(HapiApiSpec spec) throws Throwable {
        final var addressBook = downloadBook(spec);
        NodeSignatureVerifier verifier = new NodeSignatureVerifier(addressBook);
        final var nodes = verifier.nodes();
        Set<String> uniqRecordFiles = allRecordFilesFor(nodes);
        // record file name <-> list of all signature files from all nodes
        Map<String, List<File>> sigFilesAvail =
                uniqRecordFiles.stream()
                        .flatMap(
                                rcd ->
                                        nodes.stream()
                                                .map(
                                                        account ->
                                                                Path.of(
                                                                        recordsDirFor(account),
                                                                        rcd)))
                        .collect(
                                groupingBy(
                                        this::basename,
                                        mapping(
                                                (Path p) ->
                                                        Path.of(
                                                                        p.toFile()
                                                                                .getAbsolutePath()
                                                                                .replace(
                                                                                        ".gz",
                                                                                        "_sig"))
                                                                .toFile(),
                                                filtering(File::exists, toList()))));
        checkOverallValidity(sigFilesAvail, verifier);
        Assertions.assertTrue(
                allGood, " Not everything seemed good with the record streams, see logs above!");
        return false;
    }

    private void checkOverallValidity(
            Map<String, List<File>> sigFilesAvail, NodeSignatureVerifier verifier) {
        sigFilesAvail.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .forEach(entry -> checkSigValidity(entry.getKey(), entry.getValue(), verifier));
        if (!allGood) {
            // fail fast if signature files are not in check
            return;
        }
        log.info("Signature files validation has passed successfully!");
        List<String> orderedRcdNames =
                sigFilesAvail.keySet().stream()
                        .sorted(Comparator.reverseOrder())
                        .limit(50)
                        .sorted()
                        .toList();
        log.info("Record files will be checked in chronological order: {}", orderedRcdNames);
        final var transactionAssertions =
                new TransactionAssertions(transactionLogger.getSentTxns());
        for (final var nodeAccount : verifier.nodes()) {
            final var nodeRecordsDir = recordsDirFor(nodeAccount);
            final var allRecordsFileInNodeDirectory =
                    orderedRcdNames.stream()
                            .map(name -> Path.of(nodeRecordsDir, name).toFile())
                            .map(RecordFileParser::parseFrom)
                            .toList();
            log.info("**** Record Files Validation for node {} ****", nodeAccount);
            transactionAssertions.resetForNewNode();
            for (int i = 0; i < allRecordsFileInNodeDirectory.size(); i++) {
                final var nameOfCurrentFile = orderedRcdNames.get(i);
                final var recordStreamFile = allRecordsFileInNodeDirectory.get(i);
                log.info("Starting validations for record file {}", nameOfCurrentFile);
                if (i > 0) {
                    // assert blockNumber[i] = blockNumber[i-1] + 1,
                    final var prevRecordStreamFile = allRecordsFileInNodeDirectory.get(i - 1);
                    final long currentBlockNumber = recordStreamFile.getBlockNumber();
                    final long previousBlockNumber = prevRecordStreamFile.getBlockNumber();
                    if (currentBlockNumber != previousBlockNumber + 1) {
                        setUnGood(
                                "\t\tBlock number DID NOT increase as expected ({} -> {}), between"
                                        + " {} and {}",
                                previousBlockNumber,
                                currentBlockNumber,
                                orderedRcdNames.get(i - 1),
                                nameOfCurrentFile);
                    } else {
                        log.info(
                                "\t\tBlock number increased as expected from {} to {} between {}"
                                        + " and {}",
                                previousBlockNumber,
                                currentBlockNumber,
                                orderedRcdNames.get(i - 1),
                                nameOfCurrentFile);
                    }
                    // assert startObjectRunningHash[i] == endObjectRunningHash[i-1]
                    final var prevEndRunningHash =
                            prevRecordStreamFile.getEndObjectRunningHash().getHash().toByteArray();
                    final var currStartRunningHash =
                            recordStreamFile.getStartObjectRunningHash().getHash().toByteArray();
                    if (!Arrays.equals(currStartRunningHash, prevEndRunningHash)) {
                        if (Arrays.equals(EMPTY_HASH, currStartRunningHash)) {
                            setUnGood(
                                    "\t\tRecord file '{}' had an EMPTY hash instead of "
                                            + "the running hash computed from the preceding '{}'",
                                    orderedRcdNames.get(i),
                                    orderedRcdNames.get(i - 1));
                        } else {
                            setUnGood(
                                    "\t\tHash of node {} record file '{}' did NOT match "
                                            + "running hash saved in subsequent file '{}'!",
                                    nodeAccount,
                                    orderedRcdNames.get(i - 1),
                                    orderedRcdNames.get(i));
                        }
                    } else {
                        log.info(
                                "\t\tRecord file '{}' DID contain the running hash computed from"
                                        + " the preceding '{}'",
                                orderedRcdNames.get(i),
                                orderedRcdNames.get(i - 1));
                    }
                }
                // sidecar metadata verification
                final var sidecarsList = recordStreamFile.getSidecarsList();
                for (final var sidecar : sidecarsList) {
                    final int sidecarId = sidecar.getId();
                    final var sidecarPath =
                            Path.of(
                                    nodeRecordsDir,
                                    nameOfCurrentFile.replace(
                                            SIDECAR_FILE_EXTENSION,
                                            "_"
                                                    + (sidecarId < 10 ? "0" + sidecarId : sidecarId)
                                                    + SIDECAR_FILE_EXTENSION));
                    log.info(
                            "\t\tRecord file '{}' has sidecar {} related to it.",
                            nameOfCurrentFile,
                            sidecarPath);
                    // verify sidecar hash matches corresponding sidecar hash in record file
                    final byte[] sidecarFileBytes;
                    try {
                        sidecarFileBytes =
                                RecordStreamingUtils.getUncompressedStreamFileBytes(
                                        sidecarPath.toString());
                    } catch (IOException e) {
                        setUnGood("\t\t\t\tError occurred reading sidecar {}.", sidecarPath);
                        continue;
                    }
                    final var actualSidecarHash = messageDigest.digest(sidecarFileBytes);
                    final var sidecarHashInMetadata = sidecar.getHash().getHash().toByteArray();
                    if (!Arrays.equals(actualSidecarHash, sidecarHashInMetadata)) {
                        setUnGood(
                                "\t\t\t\tRecord file '{}' had a sidecar hash mismatch for sidecar"
                                        + " file {}",
                                nameOfCurrentFile,
                                sidecarPath.getName(sidecarPath.getNameCount() - 1));
                    } else {
                        log.info(
                                "\t\t\t\tRecord file '{}' DID contain the same sidecar hash in its"
                                        + " metadata for sidecar {}",
                                nameOfCurrentFile,
                                sidecarPath.getName(sidecarPath.getNameCount() - 1));
                    }
                    // verify contained sidecar types are reflected in sidecar types field
                    try {
                        final var sidecarFile = SidecarFile.parseFrom(sidecarFileBytes);
                        final var actualSidecarTypes = EnumSet.noneOf(SidecarType.class);
                        for (final var sidecarRecord : sidecarFile.getSidecarRecordsList()) {
                            switch (sidecarRecord.getSidecarRecordsCase()) {
                                case ACTIONS -> actualSidecarTypes.add(SidecarType.CONTRACT_ACTION);
                                case STATE_CHANGES -> actualSidecarTypes.add(
                                        SidecarType.CONTRACT_STATE_CHANGE);
                                case BYTECODE -> actualSidecarTypes.add(
                                        SidecarType.CONTRACT_BYTECODE);
                                default -> setUnGood(
                                        "\t\t\t\tSidecar file {} had an unexpected sidecar type {}",
                                        sidecarPath.getName(sidecarPath.getNameCount() - 1),
                                        sidecarRecord.getSidecarRecordsCase());
                            }
                        }
                        final var sidecarTypesInMetadata = sidecar.getTypesList();
                        if (actualSidecarTypes.size() != sidecarTypesInMetadata.size()
                                || !actualSidecarTypes.containsAll(sidecarTypesInMetadata)) {
                            setUnGood(
                                    "\t\t\t\tSidecar types in metadata {} are different than actual"
                                            + " {}.");
                        } else {
                            log.info(
                                    "\t\t\t\tSidecar types in metadata are equal to actual types in"
                                            + " sidecar file.");
                        }
                    } catch (InvalidProtocolBufferException e) {
                        setUnGood(
                                "An error occurred trying to reconstruct sidecar protobuf from file"
                                        + " bytes");
                    }
                }
                // transaction order verification
                // FUTURE WORK: maybe start checking after certain timestamp for optimization
                transactionAssertions.onNewRecordStreamFile(
                        recordStreamFile.getRecordStreamItemsList());
            }
            if (!transactionAssertions.getFinalizedValidity()) {
                setUnGood(
                        "Transactions / transaction records in record files of node {} were not as"
                                + " expected. See logs.",
                        nodeAccount);
            }
        }
    }

    private void checkSigValidity(
            final String recordFile, final List<File> sigs, final NodeSignatureVerifier verifier) {
        final var numAccounts = verifier.nodes().size();

        if (sigs.size() != numAccounts) {
            setUnGood(
                    "Record file {} had {} sigs ({}), not {} as expected!",
                    recordFile,
                    sigs.size(),
                    sigs,
                    numAccounts);
        }

        List<String> majority = verifier.verifySignatureFiles(sigs);
        if (majority == null) {
            setUnGood(
                    "The nodes did not find majority agreement on the hash of record file '{}'!",
                    recordFile);
        } else if (majority.size() < numAccounts) {
            setUnGood("Only {} agreed on the hash of record file '{}'!", majority, recordFile);
        } else {
            log.info(
                    "All nodes had VALID signatures on the SAME hash for record file '{}'.",
                    recordFile);
        }
    }

    private void setUnGood(String tpl, Object... varargs) {
        log.warn(tpl, varargs);
        allGood = false;
    }

    private Set<String> allRecordFilesFor(List<String> accounts) throws Exception {
        return accounts.stream()
                .map(this::recordsDirFor)
                .flatMap(this::uncheckedWalk)
                .filter(path -> RECORD_FILE_REGEX.matcher(path.toString()).find())
                .map(this::basename)
                .collect(toSet());
    }

    private String basename(Path p) {
        return p.getName(p.getNameCount() - 1).toString();
    }

    private Stream<Path> uncheckedWalk(String dir) {
        try {
            return Files.walk(Path.of(dir));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String recordsDirFor(String account) {
        return String.format("%s/record%s", baseDir.get(), account);
    }

    private NodeAddressBook downloadBook(HapiApiSpec spec) throws Exception {
        String addressBook = spec.setup().nodeDetailsName();
        HapiGetFileContents op = getFileContents(addressBook);
        allRunFor(spec, op);
        byte[] serializedBook =
                op.getResponse().getFileGetContents().getFileContents().getContents().toByteArray();
        return NodeAddressBook.parseFrom(serializedBook);
    }
}
