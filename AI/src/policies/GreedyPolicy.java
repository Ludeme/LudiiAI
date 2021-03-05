package policies;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import features.feature_sets.BaseFeatureSet;
import features.feature_sets.FeatureSet;
import function_approx.BoostedLinearFunction;
import function_approx.LinearFunction;
import game.Game;
import game.rules.play.moves.Moves;
import gnu.trove.list.array.TIntArrayList;
import main.collections.FVector;
import main.collections.FastArrayList;
import playout_move_selectors.FeaturesSoftmaxMoveSelector;
import search.mcts.MCTS;
import util.Context;
import util.Move;
import util.Trial;
import utils.ExperimentFileUtils;

/**
 * A greedy policy (plays greedily according to estimates by a linear function
 * approximator).
 * 
 * @author Dennis Soemers and cambolbro
 */
public class GreedyPolicy extends Policy 
{
	
	//-------------------------------------------------------------------------
	
	/** 
	 * Linear function approximators (can output one logit per action) 
	 * 
	 * If it contains only one function, it will be shared across all
	 * players. Otherwise, it will contain one function per player.
	 */
	protected LinearFunction[] linearFunctions;
	
	/** 
	 * Feature Sets to use to generate feature vectors for state+action pairs.
	 * 
	 * If it contains only one feature set, it will be shared across all
	 * players. Otherwise, it will contain one Feature Set per player.
	 */
	protected BaseFeatureSet[] featureSets;
	
	/** Auto-end playouts in a draw if they take more turns than this */
	protected int playoutTurnLimit = 200;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Default constructor. Will initialize important parts to null and break 
	 * down if used directly. Should customize() it first!
	 */
	public GreedyPolicy()
	{
		linearFunctions = null;
		featureSets = null;
	}
	
	/**
	 * Constructs a greedy policy with linear function approximators
	 * @param linearFunctions
	 * @param featureSets
	 */
	public GreedyPolicy
	(
		final LinearFunction[] linearFunctions, 
		final FeatureSet[] featureSets
	)
	{
		this.linearFunctions = linearFunctions;
		this.featureSets = featureSets;
	}
	
	//-------------------------------------------------------------------------

	@Override
	public FVector computeDistribution
	(
		final Context context, 
		final FastArrayList<Move> actions,
		final boolean thresholded
	)
	{
		final BaseFeatureSet featureSet;
		
		if (featureSets.length == 1)
		{
			featureSet = featureSets[0];
		}
		else
		{
			featureSet = featureSets[context.state().mover()];
		}
		
		return computeDistribution
				(
					featureSet.computeSparseFeatureVectors(context, actions, thresholded),
					context.state().mover()
				);
	}
	
	@Override
	public float computeLogit(final Context context, final Move move)
	{
		final BaseFeatureSet featureSet;
		
		if (featureSets.length == 1)
		{
			featureSet = featureSets[0];
		}
		else
		{
			featureSet = featureSets[context.state().mover()];
		}
		
		final LinearFunction linearFunction;
		
		if (linearFunctions.length == 1)
		{
			linearFunction = linearFunctions[0];
		}
		else
		{
			linearFunction = linearFunctions[context.state().mover()];
		}
		
		final FastArrayList<Move> wrappedMove = new FastArrayList<Move>(1);
		wrappedMove.add(move);
		return linearFunction.predict(featureSet.computeSparseFeatureVectors(context, wrappedMove, true).get(0));
	}
	
	/**
	 * @param sparseFeatureVectors
	 * @param player
	 * @return Logits for the actions implied by a list of 
	 * sparse feature vectors.
	 */
	public float[] computeLogits
	(
		final List<TIntArrayList> sparseFeatureVectors,
		final int player
	)
	{
		final float[] logits = new float[sparseFeatureVectors.size()];
		final LinearFunction linearFunction;
		
		if (linearFunctions.length == 1)
		{
			linearFunction = linearFunctions[0];
		}
		else
		{
			linearFunction = linearFunctions[player];
		}
		
		for (int i = 0; i < sparseFeatureVectors.size(); ++i)
		{
			logits[i] = linearFunction.predict(sparseFeatureVectors.get(i));
		}
		
		return logits;
	}
	
	/**
	 * @param sparseFeatureVectors One sparse feature vector per action
	 * @param player Player for which to use features
	 * 
	 * @return Probability distribution over actions implied by a list of sparse 
	 * feature vectors
	 */
	public FVector computeDistribution
	(
		final List<TIntArrayList> sparseFeatureVectors,
		final int player
	)
	{
		final float[] logits = computeLogits(sparseFeatureVectors, player);
		
		float maxLogit = Float.NEGATIVE_INFINITY;
		final TIntArrayList maxLogitIndices = new TIntArrayList();
		
		for (int i = 0; i < logits.length; ++i)
		{
			final float logit = logits[i];
			
			if (logit > maxLogit)
			{
				maxLogit = logit;
				maxLogitIndices.reset();
				maxLogitIndices.add(i);
			}
			else if (logit == maxLogit)
			{
				maxLogitIndices.add(i);
			}
		}
		
		// this is the probability we assign to all max logits
		final float maxProb = 1.f / maxLogitIndices.size();
		
		// now create the distribution
		final FVector distribution = new FVector(logits.length);
		for (int i = 0; i < maxLogitIndices.size(); ++i)
		{
			distribution.set(maxLogitIndices.getQuick(i), maxProb);
		}

		return distribution;
	}

	//-------------------------------------------------------------------------
	
	@Override
	public Trial runPlayout(final MCTS mcts, final Context context) 
	{
		final FVector[] params = new FVector[linearFunctions.length];
		for (int i = 0; i < linearFunctions.length; ++i)
		{
			if (linearFunctions[i] == null)
			{
				params[i] = null;
			}
			else
			{
				params[i] = linearFunctions[i].effectiveParams();
			}
		}
		
		return context.game().playout
				(
					context, 
					null, 
					1.0, 
					new FeaturesSoftmaxMoveSelector(featureSets, params), 
					-1,
					playoutTurnLimit,
					ThreadLocalRandom.current()
				);
	}
	
	@Override
	public boolean playoutSupportsGame(final Game game)
	{
		return supportsGame(game);
	}
	
	@Override
	public int backpropFlags()
	{
		return 0;
	}

	@Override
	public void customise(final String[] inputs) 
	{
		// TODO allow reading multiple different weight filepaths
		String policyWeightsFilepath = null;
		boolean boosted = false;
		
		for (int i = 1; i < inputs.length; ++i)
		{
			final String input = inputs[i];
			
			if (input.toLowerCase().startsWith("policyweights="))
			{
				policyWeightsFilepath = 
						input.substring("policyweights=".length());
			}
			else if (input.toLowerCase().startsWith("playoutturnlimit="))
			{
				playoutTurnLimit = 
						Integer.parseInt(
								input.substring("playoutturnlimit=".length()));
			}
			else if (input.toLowerCase().startsWith("friendly_name="))
			{
				friendlyName = 
						input.substring("friendly_name=".length());
			}
			else if (input.toLowerCase().startsWith("boosted="))
			{
				if (input.toLowerCase().endsWith("true"))
				{
					boosted = true;
				}
			}
		}
		
		if (policyWeightsFilepath != null)
		{
			final String parentDir = 
					new File(policyWeightsFilepath).getParent();
			
			if (! new File(policyWeightsFilepath).exists())
			{
				// replace with whatever is the latest file we have
				policyWeightsFilepath = 
						ExperimentFileUtils.getLastFilepath(
								parentDir + "/PolicyWeights", "txt");
			}
			
			if (boosted)
			{
				this.linearFunctions = new LinearFunction[]{
						BoostedLinearFunction.boostedFromFile(policyWeightsFilepath, null)
				};
			}
			else
			{
				this.linearFunctions = new LinearFunction[]{
						LinearFunction.fromFile(policyWeightsFilepath)
				};
			}
						
			this.featureSets = new FeatureSet[linearFunctions.length];
			
			for (int i = 0; i < linearFunctions.length; ++i)
			{
				if (linearFunctions[i] != null)
				{
					featureSets[i] = new FeatureSet(parentDir + File.separator + linearFunctions[i].featureSetFile());
				}
			}
		}
		else
		{
			System.err.println("Cannot construct Greedy Policy from: "
					+ Arrays.toString(inputs));
		}
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
		final Moves actions = game.moves(context);
		final BaseFeatureSet featureSet;
		
		if (featureSets.length == 1)
		{
			featureSet = featureSets[0];
		}
		else
		{
			featureSet = featureSets[context.state().mover()];
		}
		
		return actions.moves().get(FVector.wrap
				(
					computeLogits
					(
						featureSet.computeSparseFeatureVectors
						(
							context, 
							actions.moves(),
							true
						),
						context.state().mover()
					)
				).argMaxRand());
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param lines
	 * @return A greedy policy constructed from a given array of input lines
	 */
	public static GreedyPolicy fromLines(final String[] lines)
	{
		final GreedyPolicy policy = new GreedyPolicy();
		policy.customise(lines);
		return policy;
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public void initAI(final Game game, final int playerID)
	{
		if (featureSets.length == 1)
		{
			final int[] supportedPlayers = new int[game.players().count()];
			for (int i = 0; i < supportedPlayers.length; ++i)
			{
				supportedPlayers[i] = i + 1;
			}
			
			featureSets[0].init(game, supportedPlayers, linearFunctions[0].effectiveParams());
		}
		else
		{
			for (int i = 1; i < featureSets.length; ++i)
			{
				featureSets[i].init(game, new int[] {i}, linearFunctions[i].effectiveParams());
			}
		}
	}
	
	//-------------------------------------------------------------------------

}
