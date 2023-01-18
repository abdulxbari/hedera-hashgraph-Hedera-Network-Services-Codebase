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

import com.hedera.services.yahcli.commands.signedstate.HexToBytesConverter.Bytes;
import com.hedera.services.yahcli.commands.signedstate.evminfo.SelectorDescriptions;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "selector",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Resolves selector against 4byte.directory webservice")
public class ResolveSelectorCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-s", "--selector"},
            arity = "1",
            converter = HexToBytesConverter.class,
            description = "selector to look up, given in hex")
    Bytes selectorBytes;

    @Override
    public Integer call() throws Exception {
        var selector = HexToBytesConverter.asUnsignedInt(selectorBytes);
        var descriptionFetcher = new SelectorDescriptions();

        var startInstant = Clock.systemUTC().instant();

        var websvcResults = descriptionFetcher.identifySelectors(Set.of(selector));

        var endInstant = Clock.systemUTC().instant();
        var serviceCallDuration =
                Duration.between(startInstant, endInstant).toString().substring(3);

        if (websvcResults.hasAllMappings()) {
            var methodInfo = websvcResults.identifications().get(selector);
            System.out.printf(
                    "Selector 0x%08X -> '%s' (%s) (elapsed: %s)%n",
                    selector, methodInfo.methodName(), methodInfo.signature(), serviceCallDuration);
        } else {
            var ex = websvcResults.errors().get(selector);
            System.out.printf(
                    "*** Selector 0x%08X -> '%s' (elapsed: %s)%n",
                    selector, ex.getMessage(), serviceCallDuration);
        }
        return 0;
    }
}
