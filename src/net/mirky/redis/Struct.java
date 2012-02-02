package net.mirky.redis;

import java.io.PrintStream;

public final class Struct extends BinaryElementType {
    private final String name;
    private final Struct.Field[] fields;

    public Struct(String name, Struct.Field... fields) {
        this.name = name;
        this.fields = fields;
    }

    @Override
    public final int show(Cursor cursor, String indentation, String itemName, Decoding decoding, PrintStream port) throws ImageError {
        port.println(Hex.t(cursor.tell()) + ":         " + indentation + (itemName != null ? itemName + ": " : "") + name);
        int offsetPastStruct = 0;
        for (Struct.Field field : fields) {
            int offsetPastField = field.offset + field.type.show(cursor.subcursor(field.offset), indentation + "  ", field.name, decoding, port);
            if (offsetPastField > offsetPastStruct) {
                offsetPastStruct = offsetPastField;
            }
        }
        return offsetPastStruct;
    }

    public static final class Field {
        public final int offset;
        public final String name;
        public final BinaryElementType type;

        public Field(int offset, String name, BinaryElementType fieldType) {
            this.offset = offset;
            this.name = name;
            this.type = fieldType;
        }
    }
}
