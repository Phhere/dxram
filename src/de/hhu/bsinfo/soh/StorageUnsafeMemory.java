
package de.hhu.bsinfo.soh;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import sun.misc.Unsafe;
import de.hhu.bsinfo.utils.Endianness;
import de.hhu.bsinfo.utils.unsafe.UnsafeHandler;

/**
 * Implementation of a storage based on an unsafe allocated
 * block of memory.
 *
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 11.11.2015
 */
public class StorageUnsafeMemory implements Storage {
	private static final Unsafe UNSAFE = UnsafeHandler.getInstance().getUnsafe();

	private long m_memoryBase;
	private long m_memorySize;

	/**
	 * Default constructor
	 */
	public StorageUnsafeMemory() {
		m_memoryBase = -1;
		m_memorySize = -1;
	}

	@Override
	public void allocate(final long p_size) {
		assert p_size > 0;

		try {
			m_memoryBase = UNSAFE.allocateMemory(p_size);
		} catch (final Throwable e) {
			throw new MemoryRuntimeException("Could not initialize memory", e);
		}

		m_memorySize = p_size;
	}

	@Override
	public void free() {
		try {
			UNSAFE.freeMemory(m_memoryBase);
		} catch (final Throwable e) {
			throw new MemoryRuntimeException("Could not free memory", e);
		}
		m_memorySize = 0;
	}

	@Override
	public String toString() {
		return "m_memoryBase=0x" + Long.toHexString(m_memoryBase) + ", m_memorySize: " + m_memorySize;
	}

	@Override
	public void dump(final File p_file, final long p_ptr, final long p_length) {
		assert p_ptr >= 0;
		assert p_ptr < m_memorySize;
		assert p_ptr + p_length <= m_memorySize;

		RandomAccessFile outFile = null;
		try {
			outFile = new RandomAccessFile(p_file, "rw");

			long offset = 0;
			while (offset < p_length) {
				outFile.writeByte(UNSAFE.getByte(m_memoryBase + p_ptr + offset));
				offset++;
			}
		} catch (final IOException e) {
			throw new MemoryRuntimeException(e.getMessage());
		} finally {
			try {
				if (outFile != null) {
					outFile.close();
				}
			} catch (final IOException e) {}
		}
	}

	@Override
	public long getSize() {
		return m_memorySize;
	}

	@Override
	public void set(final long p_ptr, final long p_size, final byte p_value) {
		assert p_ptr >= 0;
		assert p_ptr < m_memorySize;
		assert p_ptr + p_size <= m_memorySize;

		UNSAFE.setMemory(m_memoryBase + p_ptr, p_size, p_value);
	}

	@Override
	public byte[] readBytes(final long p_ptr, final int p_length) {
		assert p_ptr >= 0;
		assert p_ptr < m_memorySize;
		assert p_ptr + p_length <= m_memorySize;

		final byte[] array = new byte[p_length];

		/// XXX quite inefficient way to write, but no other possibility (?)
		for (int i = 0; i < p_length; i++) {
			array[i] = UNSAFE.getByte(m_memoryBase + p_ptr + i);
		}

		return array;
	}

	@Override
	public int readBytes(final long p_ptr, final byte[] p_array, final int p_arrayOffset, final int p_length) {
		assert p_ptr >= 0;
		assert p_ptr < m_memorySize;
		assert p_ptr + p_length <= m_memorySize;

		int bytesRead = 0;
		
		/// XXX quite inefficient way to write, but no other possibility (?)
		for (int i = 0; i < p_length; i++) {
			p_array[p_arrayOffset + i] = UNSAFE.getByte(m_memoryBase + p_ptr + i);
			bytesRead++;
		}
		
		return bytesRead;
	}

	@Override
	public byte readByte(final long p_ptr) {
		assert p_ptr >= 0;
		assert p_ptr < m_memorySize;

		return UNSAFE.getByte(m_memoryBase + p_ptr);
	}

	@Override
	public short readShort(final long p_ptr) {
		assert p_ptr >= 0;
		assert p_ptr + 1 < m_memorySize;

		// ensure correct endianness between
		// JVM and native system
		short val = UNSAFE.getShort(m_memoryBase + p_ptr);
//		short val = 0;
//		if (Endianness.getEndianness() > 0) {
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr) & 0xFF) << 8;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 1) & 0xFF);
//		} else {
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr) & 0xFF);
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 1) & 0xFF) << 8;			
//		}
		
		return val;
	}

	@Override
	public int readInt(final long p_ptr) {
		assert p_ptr >= 0;
		assert p_ptr + 3 < m_memorySize;

		// ensure correct endianness between
		// JVM and native system
		int val = UNSAFE.getInt(m_memoryBase + p_ptr);
//		int val = 0;
//		if (Endianness.getEndianness() > 0) {
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr) & 0xFF) << 24;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 1) & 0xFF) << 16;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 2) & 0xFF) << 8;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 3) & 0xFF);
//		} else {
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr) & 0xFF);
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 1) & 0xFF) << 8;	
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 2) & 0xFF) << 16;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 3) & 0xFF) << 24;
//		}
		
		return val;
	}

	@Override
	public long readLong(final long p_ptr) {
		assert p_ptr >= 0;
		assert p_ptr + 7 < m_memorySize;

		// ensure correct endianness between
		// JVM and native system

		long val = UNSAFE.getLong(m_memoryBase + p_ptr);
//		long val = 0;
//		if (Endianness.getEndianness() > 0) {
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr) & 0xFF) << 56;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 1) & 0xFF) << 48;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 2) & 0xFF) << 40;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 3) & 0xFF) << 32;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 4) & 0xFF) << 24;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 5) & 0xFF) << 16;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 6) & 0xFF) << 8;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 7) & 0xFF);
//		} else {
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr) & 0xFF);
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 1) & 0xFF) << 8;	
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 2) & 0xFF) << 16;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 3) & 0xFF) << 24;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 4) & 0xFF) << 32;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 5) & 0xFF) << 40;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 6) & 0xFF) << 48;
//			val |= (UNSAFE.getByte(m_memoryBase + p_ptr + 7) & 0xFF) << 56;
//		}
		
		return val;
	}

	@Override
	public int writeBytes(final long p_ptr, final byte[] p_array) {
		return writeBytes(p_ptr, p_array, 0, p_array.length);
	}

	@Override
	public int writeBytes(final long p_ptr, final byte[] p_array, final int p_arrayOffset, final int p_length) {
		assert p_ptr >= 0;
		assert p_ptr + p_length <= m_memorySize;

		int bytesWritten = 0;
		
		/// XXX quite inefficient way to write, but no other possibility (?)
		for (int i = 0; i < p_length; i++) {
			UNSAFE.putByte(m_memoryBase + p_ptr + i, p_array[p_arrayOffset + i]);
			bytesWritten++;
		}
		
		return bytesWritten;
	}

	@Override
	public void writeByte(final long p_ptr, final byte p_value) {
		assert p_ptr >= 0;
		assert p_ptr < m_memorySize;

		UNSAFE.putByte(m_memoryBase + p_ptr, p_value);
	}

	@Override
	public void writeShort(final long p_ptr, final short p_value) {
		assert p_ptr >= 0;
		assert p_ptr + 1 < m_memorySize;
		
		// ensure correct endianness between
		// JVM and native system
//		if (Endianness.getEndianness() > 0) {
//			UNSAFE.putByte(m_memoryBase + p_ptr, (byte) ((p_value >> 8) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 1, (byte) ((p_value) & 0xFF));
//		} else {
//			UNSAFE.putByte(m_memoryBase + p_ptr, (byte) ((p_value) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 1, (byte) ((p_value >> 8) & 0xFF));	
//		}
		
		UNSAFE.putShort(m_memoryBase + p_ptr, p_value);
	}

	@Override
	public void writeInt(final long p_ptr, final int p_value) {
		assert p_ptr >= 0;
		assert p_ptr + 3 < m_memorySize;

		// ensure correct endianness between
		// JVM and native system
//		if (Endianness.getEndianness() > 0) {
//			UNSAFE.putByte(m_memoryBase + p_ptr, (byte) ((p_value >> 24) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 1, (byte) ((p_value >> 16) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 2, (byte) ((p_value >> 8) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 3, (byte) ((p_value) & 0xFF));
//		} else {
//			UNSAFE.putByte(m_memoryBase + p_ptr, (byte) ((p_value) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 1, (byte) ((p_value >> 8) & 0xFF));	
//			UNSAFE.putByte(m_memoryBase + p_ptr + 2, (byte) ((p_value >> 16) & 0xFF));	
//			UNSAFE.putByte(m_memoryBase + p_ptr + 3, (byte) ((p_value >> 24) & 0xFF));	
//		}
		
		UNSAFE.putInt(m_memoryBase + p_ptr, p_value);
	}

	@Override
	public void writeLong(final long p_ptr, final long p_value) {
		assert p_ptr >= 0;
		assert p_ptr + 7 < m_memorySize;

		// ensure correct endianness between
		// JVM and native system
//		if (Endianness.getEndianness() > 0) {
//			UNSAFE.putByte(m_memoryBase + p_ptr, (byte) ((p_value >> 56) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 1, (byte) ((p_value >> 48) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 2, (byte) ((p_value >> 40) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 3, (byte) ((p_value >> 32) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 4, (byte) ((p_value >> 24) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 5, (byte) ((p_value >> 16) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 6, (byte) ((p_value >> 8) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 7, (byte) ((p_value) & 0xFF));
//		} else {
//			UNSAFE.putByte(m_memoryBase + p_ptr, (byte) ((p_value) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 1, (byte) ((p_value >> 8) & 0xFF));	
//			UNSAFE.putByte(m_memoryBase + p_ptr + 2, (byte) ((p_value >> 16) & 0xFF));	
//			UNSAFE.putByte(m_memoryBase + p_ptr + 3, (byte) ((p_value >> 24) & 0xFF));	
//			UNSAFE.putByte(m_memoryBase + p_ptr + 4, (byte) ((p_value >> 32) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 5, (byte) ((p_value >> 40) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 6, (byte) ((p_value >> 48) & 0xFF));
//			UNSAFE.putByte(m_memoryBase + p_ptr + 7, (byte) ((p_value >> 56) & 0xFF));
//		}
		
		UNSAFE.putLong(m_memoryBase + p_ptr, p_value);
	}

	@Override
	public long readVal(final long p_ptr, final int p_count) {
		assert p_ptr >= 0;
		assert p_ptr + p_count <= m_memorySize;

		long val = 0;

		// take endianness into account!!!
		if (Endianness.getEndianness() > 0) {
			for (int i = 0; i < p_count; i++) {
				// work around not having unsigned data types and "wipe"
				// the sign by & 0xFF
				val |= ((long) (UNSAFE.getByte(m_memoryBase + p_ptr + i) & 0xFF)) << (8 * i);
			}
		} else {
			for (int i = 0; i < p_count; i++) {
				// work around not having unsigned data types and "wipe"
				// the sign by & 0xFF
				val |= ((long) (UNSAFE.getByte(m_memoryBase + p_ptr + i) & 0xFF)) << (8 * (7 - i));
			}
		}

		return val;
	}

	@Override
	public void writeVal(final long p_ptr, final long p_val, final int p_count) {
		assert p_ptr >= 0;
		assert p_ptr + p_count <= m_memorySize;

		// take endianness into account!!!
		if (Endianness.getEndianness() > 0) {
			for (int i = 0; i < p_count; i++) {
				UNSAFE.putByte(m_memoryBase + p_ptr + i, (byte) ((p_val >> (8 * i)) & 0xFF));
			}
		} else {
			for (int i = 0; i < p_count; i++) {
				UNSAFE.putByte(m_memoryBase + p_ptr + i, (byte) (p_val >> (8 * (7 - i)) & 0xFF));
			}
		}
	}
}
