package features.feature_sets;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import features.Feature;
import features.aspatial.AspatialFeature;
import features.spatial.FeatureUtils;
import features.spatial.SpatialFeature;
import features.spatial.cache.footprints.BaseFootprint;
import features.spatial.instances.FeatureInstance;
import game.Game;
import gnu.trove.list.array.TIntArrayList;
import main.collections.FVector;
import main.collections.FastArrayList;
import other.context.Context;
import other.move.Move;
import other.state.State;

/**
 * Abstract class for Feature Sets (basically; things that can compute feature
 * vectors for game states + actions).
 * 
 * @author Dennis Soemers
 */
public abstract class BaseFeatureSet
{
	
	//-------------------------------------------------------------------------
	
	/** Only spatial features with an absolute value greater than this are considered relevant for AI */
	public static final float SPATIAL_FEATURE_WEIGHT_THRESHOLD = 0.001f;
	
	/** Reference to game for which we currently have instantiated features */
	protected WeakReference<Game> game = new WeakReference<>(null);
	
	/** Vector of spatial feature weights for which we have last instantiated features */
	protected FVector spatialFeatureInitWeights = null;
	
	//-------------------------------------------------------------------------
	
	/** Array of aspatial features */
	protected AspatialFeature[] aspatialFeatures;
	
	/** Array of features */
	protected SpatialFeature[] spatialFeatures;
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return The array of spatial features contained in this feature set
	 */
	public final SpatialFeature[] spatialFeatures()
	{
		return spatialFeatures;
	}
	
	/**
	 * @return The number of spatial features in this feature set
	 */
	public final int getNumSpatialFeatures()
	{
		return spatialFeatures.length;
	}
	
	/**
	 * Lets the feature set initialise itself for a given game, array of supported players, and vector of weights
	 * (for example, can instantiate features here).
	 * @param newGame
	 * @param supportedPlayers
	 * @param weights
	 */
	public void init(final Game newGame, final int[] supportedPlayers, final FVector weights)
	{
		if (this.game.get() == newGame)
		{
			if (this.spatialFeatureInitWeights == null && weights == null)
				return;		// Nothing to do, already instantiated
			else if (this.spatialFeatureInitWeights != null && this.spatialFeatureInitWeights.equals(weights))
				return;		// Also nothing to do here
		}
		
		this.game = new WeakReference<>(newGame);
		
		if (weights == null)
			spatialFeatureInitWeights = null;
		else
			spatialFeatureInitWeights = new FVector(weights);
		
		// Need to instantiate
		instantiateFeatures(supportedPlayers);
	}
	
	/**
	 * Lets the feature set instantiate its features
	 * @param supportedPlayers
	 */
	protected abstract void instantiateFeatures(final int[] supportedPlayers);
	
	/**
	 * @param state
	 * @param from
	 * @param to
	 * @param player
	 * @return The complete footprint of all tests that may possibly be run
	 * for computing proactive features of actions with the given from- and to- 
	 * positions.
	 */
	public abstract BaseFootprint generateFootprint
	(
		final State state, 
		final int from, 
		final int to,
		final int player
	);
	
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
	public List<TIntArrayList> computeSparseFeatureVectors
	(
		final State state, 
		final Move lastDecisionMove,
		final FastArrayList<Move> actions,
		final boolean thresholded
	)
	{
		final List<TIntArrayList> sparseFeatureVectors = 
				new ArrayList<TIntArrayList>(actions.size());
		
		for (final Move move : actions)
		{
			final int lastFrom = FeatureUtils.fromPos(lastDecisionMove);
			final int lastTo = FeatureUtils.toPos(lastDecisionMove);
			final int from = FeatureUtils.fromPos(move);
			final int to = FeatureUtils.toPos(move);
			
//			System.out.println("last decision move = " + trial.lastDecisionMove());
//			System.out.println("lastFrom = " + lastFrom);
//			System.out.println("lastTo = " + lastTo);
//			System.out.println("from = " + from);
//			System.out.println("to = " + to);
			
			final TIntArrayList sparseFeatureVector = 
					getActiveFeatureIndices(state, lastFrom, lastTo, from, to, move.mover(), thresholded);
			
			sparseFeatureVectors.add(sparseFeatureVector);
		}
		
		return sparseFeatureVectors;
	}
	
	/**
	 * @param context
	 * @param move
	 * @return List of all active features for given move in given context (spatial and aspatial ones).
	 * 	Non-binary features are considered to be active if their value is not equal to 0
	 */
	public List<Feature> computeActiveFeatures(final Context context, final Move move)
	{
		final List<Feature> activeFeatures = new ArrayList<Feature>();
		
		// Compute and add spatial features
		final Move lastDecisionMove = context.trial().lastMove();
		final int lastFrom = FeatureUtils.fromPos(lastDecisionMove);
		final int lastTo = FeatureUtils.toPos(lastDecisionMove);
		final int from = FeatureUtils.fromPos(move);
		final int to = FeatureUtils.toPos(move);
		final TIntArrayList activeSpatialFeatureIndices = 
				getActiveFeatureIndices
				(
					context.state(),
					lastFrom, lastTo,
					from, to,
					move.mover(),
					false
				);
		
		for (int i = 0; i < activeSpatialFeatureIndices.size(); ++i)
		{
			activeFeatures.add(spatialFeatures[activeSpatialFeatureIndices.getQuick(i)]);
		}
		
		// Compute and add active aspatial features
		for (final AspatialFeature feature : aspatialFeatures)
		{
			if (feature.featureVal(context.state(), move) != 0.f)
				activeFeatures.add(feature);
		}
		
		return activeFeatures;
	}
	
	//-------------------------------------------------------------------------
	
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
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param targetGame
	 * @param newFeature
	 * @return Expanded feature set with the new feature added, or null in the case of failure.
	 */
	public abstract BaseFeatureSet createExpandedFeatureSet
	(
		final Game targetGame,
		final SpatialFeature newFeature
	);
	
	//-------------------------------------------------------------------------
	
	/**
	 * Writes the feature set to a file
	 * @param filepath Filepath to write to
	 */
	public void toFile(final String filepath)
	{
		try (final PrintWriter writer = new PrintWriter(filepath, "UTF-8"))
		{
			for (final SpatialFeature feature : spatialFeatures)
			{
				writer.println(feature);
			}
		} 
		catch (final FileNotFoundException | UnsupportedEncodingException e) 
		{
			e.printStackTrace();
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Small class for objects used as keys in HashMaps related to proactive 
	 * features. All fields are final, which enables us to pre-compute and cache
	 * the full hashCode in constructor.
	 * 
	 * @author Dennis Soemers
	 */
	public static class ProactiveFeaturesKey
	{
		//--------------------------------------------------------------------
		
		/** Player index */
		private final int playerIdx;
		
		/** from-position */
		private final int from;
		
		/** to-position */
		private final int to;
		
		/** Cached hash code */
		private transient final int cachedHashCode;
		
		//--------------------------------------------------------------------
		
		/**
		 * Constructor
		 * @param playerIdx
		 * @param from
		 * @param to
		 */
		public ProactiveFeaturesKey
		(
			final int playerIdx,
			final int from, 
			final int to
		)
		{
			this.playerIdx = playerIdx;
			this.from = from;
			this.to = to;
			
			// create and cache hash code
			final int prime = 31;
			int result = 17;
			result = prime * result + from;
			result = prime * result + playerIdx;
			result = prime * result + to;
			cachedHashCode = result;
		}
		
		//--------------------------------------------------------------------
		
		@Override
		public int hashCode()
		{
			return cachedHashCode;
		}

		@Override
		public boolean equals(final Object obj)
		{
			if (this == obj)
				return true;

			if (!(obj instanceof ProactiveFeaturesKey))
				return false;
			
			final ProactiveFeaturesKey other = (ProactiveFeaturesKey) obj;
			
			return (playerIdx == other.playerIdx &&
					from == other.from &&
					to == other.to);
		}
		
		@Override
		public String toString()
		{
			return "[ProactiveFeaturesKey: " + playerIdx + ", " + from + ", " + to + "]";
		}
		
		//--------------------------------------------------------------------
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Small class for objects used as keys in HashMaps related to reactive 
	 * features. All fields are final, which enables us to pre-compute and cache
	 * the full hashCode in constructor.
	 * 
	 * @author Dennis Soemers
	 */
	public static class ReactiveFeaturesKey
	{
		//--------------------------------------------------------------------
		
		/** Player index */
		private final int playerIdx;
		
		/** Last from-position */
		private final int lastFrom;
		
		/** Last to-position */
		private final int lastTo;
		
		/** from-position */
		private final int from;
		
		/** to-position */
		private final int to;
		
		/** Cached hash code */
		private transient final int cachedHashCode;
		
		//--------------------------------------------------------------------
		
		/**
		 * Constructor
		 * @param playerIdx
		 * @param lastFrom
		 * @param lastTo
		 * @param from
		 * @param to
		 */
		public ReactiveFeaturesKey
		(
			final int playerIdx, 
			final int lastFrom, 
			final int lastTo,
			final int from, 
			final int to
		)
		{
			this.playerIdx = playerIdx;
			this.lastFrom = lastFrom;
			this.lastTo = lastTo;
			this.from = from;
			this.to = to;
			
			// create and cache hash code
			final int prime = 31;
			int result = 17;
			result = prime * result + from;
			result = prime * result + lastFrom;
			result = prime * result + lastTo;
			result = prime * result + playerIdx;
			result = prime * result + to;
			cachedHashCode = result;
		}
		
		//--------------------------------------------------------------------
		
		@Override
		public int hashCode()
		{
			return cachedHashCode;
		}

		@Override
		public boolean equals(final Object obj)
		{
			if (this == obj)
				return true;

			if (!(obj instanceof ReactiveFeaturesKey))
				return false;
			
			final ReactiveFeaturesKey other = (ReactiveFeaturesKey) obj;
			
			return (playerIdx == other.playerIdx &&
					lastFrom == other.lastFrom &&
					lastTo == other.lastTo &&
					from == other.from &&
					to == other.to);
		}
		
		//--------------------------------------------------------------------
	}
	
	//-------------------------------------------------------------------------

}
