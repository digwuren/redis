package net.mirky.redis;

import net.mirky.redis.Disassembler.DeciphererInput;

final class OutputPhaseDecipherer {
    private OutputPhaseDecipherer() {
        // not a real class
    }

    /**
     * Run the given disassembler bytecode, append the output to the given {@link StringBuilder}, determine
     * the instruction's size, and mark referred entry points. This is the
     * output generation phase variant; it does not mark the entry points or
     * affect the sequencer.
     * 
     * @return the instruction's size
     * 
     * @throws Disassembler.IncompleteInstruction
     *             if the end of the binary object in the {@link Disassembler}
     *             is encountered before the instruction ends
     * @throws ClassicLang.UnknownOpcode
     *             if {@link Lang.Tabular} dispatch fails (which is not
     *             necessarily dispatch by the first byte in this instruction;
     *             some languages have instructions with multiple dispatches)
     */
    static final int decipher(byte[] code, int startPosition, ClassicLang.Tabular.Linkage linkage, DeciphererInput in, DeciphererOutputStringBuilder out) throws Disassembler.IncompleteInstruction, ClassicLang.UnknownOpcode {
        Disassembler.Maximiser currentInstructionSize = new Disassembler.Maximiser(0);
        int currentValue = 0;
        for (int i = startPosition;; i++) {
            byte step = code[i];
            if (step >= 0x20 && step <= 0x7E) {
                out.append((char) step);
            } else if (step >= Disassembler.Bytecode.MINITABLE_LOOKUP_0
                    && step < Disassembler.Bytecode.MINITABLE_LOOKUP_0 + Disassembler.Bytecode.MAX_MINITABLE_COUNT) {
                String[] minitable = linkage.minitables[step - Disassembler.Bytecode.MINITABLE_LOOKUP_0];
                // note that we're checking this at the minitable construction
                // time
                assert minitable.length > 0 && (minitable.length & (minitable.length - 1)) == 0;
                // mask off excess bits, then fetch a string from the minitable
                out.append(minitable[currentValue & (minitable.length - 1)]);
            } else if (step >= Disassembler.Bytecode.DISPATCH_0
                    && step < Disassembler.Bytecode.DISPATCH_0 + Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                int suboffset = step - Disassembler.Bytecode.DISPATCH_0;
                ClassicLang newLang = linkage.getReferredLanguage(suboffset);
                int subsize = newLang.decipher(currentValue, in, out.sb);
                currentInstructionSize.feed(suboffset + subsize);
            } else if (step >= Disassembler.Bytecode.TEMPSWITCH_0
                    && step < Disassembler.Bytecode.TEMPSWITCH_0 + Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                // ignore in output generation phase
            } else if (step >= Disassembler.Bytecode.ENTRY_POINT_0
                    && step < Disassembler.Bytecode.ENTRY_POINT_0 + Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                // ignore in output generation phase
            } else if (step >= Disassembler.Bytecode.GET_BYTE_0 && step <= Disassembler.Bytecode.GET_BYTE_0 + Disassembler.Bytecode.MAX_SUBOFFSET) {
                int suboffset = step - Disassembler.Bytecode.GET_BYTE_0;
                currentValue = in.getUnsignedByte(suboffset);
                currentInstructionSize.feed(suboffset + 1);
            } else if (step >= Disassembler.Bytecode.GET_LEWYDE_0 && step <= Disassembler.Bytecode.GET_LEWYDE_0 + Disassembler.Bytecode.MAX_SUBOFFSET) {
                int suboffset = step - Disassembler.Bytecode.GET_LEWYDE_0;
                currentValue = in.getUnsignedLewyde(suboffset);
                currentInstructionSize.feed(suboffset + 2);
            } else {
                switch (step) {
                    case Disassembler.Bytecode.SHR_3:
                        currentValue >>>= 3;
                        break;
    
                    case Disassembler.Bytecode.SHR_4:
                        currentValue >>>= 4;
                        break;
    
                    case Disassembler.Bytecode.SHR_5:
                        currentValue >>>= 5;
                        break;
    
                    case Disassembler.Bytecode.SHR_6:
                        currentValue >>>= 6;
                        break;
    
                    case Disassembler.Bytecode.ENTRY_POINT_REFERENCE:
                    case Disassembler.Bytecode.SUBROUTINE_ENTRY_POINT_REFERENCE:
                        // ignore in output generation phase
                        break;
    
                    case Disassembler.Bytecode.UNSIGNED_BYTE:
                        out.append("0x");
                        out.append(Hex.b(currentValue));
                        break;
    
                    case Disassembler.Bytecode.UNSIGNED_WYDE:
                        out.append("0x");
                        out.append(Hex.w(currentValue));
                        break;
    
                    case Disassembler.Bytecode.SIGNED_BYTE:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                            currentValue = -currentValue;
                            out.append('-');
                        }
                        out.append("0x");
                        out.append(Hex.b(currentValue));
                        break;
    
                    case Disassembler.Bytecode.SIGNED_WYDE:
                        if ((currentValue & 0x8000) == 0) {
                            currentValue &= 0x7FFF;
                        } else {
                            currentValue |= ~0x7FFF;
                            currentValue = -currentValue;
                            out.append('-');
                        }
                        out.append("0x");
                        out.append(Hex.w(currentValue));
                        break;
    
                    case Disassembler.Bytecode.TERMINATE:
                    case Disassembler.Bytecode.SET_COUNTDOWN_6:
                    case Disassembler.Bytecode.SET_COUNTDOWN_8:
                    case Disassembler.Bytecode.SET_COUNTDOWN_12:
                        // ignore in output generation phase
                        break;
                        
                    case Disassembler.Bytecode.SWITCH_BACK:
                        out.switchBack();
                        break;
    
                    case Disassembler.Bytecode.BYTE_SIGNEDREL_1:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                        }
                        currentValue += in.getCurrentInstructionAddress() + 1;
                        break;
    
                    case Disassembler.Bytecode.BYTE_SIGNEDREL_2:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                        }
                        currentValue += in.getCurrentInstructionAddress() + 2;
                        break;
    
                    case Disassembler.Bytecode.AND_3:
                        currentValue &= 3;
                        break;
    
                    case Disassembler.Bytecode.AND_7:
                        currentValue &= 7;
                        break;
    
                    case Disassembler.Bytecode.AND_0x38:
                        currentValue &= 0x38;
                        break;
    
                    case Disassembler.Bytecode.DECIMAL:
                        out.append(currentValue);
                        break;
    
                    case Disassembler.Bytecode.COMPLETE:
                        return currentInstructionSize.get();
    
                    default:
                        throw new RuntimeException("bug detected");
                }
            }
        }
    }
}
