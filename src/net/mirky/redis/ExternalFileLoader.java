package net.mirky.redis;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

// ExternalFileLoader instances are used for reconstruction.
// We have two implementations: ExternalFileLoader.Verification "loads external files" from the in-core
// Map prepared as the ReconstructionDataCollector was filled in.  ExternalFileLoader.Real loads
// actual external files, and is usable for actual reconstruction.
abstract class ExternalFileLoader {
	abstract byte[] loadFile(String name, int properSize) throws ReconstructionFailure.DataInconsistent;

	static final class Verification extends ExternalFileLoader {
		private final Map<String, byte[]> externalFiles;
		
		Verification(Map<String, byte[]> externalFiles) {
			this.externalFiles = externalFiles;
		}
		
		@Override
		final byte[] loadFile(String name, int properSize) {
			byte[] data = externalFiles.get(name);
			assert data != null;
			if (data.length != properSize) {
				throw new RuntimeException("bug detected: extracted file size mismatch");
			}
			assert data.length == properSize;
			return data;
		}
	}
	
	static final class Real extends ExternalFileLoader {
		@Override
		final byte[] loadFile(String name, int properSize) throws ReconstructionFailure.DataInconsistent {
			for (int i = 0; i < name.length(); i++) {
				char c = name.charAt(i);
				if (c < 0x20 || c > 0x7E) {
					throw new ReconstructionFailure.DataInconsistent("reconstruction file specifies filename with character(s) outside of printable ASCII range");
				}
			}
			// For real ExternalFileLoader:s, the filename comes from an external RCN file, so we should check it for nastiness.
			// Just in case.
			String[] filenameComponents = name.split("/", -1);
			if (filenameComponents.length == 0) {
				throw new ReconstructionFailure.DataInconsistent("reconstruction file specifies empty filename");
			}
			if (filenameComponents[0].length() == 0) {
				throw new ReconstructionFailure.DataInconsistent("reconstruction file specifies absolute filename");
			}
			for (String component : filenameComponents) {
				if (component.equals(".")) {
					throw new ReconstructionFailure.DataInconsistent("reconstruction file specifies filename with dot component");
				}
				if (component.equals("..")) {
					throw new ReconstructionFailure.DataInconsistent("reconstruction file specifies filename with dot-dot component");
				}
			}
			
			FileInputStream stream;
			try {
				stream = new FileInputStream(name);
			} catch (FileNotFoundException e) {
				throw new ReconstructionFailure.DataInconsistent("external file " + name + " not found");
			}
			assert stream != null;
			byte[] data = new byte[properSize];
			try {
				if (stream.read(data) != properSize) {
					throw new ReconstructionFailure.DataInconsistent("external file " + name + " too short");
				}
				if (stream.read() != -1) {
					throw new ReconstructionFailure.DataInconsistent("external file " + name + " too long");
				}
				stream.close();
			} catch (IOException e) {
				throw new ReconstructionFailure.DataInconsistent("read error on external file " + name);
			}
			System.out.println("loaded " + name + " " + properSize);
			return data;
		}
	}
}