package de.hhu.bsinfo.dxgraph.algo.bfs.front;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ConcurrentBitVector implements FrontierList
{
	private AtomicLongArray m_vector = null;		
	
	private AtomicInteger m_itPos = new AtomicInteger(0);
	private AtomicInteger m_itBit = new AtomicInteger(0);
	
	private AtomicLong m_count = new AtomicLong(0);
	
	public ConcurrentBitVector(final long p_vertexCount)
	{
		m_vector = new AtomicLongArray((int) ((p_vertexCount / 64L) + 1L));
	}
	
	@Override
	public void pushBack(final long p_index)
	{
		long tmp = (1L << (p_index % 64L));
		int index = (int) (p_index / 64L);
		
		while (true)
		{
			long val = m_vector.get(index);
			if ((val & tmp) == 0)
			{				
				if (!m_vector.compareAndSet(index, val, val | tmp))
					continue;
				m_count.incrementAndGet();
			}
			
			break;
		}
	}
	
	@Override
	public long size()
	{
		return m_count.get();
	}
	
	@Override
	public boolean isEmpty()
	{
		return m_count.get() == 0;
	}
	
	@Override
	public void reset()
	{
		m_itPos.set(0);
		m_itBit.set(0);
		m_count.set(0);
		for (int i = 0; i < m_vector.length(); i++) {
			m_vector.set(i, 0);
		}
	}
	
	@Override
	public long popFront()
	{
		while (m_count.get() > 0)
		{
			int itPos = m_itPos.get();
			if (m_vector.get(itPos) != 0)
			{
				int itBit = m_itBit.get();
				while (itBit < 64L)
				{
					if (((m_vector.get(itPos) >> itBit) & 1L) != 0)
					{
						long ret = itPos * 64L + itBit;
						if (itPos != m_itPos.get()) {
							itPos = m_itPos.get();
							itBit = m_itBit.get();
							continue;
						} else {
							if (!m_itBit.compareAndSet(itBit, itBit + 1)) {
								itBit = m_itBit.get();
								continue;
							} else {
								m_count.decrementAndGet();
								return ret;
							}
						}
						
					}
					
					if (!m_itBit.compareAndSet(itBit, itBit + 1)) {
						itBit = m_itBit.get();
					}
				}
				
				if (!m_itBit.compareAndSet(itBit, 0)) {
					itBit = m_itBit.get();
				}
			}
			
			if (!m_itPos.compareAndSet(itPos, itPos + 1)) {
				continue;
			}
		}
		
		return -1;
	}
}
