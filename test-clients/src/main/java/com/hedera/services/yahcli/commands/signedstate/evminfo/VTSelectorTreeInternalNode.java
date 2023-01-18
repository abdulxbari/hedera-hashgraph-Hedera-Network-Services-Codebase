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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Columns;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Variant;
import org.jetbrains.annotations.NotNull;

public final class VTSelectorTreeInternalNode extends MacroLine {

    public final long selectorSplit;
    public final int methodOffset;

    public long getSelectorSplit() {
        return selectorSplit;
    }

    public int getMethodOffset() {
        return methodOffset;
    }

    public VTSelectorTreeInternalNode(
            int codeOffset, int codeSize, long selectorSplit, int methodOffset) {
        super(codeOffset, codeSize, "VTTreeSwitch");
        this.selectorSplit = selectorSplit;
        this.methodOffset = methodOffset;
    }

    @Override
    public void formatLine(@NotNull StringBuilder sb) {
        if (Variant.DISPLAY_CODE_OFFSET.getValue()) {
            extendWithBlanksTo(sb, Columns.CODE_OFFSET.getColumn());
            sb.append("%5X".formatted(codeOffset));
        }

        extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
        sb.append(mnemonic());

        extendWithBlanksTo(sb, Columns.OPERAND.getColumn());
        sb.append(formatSelectorSplit());
        sb.append("  ");
        sb.append(formatOffset(methodOffset));
    }

    LabelLine asLabel() {
        return new LabelLine(
                methodOffset, formatSelectorSplit(), "from @" + formatOffset(codeOffset));
    }

    String formatSelectorSplit() {
        return "VT-" + hexer().toHexDigits(selectorSplit, 8);
    }

    String formatOffset(int offset) {
        return hexer().toHexDigits(offset, 4);
    }
}
