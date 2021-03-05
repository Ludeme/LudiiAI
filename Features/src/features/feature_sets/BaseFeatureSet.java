package features.feature_sets;

import java.util.List;

import features.features.Feature;
import features.instances.FeatureInstance;
import game.Game;
import gnu.trove.list.array.TIntArrayList;
import main.collections.FVector;
import main.collections.FastArrayList;
import util.Context;
import util.Move;
import util.state.State;

/**
 * Abstract class for Feature Sets (basically; things that can compute feature
 * vectors for game states + actions).
 * 
 * @author Dennis Soemers
 */
public abstract class BaseFeatureSet
{
	
	//-------------------------------------------------------------------------
	
	/** Only features with an absolute value greater than this are considered relevant for AI */
	public static final float FEATURE_WEIGHT_THRESHOLD = 0.001f;
	
	//-------------------------------------------------------------------------
	
	/** Array of features */
	protected Feature[] features;
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return The array of features contained in this feature set
	 */
	public final Feature[] features()
	{
		return features;
	}
	
	/**
	 * @return The number of features in this feature set
	 */
	public final int getNumFeatures()
	{
		return features.length;
	}
	
	/**
	 * Lets the feature set init itself for a given game, array of supported players, and vector of weights
	 * (for example, can instantiate features here).
	 * @param newGame
	 * @param supportedPlayers
	 * @param weights
	 */
	public abstract void init(final Game newGame, final int[] supportedPlayers, final FVector weights);
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param context
	 * @param actions
	 * @param thresholded
	 * 
	 * @return A list of sparse feature vectors 
	 * (one for every given action in the given trial)
	 */
	public List<TIntArrayList> computeSparseFeatureVectors
	(
		final Context context,
		final FastArrayList<Move> actions,
		final boolean thresholded
	)
	{
		return computeSparseFeatureVectors(context.state(), context.trial().lastMove(), actions, thresholded);
	}
	
	/**
	 * @param state
	 * @param lastDecisionMove
	 * @param actions
	 * @param thresholded
	 * 
	 * @return A list of sparse feature vectors 
	 * (one for every given action in the given trial)
	 */
	public abstract List<TIntArrayList> computeSparseFeatureVectors
	(
		final State state, 
		final Move lastDecisionMove,
		final FastArrayList<Move> actions,
		final boolean thresholded
	);
	
	/**
	 * @param state 
	 * @param lastFrom
	 * @param lastTo
	 * @param from
	 * @param to
	 * @param player
	 * @param thresholded
	 * 
	 * @return A list of indices of all the features that are active for a 
	 * given state+action pair (where action is defined by from and 
	 * to positions)
	 */
	public abstract TIntArrayList getActiveFeatureIndices
	(
		final State state, 
		final int lastFrom, 
		final int lastTo, 
		final int from, 
		final int to,
		final int player,
		final boolean thresholded
	);
	
	/**
	 * @param state
	 * @param lastFrom
	 * @param lastTo
	 * @param from
	 * @param to
	 * @param player
	 * @return A list of all feature instances that are active for a given 
	 * state+action pair (where action is defined by from and to positions)
	 */
	public abstract List<FeatureInstance> getActiveFeatureInstances
	(
		final State state, 
		final int lastFrom, 
		final int lastTo, 
		final int from, 
		final int to,
		final int player
	);
	
	/**
	 * 
	 * @param state
	 * @param lastFrom
	 * @param lastTo
	 * @param from
	 * @param to
	 * @param autoPlayThreshold
	 * @param weightVector
	 * @param player
	 * @param thresholded
	 * 
	 * @return Computes logit for the given state+action pair. Attempts
	 * to return quickly by immediately returning if the auto-play threshold
	 * is exceeded
	 */
	public abstract float computeLogitFastReturn
	(
		final State state, 
		final int lastFrom, 
		final int lastTo, 
		final int from, 
		final int to,
		final float autoPlayThreshold,
		final FVector weightVector,
		final int player,
		final boolean thresholded
	);
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param targetGame
	 * @param firstFeatureInstance
	 * @param secondFeatureInstance
	 * @return Expanded feature set with the two given feature instances
	 * combined, or null in the case of failure.
	 */
	public abstract FeatureSet createExpandedFeatureSet
	(
		final Game targetGame,
		final FeatureInstance firstFeatureInstance,
		final FeatureInstance secondFeatureInstance
	);
	
	//-------------------------------------------------------------------------
	
	/**
	 * Writes the feature set to a file
	 * @param filepath Filepath to write to
	 */
	public abstract void toFile(final String filepath);
	
	//-------------------------------------------------------------------------

}
