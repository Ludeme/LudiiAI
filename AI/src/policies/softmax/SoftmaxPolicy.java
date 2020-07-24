package policies.softmax;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import features.FeatureSet;
import features.features.Feature;
import function_approx.BoostedLinearFunction;
import function_approx.LinearFunction;
import game.Game;
import game.rules.play.moves.Moves;
import game.types.play.RoleType;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import main.Constants;
import main.collections.FVector;
import main.collections.FastArrayList;
import metadata.ai.features.Features;
import metadata.ai.misc.Pair;
import policies.Policy;
import util.Context;
import util.Move;
import util.Trial;
import utils.ExperimentFileUtils;

/**
 * A policy which:
 * 	- Uses a linear function approximator to compute one logit per action.
 * 	- Uses softmax to compute a probability distribution from those logits.
 * 	- Selects actions according to the softmax distribution.
 * 
 * We extend the AI abstract class, which means this policy can also be used as 
 * a full agent (even though that's not the primary intended use).
 * 
 * Similarly, we implement the PlayoutStrategy interface, so the policy can also
 * be plugged into MCTS directly as a playout strategy.
 * 
 * @author Dennis Soemers
 */
public class SoftmaxPolicy extends Policy 
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
	protected FeatureSet[] featureSets;
	
	/** 
	 * If >= 0, we'll only actually use this softmax policy in MCTS play-outs
	 * for up to this many actions. If a play-out still did not terminate
	 * after this many play-out actions, we revert to a random play-out
	 * strategy as fallback
	 */
	protected int playoutActionLimit = -1;
	
	/** Auto-end playouts in a draw if they take more turns than this */
	protected int playoutTurnLimit = -1;
	
	/** If >= 0, moves in playouts will be played immediately once their feature weights exceed this threshold */
	protected float autoPlayThreshold = -1.f;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Default constructor. Will initialise important parts to null and break 
	 * down if used directly. Should customise() it first!
	 */
	public SoftmaxPolicy()
	{
		linearFunctions = null;
		featureSets = null;
	}
	
	/**
	 * Constructs a softmax policy with a linear function approximator
	 * @param linearFunctions
	 * @param featureSets
	 */
	public SoftmaxPolicy
	(
		final LinearFunction[] linearFunctions, 
		final FeatureSet[] featureSets
	)
	{
		this.linearFunctions = linearFunctions;
		this.featureSets = Arrays.copyOf(featureSets, featureSets.length);
	}
	
	/**
	 * Constructs a softmax policy with a linear function approximator,
	 * and a limit on the number of play-out actions to run with this policy
	 * plus a fallback Play-out strategy to use afterwards.
	 * 
	 * @param linearFunctions
	 * @param featureSets
	 * @param playoutActionLimit
	 */
	public SoftmaxPolicy
	(
		final LinearFunction[] linearFunctions, 
		final FeatureSet[] featureSets,
		final int playoutActionLimit
	)
	{
		this.linearFunctions = linearFunctions;
		this.featureSets = Arrays.copyOf(featureSets, featureSets.length);
		this.playoutActionLimit = playoutActionLimit;
	}
	
	/**
	 * Constructs a softmax policy from a given set of features as created
	 * by the compiler.
	 * 
	 * @param features
	 */
	public SoftmaxPolicy(final Features features)
	{
		final List<FeatureSet> featureSetsList = new ArrayList<FeatureSet>();
		final List<LinearFunction> linFuncs = new ArrayList<LinearFunction>();
				
		for (final metadata.ai.features.FeatureSet featureSet : features.featureSets())
		{
			if (featureSet.role() == RoleType.Shared)
				addFeatureSetWeights(0, featureSet.featureStrings(), featureSet.featureWeights(), featureSetsList, linFuncs);
			else
				addFeatureSetWeights(featureSet.role().owner(), featureSet.featureStrings(), featureSet.featureWeights(), featureSetsList, linFuncs);
		}
		
		this.featureSets = featureSetsList.toArray(new FeatureSet[featureSetsList.size()]);
		this.linearFunctions = linFuncs.toArray(new LinearFunction[linFuncs.size()]);
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
		final FeatureSet featureSet;
		
		if (featureSets.length == 1)
			featureSet = featureSets[0];
		else
			featureSet = featureSets[context.state().mover()];
		
		return computeDistribution(featureSet.computeSparseFeatureVectors(context, actions, thresholded), context.state().mover());
	}
	
	@Override
	public float computeLogit(final Context context, final Move move)
	{
		final FeatureSet featureSet;
		
		if (featureSets.length == 1)
			featureSet = featureSets[0];
		else
			featureSet = featureSets[context.state().mover()];
		
		final LinearFunction linearFunction;
		
		if (linearFunctions.length == 1)
			linearFunction = linearFunctions[0];
		else
			linearFunction = linearFunctions[context.state().mover()];
		
		final FastArrayList<Move> wrappedMove = new FastArrayList<Move>(1);
		wrappedMove.add(move);
		return linearFunction.predict(featureSet.computeSparseFeatureVectors(context, wrappedMove, true).get(0));
	}
	
	/**
	 * @param sparseFeatureVectors
	 * @param player
	 * @return Probability distribution over actions implied by a list of sparse 
	 * feature vectors
	 */
	public FVector computeDistribution
	(
		final List<TIntArrayList> sparseFeatureVectors,
		final int player
	)
	{
		final float[] logits = new float[sparseFeatureVectors.size()];
		final LinearFunction linearFunction;
		
		if (linearFunctions.length == 1)
			linearFunction = linearFunctions[0];
		else
			linearFunction = linearFunctions[player];
		
		for (int i = 0; i < sparseFeatureVectors.size(); ++i)
		{
			logits[i] = linearFunction.predict(sparseFeatureVectors.get(i));
		}
		
		final FVector distribution = FVector.wrap(logits);
		distribution.softmax();
		
		return distribution;
	}
	
	/**
	 * @param estimatedDistribution
	 * @param targetDistribution
	 * @return Vector of errors for estimated distribution in comparison to 
	 * target distribution (simply estimated - target)
	 */
	public FVector computeDistributionErrors
	(
		final FVector estimatedDistribution, 
		final FVector targetDistribution
	)
	{
		final FVector errors = estimatedDistribution.copy();
		errors.subtract(targetDistribution);
		return errors;
	}
	
	/**
	 * @param errors Vector of errors in distributions
	 * @param sparseFeatureVectors One sparse feature vector for every element 
	 * (action) in the distributions.
	 * @param player The player whose parameters we want to compute gradients of
	 * @return Vector of gradients of the loss function (assumed to be 
	 * cross-entropy loss) with respect to our linear function's vector of 
	 * parameters.
	 */
	public FVector computeParamGradients
	(
		final FVector errors,
		final List<TIntArrayList> sparseFeatureVectors,
		final int player
	)
	{
		final LinearFunction linearFunction;
		
		if (linearFunctions.length == 1)
			linearFunction = linearFunctions[0];
		else
			linearFunction = linearFunctions[player];
		
		// now compute gradients w.r.t. parameters
		final FVector grads = new FVector(linearFunction.trainableParams().dim());
		final int numActions = errors.dim();
		
		for (int i = 0; i < numActions; ++i)
		{
			// error for this action
			final float error = errors.get(i);
			
			// sparse feature vector for this action
			final TIntArrayList sparseFeatureVector = 
					sparseFeatureVectors.get(i);
			
			for (int j = 0; j < sparseFeatureVector.size(); ++j)
			{
				final int featureIdx = sparseFeatureVector.getQuick(j);
				grads.addToEntry(featureIdx, error);
			}
		}
		
		//System.out.println("est. distr. = " + estimatedDistribution);
		//System.out.println("tar. distr. = " + targetDistribution);
		//System.out.println("errors      = " + errors);
		//System.out.println("grads = " + grads);
		
		return grads;
	}
	
	/**
	 * @param distribution
	 * @return Samples an action index from a previously-computed distribution
	 */
	public int selectActionFromDistribution(final FVector distribution)
	{
		return distribution.sampleFromDistribution();
	}
	
	/**
	 * Updates this policy to use a new array of Feature Sets.
	 * For now, this method assumes that Feature Sets can only grow, not shrink
	 * 
	 * @param newFeatureSets
	 */
	public void updateFeatureSets(final FeatureSet[] newFeatureSets)
	{
		for (int i = 0; i < featureSets.length; ++i)
		{
			if (newFeatureSets[i] != null)
			{
				final int numExtraFeatures = newFeatureSets[i].getNumFeatures() - featureSets[i].getNumFeatures();

				for (int j = 0; j < numExtraFeatures; ++j)
				{
					linearFunctions[i].setTheta(linearFunctions[i].trainableParams().append(0.f));
				}
				
				featureSets[i] = newFeatureSets[i];
			}
		}
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public Trial runPlayout(final Context context) 
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
					featureSets, 
					params, 
					playoutActionLimit,
					playoutTurnLimit,
					autoPlayThreshold,
					ThreadLocalRandom.current()
				);
	}
	
	@Override
	public boolean playoutSupportsGame(final Game game)
	{
		return supportsGame(game);
	}

	@Override
	public void customise(final String[] inputs) 
	{
		final List<String> policyWeightsFilepaths = new ArrayList<String>();
		boolean boosted = false;
		
		for (int i = 1; i < inputs.length; ++i)
		{
			final String input = inputs[i];
			
			if (input.toLowerCase().startsWith("policyweights="))
			{
				if (policyWeightsFilepaths.size() > 0)
					policyWeightsFilepaths.clear();
				
				policyWeightsFilepaths.add(input.substring("policyweights=".length()));
			}
			else if (input.toLowerCase().startsWith("policyweights"))
			{
				for (int p = 1; p <= Constants.MAX_PLAYERS; ++p)
				{
					if (input.toLowerCase().startsWith("policyweights" + p + "="))
					{
						while (policyWeightsFilepaths.size() <= p)
						{
							policyWeightsFilepaths.add(null);
						}
						
						policyWeightsFilepaths.set(p, input.substring("policyweightsX=".length()));
					}
				}
			}
			else if (input.toLowerCase().startsWith("playoutactionlimit="))
			{
				playoutActionLimit = 
						Integer.parseInt(input.substring(
								"playoutactionlimit=".length()));
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
			else if (input.toLowerCase().startsWith("auto_play_threshold="))
			{
				autoPlayThreshold = Float.parseFloat(input.substring("auto_play_threshold=".length()));
			}
		}
		
		if (!policyWeightsFilepaths.isEmpty())
		{
			this.linearFunctions = new LinearFunction[policyWeightsFilepaths.size()];
			this.featureSets = new FeatureSet[linearFunctions.length];
			
			for (int i = 0; i < policyWeightsFilepaths.size(); ++i)
			{
				String policyWeightsFilepath = policyWeightsFilepaths.get(i);
				
				if (policyWeightsFilepath != null)
				{
					final String parentDir = new File(policyWeightsFilepath).getParent();
					
					if (!new File(policyWeightsFilepath).exists())
					{
						// replace with whatever is the latest file we have
						policyWeightsFilepath = 
								ExperimentFileUtils.getLastFilepath(
										parentDir + "/PolicyWeightsCE_P" + i, "txt");
					}
					
					if (boosted)
						linearFunctions[i] = BoostedLinearFunction.boostedFromFile(policyWeightsFilepath, null);
					else
						linearFunctions[i] = LinearFunction.fromFile(policyWeightsFilepath);
					
					featureSets[i] = new FeatureSet(parentDir + File.separator + linearFunctions[i].featureSetFile());
				}
			}
		}
		else
		{
			System.err.println("Cannot construct Softmax Policy from: " + Arrays.toString(inputs));
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
		
		if (actions.moves().size() == 0)
		{
			// have to pass
			return Game.createPassMove(context);
		}
		
		final FeatureSet featureSet;
			
		if (featureSets.length == 1)
		{
			featureSet = featureSets[0];
		}
		else
		{
			featureSet = featureSets[context.state().mover()];
		}

		return actions.moves().get(selectActionFromDistribution
				(
					computeDistribution
					(
						featureSet.computeSparseFeatureVectors
						(
							context, 
							actions.moves(), 
							true
						), 
						context.state().mover()
					)
				));
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
			
			featureSets[0].instantiateFeatures(game, supportedPlayers, linearFunctions[0].effectiveParams());
		}
		else
		{
			for (int i = 1; i < featureSets.length; ++i)
			{
				if (!featureSets[i].hasInstantiatedFeatures(game, linearFunctions[i].effectiveParams()))
				{
					// need to instantiate features
					featureSets[i].instantiateFeatures(game, new int[] {i}, linearFunctions[i].effectiveParams());
				}
			}
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param player
	 * @return Linear function corresponding to given player
	 */
	public LinearFunction linearFunction(final int player)
	{
		if (linearFunctions.length == 1)
			return linearFunctions[0];
		else
			return linearFunctions[player];
	}
	
	/**
	 * @return The linear functions used to compute logits
	 */
	public LinearFunction[] linearFunctions()
	{
		return linearFunctions;
	}
	
	/**
	 * @return Feature Sets used by this policy
	 */
	public FeatureSet[] featureSets()
	{
		return featureSets;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return A metadata Features item describing the features + weights for this policy
	 */
	public metadata.ai.features.Features generateFeaturesMetadata()
	{
		final Features features;
		
		if (featureSets.length == 1)
		{
			// Just a single featureset for all players
			final FeatureSet featureSet = featureSets[0];
			final LinearFunction linFunc = linearFunctions[0];
			final Pair[] pairs = new Pair[featureSet.features().length];
			
			for (int i = 0; i < pairs.length; ++i)
			{
				pairs[i] = new Pair(featureSet.features()[i].toString(), Float.valueOf(linFunc.effectiveParams().get(i)));
			}
			
			features = new Features(new metadata.ai.features.FeatureSet(RoleType.Shared, pairs));
		}
		else
		{
			// One featureset per player
			final metadata.ai.features.FeatureSet[] metadataFeatureSets = new metadata.ai.features.FeatureSet[featureSets.length - 1];
			
			for (int p = 1; p < featureSets.length; ++p)
			{
				final FeatureSet featureSet = featureSets[p];
				final LinearFunction linFunc = linearFunctions[p];
				final Pair[] pairs = new Pair[featureSet.features().length];
				
				for (int i = 0; i < pairs.length; ++i)
				{
					pairs[i] = new Pair(featureSet.features()[i].toString(), Float.valueOf(linFunc.effectiveParams().get(i)));
				}
				
				metadataFeatureSets[p - 1] = new metadata.ai.features.FeatureSet(RoleType.roleForPlayerId(p), pairs);
			}
			
			features = new Features(metadataFeatureSets);
		}
		
		return features;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param lines
	 * @return A softmax policy constructed from a given array of input lines
	 */
	public static SoftmaxPolicy fromLines(final String[] lines)
	{
		SoftmaxPolicy policy = null;
		
		for (final String line : lines)
		{
			if (line.equalsIgnoreCase("features=from_metadata"))
			{
				policy = new SoftmaxFromMetadata();
				break;
			}
		}
		
		if (policy == null)
			policy = new SoftmaxPolicy();

		policy.customise(lines);
		return policy;
	}
	
	/**
	 * @param weightsFile
	 * @return A Softmax policy constructed from a given file
	 */
	public static SoftmaxPolicy fromFile(final File weightsFile)
	{
		final SoftmaxPolicy policy = new SoftmaxPolicy();
		boolean boosted = false;
		
		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(weightsFile.getAbsolutePath()), "UTF-8")))
		{
			String line = reader.readLine();
			String lastLine = null;
			
			while (line != null)
			{
				lastLine = line;
				line = reader.readLine();
			}
			
			if (!lastLine.startsWith("FeatureSet="))
			{
				boosted = true;
			}
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
		
		policy.customise(new String[]{
				"softmax",
				"policyweights=" + weightsFile.getAbsolutePath(),
				"boosted=" + boosted
		});
		return policy;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Helper method that adds a Feature Set and a Linear Function for the 
	 * given player index
	 * 
	 * @param playerIdx
	 * @param featureStrings
	 * @param featureWeights
	 * @param outFeatureSets
	 * @param outLinFuncs
	 */
	protected static void addFeatureSetWeights
	(
		final int playerIdx, 
		final String[] featureStrings,
		final float[] featureWeights,
		final List<FeatureSet> outFeatureSets, 
		final List<LinearFunction> outLinFuncs
	)
	{
		while (outFeatureSets.size() <= playerIdx)
		{
			outFeatureSets.add(null);
		}
		
		while (outLinFuncs.size() <= playerIdx)
		{
			outLinFuncs.add(null);
		}
		
		final List<Feature> features = new ArrayList<Feature>();
		final TFloatArrayList weights = new TFloatArrayList();
		
		for (int i = 0; i < featureStrings.length; ++i)
		{
			features.add(Feature.fromString(featureStrings[i]));
			weights.add(featureWeights[i]);
		}
		
		outFeatureSets.set(playerIdx, new FeatureSet(features));
		outLinFuncs.set(playerIdx, new LinearFunction(new FVector(weights.toArray())));
	}

	//-------------------------------------------------------------------------
	
}
