package playout_move_selectors;

import java.util.List;

import gnu.trove.list.array.TIntArrayList;
import main.collections.FVector;
import main.collections.FastArrayList;
import util.Context;
import util.FeatureSetInterface;
import util.Move;
import util.playout.PlayoutMoveSelector;

/**
 * PlayoutMoveSelector for playouts which uses a softmax over actions with logits
 * computed by features.
 *
 * @author Dennis Soemers
 */
public class FeaturesSoftmaxMoveSelector extends PlayoutMoveSelector		// TODO also a greedy version?
{
	
	//-------------------------------------------------------------------------
	
	/** Feature sets (one per player, or just a shared one at index 0) */
	protected final FeatureSetInterface[] featureSets;
	
	/** Weight vectors (one per player, or just a shared one at index 0) */
	protected final FVector[] weights;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param featureSets Feature sets (one per player, or just a shared one at index 0)
	 * @param weights Weight vectors (one per player, or just a shared one at index 0)
	 */
	public FeaturesSoftmaxMoveSelector
	(
		final FeatureSetInterface[] featureSets, 
		final FVector[] weights
	)
	{
		this.featureSets = featureSets;
		this.weights = weights;
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public Move selectMove
	(
		final Context context, 
		final FastArrayList<Move> maybeLegalMoves, 
		final int p,
		final IsMoveReallyLegal isMoveReallyLegal
	)
	{
		final FeatureSetInterface featureSet;
		final FVector weightVector;
		if (featureSets.length == 1)
		{
			featureSet = featureSets[0];
			weightVector = weights[0];
		}
		else
		{
			featureSet = featureSets[p];
			weightVector = weights[p];
		}

		final List<TIntArrayList> sparseFeatureVectors = 
				featureSet.computeSparseFeatureVectors(context, maybeLegalMoves, true);

		final float[] logits = new float[sparseFeatureVectors.size()];

		for (int i = 0; i < sparseFeatureVectors.size(); ++i)
		{
			logits[i] = weightVector.dotSparse(sparseFeatureVectors.get(i));
		}

		final FVector distribution = FVector.wrap(logits);
		distribution.softmax();
		
		int numLegalMoves = maybeLegalMoves.size();
		
		while (numLegalMoves > 0)
		{
			--numLegalMoves;	// We're trying a move; if this one fails, it's actually not legal
			
			final int n = distribution.sampleFromDistribution();
			final Move move = maybeLegalMoves.get(n);
			
			if (isMoveReallyLegal.checkMove(move))
				return move;	// Only return this move if it's really legal
			else
				distribution.updateSoftmaxInvalidate(n);	// Incrementally update the softmax, move n is invalid
		}
		
		// No legal moves?
		return null;
	}
	
	//-------------------------------------------------------------------------

}
