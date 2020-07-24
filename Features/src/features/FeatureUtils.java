package features;

import util.Move;

/**
 * Some utility methods related to features
 * 
 * @author Dennis Soemers
 */
public class FeatureUtils
{

	//-------------------------------------------------------------------------

	/**
	 * Private constructor, should not use
	 */
	private FeatureUtils()
	{
		// should not instantiate
	}

	//-------------------------------------------------------------------------

	/**
	 * @param move
	 * @return Extracts a from-position from an action
	 */
	public static int fromPos(final Move move)
	{
		if (move == null || move.isPass())
		{
			return -1;
		}
		
		int fromPos = move.fromNonDecision();
		
		if (fromPos == toPos(move))
		{
			fromPos = -1;
		}
		
		return fromPos;
	}

	/**
	 * @param move
	 * @return Extracts a to-position from an action
	 */
	public static int toPos(final Move move)
	{
		if (move == null || move.isPass())
		{
			return -1;
		}
		
		return move.toNonDecision();
	}

	//-------------------------------------------------------------------------

}
