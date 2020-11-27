package utils;

import game.Game;
import util.AI;
import util.Context;
import util.Move;

/**
 * AI player doing nothing.
 * 
 * @author Eric.Piette
 */
public class BaseAI extends AI
{

	//-------------------------------------------------------------------------
	
	/** Our player index */
	protected int player = -1;
	
	/** The last move we returned */
	protected Move lastReturnedMove = null;
	
	//-------------------------------------------------------------------------

	/**
	 * Constructor
	 */
	public BaseAI()
	{
		friendlyName = "Base";
	}

	//-------------------------------------------------------------------------

	@Override
	public Move selectAction
	(
		final Game game, 
		final Context context, 
		final double maxSeconds,
		final int maxIterations,
		final int maxDepth
	)
	{
		return null;
	}
	
	/**
	 * @return The last move we returned
	 */
	public Move lastReturnedMove()
	{
		return lastReturnedMove;
	}
	
	@Override
	public void initAI(final Game game, final int playerID)
	{
		this.player = playerID;
		lastReturnedMove = null;
	}
	
	//-------------------------------------------------------------------------

}
