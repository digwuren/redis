package net.mirky.redis;

import java.io.PrintStream;

public abstract class Struct {
    public final String name;

    public Struct(String name) {
        this.name = name;
    }

    protected final void showBreadcrumbs(Cursor cursor, String path, PrintStream port) {
        port.println(Hex.t(cursor.tell()) + ": " + name + " @ " + path);
    }

    public abstract void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError;

    static final Struct.Basic BLANK = new Struct.Basic("blank");
    
    public static final Struct D64DIRENTRY = new Struct("d64direntry") {
        @Override
        public final void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError {
            Struct struct;
            if (cursor.regionBlank(2, 30)) {
                struct = BLANK;
            } else if (cursor.getUnsignedByte(2) != 0) {
                struct = D64DIRENTRY_REGULAR;
            } else {
                struct = new Void("d64direntry.unused", 32);
            }
            struct.show(cursor, path, port, decoding);
        }
    };

    static final Struct.Basic D64DIRENTRY_REGULAR = new Struct.Basic("d64direntry.regular", 
            new Field(5, "filename", new StructFieldType.PaddedString(16, ((byte) 0xA0))),
            new Field(2, "file type", StructFieldType.D64_FILE_TYPE_BYTE),
            new Field(3, "data start", StructFieldType.D64_SECTOR_CHAIN_START),
            new Field(21, "side chain", StructFieldType.D64_SECTOR_CHAIN_START),
            new Field(30, "sector count", StructFieldType.UNSIGNED_LEWYDE)
    );

    static final class Basic extends Struct {
        private final Struct.Field[] fields;
    
        public Basic(String name, Struct.Field... fields) {
            super(name);
            this.fields = fields;
        }
    
        @Override
        public final void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError {
            showBreadcrumbs(cursor, path, port);
            for (Struct.Field field : fields) {
                field.type.show(cursor, field.offset, field.name, port, decoding);
            }
        }
    }

    static final class Void extends Struct {
        private final int size;
        
        public Void(String name, int size) {
            super(name);
            this.size = size;
        }
    
        @Override
        public final void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError {
            showBreadcrumbs(cursor, path, port);
            Hex.dump(cursor.getBytes(0, size), cursor.tell(), decoding, port);
        }
    }

    static final class Field {
        public final int offset;
        public final String name;
        public final StructFieldType type;
        
        public Field(int offset, String name, StructFieldType type) {
            this.offset = offset;
            this.name = name;
            this.type = type;
        }
    }
}