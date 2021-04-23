package expert_iteration.feature_discovery;

import java.io.PrintWriter;

import expert_iteration.ExItExperience;
import expert_iteration.params.ObjectiveParams;
import features.feature_sets.BaseFeatureSet;
import features.spatial.SpatialFeature;
import features.spatial.instances.FeatureInstance;
import game.Game;
import gnu.trove.list.array.TDoubleArrayList;
import policies.softmax.SoftmaxPolicy;
import utils.experiments.InterruptableExperiment;

/**
 * Interface for an object that can create expanded versions of feature sets.
 *
 * @author Dennis Soemers
 */
public interface FeatureSetExpander
{
	
	/**
	 * @param batch Batch of samples of experience
	 * @param featureSet Feature set to expand
	 * @param policy Current policy with current weights for predictions 
	 * @param game
	 * @param featureDiscoveryMaxNumFeatureInstances
	 * @param fActiveRatios
	 * @param objectiveParams
	 * @param logWriter
	 * @param experiment Experiment in which this is being used
	 * @return Expanded version of given feature set, or null if no expanded version
	 * 	was constructed.
	 */
	public BaseFeatureSet expandFeatureSet
	(
		final ExItExperience[] batch,
		final BaseFeatureSet featureSet,
		final SoftmaxPolicy policy,
		final Game game,
		final int featureDiscoveryMaxNumFeatureInstances,
		final TDoubleArrayList fActiveRatios,
		final ObjectiveParams objectiveParams,
		final PrintWriter logWriter,
		final InterruptableExperiment experiment
	);
	
	//-----------------------------------------------------------------
	
	/**
	 * Wrapper class for a pair of combined feature instances and a score
	 * 
	 * @author Dennis Soemers
	 */
	public static final class ScoredFeatureInstancePair
	{
		/** First int */
		public final CombinableFeatureInstancePair pair;

		/** Score */
		public final double score;

		/**
		 * Constructor
		 * @param pair
		 * @param score
		 */
		public ScoredFeatureInstancePair(final CombinableFeatureInstancePair pair, final double score)
		{
			this.pair = pair;
			this.score = score;
		}
	}

	//-----------------------------------------------------------------

	/**
	 * Wrapper class for two feature instances that could be combined, with
	 * hashCode() and equals() implementations that should be invariant to
	 * small differences in instantiations (such as different anchor positions)
	 * that would result in equal combined features.
	 * 
	 * @author Dennis Soemers
	 */
	final class CombinableFeatureInstancePair
	{
		/** First feature instance */
		public final FeatureInstance a;

		/** Second feature instance */
		public final FeatureInstance b;

		/** Feature obtained by combining the two instances */
		protected final SpatialFeature combinedFeature;

		/** Cached hash code */
		private int cachedHash = Integer.MIN_VALUE;

		/**
		 * Constructor
		 * @param game
		 * @param a
		 * @param b
		 */
		public CombinableFeatureInstancePair
		(
			final Game game,
			final FeatureInstance a, 
			final FeatureInstance b
		)
		{
			this.a = a;
			this.b = b;

			// we don't just arbitrarily combine a with b, but want to make
			// sure to do so in a consistent, reproducible order

			if (a.feature().featureSetIndex() < b.feature().featureSetIndex())
			{
				combinedFeature = SpatialFeature.combineFeatures(game, a, b);
			}
			else if (b.feature().featureSetIndex() < a.feature().featureSetIndex())
			{
				combinedFeature = SpatialFeature.combineFeatures(game, b, a);
			}
			else
			{
				if (a.reflection() > b.reflection())
				{
					combinedFeature = SpatialFeature.combineFeatures(game, a, b);
				}
				else if (b.reflection() > a.reflection())
				{
					combinedFeature = SpatialFeature.combineFeatures(game, b, a);
				}
				else
				{
					if (a.rotation() < b.rotation())
					{
						combinedFeature = SpatialFeature.combineFeatures(game, a, b);
					}
					else if (b.rotation() < a.rotation())
					{
						combinedFeature = SpatialFeature.combineFeatures(game, b, a);
					}
					else
					{
						if (a.anchorSite() < b.anchorSite())
							combinedFeature = SpatialFeature.combineFeatures(game, a, b);
						else if (b.anchorSite() < a.anchorSite())
							combinedFeature = SpatialFeature.combineFeatures(game, b, a);
						else
							combinedFeature = SpatialFeature.combineFeatures(game, a, b);
					}
				}
			}
		}

		@Override
		public boolean equals(final Object other)
		{
			if (!(other instanceof CombinableFeatureInstancePair))
				return false;

			return combinedFeature.equals(((CombinableFeatureInstancePair) other).combinedFeature);
		}

		@Override
		public int hashCode()
		{
			if (cachedHash == Integer.MIN_VALUE)
				cachedHash = combinedFeature.hashCode();

			return cachedHash;
		}

		@Override
		public String toString()
		{
			return combinedFeature + " (from " + a + " and " + b + ")";
		}
	}

}
