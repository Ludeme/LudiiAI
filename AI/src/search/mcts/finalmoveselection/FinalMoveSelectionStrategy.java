package search.mcts.finalmoveselection;

import org.json.JSONObject;

import search.mcts.nodes.BaseNode;
import util.Move;

/**
 * Interface for different strategies of finally selecting the move to play in the real game
 * (after searching finished)
 * 
 * @author Dennis Soemers
 *
 */
public interface FinalMoveSelectionStrategy
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Should be implemented to select the move to play in the real game
	 * 
	 * @param rootNode
	 * @return The move.
	 */
	public Move selectMove(final BaseNode rootNode);

	//-------------------------------------------------------------------------
	
	/**
	 * Customize the final move selection strategy based on a list of given string inputs
	 * 
	 * @param inputs
	 */
	public void customize(final String[] inputs);

	//-------------------------------------------------------------------------
	
	/**
	 * @param json
	 * @return Final Move Selection strategy constructed from given JSON object
	 */
	public static FinalMoveSelectionStrategy fromJson(final JSONObject json)
	{
		FinalMoveSelectionStrategy selection = null;
		final String strategy = json.getString("strategy");
		
		if (strategy.equalsIgnoreCase("RobustChild"))
		{
			return new RobustChild();
		}
		
		return selection;
	}
	
	//-------------------------------------------------------------------------
	
}
