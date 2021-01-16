package search.mcts.selection;

import main.collections.FVector;
import search.mcts.nodes.BaseNode;
import search.mcts.utils.RegPolOptMCTS;

public class SearchRegPolOpt implements SelectionStrategy
{
	
	//-------------------------------------------------------------------------
	
	/** Exploration constant */
	protected double explorationConstant;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor with default exploration constant of 2.5.
	 */
	public SearchRegPolOpt()
	{
		this(2.5);
	}
	
	/**
	 * Constructor with custom exploration constant
	 * @param explorationConstant
	 */
	public SearchRegPolOpt(final double explorationConstant)
	{
		this.explorationConstant = explorationConstant;
	}
	
	//-------------------------------------------------------------------------

	@Override
	public int select(final BaseNode current)
	{
		final FVector distribution = RegPolOptMCTS.computePiBar(current, explorationConstant);
		return distribution.sampleProportionally();
	}

	@Override
	public int backpropFlags()
	{
		return 0;
	}

	@Override
	public void customise(String[] inputs)
	{
		// Nothing to do
	}
	
	//-------------------------------------------------------------------------

}
