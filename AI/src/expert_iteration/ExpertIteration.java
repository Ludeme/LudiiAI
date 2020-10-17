package expert_iteration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import features.FeatureSet;
import features.FeatureUtils;
import features.elements.FeatureElement;
import features.elements.RelativeFeatureElement;
import features.features.Feature;
import features.generation.AtomicFeatureGenerator;
import features.instances.FeatureInstance;
import features.patterns.Pattern;
import function_approx.BoostedLinearFunction;
import function_approx.LinearFunction;
import game.Game;
import gnu.trove.impl.Constants;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import main.CommandLineArgParse;
import main.CommandLineArgParse.ArgOption;
import main.CommandLineArgParse.OptionTypes;
import main.FileHandling;
import main.collections.FVector;
import main.collections.FastArrayList;
import main.grammar.Report;
import metadata.ai.features.Features;
import metadata.ai.heuristics.Heuristics;
import metadata.ai.misc.BestAgent;
import optimisers.Optimiser;
import optimisers.OptimiserFactory;
import policies.softmax.SoftmaxPolicy;
import search.mcts.MCTS;
import search.mcts.finalmoveselection.RobustChild;
import search.mcts.selection.AG0Selection;
import search.minimax.AlphaBetaSearch;
import util.AI;
import util.Context;
import util.GameLoader;
import util.Move;
import util.Trial;
import utils.AIFactory;
import utils.AIUtils;
import utils.ExperimentFileUtils;
import utils.ExponentialMovingAverage;
import utils.data_structures.experience_buffers.ExperienceBuffer;
import utils.data_structures.experience_buffers.PrioritizedReplayBuffer;
import utils.data_structures.experience_buffers.UniformExperienceBuffer;
import utils.experiments.InterruptableExperiment;

/**
 * Implementation of the Expert Iteration self-play training framework,
 * with additional support for feature learning instead of the standard
 * DNNs (see our various papers).
 * 
 * Currently, this is a sequential implementation, where experience generation
 * and training are all performed on a single thread.
 * 
 * @author Dennis Soemers
 */
public class ExpertIteration
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * When do we want to store checkpoints of trained weights?
	 * @author Dennis Soemers
	 */
	public enum CheckpointTypes
	{
		/** Store checkpoint after N self-play training games */
		Game,
		/** Store checkpoint after N weight updates */
		WeightUpdate
	}
	
	/** Format used for checkpoints based on training game count */
	private static final String gameCheckpointFormat = "%s_%05d.%s";
	
	/** Format used for checkpoints based on weight update count */
	private static final String weightUpdateCheckpointFormat = "%s_%08d.%s";
	
	//-------------------------------------------------------------------------
	
	/*
	 * Game setup 
	 */
	
	/** Name of the game to play. Should end with .lud */
	protected String gameName;
	
	/** List of game options to use when compiling game */
	public List<String> gameOptions;
	
	
	/*
	 * Basic experiment setup
	 */
	
	/** Type of AI to use as expert */
	protected String expertAI;
	
	/** Filepath for best agents data directory for this specific game (+ options) */
	protected String bestAgentsDataDir;

	/** Number of training games to run */
	protected int numTrainingGames;
	
	/** Maximum game duration (in moves) */
	public int gameLengthCap;
	
	/** Max allowed thinking time per move (in seconds) */
	protected double thinkingTime;
	
	/** Max allowed number of MCTS iterations per move */
	protected int iterationLimit;
	
	/** Search depth limit (for e.g. Alpha-Beta experts) */
	protected int depthLimit;
	
	
	/*
	 * Training options
	 */
	
	/** After this many training games, we add a new feature. */
	protected int addFeatureEvery;
	
	/** Max size of minibatches in training. */
	protected int batchSize;
	
	/** Max size of the experience buffer. */
	protected int experienceBufferSize;
	
	/** After this many moves (decision points) in training games, we update weights. */
	protected int updateWeightsEvery;
	
	/** If true, we'll not grow feature set (but still train weights) */
	protected boolean noGrowFeatureSet;
	
	/** If true, we'll train a policy on TSPG objective (see COG paper) */
	protected boolean trainTSPG;
	
	/** Optimiser to use when optimising policy on Cross-Entropy loss */
	protected String crossEntropyOptimiserConfig;
	
	/** Optimiser to use when optimising the Cross-Entropy Exploration policy */
	protected String ceExploreOptimiserConfig;
	
	/** Optimiser to use when optimising policy on TSPG objective (see COG paper) */
	protected String tspgOptimiserConfig;
	
	/** Optimiser to use when optimising value function */
	protected String valueOptimiserConfig;
	
	/** At most this number of feature instances will be taken into account when combining features */
	protected int combiningFeatureInstanceThreshold;
	
	/** If true, we'll use importance sampling weights based on episode durations for CE-loss */
	protected boolean importanceSamplingEpisodeDurations;
	
	/** If true, we'll use prioritized experience replay */
	protected boolean prioritizedExperienceReplay;
	
	/** If true, we'll use extra exploration based on cross-entropy losses */
	protected boolean ceExplore;
	
	/** Proportion of exploration policy in our behaviour mix */
	protected float ceExploreMix;
	
	/** Discount factor gamma for rewards awarded to CE Explore policy */
	protected double ceExploreGamma;
	
	/** If true, our CE Explore policy will not be trained, but remain completely uniform */
	protected boolean ceExploreUniform;
	
	/** If true, we ignore importance sampling when doing CE Exploration */
	protected boolean noCEExploreIS;
	
	/** If true, we use Weighted Importance Sampling instead of Ordinary Importance Sampling for any of the above */
	protected boolean weightedImportanceSampling;
	
	/** If true, we don't do any value function learning */
	protected boolean noValueLearning;
	
	
	/*
	 * Setup of our expert
	 */
	
	/** Maximum number of actions per playout which we'll bias using features (-1 for no limit) */
	protected int maxNumBiasedPlayoutActions;
	
	
	/*
	 * Feature init
	 */
	
	/** If true, we will keep full atomic feature set and not prune anything */
	protected boolean noPruneInitFeatures;
	
	/** Will only consider pruning features if they have been active at least this many times */
	protected int pruneInitFeaturesThreshold;
	
	/** Number of random games to play out for determining features to prune */
	protected int numPruningGames;
	
	/** Max number of seconds to spend on random games for pruning atomic features */
	protected int maxNumPruningSeconds;
	
	
	/*
	 * File saving stuff
	 */
	
	/** Output directory */
	protected File outDir;
	
	/** When do we store checkpoints of trained weights? */
	protected CheckpointTypes checkpointType;
	
	/** Frequency of checkpoint updates */
	protected int checkpointFrequency;
	
	/** If true, we suppress a bunch of log messages to a log file. */
	protected boolean noLogging;
	
	
	/*
	 * Auxiliary experiment setup
	 */
	
	/** 
	 * Whether to create a small GUI that can be used to manually interrupt training run. 
	 * False by default. 
	 */
	protected boolean useGUI;
	
	/** Max wall time in minutes (or -1 for no limit) */
	protected int maxWallTime;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor. No GUI for interrupting experiment, no wall time limit.
	 */
	public ExpertIteration()
	{
		// Do nothing
	}

	/**
	 * Constructor. No wall time limit.
	 * @param useGUI
	 */
	public ExpertIteration(final boolean useGUI)
	{
		this.useGUI = useGUI;
	}
	
	/**
	 * Constructor
	 * @param useGUI
	 * @param maxWallTime Wall time limit in minutes.
	 */
	public ExpertIteration(final boolean useGUI, final int maxWallTime)
	{
		this.useGUI = useGUI;
		this.maxWallTime = maxWallTime;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Starts the experiment
	 */
	public void startExperiment()
	{
		try (final PrintWriter logWriter = createLogWriter())
		{
			startTraining(logWriter);
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Start the training run
	 */
	private void startTraining(final PrintWriter logWriter)
	{
		final Game game = GameLoader.loadGameFromName(gameName, gameOptions);
		
		final int numPlayers = game.players().count();
		
		if (gameLengthCap >= 0)
			game.setMaxTurns(Math.min(gameLengthCap, game.getMaxTurnLimit()));
				
		@SuppressWarnings("unused")
		final InterruptableExperiment experiment = new InterruptableExperiment(useGUI, maxWallTime)
		{
			
			//-----------------------------------------------------------------
			
			/** Last checkpoint for which we've saved files */
			protected long lastCheckpoint;
			
			/** Filenames corresponding to our current Feature Sets */
			protected String[] currentFeatureSetFilenames;
			
			/** Filenames corresponding to our current policy weights optimised for CE */
			protected String[] currentPolicyWeightsCEFilenames;
			
			/** Filenames corresponding to our current policy weights optimised for TSPG */
			protected String[] currentPolicyWeightsTSPGFilenames;
			
			/** Filenames corresponding to our current policy weights optimised for CE exploration */
			protected String[] currentPolicyWeightsCEEFilenames;
			
			/** Filename for our current heuristics and weights for value function */
			protected String currentValueFunctionFilename;
			
			/** Filenames corresponding to our current experience buffers */
			protected String[] currentExperienceBufferFilenames;
			
			/** Filenames corresponding to our current moving average trackers of game durations */
			protected String[] currentGameDurationTrackerFilenames;
			
			/** Filenames corresponding to our current optimisers for CE */
			protected String[] currentOptimiserCEFilenames;
			
			/** Filenames corresponding to our current optimisers for TSPG */
			protected String[] currentOptimiserTSPGFilenames;
			
			/** Filenames corresponding to our current optimisers for CE exploration */
			protected String[] currentOptimiserCEEFilenames;
			
			/** Filename corresponding to our current optimiser for Value function */
			protected String currentOptimiserValueFilename;
			
			/**
			 * Init class members. Cant do this in field declarations because
			 * runExperiment() is called inside constructor of parent class.
			 */
			private void initMembers()
			{
				lastCheckpoint = Long.MAX_VALUE;
				currentFeatureSetFilenames = new String[numPlayers + 1];
				currentPolicyWeightsCEFilenames = new String[numPlayers + 1];
				currentPolicyWeightsTSPGFilenames = new String[numPlayers + 1];
				currentPolicyWeightsCEEFilenames = new String[numPlayers + 1];
				currentValueFunctionFilename = null;
				currentExperienceBufferFilenames = new String[numPlayers + 1];
				currentGameDurationTrackerFilenames = new String[numPlayers + 1];
				currentOptimiserCEFilenames = new String[numPlayers + 1];
				currentOptimiserTSPGFilenames = new String[numPlayers + 1];
				currentOptimiserCEEFilenames = new String[numPlayers + 1];
				currentOptimiserValueFilename = null;
			}
			
			//-----------------------------------------------------------------

			@Override
			public void runExperiment()
			{
				if (outDir == null)
				{
					System.err.println("Warning: we're not writing any output files for this run!");
				}
				else if (!outDir.exists())
				{
					outDir.mkdirs();
				}
				
				initMembers();
				
				// TODO add log statements describing complete setup of experiment
				
				// prepare our feature sets
				FeatureSet[] featureSets = prepareFeatureSets();
				
				// prepare our linear functions
				final LinearFunction[] crossEntropyFunctions = prepareCrossEntropyFunctions(featureSets);
				final LinearFunction[] tspgFunctions = prepareTSPGFunctions(featureSets, crossEntropyFunctions);
				final LinearFunction[] ceExploreFunctions = prepareCEExploreFunctions(featureSets);
				
				// create our policies
				final SoftmaxPolicy cePolicy = 
						new SoftmaxPolicy
						(
							crossEntropyFunctions, 
							featureSets,
							maxNumBiasedPlayoutActions
						);
				
				final SoftmaxPolicy tspgPolicy = 
						new SoftmaxPolicy
						(
							tspgFunctions, 
							featureSets,
							maxNumBiasedPlayoutActions
						);
				
				final SoftmaxPolicy ceExplorePolicy = 
						new SoftmaxPolicy
						(
							ceExploreFunctions, 
							featureSets
						);
				
				// create our value function
				final Heuristics valueFunction = prepareValueFunction();
				
				// construct optimisers
				final Optimiser[] ceOptimisers = prepareCrossEntropyOptimisers();
				final Optimiser[] tspgOptimisers = prepareTSPGOptimisers();
				final Optimiser[] ceExploreOptimisers = prepareCEExploreOptimisers();
				final Optimiser valueFunctionOptimiser = prepareValueFunctionOptimiser();
				
				// instantiate trial / context
				final Trial trial = new Trial(game);
				final Context context = new Context(game, trial);
				
				// instantiate experts
				final List<ExpertPolicy> experts = new ArrayList<ExpertPolicy>(numPlayers + 1);
				experts.add(null);	// 0 entry not used
				final SoftmaxPolicy playoutPolicy = cePolicy;

				for (int p = 1; p <= numPlayers; ++p)
				{
					final AI ai;
					final Report report = new Report();
									
					if (expertAI.equals("BEST_AGENT"))
					{
						BestAgent bestAgent;
						try
						{
							bestAgent = (BestAgent) language.compiler.Compiler.compileObject
							(
									FileHandling.loadTextContentsFromFile(bestAgentsDataDir + "/BestAgent.txt"), 
									"metadata.ai.misc.BestAgent",
									report
							);
							
							if (bestAgent.agent().equals("AlphaBeta"))
							{
								ai = new AlphaBetaSearch(bestAgentsDataDir + "/BestHeuristics.txt");
							}
							else if (bestAgent.agent().equals("AlphaBetaMetadata"))
							{
								ai = new AlphaBetaSearch();
							}
							else if (bestAgent.agent().equals("UCT"))
							{
								ai = AIFactory.createAI("UCT");
							}
							else if (bestAgent.agent().equals("MC-GRAVE"))
							{
								ai = AIFactory.createAI("MC-GRAVE");
							}
							else if (bestAgent.agent().equals("Biased MCTS"))
							{
								final Features features = (Features) language.compiler.Compiler.compileObject
								(
									FileHandling.loadTextContentsFromFile(bestAgentsDataDir + "/BestFeatures.txt"), 
									"metadata.ai.features.Features",
									report
								);
								
								ai = MCTS.createBiasedMCTS(features, true);
							}
							else if (bestAgent.agent().equals("Biased MCTS (Uniform Playouts)"))
							{
								final Features features = (Features) language.compiler.Compiler.compileObject
								(
									FileHandling.loadTextContentsFromFile(bestAgentsDataDir + "/BestFeatures.txt"), 
									"metadata.ai.features.Features",
									report
								);
								
								ai = MCTS.createBiasedMCTS(features, false);
							}
							else if (bestAgent.agent().equals("Random"))
							{
								// Don't wanna train with Random, so we'll take UCT instead
								ai = MCTS.createUCT();
							}
							else
							{
								System.err.println("Unrecognised best agent: " + bestAgent.agent());
								return;
							}
						}
						catch (final IOException e)
						{
							e.printStackTrace();
							return;
						}
					}
					else if (expertAI.equals("FROM_METADATA"))
					{
						ai = AIFactory.fromMetadata(game);
						
						if (ai == null)
						{
							System.err.println("AI from metadata is null!");
							return;
						}
						
						if (!(ai instanceof ExpertPolicy))
						{
							System.err.println("AI from metadata is not an expert policy!");
							return;
						}
					}
					else if (expertAI.equals("Biased MCTS"))
					{
						final MCTS mcts = 
								new MCTS
								(
									new AG0Selection(), 
									playoutPolicy,
									new RobustChild()
								);
						
						mcts.setLearnedSelectionPolicy(cePolicy);
						mcts.friendlyName = "Biased MCTS";
						ai = mcts;
					}
					else
					{
						System.err.println("Cannot recognise expert AI: " + expertAI);
						return;
					}
					
					if (ai instanceof MCTS)
					{
						// Need to preserve root node such that we can extract distributions from it
						((MCTS) ai).setPreserveRootNode(true);
					}
					else if (trainTSPG)
					{
						System.err.println("A non-MCTS expert cannot be used for training the TSPG objective!");
						return;
					}
					
					experts.add((ExpertPolicy) ai);
				}
				
				// prepare our replay buffers (we use one per player)
				final ExperienceBuffer[] experienceBuffers = prepareExperienceBuffers(prioritizedExperienceReplay);
				
				// keep track of average game duration (separate per player) 
				final ExponentialMovingAverage[] avgGameDurations = prepareGameDurationTrackers();
				
				// our big game-playing loop
				long actionCounter = 0L;
				long weightsUpdateCounter = (checkpointType == CheckpointTypes.WeightUpdate) ? lastCheckpoint : 0L;

				int gameCounter = 0;

				if (checkpointType == CheckpointTypes.Game && lastCheckpoint >= 0L)
				{
					gameCounter = (int)lastCheckpoint;
					numTrainingGames += lastCheckpoint;
				}
				
				for (/**/; gameCounter < numTrainingGames; ++gameCounter)
				{
					checkWallTime(0.05);
					
					if (interrupted) // time to abort the experiment
					{
						logLine(logWriter, "interrupting experiment...");
						break;
					}
					
					saveCheckpoints
					(
						gameCounter, 
						weightsUpdateCounter, 
						featureSets, 
						crossEntropyFunctions, 
						tspgFunctions,
						ceExploreFunctions,
						valueFunction,
						experienceBuffers,
						ceOptimisers,
						tspgOptimisers,
						ceExploreOptimisers,
						valueFunctionOptimiser,
						avgGameDurations,
						false
					);
					
					final FeatureSet[] expandedFeatureSets = new FeatureSet[numPlayers + 1];
					
					if (!noGrowFeatureSet && gameCounter > 0 && gameCounter % addFeatureEvery == 0)
					{
						for (int p = 1; p <= numPlayers; ++p)
						{
							// we'll sample a batch from our replay buffer, and grow feature set
							final ExItExperience[] batch = experienceBuffers[p].sampleExperienceBatchUniformly(batchSize);
							
							if (batch.length > 0)
							{
								final long startTime = System.currentTimeMillis();
								final FeatureSet expandedFeatureSet = 
										expandFeatureSetCorrelationBased
										(
											batch,
											featureSets[p],
											cePolicy,
											game,
											combiningFeatureInstanceThreshold
										);
								
								if (expandedFeatureSet != null)
								{
									expandedFeatureSets[p] = expandedFeatureSet;
									expandedFeatureSet.instantiateFeatures(game, new int[]{p}, null);
								}
								else
								{
									expandedFeatureSets[p] = featureSets[p];
								}
								
								logLine
								(
									logWriter,
									"Expanded feature set in " + (System.currentTimeMillis() - startTime) + " ms for P" + p + "."
								);
							}
							else
							{
								expandedFeatureSets[p] = featureSets[p];
							}
						}
						
						cePolicy.updateFeatureSets(expandedFeatureSets);
						
						if (trainTSPG)
							tspgPolicy.updateFeatureSets(expandedFeatureSets);
						
						if (ceExplore)
							ceExplorePolicy.updateFeatureSets(expandedFeatureSets);
						
						featureSets = expandedFeatureSets;
					}
					
					logLine(logWriter, "starting game " + (gameCounter + 1));
					
					// play a game
					game.start(context);
					
					// here we'll collect all tuples of experience during this game
					final List<List<ExItExperience>> gameExperienceSamples = new ArrayList<List<ExItExperience>>(numPlayers + 1);
					gameExperienceSamples.add(null);
					
					for (int p = 1; p < experts.size(); ++p)
					{
						experts.get(p).initAI(game, p);
						gameExperienceSamples.add(new ArrayList<ExItExperience>());
					}
					
					// init some stuff for CE exploration
					double ceExploreCurrISWeight = 1.0;
					final List<FVector> ceExploreGradientVectors = new ArrayList<FVector>();
					final TIntArrayList ceExploreMovers = new TIntArrayList();
					final TFloatArrayList ceExploreRewards = new TFloatArrayList();
					
					while (!context.trial().over())
					{
						if (interrupted) // time to abort the experiment
						{
							logLine(logWriter, "interrupting experiment...");
							break;
						}
						
						// have expert choose action
						final int mover = context.state().mover();
						final ExpertPolicy expert = experts.get(context.state().playerToAgent(mover));
						final Move move;
						
						expert.selectAction
						(
							game, 
							new Context(context), 
							thinkingTime,
							iterationLimit,
							depthLimit
						);

						final FastArrayList<Move> legalMoves = new FastArrayList<Move>();
						for (final Move legalMove : expert.lastSearchRootMoves())
						{
							legalMoves.add(legalMove);
						}

						final FVector expertDistribution = expert.computeExpertPolicy(1.0);
						
						if (ceExplore)
						{
							final FVector ceExploreDistribution = 
									ceExplorePolicy.computeDistribution(context, legalMoves, false);
							
//							System.out.println();
//							System.out.println("visits distribution  = " + visitsDistribution);
//							System.out.println("explore distribution = " + ceExploreDistribution);
//							System.out.println("visits norm. entropy  = " + visitsDistribution.normalisedEntropy());
//							System.out.println("learned norm. entropy = " + mcts.rootNode().learnedSelectionPolicyNormalisedEntropy());
//							System.out.println("explore norm. entropy = " + ceExploreDistribution.normalisedEntropy());
							
							final FVector mixedDistribution = expertDistribution.copy();
							mixedDistribution.mult(1.f - ceExploreMix);
							mixedDistribution.addScaled(ceExploreDistribution, ceExploreMix);
							
							final int moveIdx = mixedDistribution.sampleProportionally();
							move = legalMoves.get(moveIdx);
							
							ceExploreCurrISWeight *= (expertDistribution.get(moveIdx) / mixedDistribution.get(moveIdx));
//							System.out.println("visitsDistribution.get(moveIdx) = " + visitsDistribution.get(moveIdx));
//							System.out.println("mixedDistribution.get(moveIdx) = " + mixedDistribution.get(moveIdx));
//							System.out.println("ceExploreCurrISWeight = " + ceExploreCurrISWeight);
//							System.out.println();
							
							// compute and store gradient of log of policy vector: 
							// \nabla_{\theta} \log (\pi (s, a)) = \phi(s, a) - E_{\pi} [\phi(s, \cdot)]
							final List<TIntArrayList> sparseFeatureVectors = 
									featureSets[mover].computeSparseFeatureVectors(context, legalMoves, false);
							
							final FVector gradLog = new FVector(ceExplorePolicy.linearFunction(mover).trainableParams().dim());
							for (int i = 0; i < sparseFeatureVectors.size(); ++i)
							{
								final TIntArrayList featureVector = sparseFeatureVectors.get(i);
								
								for (int j = 0; j < featureVector.size(); ++j) 
								{
									gradLog.addToEntry(featureVector.getQuick(j), -1.f * ceExploreDistribution.get(i));
								}
							}
							
							final TIntArrayList featureVector = sparseFeatureVectors.get(moveIdx);
							for (int i = 0; i < featureVector.size(); ++i)
							{
								gradLog.addToEntry(featureVector.getQuick(i), 1.f);
							}
							
							ceExploreGradientVectors.add(gradLog);
							
							// compute our CE Explore reward for this time step
							final FVector learnedDistribution = cePolicy.computeDistribution(sparseFeatureVectors, mover);
							final FVector errors = expertDistribution.copy();
							errors.subtract(learnedDistribution);
							errors.abs();
							ceExploreRewards.add(errors.sum());
							
							// also remember who the mover was
							ceExploreMovers.add(mover);
						}
						else
						{
							final int moveIdx = expertDistribution.sampleProportionally();
							move = legalMoves.get(moveIdx);	
						}
							
						// collect experience for this game (don't store in buffer yet, don't know episode duration or value)
						final ExItExperience newExperience = expert.generateExItExperience();
						
						if (valueFunction != null)
							newExperience.setStateFeatureVector(valueFunction.computeStateFeatureVector(context, mover));
						
						gameExperienceSamples.get(mover).add(newExperience);

						if (ceExplore)
						{
							newExperience.setWeightCEExplore((float) ceExploreCurrISWeight);
						}
						
						// apply chosen action
						game.apply(context, move);
						++actionCounter;
						
						if (actionCounter % updateWeightsEvery == 0)
						{
							// time to update our weights a bit (once for every player-specific model)
							for (int p = 1; p <= numPlayers; ++p)
							{
								final ExItExperience[] batch = experienceBuffers[p].sampleExperienceBatch(batchSize);
								
								if (batch.length == 0)
									continue;
								
								final List<FVector> gradientsCE = new ArrayList<FVector>(batch.length);
								final List<FVector> gradientsTSPG = new ArrayList<FVector>(batch.length);
								final List<FVector> gradientsCEExplore = new ArrayList<FVector>(batch.length);
								final List<FVector> gradientsValueFunction = new ArrayList<FVector>(batch.length);
								
								// for PER
								final int[] indices = new int[batch.length];
								final float[] priorities = new float[batch.length];
								
								// for WIS
								double sumImportanceSamplingWeights = 0.0;
								
								for (int idx = 0; idx < batch.length; ++idx)
								{
									final ExItExperience sample = batch[idx];
									final List<TIntArrayList> sparseFeatureVectors = 
											featureSets[p].computeSparseFeatureVectors
											(
												sample.state().state(),
												sample.state().lastDecisionMove(),
												sample.moves(), 
												false
											);
									
									// first gradients for Cross-Entropy
									final FVector apprenticePolicy = 
											cePolicy.computeDistribution(sparseFeatureVectors, sample.state().state().mover());
									final FVector expertPolicy = sample.expertDistribution();
									final FVector errors = cePolicy.computeDistributionErrors(apprenticePolicy, expertPolicy);
															
									// TODO remove this
									if (sample.state().state().mover() != p)
									{
										System.err.println("Sample's mover not equal to p!");
									}
									
									final FVector ceGradients = cePolicy.computeParamGradients
										(
											errors,
											sparseFeatureVectors,
											sample.state().state().mover()
										);
									
									FVector valueGradients = null;
									if (valueFunction != null)
									{
										// Compute gradients for value function
										final FVector valueFunctionParams = valueFunction.paramsVector();
										final float predictedValue = (float) Math.tanh(valueFunctionParams.dot(sample.stateFeatureVector()));
										final float gameOutcome = (float) sample.playerOutcomes()[sample.state().state().mover()];
										
										final float valueError = predictedValue - gameOutcome;
										valueGradients = new FVector(valueFunctionParams.dim());
										
										// Need to multiply this by feature value to compute gradient per feature
										final float gradDivFeature = 2.f * valueError * (1.f - predictedValue*predictedValue);
										
										for (int i = 0; i < valueGradients.dim(); ++i)
										{
											valueGradients.set(i, gradDivFeature * sample.stateFeatureVector().get(i));
										}
										
//										System.out.println();
//										System.out.println("State Features = " + sample.stateFeatureVector());
//										System.out.println("pred. value = " + predictedValue);
//										System.out.println("observed outcome = " + gameOutcome);
//										System.out.println("value error = " + valueError);
//										System.out.println("value grads = " + valueGradients);
//										System.out.println();
									}

									double importanceSamplingWeight = 1.0;
									
									if (importanceSamplingEpisodeDurations)
										importanceSamplingWeight *= (avgGameDurations[p].movingAvg() / sample.episodeDuration());
									
									if (prioritizedExperienceReplay)
									{
										final FVector absErrors = errors.copy();
										absErrors.abs();
										
										// Minimum priority of 0.05 to avoid crashes with 0-error samples
										priorities[idx] = Math.max(0.05f, absErrors.sum());
										importanceSamplingWeight *= sample.weightPER();
										indices[idx] = sample.bufferIdx();
									}
									
									if (ceExplore && !noCEExploreIS)
									{
										// TODO make truncation bounds proper parameters
										float ceExploreWeight = sample.weightCEExplore();
										if (ceExploreWeight < 0.1f)
											ceExploreWeight = 0.1f;
										else if (ceExploreWeight > 2.f)
											ceExploreWeight = 2.f;
										importanceSamplingWeight *= ceExploreWeight;
									}
									
									sumImportanceSamplingWeights += importanceSamplingWeight;
									ceGradients.mult((float) importanceSamplingWeight);
									gradientsCE.add(ceGradients);
									
									if (valueGradients != null)
									{
										valueGradients.mult((float) importanceSamplingWeight); 
										gradientsValueFunction.add(valueGradients);
									}
									
									if (trainTSPG)
									{
										// and gradients for TSPG
										final FVector pi = 
												tspgPolicy.computeDistribution(sparseFeatureVectors, sample.state().state().mover());
										final FVector expertQs = sample.expertValueEstimates();
										
										final FVector grads = new FVector(tspgFunctions[p].trainableParams().dim());
										for (int i = 0; i < sample.moves().size(); ++i)
										{
											final float expertQ = expertQs.get(i);
											final float pi_sa = pi.get(i);
											
											for (int j = 0; j < sample.moves().size(); ++j)
											{
												final TIntArrayList activeFeatures = sparseFeatureVectors.get(j);
												
												for (int k = 0; k < activeFeatures.size(); ++k)
												{
													final int feature = activeFeatures.getQuick(k);
													
													if (i == j)
														grads.addToEntry(feature, expertQ * pi_sa * (1.f - pi_sa));
													else
														grads.addToEntry(feature, expertQ * pi_sa * (0.f - pi.get(j)));
												}
											}
										}

										gradientsTSPG.add(grads);
									}
								}
								
								final FVector meanGradientsCE;
								FVector meanGradientsValue = null;
								
								if (weightedImportanceSampling)
								{
									// for WIS, we don't divide by number of vectors, but by sum of IS weights
									meanGradientsCE = gradientsCE.get(0).copy();
									for (int i = 1; i < gradientsCE.size(); ++i)
									{
										meanGradientsCE.add(gradientsCE.get(i));
									}
									meanGradientsCE.div((float)sumImportanceSamplingWeights);
									
									if (!gradientsValueFunction.isEmpty())
									{
										meanGradientsValue = gradientsValueFunction.get(0).copy();
										for (int i = 1; i < gradientsValueFunction.size(); ++i)
										{
											meanGradientsValue.add(gradientsValueFunction.get(i));
										}
										meanGradientsValue.div((float)sumImportanceSamplingWeights);
									}
								}
								else
								{
									meanGradientsCE = FVector.mean(gradientsCE);
									
									if (!gradientsValueFunction.isEmpty())
										meanGradientsValue = FVector.mean(gradientsValueFunction);
								}
								
								ceOptimisers[p].minimiseObjective(crossEntropyFunctions[p].trainableParams(), meanGradientsCE);
								
								if (meanGradientsValue != null)
								{
									final FVector valueFunctionParams = valueFunction.paramsVector();
									valueFunctionOptimiser.minimiseObjective(valueFunctionParams, meanGradientsValue);
									valueFunction.updateParams(game, valueFunctionParams, 0);
								}
								
								if (trainTSPG)
								{
									final FVector meanGradientsTSPG = FVector.mean(gradientsTSPG);
									tspgOptimisers[p].maximiseObjective(tspgFunctions[p].trainableParams(), meanGradientsTSPG);
								}
								
								// update PER priorities
								if (prioritizedExperienceReplay)
								{
									final PrioritizedReplayBuffer buffer = (PrioritizedReplayBuffer) experienceBuffers[p];
									buffer.setPriorities(indices, priorities);
								}
							}
							
							++weightsUpdateCounter;
						}
					}
					
					if (!interrupted)
					{
						// game is over, we can now store all experience collected in the real buffers
						for (int p = 1; p <= numPlayers; ++p)
						{
							Collections.shuffle(gameExperienceSamples.get(p), ThreadLocalRandom.current());
							
							// Note: not really game duration! Just from perspective of one player!
							final int gameDuration = gameExperienceSamples.get(p).size();
							avgGameDurations[p].observe(gameDuration);
							
							final double[] playerOutcomes = AIUtils.agentUtilities(context);
							
							for (final ExItExperience experience : gameExperienceSamples.get(p))
							{
								experience.setEpisodeDuration(gameDuration);
								experience.setPlayerOutcomes(playerOutcomes);
								experienceBuffers[p].add(experience);
							}
						}
						
						// train our CE Explore policy using REINFORCE
						if (ceExplore && !ceExploreUniform)
						{
							final List<List<FVector>> gradVectorsPerPlayer = new ArrayList<List<FVector>>(numPlayers + 1);
							gradVectorsPerPlayer.add(null);
							
							for (int p = 1; p <= numPlayers; ++p)
							{
								gradVectorsPerPlayer.add(new ArrayList<FVector>());
							}
							
							for (int t = 0; t < ceExploreGradientVectors.size(); ++t)
							{
								final FVector gradLog = ceExploreGradientVectors.get(t);
								float returns = 0.f;
								
								// NOTE: tt skips 0, we skip the rewards obtained in initial game state
								// because policy has no influence on initial game state distribution
								for (int tt = t + 1; tt < ceExploreRewards.size(); ++tt)
								{
									returns += Math.pow(ceExploreGamma, tt - (t + 1)) * ceExploreRewards.getQuick(tt);
								}
								
								//System.out.println("returns = " + returns + " from t = " + t + " onwards");
								
								gradLog.mult(returns);
								gradVectorsPerPlayer.get(ceExploreMovers.getQuick(t)).add(gradLog);
							}
							
							// for every player, take an optimisation step for the corresponding CE Explore policy
							for (int p = 1; p <= numPlayers; ++p)
							{
								if (gradVectorsPerPlayer.get(p).size() > 0)
								{
									final FVector meanGradientsCEExplore = FVector.mean(gradVectorsPerPlayer.get(p));
									ceExploreOptimisers[p].minimiseObjective(ceExploreFunctions[p].trainableParams(), meanGradientsCEExplore);
								}
							}
						}
					}
					
					if (context.trial().over())
					{
						logLine(logWriter, "Finished running game " + (gameCounter + 1));
					}
				}
				
				// final forced save of checkpoints at end of run
				saveCheckpoints
				(
					gameCounter + 1, 
					weightsUpdateCounter, 
					featureSets, 
					crossEntropyFunctions, 
					tspgFunctions,
					ceExploreFunctions,
					valueFunction,
					experienceBuffers,
					ceOptimisers,
					tspgOptimisers,
					ceExploreOptimisers,
					valueFunctionOptimiser,
					avgGameDurations,
					true
				);
			}
			
			//-----------------------------------------------------------------
			
			/**
			 * Creates (or loads) optimisers for CE (one per player)
			 * 
			 * @return
			 */
			private Optimiser[] prepareCrossEntropyOptimisers()
			{
				final Optimiser[] optimisers = new Optimiser[numPlayers + 1];
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					Optimiser optimiser = null;
					
					currentOptimiserCEFilenames[p] = getFilenameLastCheckpoint("OptimiserCE_P" + p, "opt");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentOptimiserCEFilenames[p], "OptimiserCE_P" + p, "opt")
							);
					//System.out.println("CE opt set lastCheckpoint = " + lastCheckpoint);
					
					if (currentOptimiserCEFilenames[p] == null)
					{
						// create new optimiser
						optimiser = OptimiserFactory.createOptimiser(crossEntropyOptimiserConfig);
						logLine(logWriter, "starting with new optimiser for Cross-Entropy");
					}
					else
					{
						// load optimiser from file
						try 
						(
							final ObjectInputStream reader = 
								new ObjectInputStream(new BufferedInputStream(new FileInputStream(
										outDir.getAbsolutePath() + File.separator + currentOptimiserCEFilenames[p]
								)))
						)
						{
							optimiser = (Optimiser) reader.readObject();
						}
						catch (final IOException | ClassNotFoundException e)
						{
							e.printStackTrace();
						}
						
						logLine(logWriter, "continuing with CE optimiser loaded from " + currentOptimiserCEFilenames[p]);
					}
					
					optimisers[p] = optimiser;
				}
				
				return optimisers;
			}
			
			/**
			 * Creates (or loads) optimisers for TSPG (one per player)
			 * 
			 * @return
			 */
			private Optimiser[] prepareTSPGOptimisers()
			{
				final Optimiser[] optimisers = new Optimiser[numPlayers + 1];
				
				if (trainTSPG)
				{
					for (int p = 1; p <= numPlayers; ++p)
					{
						Optimiser optimiser = null;
						
						currentOptimiserTSPGFilenames[p] = getFilenameLastCheckpoint("OptimiserTSPG_P" + p, "opt");
						lastCheckpoint = 
								Math.min
								(
									lastCheckpoint,
									extractCheckpointFromFilename(currentOptimiserTSPGFilenames[p], "OptimiserTSPG_P" + p, "opt")
								);
						//System.out.println("TSPG opt set lastCheckpoint = " + lastCheckpoint);
						
						if (currentOptimiserTSPGFilenames[p] == null)
						{
							// create new optimiser
							optimiser = OptimiserFactory.createOptimiser(tspgOptimiserConfig);
							logLine(logWriter, "starting with new optimiser for TSPG");
						}
						else
						{
							// load optimiser from file
							try 
							(
								final ObjectInputStream reader = 
									new ObjectInputStream(new BufferedInputStream(new FileInputStream(
											outDir.getAbsolutePath() + File.separator + currentOptimiserTSPGFilenames[p]
									)))
							)
							{
								optimiser = (Optimiser) reader.readObject();
							}
							catch (final IOException | ClassNotFoundException e)
							{
								e.printStackTrace();
							}
							
							logLine(logWriter, "continuing with TSPG optimiser loaded from " + currentOptimiserTSPGFilenames[p]);
						}
						
						optimisers[p] = optimiser;
					}
				}
				
				return optimisers;
			}
			
			/**
			 * Creates (or loads) optimisers for CEE (one per player)
			 * 
			 * @return
			 */
			private Optimiser[] prepareCEExploreOptimisers()
			{
				final Optimiser[] optimisers = new Optimiser[numPlayers + 1];
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					Optimiser optimiser = null;
					
					currentOptimiserCEEFilenames[p] = getFilenameLastCheckpoint("OptimiserCEE_P" + p, "opt");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentOptimiserCEEFilenames[p], "OptimiserCEE_P" + p, "opt")
							);
					//System.out.println("CEE opt set lastCheckpoint = " + lastCheckpoint);
					
					if (currentOptimiserCEEFilenames[p] == null)
					{
						// create new optimiser		TODO allow different one from CE here
						optimiser = OptimiserFactory.createOptimiser(ceExploreOptimiserConfig);
						logLine(logWriter, "starting with new optimiser for CEE");
					}
					else
					{
						// load optimiser from file
						try 
						(
							final ObjectInputStream reader = 
								new ObjectInputStream(new BufferedInputStream(new FileInputStream(
										outDir.getAbsolutePath() + File.separator + currentOptimiserCEEFilenames[p]
								)))
						)
						{
							optimiser = (Optimiser) reader.readObject();
						}
						catch (final IOException | ClassNotFoundException e)
						{
							e.printStackTrace();
						}
						
						logLine(logWriter, "continuing with CEE optimiser loaded from " + currentOptimiserCEEFilenames[p]);
					}
					
					optimisers[p] = optimiser;
				}
				
				return optimisers;
			}
			
			/**
			 * Creates (or loads) optimiser for Value function (one shared for all players)
			 * 
			 * @return
			 */
			private Optimiser prepareValueFunctionOptimiser()
			{
				final Optimiser[] optimisers = new Optimiser[numPlayers + 1];
				
				Optimiser optimiser = null;
					
				currentOptimiserValueFilename = getFilenameLastCheckpoint("OptimiserValue", "opt");
				lastCheckpoint = 
						Math.min
						(
							lastCheckpoint,
							extractCheckpointFromFilename(currentOptimiserValueFilename, "OptimiserValue", "opt")
						);

				if (currentOptimiserValueFilename == null)
				{
					// create new optimiser
					optimiser = OptimiserFactory.createOptimiser(valueOptimiserConfig);
					logLine(logWriter, "starting with new optimiser for Value function");
				}
				else
				{
					// load optimiser from file
					try 
					(
							final ObjectInputStream reader = 
							new ObjectInputStream(new BufferedInputStream(new FileInputStream(
									outDir.getAbsolutePath() + File.separator + currentOptimiserValueFilename
								)))
							)
					{
						optimiser = (Optimiser) reader.readObject();
					}
					catch (final IOException | ClassNotFoundException e)
					{
						e.printStackTrace();
					}

					logLine(logWriter, "continuing with Value function optimiser loaded from " + currentOptimiserValueFilename);
				}
				
				return optimiser;
			}
			
			/**
			 * Creates (or loads) experience buffers (one per player)
			 * 
			 * @param prio
			 * @return
			 */
			private ExperienceBuffer[] prepareExperienceBuffers(final boolean prio)
			{
				final ExperienceBuffer[] experienceBuffers = new ExperienceBuffer[numPlayers + 1];
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					final ExperienceBuffer experienceBuffer;
					
					currentExperienceBufferFilenames[p] = getFilenameLastCheckpoint("ExperienceBuffer_P" + p, "buf");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentExperienceBufferFilenames[p], "ExperienceBuffer_P" + p, "buf")
							);
					//System.out.println("Buffers set lastCheckpoint = " + lastCheckpoint);
					
					if (currentExperienceBufferFilenames[p] == null)
					{
						// create new Experience Buffer
						if (prio)
							experienceBuffer = new PrioritizedReplayBuffer(experienceBufferSize);
						else
							experienceBuffer = new UniformExperienceBuffer(experienceBufferSize);
						logLine(logWriter, "starting with empty experience buffer");
					}
					else
					{
						// load experience buffer from file
						experienceBuffer = 
								prio
								? PrioritizedReplayBuffer.fromFile(game, outDir.getAbsolutePath() + File.separator + currentExperienceBufferFilenames[p])
								: UniformExperienceBuffer.fromFile(game, outDir.getAbsolutePath() + File.separator + currentExperienceBufferFilenames[p]);
						
						logLine(logWriter, "continuing with experience buffer loaded from " + currentExperienceBufferFilenames[p]);
					}
					
					experienceBuffers[p] = experienceBuffer;
				}
				
				return experienceBuffers;
			}
			
			/**
			 * Creates (or loads) trackers for average game duration (one per player)
			 * 
			 * @return
			 */
			private ExponentialMovingAverage[] prepareGameDurationTrackers()
			{
				final ExponentialMovingAverage[] trackers = new ExponentialMovingAverage[numPlayers + 1];
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					ExponentialMovingAverage tracker = null;
					
					currentGameDurationTrackerFilenames[p] = getFilenameLastCheckpoint("GameDurationTracker_P" + p, "bin");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentGameDurationTrackerFilenames[p], "GameDurationTracker_P" + p, "bin")
							);
					//System.out.println("Game dur trackers set lastCheckpoint = " + lastCheckpoint);
					
					if (currentGameDurationTrackerFilenames[p] == null)
					{
						// create new tracker
						tracker = new ExponentialMovingAverage();
						logLine(logWriter, "starting with new tracker for average game duration");
					}
					else
					{
						// load tracker from file
						try 
						(
							final ObjectInputStream reader = 
								new ObjectInputStream(new BufferedInputStream(new FileInputStream(
										outDir.getAbsolutePath() + File.separator + currentGameDurationTrackerFilenames[p]
								)))
						)
						{
							tracker = (ExponentialMovingAverage) reader.readObject();
						}
						catch (final IOException | ClassNotFoundException e)
						{
							e.printStackTrace();
						}
						
						logLine(logWriter, "continuing with average game duration tracker loaded from " + currentGameDurationTrackerFilenames[p]);
					}
					
					trackers[p] = tracker;
				}
				
				return trackers;
			}
			
			/**
			 * Creates (or loads) linear functions (one per player)
			 * @param featureSets
			 * @return
			 */
			private LinearFunction[] prepareCrossEntropyFunctions(final FeatureSet[] featureSets)
			{
				final LinearFunction[] linearFunctions = new LinearFunction[numPlayers + 1];
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					final LinearFunction linearFunction;
					
					currentPolicyWeightsCEFilenames[p] = getFilenameLastCheckpoint("PolicyWeightsCE_P" + p, "txt");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentPolicyWeightsCEFilenames[p], "PolicyWeightsCE_P" + p, "txt")
							);
					//System.out.println("CE funcs set lastCheckpoint = " + lastCheckpoint);
					
					if (currentPolicyWeightsCEFilenames[p] == null)
					{
						// create new linear function
						linearFunction = new LinearFunction(new FVector(featureSets[p].getNumFeatures()));
						logLine(logWriter, "starting with new 0-weights linear function for Cross-Entropy");
					}
					else
					{
						// load weights from file
						linearFunction = 
								LinearFunction.fromFile(outDir.getAbsolutePath() + File.separator + currentPolicyWeightsCEFilenames[p]);
						logLine(logWriter, "continuing with Selection policy weights loaded from " + currentPolicyWeightsCEFilenames[p]);
						
						try 
						{
							// make sure we're combining correct function with feature set
							String featureSetFilepath = 
									new File(outDir.getAbsolutePath() + File.separator + currentPolicyWeightsCEFilenames[p]).getParent();
							featureSetFilepath += File.separator + linearFunction.featureSetFile();
							
							if 
							(
								!new File(featureSetFilepath).getCanonicalPath().equals
								(
									new File(outDir.getAbsolutePath() + File.separator + currentFeatureSetFilenames[p]).getCanonicalPath()
								)
							)
							{
								System.err.println
								(
									"Warning: policy weights were saved for feature set " + featureSetFilepath 
									+ ", but we are now using " + currentFeatureSetFilenames[p]
								);
							}
						}
						catch (final IOException e)
						{
							e.printStackTrace();
						}
					}
					
					linearFunctions[p] = linearFunction;
				}
				
				return linearFunctions;
			}
			
			/**
			 * Creates (or loads) linear functions (one per player)
			 * @param featureSets
			 * @param crossEntropyFunctions CE-trained functions used for boosting
			 * @return
			 */
			private LinearFunction[] prepareTSPGFunctions
			(
				final FeatureSet[] featureSets, 
				final LinearFunction[] crossEntropyFunctions
			)
			{
				final LinearFunction[] linearFunctions = new LinearFunction[numPlayers + 1];
				
				if (trainTSPG)
				{
					for (int p = 1; p <= numPlayers; ++p)
					{
						final LinearFunction linearFunction;
						
						currentPolicyWeightsTSPGFilenames[p] = getFilenameLastCheckpoint("PolicyWeightsTSPG_P" + p, "txt");
						lastCheckpoint = 
								Math.min
								(
									lastCheckpoint,
									extractCheckpointFromFilename(currentPolicyWeightsTSPGFilenames[p], "PolicyWeightsTSPG_P" + p, "txt")
								);
						//System.out.println("TSPG funcs set lastCheckpoint = " + lastCheckpoint);
						
						if (currentPolicyWeightsTSPGFilenames[p] == null)
						{
							// create new boosted linear function
							linearFunction = 
									new BoostedLinearFunction
									(
										new FVector(featureSets[p].getNumFeatures()),
										crossEntropyFunctions[p]
									);
							logLine(logWriter, "starting with new 0-weights linear function for TSPG");
						}
						else
						{
							// load weights from file
							linearFunction = 
									BoostedLinearFunction.boostedFromFile
									(
										outDir.getAbsolutePath() + File.separator + currentPolicyWeightsTSPGFilenames[p],
										crossEntropyFunctions[p]
									);
							logLine(logWriter, "continuing with Selection policy weights loaded from " + currentPolicyWeightsTSPGFilenames[p]);
							
							try 
							{
								// make sure we're combining correct function with feature set
								String featureSetFilepath = 
										new File(outDir.getAbsolutePath() + File.separator + currentPolicyWeightsTSPGFilenames[p]).getParent();
								featureSetFilepath += File.separator + linearFunction.featureSetFile();
								
								if 
								(
									!new File(featureSetFilepath).getCanonicalPath().equals
									(
										new File(outDir.getAbsolutePath() + File.separator + currentFeatureSetFilenames[p]).getCanonicalPath()
									)
								)
								{
									System.err.println
									(
										"Warning: policy weights were saved for feature set " + featureSetFilepath 
										+ ", but we are now using " + currentFeatureSetFilenames[p]
									);
								}
							}
							catch (final IOException e)
							{
								e.printStackTrace();
							}
						}
						
						linearFunctions[p] = linearFunction;
					}
				}
				
				return linearFunctions;
			}
			
			/**
			 * Creates (or loads) linear functions (one per player) for CE Exploration
			 * @param featureSets
			 * @return
			 */
			private LinearFunction[] prepareCEExploreFunctions(final FeatureSet[] featureSets)
			{
				final LinearFunction[] linearFunctions = new LinearFunction[numPlayers + 1];
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					final LinearFunction linearFunction;
					
					currentPolicyWeightsCEEFilenames[p] = getFilenameLastCheckpoint("PolicyWeightsCEE_P" + p, "txt");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentPolicyWeightsCEEFilenames[p], "PolicyWeightsCEE_P" + p, "txt")
							);
					//System.out.println("CEE funcs set lastCheckpoint = " + lastCheckpoint);
					
					if (currentPolicyWeightsCEEFilenames[p] == null)
					{
						// create new linear function
						linearFunction = new LinearFunction(new FVector(featureSets[p].getNumFeatures()));
						logLine(logWriter, "starting with new 0-weights linear function for Cross-Entropy Exploration");
					}
					else
					{
						// load weights from file
						linearFunction = 
								LinearFunction.fromFile(outDir.getAbsolutePath() + File.separator + currentPolicyWeightsCEEFilenames[p]);
						logLine(logWriter, "continuing with Selection policy weights loaded from " + currentPolicyWeightsCEEFilenames[p]);
						
						try 
						{
							// make sure we're combining correct function with feature set
							String featureSetFilepath = 
									new File(outDir.getAbsolutePath() + File.separator + currentPolicyWeightsCEEFilenames[p]).getParent();
							featureSetFilepath += File.separator + linearFunction.featureSetFile();
							
							if 
							(
								!new File(featureSetFilepath).getCanonicalPath().equals
								(
									new File(outDir.getAbsolutePath() + File.separator + currentFeatureSetFilenames[p]).getCanonicalPath()
								)
							)
							{
								System.err.println
								(
									"Warning: CE Exploration policy weights were saved for feature set " + featureSetFilepath 
									+ ", but we are now using " + currentFeatureSetFilenames[p]
								);
							}
						}
						catch (final IOException e)
						{
							e.printStackTrace();
						}
					}
					
					linearFunctions[p] = linearFunction;
				}
				
				return linearFunctions;
			}
			
			/**
			 * Creates (or loads) value function
			 * @return
			 */
			private Heuristics prepareValueFunction()
			{
				if (noValueLearning)
					return null;
				
				Heuristics valueFunction = null;
				
				currentValueFunctionFilename = getFilenameLastCheckpoint("ValueFunction", "txt");
				lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentValueFunctionFilename, "ValueFunction", "txt")
							);
				final Report report = new Report();
				
				if (currentValueFunctionFilename == null)
				{
					if (bestAgentsDataDir != null)
					{
						// load heuristics from the best-agents-data dir
						try
						{
							final String descr = FileHandling.loadTextContentsFromFile(bestAgentsDataDir + "/BestHeuristics.txt");
							valueFunction = (Heuristics)language.compiler.Compiler.compileObject
											(
												descr, 
												"metadata.ai.heuristics.Heuristics",
												report
											);
							valueFunction.init(game);
						}
						catch (final IOException e)
						{
							e.printStackTrace();
						}
					}
					else
					{
						// copy value function from game metadata
						valueFunction = game.metadata().ai().heuristics();
						valueFunction.init(game);
						logLine(logWriter, "starting with new initial value function from .lud metadata");
					}
				}
				else
				{
					// load value function from file
					try
					{
						final String descr = FileHandling.loadTextContentsFromFile(
								outDir.getAbsolutePath() + File.separator + currentValueFunctionFilename);
						valueFunction = (Heuristics)language.compiler.Compiler.compileObject
										(
											descr, 
											"metadata.ai.heuristics.Heuristics",
											report
										);
						valueFunction.init(game);
					} 
					catch (final IOException e)
					{
						e.printStackTrace();
					}

					logLine
					(
						logWriter, 
						"continuing with value function from " + 
							outDir.getAbsolutePath() + File.separator + currentValueFunctionFilename
					);
				}
				
				return valueFunction;
			}
			
			/**
			 * Creates (or loads) feature sets (one per player)
			 * @return
			 */
			private FeatureSet[] prepareFeatureSets()
			{
				final FeatureSet[] featureSets = new FeatureSet[numPlayers + 1];
				final TIntArrayList newlyCreated = new TIntArrayList();
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					final FeatureSet featureSet;
					
					currentFeatureSetFilenames[p] = getFilenameLastCheckpoint("FeatureSet_P" + p, "fs");
					lastCheckpoint = 
							Math.min
							(
								lastCheckpoint,
								extractCheckpointFromFilename(currentFeatureSetFilenames[p], "FeatureSet_P" + p, "fs")
							);
					//System.out.println("Feature sets set lastCheckpoint = " + lastCheckpoint);
					
					if (currentFeatureSetFilenames[p] == null)
					{
						// create new Feature Set
						final AtomicFeatureGenerator atomicFeatures = new AtomicFeatureGenerator(game, 2, 4);
						featureSet = new FeatureSet(atomicFeatures.getFeatures());
						newlyCreated.add(p);
						logLine(logWriter, "starting with new initial feature set for Player " + p);
						logLine(logWriter, "num atomic features = " + featureSet.getNumFeatures());
					}
					else
					{
						// load feature set from file
						featureSet = new FeatureSet(outDir.getAbsolutePath() + File.separator + currentFeatureSetFilenames[p]);
						logLine
						(
							logWriter, 
							"continuing with feature set loaded from " + 
							outDir.getAbsolutePath() + File.separator + currentFeatureSetFilenames[p] +
							" for Player " + p
						);
					}
					
					if (featureSet.getNumFeatures() == 0)
					{
						System.err.println("ERROR: Feature Set has 0 features!");
						logLine(logWriter, "Training with 0 features makes no sense, interrupting experiment.");
						interrupted = true;
					}
					
					featureSet.instantiateFeatures(game, new int[]{p}, null);
					featureSets[p] = featureSet;
				}
				
				if (newlyCreated.size() > 0)
				{
					// we have some brand new feature sets; we'll likely have
					// obsolete features in there, and want to prune them
					
					// create matrices to store frequencies
					final long[][][] frequencies = new long[numPlayers + 1][][];
					
					for (int p = 1; p <= numPlayers; ++p)
					{
						final int numAtomicFeatures = featureSets[p].getNumFeatures();
						frequencies[p] = new long[numAtomicFeatures][numAtomicFeatures];
					}
					
					// play random games
					final Trial trial = new Trial(game);
					final Context context = new Context(game, trial);
					
					final long pruningGamesStartTime = System.currentTimeMillis();
					final long endTime = pruningGamesStartTime + maxNumPruningSeconds * 1000L;
					
					for (int gameCounter = 0; gameCounter < numPruningGames; ++gameCounter)
					{
						if (System.currentTimeMillis() > endTime)
							break;
						
						game.start(context);
						int numActions = 0;
						
						while (!context.trial().over())
						{
							final FastArrayList<Move> legal = game.moves(context).moves();
							final int mover = context.state().mover();
							
							if (newlyCreated.contains(mover))
							{
								final FeatureSet featureSet = featureSets[mover];
								
								// compute active feature indices for all actions
								final List<TIntArrayList> sparseFeatureVectors = 
										featureSet.computeSparseFeatureVectors(context, legal, false);
								
								// update frequencies matrix
								for (final TIntArrayList sparse : sparseFeatureVectors)
								{
									for (int i = 0; i < sparse.size(); ++i)
									{
										final int firstFeature = sparse.getQuick(i);
										
										// update diagonal
										frequencies[mover][firstFeature][firstFeature]++;
										
										for (int j = i + 1; j < sparse.size(); ++j)
										{
											final int secondFeature = sparse.getQuick(j);
											
											// update off-diagonals
											frequencies[mover][firstFeature][secondFeature]++;
											frequencies[mover][secondFeature][firstFeature]++;
										}
									}
								}
							}
							
							// apply random action
							final int r = ThreadLocalRandom.current().nextInt(legal.size());
							game.apply(context, legal.get(r));
							
							++numActions;
						}
					}
					
					// find features that we can safely remove for every newly created set
					for (int f = 0; f < newlyCreated.size(); ++f)
					{
						final int p = newlyCreated.getQuick(f);
						
						final TIntArrayList featuresToRemove = new TIntArrayList();
						final FeatureSet featureSet = featureSets[p];
						final int numAtomicFeatures = featureSet.getNumFeatures();
						
						for (int i = 0; i < numAtomicFeatures; ++i)
						{
							// only proceed if we didn't already decide to remove 
							// this feature
							if (featuresToRemove.contains(i))
								continue;
							
							final long soloCount = frequencies[p][i][i];
							
							// only proceed if we have enough observations for feature
							if (soloCount < pruneInitFeaturesThreshold)
								continue;
							
							for (int j = i + 1; j < numAtomicFeatures; ++j)
							{
								// only proceed if we didn't already decide to remove 
								// this feature
								if (featuresToRemove.contains(j))
									continue;
								
								if (soloCount == frequencies[p][i][j] && soloCount == frequencies[p][j][j])
								{
									// should remove the most complex of i and j
									final Feature firstFeature = featureSet.features()[i];
									final Feature secondFeature = featureSet.features()[j];
									final Pattern a = firstFeature.pattern();
									final Pattern b = secondFeature.pattern();
									
									// by default just keep the first feature if both
									// are equally "complex"
									boolean keepFirst = true;
									
									if (b.featureElements().size() < a.featureElements().size())
									{
										// fewer elements is simpler
										keepFirst = false;
									}
									else 
									{
										int sumWalkLengthsA = 0;
										for (final FeatureElement el : a.featureElements())
										{
											if (el instanceof RelativeFeatureElement)
											{
												final RelativeFeatureElement rel = (RelativeFeatureElement) el;
												sumWalkLengthsA += rel.walk().steps().size();
											}
										}
										
										int sumWalkLengthsB = 0;
										for (final FeatureElement el : b.featureElements())
										{
											if (el instanceof RelativeFeatureElement)
											{
												final RelativeFeatureElement rel = (RelativeFeatureElement) el;
												sumWalkLengthsB += rel.walk().steps().size();
											}
										}
										
										if (sumWalkLengthsB < sumWalkLengthsA)
										{
											// fewer steps in Walks is simpler
											keepFirst = false;
										}
									}
									
									if (keepFirst)
										featuresToRemove.add(j);
									else
										featuresToRemove.add(i);
								}
							}
						}
						
						// create new feature set
						final List<Feature> keepFeatures = new ArrayList<Feature>();
						for (int i = 0; i < numAtomicFeatures; ++i)
						{
							if (!featuresToRemove.contains(i))
								keepFeatures.add(featureSet.features()[i]);
						}
						final FeatureSet newFeatureSet = new FeatureSet(keepFeatures);
						newFeatureSet.instantiateFeatures(game, new int[]{p}, null);
						featureSets[p] = newFeatureSet;
						
						logLine(logWriter, "Finished pruning atomic feature set for Player " + p);
						logLine(logWriter, "Num atomic features after pruning = " + newFeatureSet.getNumFeatures());
					}
				}
				
				return featureSets;
			}
			
			//-----------------------------------------------------------------
			
			// TODO make sure we can also correctly combine reactive features
			
			// TODO should put this monster into a class of its own
			public FeatureSet expandFeatureSetCorrelationBased
			(
				final ExItExperience[] batch,
				final FeatureSet featureSet,
				final SoftmaxPolicy crossEntropyPolicy,
				final Game g,
				final int featureDiscoveryMaxNumFeatureInstances
			)
			{	
				// we need a matrix C_f with, at every entry (i, j), 
				// the sum of cases in which features i and j are both active
				//
				// we also need a similar matrix C_e with, at every entry (i, j),
				// the sum of errors in cases where features i and j are both active
				//
				// NOTE: both of the above are symmetric matrices
				//
				// we also need a vector X_f with, for every feature i, the sum of
				// cases where feature i is active. (we would need a similar vector
				// with sums of squares, but that can be omitted because our features
				// are binary)
				//
				// NOTE: in writing it is easier to mention X_f as described above as
				// a separate vector, but actually we don't need it; we can simply
				// use the main diagonal of C_f instead
				//
				// we need a scalar S which is the sum of all errors,
				// and a scalar SS which is the sum of all squared errors
				//
				// Given a candidate pair of features (i, j), we can compute the
				// correlation of that pair of features with the errors (an
				// indicator of the candidate pair's potential to be useful) as
				// (where n = the number of cases = number of state-action pairs):
				//
				//
				//                 n * C_e(i, j) - C_f(i, j) * S
				//------------------------------------------------------------
				//  SQRT( n * C_f(i, j) - C_f(i, j)^2 ) * SQRT( n * SS - S^2 )
				//
				//
				// For numeric stability with extremely large batch sizes, 
				// it is better to compute this as:
				// 
				//                 n * C_e(i, j) - C_f(i, j) * S
				//------------------------------------------------------------
				//  SQRT( C_f(i, j) * (n - C_f(i, j)) ) * SQRT( n * SS - S^2 )
				//
				//
				// We can similarly compare the correlation between any candidate pair 
				// of features (i, j) and either of its constituents i (an indicator
				// of redundancy of the candidate pair) as:
				//
				//
				//                 n * C_f(i, j) - C_f(i, j) * X_f(i)
				//--------------------------------------------------------------------
				//  SQRT( n * C_f(i, j) - C_f(i, j)^2 ) * SQRT( n * X_f(i) - X_f(i)^2 )
				//
				//
				// For numeric stability with extremely large batch sizes, 
				// it is better to compute this as:
				//
				//                C_f(i, j) * (n - X_f(i))
				//--------------------------------------------------------------------
				//  SQRT( C_f(i, j) * (n - C_f(i, j)) ) * SQRT( X_f(i) * (n - X_f(i)) )
				//
				//
				// (note; even better would be if we could compute correlation between
				// candidate pair and ANY other feature, rather than just its
				// constituents, but that would probably become too expensive)
				//
				// We want to maximise the absolute value of the first (correlation
				// between candidate feature pair and distribution error), but minimise
				// the worst-case absolute value of the second (correlation between 
				// candidate feature pair and either of its constituents).
				//
				//
				// NOTE: in all of the above, when we say "feature", we actually
				// refer to a particular instantiation of a feature. This is important,
				// because otherwise we wouldn't be able to merge different
				// instantiations of the same feature into a more complex new feature
				//
				// Due to the large amount of possible pairings when considering
				// feature instances, we implement our "matrices" using hash tables,
				// and automatically ignore entries that would be 0 (they won't
				// be created if such pairs are never observed activating together)
				
				int numCases = 0;	// we'll increment  this as we go
				
				// this is our C_f matrix
				final TObjectIntHashMap<CombinableFeatureInstancePair> featurePairActivations = 
						new TObjectIntHashMap<CombinableFeatureInstancePair>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0);
				
				// this is our C_e matrix
				final TObjectDoubleHashMap<CombinableFeatureInstancePair> errorSums = 
						new TObjectDoubleHashMap<CombinableFeatureInstancePair>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0.0);
				
				// these are our S and SS scalars
				double sumErrors = 0.0;
				double sumSquaredErrors = 0.0;
				
				// similar to some of the variables above, but specific for selection/playout errors
				// these are only used for initial value of new feature, not for determining features to combine
				final HashMap<CombinableFeatureInstancePair, Double> selectionErrorSums = 
						new HashMap<CombinableFeatureInstancePair, Double>();
				final double sumSelectionErrors = 0.0;
				final double sumSquaredSelectionErrors = 0.0;
				
				final HashMap<CombinableFeatureInstancePair, Double> playoutErrorSums = 
						new HashMap<CombinableFeatureInstancePair, Double>();
				final double sumPlayoutErrors = 0.0;
				final double sumSquaredPlayoutErrors = 0.0;
				
				// loop through all samples in batch
				for (final ExItExperience sample : batch)
				{
					final List<TIntArrayList> sparseFeatureVectors = 
							featureSet.computeSparseFeatureVectors
							(
								sample.state().state(), 
								sample.state().lastDecisionMove(), 
								sample.moves(), 
								false
							);
					
					final FVector errors = 
							crossEntropyPolicy.computeDistributionErrors
							(
								crossEntropyPolicy.computeDistribution
								(
									sparseFeatureVectors, sample.state().state().mover()
								), 
								sample.expertDistribution()
							);
					
					// create a Hash Set of features already in Feature Set; we won't
					// have to consider combinations that are already in
					final Set<Feature> existingFeatures = 
							new HashSet<Feature>
							(
								(int) Math.ceil(featureSet.getNumFeatures() / 0.75f), 
								0.75f
							);
					
					for (final Feature feature : featureSet.features())
					{
						existingFeatures.add(feature);
					}
					
					// every action in the sample is a new "case" (state-action pair)
					for (int a = 0; a < sample.moves().size(); ++a)
					{
						++numCases;
						
						// keep track of pairs we've already seen in this "case"
						final Set<CombinableFeatureInstancePair> observedCasePairs = 
								new HashSet<CombinableFeatureInstancePair>(256, .75f);
						
						List<FeatureInstance> activeInstances = 
								featureSet.getActiveFeatureInstances
								(
									sample.state().state(), 
									FeatureUtils.fromPos(sample.state().lastDecisionMove()), 
									FeatureUtils.toPos(sample.state().lastDecisionMove()), 
									FeatureUtils.fromPos(sample.moves().get(a)), 
									FeatureUtils.toPos(sample.moves().get(a)),
									sample.moves().get(a).mover()
								);
						
						if (activeInstances.size() > featureDiscoveryMaxNumFeatureInstances)
						{
							// too many active feature instances, we'll sort them
							// by absolute weights and only consider combining the top ones
							activeInstances.sort(new Comparator<FeatureInstance>() 
							{

								@Override
								public int compare
								(
									final FeatureInstance instanceA,
									final FeatureInstance instanceB
								) 
								{
									final int featureIdxA = instanceA.feature().featureSetIndex();
									final int featureIdxB = instanceB.feature().featureSetIndex();
									
									final float absWeightA = 
											Math.abs
											(
												crossEntropyPolicy.linearFunction(sample.state().state().mover())
												.effectiveParams().get(featureIdxA)
											);
									final float absWeightB = 
											Math.abs
											(
												crossEntropyPolicy.linearFunction(sample.state().state().mover())
												.effectiveParams().get(featureIdxB)
											);

									
									if (absWeightA == absWeightB)
										return 0;
									else if (absWeightA > absWeightB)
										return -1;
									else
										return 1;
								}
								
							});
							
							activeInstances = activeInstances.subList(0, featureDiscoveryMaxNumFeatureInstances);
						}
						
						final int numActiveInstances = activeInstances.size();
						
						final float error = errors.get(a);
						sumErrors += error;
						sumSquaredErrors += error * error;
						
						for (int i = 0; i < numActiveInstances; ++i)
						{
							final FeatureInstance instanceI = activeInstances.get(i);
							
							// increment entries on ''main diagonals''
							final CombinableFeatureInstancePair combinedSelf = 
									new CombinableFeatureInstancePair(g, instanceI, instanceI);
												
							if (!observedCasePairs.contains(combinedSelf))
							{
								featurePairActivations.put
								(
									combinedSelf, 
									featurePairActivations.get(combinedSelf) + 1
								);
								errorSums.put
								(
									combinedSelf, 
									errorSums.get(combinedSelf) + error
								);
								
								observedCasePairs.add(combinedSelf);
							}
												
							for (int j = i + 1; j < numActiveInstances; ++j)
							{
								final FeatureInstance instanceJ = activeInstances.get(j);
								
								// increment off-diagonal entries
								final CombinableFeatureInstancePair combined = 
										new CombinableFeatureInstancePair(g, instanceI, instanceJ);
								
								if (!existingFeatures.contains(combined.combinedFeature))
								{
									if (!observedCasePairs.contains(combined))
									{
										featurePairActivations.put
										(
											combined, 
											featurePairActivations.get(combined) + 1
										);
										errorSums.put
										(
											combined, 
											errorSums.get(combined) + error
										);
										
										observedCasePairs.add(combined);
									}
								}
							}
						}
					}
				}
				
				if (sumErrors == 0.0 || sumSquaredErrors == 0.0)
				{
					// incredibly rare case but appears to be possible in Fanorona
					// we have nothing to guide our feature growing, so let's 
					// just refuse to add a feature
					return null;
				}
				
				// construct all possible pairs and scores
				// as we go, keep track of best score and index at which we can find it
				final List<ScoredPair> scoredPairs = new ArrayList<ScoredPair>(featurePairActivations.size());
				double bestScore = Double.NEGATIVE_INFINITY;
				int bestPairIdx = -1;
				
				for (final CombinableFeatureInstancePair pair : featurePairActivations.keySet())
				{
					if (! pair.a.equals(pair.b))
					{
						// only interested in combinations of different instances
						final int actsI = 
								featurePairActivations.get
								(
									new CombinableFeatureInstancePair(g, pair.a, pair.a)
								);
						
						final int actsJ = 
								featurePairActivations.get
								(
									new CombinableFeatureInstancePair(g, pair.b, pair.b)
								);
						
						final int pairActs = featurePairActivations.get(pair);
						if (pairActs == numCases)
						{
							// Perfect correlation, so we should just skip this one
							continue;
						}
						
						if (actsI == numCases || actsJ == numCases)
						{
							// Perfect correlation, so we should just skip this one
							continue;
						}
						
						final double pairErrorSum = errorSums.get(pair);
						
						final double errorCorr = 
								(
									(numCases * pairErrorSum - pairActs * sumErrors) 
									/ 
									(
										Math.sqrt(pairActs * (numCases - pairActs)) * 
										Math.sqrt(numCases * sumSquaredErrors - sumErrors * sumErrors)
									)
								);
						
						final double featureCorrI = 
								(
									(pairActs * (numCases - actsI)) 
									/ 
									(
										Math.sqrt(pairActs * (numCases - pairActs)) * 
										Math.sqrt(actsI * (numCases - actsI))
									)
								);
						
						final double featureCorrJ = 
								(
									(pairActs * (numCases - actsJ)) 
									/ 
									(
										Math.sqrt(pairActs * (numCases - pairActs)) * 
										Math.sqrt(actsJ * (numCases - actsJ))
									)
								);
						
						final double worstFeatureCorr = 
								Math.max
								(
									Math.abs(featureCorrI), 
									Math.abs(featureCorrJ)
								);
						
						final double score = Math.abs(errorCorr) * (1.0 - worstFeatureCorr);
						
						if (Double.isNaN(score))
						{
							continue;
//							System.err.println("numCases = " + numCases);
//							System.err.println("pairActs = " + pairActs);
//							System.err.println("actsI = " + actsI);
//							System.err.println("actsJ = " + actsJ);
//							System.err.println("sumErrors = " + sumErrors);
//							System.err.println("sumSquaredErrors = " + sumSquaredErrors);
						}
						
						scoredPairs.add(new ScoredPair(pair, score));
						if (score > bestScore)
						{
							bestScore = score;
							bestPairIdx = scoredPairs.size() - 1;
						}
					}
				}
				
				// keep trying to generate an expanded (by one) feature set, until
				// we succeed (almost always this should be on the very first iteration)
				while (scoredPairs.size() > 0)
				{
					// extract pair of feature instances we want to try combining
					final ScoredPair bestPair = scoredPairs.remove(bestPairIdx);
					
					final FeatureSet newFeatureSet = 
							featureSet.createExpandedFeatureSet(g, bestPair.pair.a, bestPair.pair.b);
					
					if (newFeatureSet != null)
					{
						final int actsI = 
								featurePairActivations.get
								(
									new CombinableFeatureInstancePair(g, bestPair.pair.a, bestPair.pair.a)
								);
						
						final int actsJ = 
								featurePairActivations.get
								(
									new CombinableFeatureInstancePair(g, bestPair.pair.b, bestPair.pair.b)
								);
						
						final int pairActs = 
								featurePairActivations.get
								(
									new CombinableFeatureInstancePair(g, bestPair.pair.a, bestPair.pair.b)
								);
						
						final double pairErrorSum = 
								errorSums.get
								(
									new CombinableFeatureInstancePair(g, bestPair.pair.a, bestPair.pair.b)
								);
						
						final double errorCorr = 
								(
									(numCases * pairErrorSum - pairActs * sumErrors) 
									/ 
									(
										Math.sqrt(numCases * pairActs - pairActs * pairActs) * 
										Math.sqrt(numCases * sumSquaredErrors - sumErrors * sumErrors)
									)
								);
						
						final double featureCorrI = 
								(
									(numCases * pairActs - pairActs * actsI) 
									/ 
									(
										Math.sqrt(numCases * pairActs - pairActs * pairActs) * 
										Math.sqrt(numCases * actsI - actsI * actsI)
									)
								);
						
						final double featureCorrJ = 
								(
									(numCases * pairActs - pairActs * actsJ) 
									/ 
									(
										Math.sqrt(numCases * pairActs - pairActs * pairActs) * 
										Math.sqrt(numCases * actsJ - actsJ * actsJ)
									)
								);
						
						logLine(logWriter, "New feature added!");
						logLine(logWriter, "new feature = " + newFeatureSet.features()[newFeatureSet.getNumFeatures() - 1]);
						logLine(logWriter, "active feature A = " + bestPair.pair.a.feature());
						logLine(logWriter, "rot A = " + bestPair.pair.a.rotation());
						logLine(logWriter, "ref A = " + bestPair.pair.a.reflection());
						logLine(logWriter, "anchor A = " + bestPair.pair.a.anchorSite());
						logLine(logWriter, "active feature B = " + bestPair.pair.b.feature());
						logLine(logWriter, "rot B = " + bestPair.pair.b.rotation());
						logLine(logWriter, "ref B = " + bestPair.pair.b.reflection());
						logLine(logWriter, "anchor B = " + bestPair.pair.b.anchorSite());
						logLine(logWriter, "score = " + bestPair.score);
						logLine(logWriter, "correlation with errors = " + errorCorr);
						logLine(logWriter, "correlation with first constituent = " + featureCorrI);
						logLine(logWriter, "correlation with second constituent = " + featureCorrJ);
						logLine(logWriter, "observed pair of instances " + pairActs + " times");
						logLine(logWriter, "observed first constituent " + actsI + " times");
						logLine(logWriter, "observed second constituent " + actsJ + " times");
						
						return newFeatureSet;
					}
					
					// if we reach this point, it means we failed to create an
					// expanded feature set with our top-score pair.
					// so, we should search again for the next best pair
					bestScore = Double.NEGATIVE_INFINITY;
					bestPairIdx = -1;
					
					for (int i = 0; i < scoredPairs.size(); ++i)
					{
						if (scoredPairs.get(i).score > bestScore)
						{
							bestScore = scoredPairs.get(i).score;
							bestPairIdx = i;
						}
					}
				}
				
				return null;
			}
			
			//-----------------------------------------------------------------
			
			/**
			 * Wrapper class for a pair of combined feature instances and a score
			 * 
			 * @author Dennis Soemers
			 */
			final class ScoredPair
			{
				/** First int */
				public final CombinableFeatureInstancePair pair;
				
				/** Score */
				public final double score;
				
				/**
				 * Constructor
				 * @param score
				 */
				public ScoredPair(final CombinableFeatureInstancePair pair, final double score)
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
				protected final Feature combinedFeature;
				
				/** Cached hash code */
				private int cachedHash = Integer.MIN_VALUE;
				
				/**
				 * Constructor
				 * @param g
				 * @param a
				 * @param b
				 */
				public CombinableFeatureInstancePair
				(
					final Game g,
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
						combinedFeature = Feature.combineFeatures(g, a, b);
					}
					else if (b.feature().featureSetIndex() < a.feature().featureSetIndex())
					{
						combinedFeature = Feature.combineFeatures(g, b, a);
					}
					else
					{
						if (a.reflection() > b.reflection())
						{
							combinedFeature = Feature.combineFeatures(g, a, b);
						}
						else if (b.reflection() > a.reflection())
						{
							combinedFeature = Feature.combineFeatures(g, b, a);
						}
						else
						{
							if (a.rotation() < b.rotation())
							{
								combinedFeature = Feature.combineFeatures(g, a, b);
							}
							else if (b.rotation() < a.rotation())
							{
								combinedFeature = Feature.combineFeatures(g, b, a);
							}
							else
							{
								if (a.anchorSite() < b.anchorSite())
									combinedFeature = Feature.combineFeatures(g, a, b);
								else if (b.anchorSite() < a.anchorSite())
									combinedFeature = Feature.combineFeatures(g, b, a);
								else
									combinedFeature = Feature.combineFeatures(g, a, b);
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
			
			//-----------------------------------------------------------------
			
			/**
			 * @return When should the next checkpoint be?
			 */
			private long computeNextCheckpoint()
			{
				if (lastCheckpoint < 0L)
					return 0L;
				else
					return lastCheckpoint + checkpointFrequency;
			}
			
			/**
			 * Creates a filename for a given checkpoint
			 * @param baseFilename
			 * @param checkpoint
			 * @param extension
			 * @return
			 */
			private String createCheckpointFilename
			(
				final String baseFilename,
				final long checkpoint,
				final String extension
			)
			{
				final String format = (checkpointType == CheckpointTypes.Game) 
						? gameCheckpointFormat : weightUpdateCheckpointFormat;
				
				return String.format(format, baseFilename, Long.valueOf(checkpoint), extension);
			}
			
			/**
			 * @param baseFilename
			 * @param extension
			 * @return Checkpoint extracted from existing filename
			 */
			private int extractCheckpointFromFilename
			(
				final String filename,
				final String baseFilename,
				final String extension
			)
			{
				if (filename == null)
					return -1;
				
				final String checkpoint = 
						filename.substring
						(
							(baseFilename + "_").length(), 
							filename.length() - ("." + extension).length()
						);
				
				return Integer.parseInt(checkpoint);
			}
			
			/**
			 * Computes a filename for the last checkpoint
			 * @param baseFilename
			 * @param extension
			 * @return Computed filepath, or null if none saved yet
			 */
			private String getFilenameLastCheckpoint
			(
				final String baseFilename,
				final String extension
			)
			{
				if (outDir == null)
					return null;
				
				final String[] filenames = outDir.list();
				int maxCheckpoint = -1;
				
				for (final String filename : filenames)
				{
					if 
					(
						filename.startsWith(baseFilename + "_") && 
						filename.endsWith("." + extension)
					)
					{
						final int checkpoint = extractCheckpointFromFilename(filename, baseFilename, extension);
						if (checkpoint > maxCheckpoint)
							maxCheckpoint = checkpoint;
					}
				}
				
				if (maxCheckpoint < 0)
					return null;
				
				return createCheckpointFilename(baseFilename, maxCheckpoint, extension);
			}
			
			/**
			 * Saves checkpoints (if we want to or are forced to)
			 * @param gameCounter
			 * @param weightsUpdateCounter
			 * @param featureSets
			 * @param crossEntropyFunctions
			 * @param tspgFunctions
			 * @param ceExploreFunctions
			 * @param experienceBuffers
			 * @param ceOptimisers
			 * @param tspgOptimisers
			 * @param ceeOptimisers
			 * @param valueFunctionOptimiser
			 * @param avgGameDurations
			 * @param forced
			 */
			private void saveCheckpoints
			(
				final int gameCounter, 
				final long weightsUpdateCounter,
				final FeatureSet[] featureSets, 
				final LinearFunction[] crossEntropyFunctions,
				final LinearFunction[] tspgFunctions,
				final LinearFunction[] ceExploreFunctions,
				final Heuristics valueFunction,
				final ExperienceBuffer[] experienceBuffers,
				final Optimiser[] ceOptimisers,
				final Optimiser[] tspgOptimisers,
				final Optimiser[] ceeOptimisers,
				final Optimiser valueFunctionOptimiser,
				final ExponentialMovingAverage[] avgGameDurations,
				final boolean forced
			)
			{
				long nextCheckpoint = computeNextCheckpoint();
				
				if (checkpointType == CheckpointTypes.Game)
				{
					if (!forced && gameCounter < nextCheckpoint)
						return;
					else
						nextCheckpoint = gameCounter;
				}
				else if (checkpointType == CheckpointTypes.WeightUpdate)
				{
					if (!forced && weightsUpdateCounter < nextCheckpoint)
						return;
					else
						nextCheckpoint = weightsUpdateCounter;
				}
				
				for (int p = 1; p <= numPlayers; ++p)
				{
					// save feature set
					final String featureSetFilename = createCheckpointFilename("FeatureSet_P" + p, nextCheckpoint, "fs");
					featureSets[p].toFile(outDir.getAbsolutePath() + File.separator + featureSetFilename);
					currentFeatureSetFilenames[p] = featureSetFilename;
					
					// save CE weights
					final String ceWeightsFilename = createCheckpointFilename("PolicyWeightsCE_P" + p, nextCheckpoint, "txt");
					crossEntropyFunctions[p].writeToFile(
							outDir.getAbsolutePath() + File.separator + ceWeightsFilename, new String[]{currentFeatureSetFilenames[p]});
					currentPolicyWeightsCEFilenames[p] = ceWeightsFilename;
					
					// save TSPG weights
					if (trainTSPG)
					{
						final String tspgWeightsFilename = createCheckpointFilename("PolicyWeightsTSPG_P" + p, nextCheckpoint, "txt");
						tspgFunctions[p].writeToFile(
								outDir.getAbsolutePath() + File.separator + tspgWeightsFilename, new String[]{currentFeatureSetFilenames[p]});
						currentPolicyWeightsTSPGFilenames[p] = tspgWeightsFilename;
					}
					
					// save CE Exploration policy weights
					if (ceExplore && !ceExploreUniform)
					{
						final String ceExploreWeightsFilename = createCheckpointFilename("PolicyWeightsCEE_P" + p, nextCheckpoint, "txt");
						ceExploreFunctions[p].writeToFile(
								outDir.getAbsolutePath() + File.separator + ceExploreWeightsFilename, new String[]{currentFeatureSetFilenames[p]});
						currentPolicyWeightsCEEFilenames[p] = ceExploreWeightsFilename;
					}
					
					if (valueFunction != null)
					{
						// save Value function
						final String valueFunctionFilename = createCheckpointFilename("ValueFunction", nextCheckpoint, "txt");
						valueFunction.toFile(game, outDir.getAbsolutePath() + File.separator + valueFunctionFilename);
					}

					if (forced)
					{
						// in this case, we'll also store experience buffers
						final String experienceBufferFilename = createCheckpointFilename("ExperienceBuffer_P" + p, nextCheckpoint, "buf");
						experienceBuffers[p].writeToFile(outDir.getAbsolutePath() + File.separator + experienceBufferFilename);
						
						// and optimisers
						final String ceOptimiserFilename = createCheckpointFilename("OptimiserCE_P" + p, nextCheckpoint, "opt");
						ceOptimisers[p].writeToFile(outDir.getAbsolutePath() + File.separator + ceOptimiserFilename);
						currentOptimiserCEFilenames[p] = ceOptimiserFilename;
						
						if (trainTSPG)
						{
							final String tspgOptimiserFilename = createCheckpointFilename("OptimiserTSPG_P" + p, nextCheckpoint, "opt");
							tspgOptimisers[p].writeToFile(outDir.getAbsolutePath() + File.separator + tspgOptimiserFilename);
							currentOptimiserTSPGFilenames[p] = tspgOptimiserFilename;
						}
						
						if (ceExplore && !ceExploreUniform)
						{
							final String ceExploreOptimiserFilename = createCheckpointFilename("OptimiserCEE_P" + p, nextCheckpoint, "opt");
							ceeOptimisers[p].writeToFile(outDir.getAbsolutePath() + File.separator + ceExploreOptimiserFilename);
							currentOptimiserCEEFilenames[p] = ceExploreOptimiserFilename;
						}
						
						// and average game duration trackers
						final String gameDurationTrackerFilename = createCheckpointFilename("GameDurationTracker_P" + p, nextCheckpoint, "bin");
						avgGameDurations[p].writeToFile(outDir.getAbsolutePath() + File.separator + gameDurationTrackerFilename);
						currentGameDurationTrackerFilenames[p] = gameDurationTrackerFilename;
					}
				}
				
				if (forced)
				{
					final String valueOptimiserFilename = createCheckpointFilename("OptimiserValue", nextCheckpoint, "opt");
					valueFunctionOptimiser.writeToFile(outDir.getAbsolutePath() + File.separator + valueOptimiserFilename);
					currentOptimiserValueFilename = valueOptimiserFilename;
				}
				
				lastCheckpoint = nextCheckpoint;
			}
			
			//-----------------------------------------------------------------
			
			@Override
			public void logLine(final PrintWriter log, final String line)
			{
				if (!noLogging)
					super.logLine(log, line);
			}
			
			//-----------------------------------------------------------------
		};
		
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Creates a writer for output log, or null if we don't want one
	 * @return
	 */
	private PrintWriter createLogWriter()
	{
		if (outDir != null && !noLogging)
		{
			final String nextLogFilepath = 
					ExperimentFileUtils.getNextFilepath(outDir.getAbsolutePath() + File.separator + "ExIt", "log");
			
			// first create directories if necessary
			new File(nextLogFilepath).getParentFile().mkdirs();
			
			try
			{
				return new PrintWriter(nextLogFilepath, "UTF-8");
			}
			catch (final FileNotFoundException | UnsupportedEncodingException e)
			{
				e.printStackTrace();
				return null;
			}
		}
		else
		{
			return null;
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Can be used for quick testing without command-line args, or proper
	 * testing with elaborate setup through command-line args
	 * @param args
	 */
	@SuppressWarnings("unchecked")
	public static void main(final String[] args)
	{		
		// define options for arg parser
		final CommandLineArgParse argParse = 
				new CommandLineArgParse
				(
					true,
					"Execute a training run from self-play using Expert Iteration."
				);
		
		argParse.addOption(new ArgOption()
				.withNames("--game")
				.help("Name of the game to play. Should end with \".lud\".")
				.withDefault("board/space/blocking/Amazons.lud")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--game-options")
				.help("Game Options to load.")
				.withDefault(new ArrayList<String>(0))
				.withNumVals("*")
				.withType(OptionTypes.String));
		
		argParse.addOption(new ArgOption()
				.withNames("--expert-ai")
				.help("Type of AI to use as expert.")
				.withDefault("BEST_AGENT")
				.withNumVals(1)
				.withType(OptionTypes.String)
				.withLegalVals("BEST_AGENT", "FROM_METADATA", "Biased MCTS"));
		argParse.addOption(new ArgOption()
				.withNames("--best-agents-data-dir")
				.help("Filepath for directory with best agents data for this game (+ options).")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("-n", "--num-games", "--num-training-games")
				.help("Number of training games to run.")
				.withDefault(Integer.valueOf(200))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--game-length-cap", "--max-num-actions")
				.help("Maximum number of actions that may be taken before a game is terminated as a draw (-1 for no limit).")
				.withDefault(Integer.valueOf(-1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--thinking-time", "--time", "--seconds")
				.help("Max allowed thinking time per move (in seconds).")
				.withDefault(Double.valueOf(1.0))
				.withNumVals(1)
				.withType(OptionTypes.Double));
		argParse.addOption(new ArgOption()
				.withNames("--iteration-limit", "--iterations")
				.help("Max allowed number of MCTS iterations per move.")
				.withDefault(Integer.valueOf(-1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--depth-limit")
				.help("Search depth limit (e.g. for Alpha-Beta experts).")
				.withDefault(Integer.valueOf(-1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		
		argParse.addOption(new ArgOption()
				.withNames("--add-feature-every")
				.help("After this many training games, we add a new feature.")
				.withDefault(Integer.valueOf(1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--batch-size")
				.help("Max size of minibatches in training.")
				.withDefault(Integer.valueOf(30))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--buffer-size", "--experience-buffer-size")
				.help("Max size of the experience buffer.")
				.withDefault(Integer.valueOf(2500))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--update-weights-every")
				.help("After this many moves (decision points) in training games, we update weights.")
				.withDefault(Integer.valueOf(1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--no-grow-features", "--no-grow-featureset", "--no-grow-feature-set")
				.help("If true, we'll not grow feature set (but still train weights).")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--train-tspg")
				.help("If true, we'll train a policy on TSPG objective (see COG paper).")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--ce-optimiser", "--cross-entropy-optimiser")
				.help("Optimiser to use for policy trained on Cross-Entropy loss.")
				.withDefault("RMSProp")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--cee-optimiser", "--cross-entropy-exploration-optimiser")
				.help("Optimiser to use for training Cross-Entropy Exploration policy.")
				.withDefault("RMSProp")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--tspg-optimiser")
				.help("Optimiser to use for policy trained on TSPG objective (see COG paper).")
				.withDefault("RMSProp")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--value-optimiser")
				.help("Optimiser to use for value function optimisation.")
				.withDefault("RMSProp")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--combining-feature-instance-threshold")
				.help("At most this number of feature instances will be taken into account when combining features.")
				.withDefault(Integer.valueOf(75))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--is-episode-durations")
				.help("If true, we'll use importance sampling weights based on episode durations for CE-loss.")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--prioritized-experience-replay", "--per")
				.help("If true, we'll use prioritized experience replay")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--ce-explore")
				.help("If true, we'll use extra exploration based on cross-entropy losses")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--ce-explore-mix")
				.help("Proportion of exploration policy in our behaviour mix")
				.withDefault(Float.valueOf(0.1f))
				.withNumVals(1)
				.withType(OptionTypes.Float));
		argParse.addOption(new ArgOption()
				.withNames("--ce-explore-gamma")
				.help("Discount factor gamma for rewards awarded to CE Explore policy")
				.withDefault(Double.valueOf(0.99))
				.withNumVals(1)
				.withType(OptionTypes.Double));
		argParse.addOption(new ArgOption()
				.withNames("--ce-explore-uniform")
				.help("If true, our CE Explore policy will not be trained, but remain completely uniform")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--no-ce-explore-is")
				.help("If true, we ignore importance sampling when doing CE Exploration")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--wis", "--weighted-importance-sampling")
				.help("If true, we use Weighted Importance Sampling instead of Ordinary Importance Sampling for any of the above")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--no-value-learning")
				.help("If true, we don't do any value function learning.")
				.withType(OptionTypes.Boolean));

		argParse.addOption(new ArgOption()
				.withNames("--max-biased-playout-actions", "--max-num-biased-playout-actions")
				.help("Maximum number of actions per playout which we'll bias using features (-1 for no limit).")
				.withDefault(Integer.valueOf(-1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		
		argParse.addOption(new ArgOption()
				.withNames("--no-prune-init-features")
				.help("If true, we will keep full atomic feature set and not prune anything.")
				.withType(OptionTypes.Boolean));
		argParse.addOption(new ArgOption()
				.withNames("--prune-init-features-threshold")
				.help("Will only consider pruning features if they have been active at least this many times.")
				.withDefault(Integer.valueOf(50))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--num-pruning-games")
				.help("Number of random games to play out for determining features to prune.")
				.withDefault(Integer.valueOf(1500))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--max-pruning-seconds")
				.help("Maximum number of seconds to spend on random games for pruning initial featureset.")
				.withDefault(Integer.valueOf(60))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		
		argParse.addOption(new ArgOption()
				.withNames("--checkpoint-type", "--checkpoints")
				.help("When do we store checkpoints of trained weights?")
				.withDefault(CheckpointTypes.Game.toString())
				.withNumVals(1)
				.withType(OptionTypes.String)
				.withLegalVals(Arrays.stream(CheckpointTypes.values()).map(Object::toString).toArray()));
		argParse.addOption(new ArgOption()
				.withNames("--checkpoint-freq", "--checkpoint-frequency")
				.help("Frequency of checkpoint updates")
				.withDefault(Integer.valueOf(1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--out-dir", "--output-directory")
				.help("Filepath for output directory")
				.withNumVals(1)
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--no-logging")
				.help("If true, we don't write a bunch of messages to a log file.")
				.withType(OptionTypes.Boolean));
		
		argParse.addOption(new ArgOption()
				.withNames("--useGUI")
				.help("Whether to create a small GUI that can be used to "
						+ "manually interrupt training run. False by default."));
		argParse.addOption(new ArgOption()
				.withNames("--max-wall-time")
				.help("Max wall time in minutes (or -1 for no limit).")
				.withDefault(Integer.valueOf(-1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		
		// parse the args
		if (!argParse.parseArguments(args))
			return;
		
		// use the parsed args
		final ExpertIteration exIt = 
				new ExpertIteration
				(
					argParse.getValueBool("--useGUI"), 
					argParse.getValueInt("--max-wall-time")
				);
		
		exIt.gameName = argParse.getValueString("--game");
		exIt.gameOptions = (List<String>) argParse.getValue("--game-options"); 
		
		exIt.expertAI = argParse.getValueString("--expert-ai");
		exIt.bestAgentsDataDir = argParse.getValueString("--best-agents-data-dir");
		exIt.numTrainingGames = argParse.getValueInt("-n");
		exIt.gameLengthCap = argParse.getValueInt("--game-length-cap");
		exIt.thinkingTime = argParse.getValueDouble("--thinking-time");
		exIt.iterationLimit = argParse.getValueInt("--iteration-limit");
		exIt.depthLimit = argParse.getValueInt("--depth-limit");
		
		exIt.addFeatureEvery = argParse.getValueInt("--add-feature-every");
		exIt.batchSize = argParse.getValueInt("--batch-size");
		exIt.experienceBufferSize = argParse.getValueInt("--buffer-size");
		exIt.updateWeightsEvery = argParse.getValueInt("--update-weights-every");
		exIt.noGrowFeatureSet = argParse.getValueBool("--no-grow-features");
		exIt.trainTSPG = argParse.getValueBool("--train-tspg");
		exIt.crossEntropyOptimiserConfig = argParse.getValueString("--ce-optimiser");
		exIt.ceExploreOptimiserConfig = argParse.getValueString("--cee-optimiser");
		exIt.tspgOptimiserConfig = argParse.getValueString("--tspg-optimiser");
		exIt.valueOptimiserConfig = argParse.getValueString("--value-optimiser");
		exIt.combiningFeatureInstanceThreshold = argParse.getValueInt("--combining-feature-instance-threshold");
		exIt.importanceSamplingEpisodeDurations = argParse.getValueBool("--is-episode-durations");
		exIt.prioritizedExperienceReplay = argParse.getValueBool("--prioritized-experience-replay");
		exIt.ceExplore = argParse.getValueBool("--ce-explore");
		exIt.ceExploreMix = argParse.getValueFloat("--ce-explore-mix");
		exIt.ceExploreGamma = argParse.getValueDouble("--ce-explore-gamma");
		exIt.ceExploreUniform = argParse.getValueBool("--ce-explore-uniform");
		exIt.noCEExploreIS = argParse.getValueBool("--no-ce-explore-is");
		exIt.weightedImportanceSampling = argParse.getValueBool("--wis");
		exIt.noValueLearning = argParse.getValueBool("--no-value-learning");
		
		exIt.maxNumBiasedPlayoutActions = argParse.getValueInt("--max-num-biased-playout-actions");
		
		exIt.noPruneInitFeatures = argParse.getValueBool("--no-prune-init-features");
		exIt.pruneInitFeaturesThreshold = argParse.getValueInt("--prune-init-features-threshold");
		exIt.numPruningGames = argParse.getValueInt("--num-pruning-games");
		exIt.maxNumPruningSeconds = argParse.getValueInt("--max-pruning-seconds");
		
		exIt.checkpointType = CheckpointTypes.valueOf(argParse.getValueString("--checkpoint-type"));
		exIt.checkpointFrequency = argParse.getValueInt("--checkpoint-freq");
		final String outDirFilepath = argParse.getValueString("--out-dir");
		if (outDirFilepath != null)
			exIt.outDir = new File(outDirFilepath);
		else
			exIt.outDir = null;
		exIt.noLogging = argParse.getValueBool("--no-logging");
		
		exIt.startExperiment();
	}

}
