package net.mirky.redis;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class ReconstructionDataCollector extends ReconstructionItem.Collector {
	private final Map<String, byte[]> externalFiles;
	final ArrayList<String> externalFileOrder;
	private final Map<String, Format> contextualFormats; // only used for recursive dissection, not stored in the rcn file

	public ReconstructionDataCollector(int size) {
		super(size);
		externalFiles = new HashMap<String, byte[]>();
		externalFileOrder = new ArrayList<String>();
		contextualFormats = new HashMap<String, Format>();
	}

	// Record given data as a child object of the given name at parent's given offset.  If format
	// is not null, this will be considered the child object's context-derived format; otherwise,
	// the child object's format would need to be guessed separately.
	public final void contiguousFile(String filename, byte[] data, Format format, int offset) throws ImageError.DuplicateChildFilenameError {
		if (externalFiles.containsKey(filename)) {
			throw new ImageError.DuplicateChildFilenameError("duplicate filename " + filename);
		}
		externalFiles.put(filename, data);
		externalFileOrder.add(filename);
		if (format != null) {
			contextualFormats.put(filename, format);
		}
		try {
			addItem(new ReconstructionItem.ContiguousFile(filename, data.length, BinaryUtil.crc32(data), offset));
		} catch (ReconstructionFailure.DataInconsistent e) {
			throw new RuntimeException("bug detected", e);
		}
	}

	// Record given data as a child object of the given name, stored in the parent as blocks of
	// the given size at given offsets.  (The last block may be incomplete, provided that the
	// parent format supports explicit slack.)  If format is not null, this will be considered
	// the child object's context-derived format; otherwise, the child object's format would need
	// to be guessed separately.
	public final void fileBlocks(String filename, byte[] data, Format format, int blockSize,
			int[] blockOffsets) throws ImageError.DuplicateChildFilenameError {
		assert data.length <= blockSize * blockOffsets.length;
		assert data.length > blockSize * (blockOffsets.length - 1);
		if (externalFiles.containsKey(filename)) {
			throw new ImageError.DuplicateChildFilenameError("duplicate filename " + filename);
		}
		externalFiles.put(filename, data);
		externalFileOrder.add(filename);
		if (format != null) {
			contextualFormats.put(filename, format);
		}
		try {
			addItem(new ReconstructionItem.FileBlocks(filename, data.length, BinaryUtil.crc32(data), blockSize, blockOffsets));
		} catch (ReconstructionFailure.DataInconsistent e) {
			throw new RuntimeException("unexpected exception", e);
		}
	}

	public final void patch(int offset, byte[] data) throws ReconstructionFailure.DataInconsistent {
		addItem(new ReconstructionItem.Patch(offset, data));
	}
	
	public final void checksum(int crc32) {
		addItem(new ReconstructionItem.CheckImageCRC32(crc32));
	}

	final TaggedData getFile(String name) {
		byte[] data = externalFiles.get(name);
		Format format = contextualFormats.get(name);
		if (format == null) {
			format = Format.guess(name, data);
		}
		return new TaggedData(data, format);
	}
	
	final void listExternalFiles() {
		for (String filename : externalFileOrder) {
			System.out.println(filename + " (" + externalFiles.get(filename).length + " bytes)");
		}
	}

	final void writeExternalFilesOut(String rcnFilename) throws IOException {
		for (String filename : externalFileOrder) {
			FileOutputStream stream = new FileOutputStream(filename);
			byte[] data = externalFiles.get(filename);
			stream.write(data);
			stream.close();
			System.out.println("wrote " + filename + " (" + data.length + " bytes)");
		}
		writeOut(rcnFilename);
		System.out.println("wrote " + rcnFilename);
	}

	final void verifyReconstruction(byte[] etalon) throws RuntimeException {
		byte[] reconstitutedImage;
		try {
			ExternalFileLoader loader = new ExternalFileLoader.Verification(externalFiles);
			reconstitutedImage = reconstruct(loader);
		} catch (ReconstructionFailure.DataInconsistent e) {
			throw new RuntimeException("bug detected", e);
		}
		if (reconstitutedImage.length != etalon.length) {
			throw new RuntimeException("bug detected: reconstituted image size mismatch");
		}
		for (int i = 0; i < reconstitutedImage.length; i++) {
			if (reconstitutedImage[i] != etalon[i]) {
				throw new RuntimeException("bug detected: reconstituted data mismatch");
			}
		}
	}
}
