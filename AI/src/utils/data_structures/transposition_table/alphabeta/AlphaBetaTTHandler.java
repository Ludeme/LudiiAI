package utils.data_structures.transposition_table.alphabeta;

import util.Move;

/**
 * Interface for handling interaction between Alpha-Beta Search and Transposition Tables
 * 
 * @author Dennis Soemers
 */
public interface AlphaBetaTTHandler
{

	/**
	 * Data we wish to store in TT entries for Alpha-Beta Search
	 * 
	 * @author Dennis Soemers
	 */
	public static final class ABTTData
	{
		
		/** The best move according to previous AB search */
		public Move bestMove = null;
		
		/** Full 64bits hash code for which this data was stored */
		public long fullHash = -1L;
		
		// TODO
		
	}
	
}
