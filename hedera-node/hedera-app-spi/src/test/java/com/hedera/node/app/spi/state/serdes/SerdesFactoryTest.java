/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.state.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SerdesFactoryTest {
    @Mock
    private SerializableDataInputStream input;

    @Mock
    private SerializableDataOutputStream output;

    @Mock
    private PbjParser<String> parser;

    @Mock
    private PbjWriter<String> writer;

    private Serdes<String> subject;

    @BeforeEach
    void setUp() {
        subject = SerdesFactory.newInMemorySerdes(parser, writer);
    }

    @Test
    void unusedMethodsAreUnsupported() {
        assertThrows(UnsupportedOperationException.class, subject::typicalSize);
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals("A", input));
    }

    @Test
    void delegatesWrite() throws IOException {
        subject.write("B", output);

        verify(writer).write(eq("B"), any());
    }

    @Test
    void failsOnWrongDataOutput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.write("B", new DataOutputStream(new ByteArrayOutputStream())));
    }

    @Test
    void delegatesParse() throws IOException {
        given(parser.parse(any())).willReturn("C");

        final var value = subject.parse(input);

        assertEquals("C", value);
    }

    @Test
    void failsOnWrongDataInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.parse(new DataInputStream(new ByteArrayInputStream(new byte[0]))));
    }
}