package de.uniduesseldorf.dxram.core.chunk;

public interface DataStructureReader 
{
	public byte getByte(final long p_startAddress, final int p_offset);
	
	public short getShort(final long p_startAddress, final int p_offset);
	
	public int getInt(final long p_startAddress, final int p_offset);
	
	public long getLong(final long p_startAddress, final int p_offset);
	
	public int getBytes(final long p_startAddress, final int p_offset, final byte[] p_array, final int p_arrayOffset, int p_length);
}
