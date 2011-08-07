package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Cursor;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Analyser;
import net.mirky.redis.ImageError;

/**
 * Parses the variable data attached to a ZXS Basic program object.
 */
@Format.Options("zxs-basic-vars/decoding:decoding=zx-spectrum")
public final class ZXSBasicVariableDataAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) {
        Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
        ZXSBasicVariableDataAnalyser.Template[] templates = new ZXSBasicVariableDataAnalyser.Template[]{
                new ZXSBasicVariableDataAnalyser.StringVariableTemplate(),
                new ZXSBasicVariableDataAnalyser.NumericVariableTemplate(),
                new ZXSBasicVariableDataAnalyser.NumberArrayTemplate(),
                new ZXSBasicVariableDataAnalyser.LongNamedNumericVariableTemplate(),
                new ZXSBasicVariableDataAnalyser.CharArrayTemplate(),
                new ZXSBasicVariableDataAnalyser.ForVariableTemplate(),};
        VAR_LOOP : while (!cursor.atEnd()) {
            for (ZXSBasicVariableDataAnalyser.Template template : templates) {
                try {
                    if (template.matches(cursor)) {
                        template.dis(cursor, format.getDecoding(), port);
                        cursor.advance(template.size(cursor));
                        continue VAR_LOOP;
                    }
                } catch (ImageError e) {
                    return cursor.tell();
                }
            }
            // no matching template -- probably garbage
            return cursor.tell();
        }
        return cursor.tell();
    }

    public static void printIndices(int[] indices, PrintStream port) {
        port.print('(');
        for (int i = 0; i < indices.length; i++) {
            if (i != 0) {
                port.print(", ");
            }
            port.print(indices[i]);
        }
        port.print(')');
    }

    static abstract class Template {
        abstract boolean matches(Cursor cursor) throws ImageError;

        abstract void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError;

        abstract int size(Cursor cursor) throws ImageError;
    }

    static final class StringVariableTemplate extends ZXSBasicVariableDataAnalyser.Template {
        @Override
        final boolean matches(Cursor cursor) {
            int name = cursor.getUnsignedByte(0);
            return name >= 0x41 && name <= 0x5A;
        }

        @Override
        final void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError {
            int length = cursor.getUnsignedLewyde(1);
            // We'll want to throw ImageFileError, if applicable, before we
            // display anything.
            byte[] rawContent = cursor.getBytes(3, length);
            port.print(name(cursor) + ": ");
            decoding.displayForeignString(rawContent, port);
            port.println();
        }

        final String name(Cursor cursor) {
            return (char) (cursor.getUnsignedByte(0) + 0x20) + "$";
        }

        @Override
        final int size(Cursor cursor) throws ImageError {
            return 3 + cursor.getUnsignedLewyde(1);
        }
    }

    static final class NumericVariableTemplate extends ZXSBasicVariableDataAnalyser.Template {
        @Override
        final boolean matches(Cursor cursor) {
            int name = cursor.getUnsignedByte(0);
            return (name >= 0x61 && name <= 0x7A);
        }

        @Override
        final void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError {
            assert matches(cursor);
            ZXSBasicProgramAnalyser.ZXSpectrumNumber binary = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(
                    cursor.getBytes(1, 5));
            port.println(name(cursor) + ": " + binary.prepareForDisplay());
        }

        final char name(Cursor cursor) {
            return (char) ((cursor.getUnsignedByte(0) & 0x1F) + 0x60);
        }

        @Override
        final int size(Cursor cursor) {
            return 6;
        }
    }

    static final class NumberArrayTemplate extends ZXSBasicVariableDataAnalyser.Template {
        @Override
        final boolean matches(Cursor cursor) throws ImageError {
            int firstByte = cursor.getUnsignedByte(0);
            if (!(firstByte >= 0x81 && firstByte <= 0x9A)) {
                return false;
            }
            int size = cursor.getUnsignedLewyde(1);
            int rank = cursor.getUnsignedByte(3);
            if (rank == 0) {
                return false;
            }
            int[] shape = new int[rank];
            int calculatedSize = 5;
            for (int i = 0; i < rank; i++) {
                shape[i] = cursor.getUnsignedLewyde(4 + i * 2);
                calculatedSize *= shape[i];
                if (calculatedSize >= size) {
                    // size mismatch detected; risk of future overflow if we
                    // continue
                    return false;
                }
            }
            calculatedSize += 1 + rank * 2;
            if (calculatedSize != size) {
                return false;
            }
            return true;
        }

        @Override
        final void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError {
            assert matches(cursor);
            char name = (char) ((cursor.getUnsignedByte(0) & 0x1F) + 0x60);
            int rank = cursor.getUnsignedByte(3);
            int[] shape = new int[rank];
            port.print("DIM " + name);
            for (int i = 0; i < shape.length; i++) {
                shape[i] = cursor.getUnsignedLewyde(4 + i * 2);
            }
            printIndices(shape, port);
            port.println();
            int[] currentIndices = new int[rank];
            int currentSuboffset = 4 + rank * 2;
            for (int i = 0; i < rank; i++) {
                currentIndices[i] = 1;
            }
            do {
                port.print(name);
                printIndices(currentIndices, port);
                ZXSBasicProgramAnalyser.ZXSpectrumNumber binary = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(
                        cursor.getBytes(currentSuboffset, 5));
                currentSuboffset += 5;
                port.println(": " + binary.prepareForDisplay());
            } while (incrementIndices(currentIndices, shape));
        }

        static final boolean incrementIndices(int[] indices, int[] shape) {
            assert indices.length == shape.length;
            for (int i = indices.length - 1; i >= 0; i--) {
                if (indices[i] < shape[i]) {
                    indices[i]++;
                    return true;
                } else {
                    indices[i] = 1;
                }
            }
            return false;
        }

        @Override
        final int size(Cursor cursor) throws ImageError {
            return cursor.getUnsignedLewyde(1) + 3;
        }
    }

    static final class LongNamedNumericVariableTemplate extends ZXSBasicVariableDataAnalyser.Template {
        @Override
        final boolean matches(Cursor cursor) {
            int name = cursor.getUnsignedByte(0);
            if (!(name >= 0xA1 && name <= 0xBA)) {
                return false;
            }
            int b;
            int i = 1;
            do {
                b = cursor.getUnsignedByte(i);
                int c = b & 0x7F;
                if (!((c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A) || (c >= 0x30 && c <= 0x39))) {
                    return false;
                }
                i++;
            } while ((b & 0x80) == 0);
            return true;
        }

        @Override
        final void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError {
            assert matches(cursor);
            ZXSBasicProgramAnalyser.ZXSpectrumNumber binary = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(
                    cursor.getBytes(name(cursor).length(), 5));
            port.println(name(cursor) + ": " + binary.prepareForDisplay());
        }

        final String name(Cursor cursor) {
            StringBuilder sb = new StringBuilder();
            sb.append((char) ((cursor.getUnsignedByte(0) & 0x1F) + 0x60));
            int b;
            int i = 1;
            do {
                b = cursor.getUnsignedByte(i);
                sb.append((char) (b & 0x7F));
                i++;
            } while ((b & 0x80) == 0);
            return sb.toString();
        }

        @Override
        final int size(Cursor cursor) {
            return name(cursor).length() + 5;
        }
    }

    static final class CharArrayTemplate extends ZXSBasicVariableDataAnalyser.Template {
        @Override
        final boolean matches(Cursor cursor) throws ImageError {
            int firstByte = cursor.getUnsignedByte(0);
            if (!(firstByte >= 0xC1 && firstByte <= 0xDA)) {
                return false;
            }
            int size = cursor.getUnsignedLewyde(1);
            int rank = cursor.getUnsignedByte(3);
            if (rank == 0) {
                return false;
            }
            int[] shape = new int[rank];
            int calculatedSize = 1;
            for (int i = 0; i < rank; i++) {
                shape[i] = cursor.getUnsignedLewyde(4 + i * 2);
                calculatedSize *= shape[i];
                if (calculatedSize >= size) {
                    // size mismatch detected; risk of future overflow if we
                    // continued
                    return false;
                }
            }
            calculatedSize += 1 + rank * 2;
            if (calculatedSize != size) {
                return false;
            }
            return true;
        }

        @Override
        final void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError {
            assert matches(cursor);
            char name = (char) ((cursor.getUnsignedByte(0) & 0x1F) + 0x60);
            int rank = cursor.getUnsignedByte(3);
            int[] shape = new int[rank];
            port.print("DIM " + name + '$');
            for (int i = 0; i < shape.length; i++) {
                shape[i] = cursor.getUnsignedLewyde(4 + i * 2);
            }
            printIndices(shape, port);
            port.println();
            int[] currentIndices = new int[rank - 1];
            int currentSuboffset = 4 + rank * 2;
            for (int i = 0; i < currentIndices.length; i++) {
                currentIndices[i] = 1;
            }
            do {
                port.print(name);
                printIndices(currentIndices, port);
                port.print(": ");
                decoding.displayForeignString(cursor.getBytes(currentSuboffset, shape[shape.length - 1]), port);
                currentSuboffset += shape[shape.length - 1];
                port.println();
            } while (incrementIndices(currentIndices, shape));
        }

        static final boolean incrementIndices(int[] indices, int[] shape) {
            assert indices.length <= shape.length;
            for (int i = indices.length - 1; i >= 0; i--) {
                if (indices[i] < shape[i]) {
                    indices[i]++;
                    return true;
                } else {
                    indices[i] = 1;
                }
            }
            return false;
        }

        @Override
        final int size(Cursor cursor) throws ImageError {
            return cursor.getUnsignedLewyde(1) + 3;
        }
    }

    static final class ForVariableTemplate extends ZXSBasicVariableDataAnalyser.Template {
        @Override
        final boolean matches(Cursor cursor) {
            int name = cursor.getUnsignedByte(0);
            return name >= 0xE1 && name < 0xFA;
        }

        @Override
        final void dis(Cursor cursor, Decoding decoding, PrintStream port) throws ImageError {
            assert matches(cursor);
            char name = (char) ((cursor.getUnsignedByte(0) & 0x1F) + 0x60);
            ZXSBasicProgramAnalyser.ZXSpectrumNumber value = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(
                    cursor.getBytes(1, 5));
            ZXSBasicProgramAnalyser.ZXSpectrumNumber boundary = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(
                    cursor.getBytes(6, 5));
            ZXSBasicProgramAnalyser.ZXSpectrumNumber step = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(
                    cursor.getBytes(11, 5));
            port.println("FOR " + name + " = " + value.prepareForDisplay() + " TO "
                    + boundary.prepareForDisplay() + " STEP " + step.prepareForDisplay());
            int lineno = cursor.getUnsignedLewyde(16);
            int stmtno = cursor.getUnsignedByte(18);
            port.println("    (loop body starts on line " + lineno + ", statement " + stmtno + ")");
        }

        @Override
        final int size(Cursor cursor) {
            return 19;
        }
    }
}