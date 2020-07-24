package utils.data_structures.transposition_table;

import java.lang.reflect.Array;

/**
 * Transposition tables containing elements of type E in every entry.
 *
 * @param <E> Type of data to store in every entry
 * 
 * @author Dennis Soemers
 */
public class TranspositionTable<E>
{
	
	//-------------------------------------------------------------------------
	
	/** Number of bits from hashes to use as primary coed */
	private final int numBitsPrimaryCode;
	
	/** Max number of entries for which we've allocated space */
	private final int maxNumEntries;
	
	/** Our table of entries */
	private E[] table;
	
	/** Our data type for entries */
	private final Class<E> dataType;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor.
	 * 
	 * Will allocate space for 2^(numBitsPrimaryCode) entries.
	 * 
	 * @param dataType Type of data to store in entries
	 * @param numBitsPrimaryCode Number of bits from hashes to use as primary code.
	 */
	@SuppressWarnings("unchecked")
	public TranspositionTable(final Class<E> dataType, final int numBitsPrimaryCode)
	{
		this.numBitsPrimaryCode = numBitsPrimaryCode;
		maxNumEntries = 1 << numBitsPrimaryCode;
		table = (E[]) Array.newInstance(dataType, maxNumEntries);
		this.dataType = dataType;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Clears all our data
	 */
	@SuppressWarnings("unchecked")
	public void clear()
	{
		table = (E[]) Array.newInstance(dataType, maxNumEntries);
	}
	
	/**
	 * @param fullHash
	 * @return Stored entry for given full hash (full 64bits code)
	 */
	public E retrieve(final long fullHash)
	{
		return table[(int) (fullHash >>> (Long.SIZE - numBitsPrimaryCode))];
	}
	
	/**
	 * Stores new entry for given full hash (full 64bits code)
	 * @param e
	 * @param fullHash
	 */
	public void store(final E e, final long fullHash)
	{
		table[(int) (fullHash >>> (Long.SIZE - numBitsPrimaryCode))] = e;
	}
	
	//-------------------------------------------------------------------------

}
