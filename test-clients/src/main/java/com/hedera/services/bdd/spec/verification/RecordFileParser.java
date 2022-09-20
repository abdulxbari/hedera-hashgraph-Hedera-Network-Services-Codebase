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
package com.hedera.services.bdd.spec.verification;

import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.RecordStreamFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordFileParser {
    private static final Logger log = LogManager.getLogger(RecordFileParser.class);

    public static RecordStreamFile parseFrom(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("No such file - " + file);
        }
        try {
            final var recordFilePair =
                    RecordStreamingUtils.readRecordStreamFile(file.getAbsolutePath());
            final var recordStreamFile = recordFilePair.getRight().get();
            log.debug("File '{}' is: ", file);
            log.debug("  -> Record format v{}", recordFilePair.getLeft());
            log.debug("  -> HAPI protocol v{}", recordStreamFile.getHapiProtoVersion());
            return recordStreamFile;
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        } catch (IOException e) {
            log.error("Problem reading record file '{}'!", file, e);
            throw new IllegalStateException();
        } catch (Exception e) {
            log.error("Problem parsing record file '{}'!", file, e);
            throw new IllegalStateException();
        }
    }
}
