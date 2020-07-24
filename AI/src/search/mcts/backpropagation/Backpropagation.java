package search.mcts.backpropagation;

import java.util.ArrayList;
import java.util.List;

import search.mcts.nodes.BaseNode;
import search.mcts.nodes.BaseNode.MoveKey;
import search.mcts.nodes.BaseNode.NodeStatistics;
import util.Context;
import util.Move;

/**
 * Implements backpropagation of results for MCTS.
 * 
 * @author Dennis Soemers
 */
public final class Backpropagation
{
	
	//-------------------------------------------------------------------------
	
	/** Flags for things we have to backpropagate */
	public final int backpropFlags;
	
	/** AMAF stats per node for use by GRAVE (may be slightly different than stats used by RAVE/AMAF) */
	public final static int GRAVE_STATS		= 0x0001;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param backpropFlags
	 */
	public Backpropagation(final int backpropFlags)
	{
		this.backpropFlags = backpropFlags;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Updates the given node with statistics based on the given trial
	 * @param startNode
	 * @param context
	 * @param utilities
	 * @param numPlayoutMoves
	 */
	public void update
	(
		final BaseNode startNode, 
		final Context context, 
		final double[] utilities, 
		final int numPlayoutMoves
	)
	{
		BaseNode node = startNode;
		
		//System.out.println("utilities = " + Arrays.toString(utilities));
		final boolean updateGRAVE = ((backpropFlags & GRAVE_STATS) != 0);
		final List<MoveKey> moveKeysAMAF = new ArrayList<MoveKey>();
		final List<Move> trialMoves = context.trial().moves();
		final int numTrialMoves = trialMoves.size();
		int movesIdxAMAF = numTrialMoves - 1;
		
		if (updateGRAVE)
		{
			// collect all move keys for playout moves
			while (movesIdxAMAF >= (numTrialMoves - numPlayoutMoves))
			{
				moveKeysAMAF.add(new MoveKey(trialMoves.get(movesIdxAMAF), movesIdxAMAF));
				--movesIdxAMAF;
			}
		}
		
		while (node != null)
		{
			// TODO state evaluation function would be useful instead of
			// defaulting to 0 for unfinished games
			
			node.update(utilities);
			
			if (updateGRAVE)
			{
				for (final MoveKey moveKey : moveKeysAMAF)
				{
					final NodeStatistics graveStats = node.getOrCreateGraveStatsEntry(moveKey);
					//System.out.println("updating GRAVE stats in " + node + " for move: " + moveKey);
					graveStats.visitCount += 1;
					graveStats.accumulatedScore += utilities[context.state().playerToAgent(moveKey.move.mover())];
					
					// the below would be sufficient for RAVE, but for GRAVE we also need moves
					// made by the "incorrect" colour in higher-up nodes
					
					/*
					final int mover = moveKey.move.mover();
					if (nodeColour == 0 || nodeColour == mover)
					{
						final NodeStatistics graveStats = node.getOrCreateGraveStatsEntry(moveKey);
						graveStats.visitCount += 1;
						graveStats.accumulatedScore += utilities[mover];
					}*/
				}
				
				// we're going up one level, so also one more move to count as AMAF-move
				if (movesIdxAMAF >= 0)
				{
					moveKeysAMAF.add(new MoveKey(trialMoves.get(movesIdxAMAF), movesIdxAMAF));
					--movesIdxAMAF;
				}
			}
			
			node = node.parent();
		}
	}
	
	//-------------------------------------------------------------------------

}
