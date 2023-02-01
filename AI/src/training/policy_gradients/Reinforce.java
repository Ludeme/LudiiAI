package training.policy_gradients;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import features.FeatureVector;
import features.feature_sets.BaseFeatureSet;
import features.feature_sets.network.JITSPatterNetFeatureSet;
import features.spatial.FeatureUtils;
import game.Game;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import main.DaemonThreadFactory;
import main.collections.FVector;
import main.collections.FastArrayList;
import main.collections.ListUtils;
import optimisers.Optimiser;
import other.RankUtils;
import other.context.Context;
import other.move.Move;
import other.state.State;
import other.trial.Trial;
import policies.softmax.SoftmaxPolicyLinear;
import training.ExperienceSample;
import training.expert_iteration.params.FeatureDiscoveryParams;
import training.expert_iteration.params.ObjectiveParams;
import training.expert_iteration.params.TrainingParams;
import training.feature_discovery.FeatureSetExpander;
import utils.ExponentialMovingAverage;
import utils.experiments.InterruptableExperiment;

/**
 * Self-play feature (pre-)training and discovery with REINFORCE
 * 
 * @author Dennis Soemers
 */
public class Reinforce
{
	
	//-------------------------------------------------------------------------
	
	/** We don't store experiences for which the discount factor drops below this threshold */
	private static final double EXPERIENCE_DISCOUNT_THRESHOLD = 0.001;
	
	/** If we have a discount factor gamma = 1, we'll use this threshold to limit amount of data stored per trial */
	private static final int DATA_PER_TRIAL_THRESHOLD = 50;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Runs self-play with Policy Gradient training of features
	 * 
	 * @param game
	 * @param selectionPolicy
	 * @param inFeatureSets
	 * @param featureSetExpander
	 * @param optimisers
	 * @param objectiveParams
	 * @param featureDiscoveryParams
	 * @param trainingParams
	 * @param logWriter
	 * @return New array of feature sets
	 */
	@SuppressWarnings("unchecked")
	public static BaseFeatureSet[] runSelfPlayPG
	(
		final Game game,
		final SoftmaxPolicyLinear selectionPolicy,
		final SoftmaxPolicyLinear playoutPolicy,
		final SoftmaxPolicyLinear tspgPolicy,
		final BaseFeatureSet[] inFeatureSets,
		final FeatureSetExpander featureSetExpander,
		final Optimiser[] optimisers,
		final ObjectiveParams objectiveParams,
		final FeatureDiscoveryParams featureDiscoveryParams,
		final TrainingParams trainingParams,
		final PrintWriter logWriter,
		final InterruptableExperiment experiment
	)
	{
		BaseFeatureSet[] featureSets = inFeatureSets;
		final int numPlayers = game.players().count();
		
		// TODO actually use these?
		final ExponentialMovingAverage[] avgGameDurations = new ExponentialMovingAverage[numPlayers + 1];
		final ExponentialMovingAverage[] avgPlayerOutcomeTrackers = new ExponentialMovingAverage[numPlayers + 1];
		for (int p = 1; p <= numPlayers; ++p)
		{
			avgGameDurations[p] = new ExponentialMovingAverage();
			avgPlayerOutcomeTrackers[p] = new ExponentialMovingAverage();
		}
		
		final TLongArrayList[] featureLifetimes = new TLongArrayList[featureSets.length];
		final TDoubleArrayList[] featureActiveRatios = new TDoubleArrayList[featureSets.length];
		
		for (int i = 0; i < featureSets.length; ++i)
		{
			if (featureSets[i] != null)
			{
				final TLongArrayList featureLifetimesList = new TLongArrayList();
				featureLifetimesList.fill(0, featureSets[i].getNumSpatialFeatures(), 0L);
				featureLifetimes[i] = featureLifetimesList;
				
				final TDoubleArrayList featureActiveRatiosList = new TDoubleArrayList();
				featureActiveRatiosList.fill(0, featureSets[i].getNumSpatialFeatures(), 0.0);
				featureActiveRatios[i] = featureActiveRatiosList;
			}
		}
		
		// Thread pool for running trials in parallel
		final ExecutorService trialsThreadPool = Executors.newFixedThreadPool(trainingParams.numPolicyGradientThreads, DaemonThreadFactory.INSTANCE);
		
		for (int epoch = 0; epoch < trainingParams.numPolicyGradientEpochs; ++epoch)
		{
			if (experiment.wantsInterrupt())
				break;
			
			//System.out.println("Starting Policy Gradient epoch: " + epoch);
			
			// Collect all experience (per player) for this epoch here
			final List<PGExperience>[] epochExperiences = new List[numPlayers + 1];
			for (int p = 1; p <= numPlayers; ++p)
			{
				epochExperiences[p] = new ArrayList<PGExperience>();
			}
			
			// Softmax should be thread-safe except for its initAI() and closeAI() methods
			// It's not perfect, but it works fine in the specific case of SoftmaxPolicy to only
			// init and close it before and after an entire batch of trials, rather than before
			// and after every trial. So, we'll just do that for the sake of thread-safety.
			//
			// Since our object will play as all players at once, we pass -1 for the player ID
			// This is fine since SoftmaxPolicy doesn't actually care about that argument
			playoutPolicy.initAI(game, -1);
			
			final CountDownLatch trialsLatch = new CountDownLatch(trainingParams.numPolicyGradientThreads);
			final AtomicInteger epochTrialsCount = new AtomicInteger(0);
			final BaseFeatureSet[] epochFeatureSets = featureSets;
			
			for (int th = 0; th < trainingParams.numPolicyGradientThreads; ++th)
			{
				trialsThreadPool.submit
				(
					() ->
					{
						try
						{
							while (epochTrialsCount.getAndIncrement() < trainingParams.numTrialsPerPolicyGradientEpoch)
							{
								final List<State>[] encounteredGameStates = new List[numPlayers + 1];
								final List<Move>[] lastDecisionMoves = new List[numPlayers + 1];
								final List<FastArrayList<Move>>[] legalMovesLists = new List[numPlayers + 1];
								final List<FeatureVector[]>[] featureVectorArrays = new List[numPlayers + 1];
								final TIntArrayList[] playedMoveIndices = new TIntArrayList[numPlayers + 1];
								
								for (int p = 1; p <= numPlayers; ++p)
								{
									encounteredGameStates[p] = new ArrayList<State>();
									lastDecisionMoves[p] = new ArrayList<Move>();
									legalMovesLists[p] = new ArrayList<FastArrayList<Move>>();
									featureVectorArrays[p] = new ArrayList<FeatureVector[]>();
									playedMoveIndices[p] = new TIntArrayList();
								}
								
								final Trial trial = new Trial(game);
								final Context context = new Context(game, trial);
								
								game.start(context);
								
								// Play trial
								while (!trial.over())
								{
									final int mover = context.state().mover();
									final FastArrayList<Move> moves = game.moves(context).moves();
									final BaseFeatureSet featureSet = epochFeatureSets[mover];
				
									final FeatureVector[] featureVectors = featureSet.computeFeatureVectors(context, moves, false);
									for (final FeatureVector featureVector : featureVectors)
									{
										featureVector.activeSpatialFeatureIndices().trimToSize();
									}
									final FVector distribution = playoutPolicy.computeDistribution(featureVectors, mover);
									
									final int moveIdx = playoutPolicy.selectActionFromDistribution(distribution);
									final Move move = moves.get(moveIdx);
									
									encounteredGameStates[mover].add(new Context(context).state());
									lastDecisionMoves[mover].add(context.trial().lastMove());
									legalMovesLists[mover].add(new FastArrayList<Move>(moves));
									featureVectorArrays[mover].add(featureVectors);
									playedMoveIndices[mover].add(moveIdx);
									
									game.apply(context, move);
									
									updateFeatureActivityData(featureVectors, featureLifetimes, featureActiveRatios, mover);
								}
								
								final double[] utilities = RankUtils.agentUtilities(context);
								
								// Store all experiences
								addTrialData
								(
									epochExperiences, numPlayers, encounteredGameStates, lastDecisionMoves, 
									legalMovesLists, featureVectorArrays, playedMoveIndices, utilities,
									avgGameDurations, avgPlayerOutcomeTrackers, trainingParams
								);
							}
						}
						catch (final Exception e)
						{
							e.printStackTrace();	// Need to do this here since we don't retrieve runnable's Future result
						}
						finally
						{
							trialsLatch.countDown();
						}
					}
				);
			}
			
			try
			{
				trialsLatch.await();
			}
			catch (final InterruptedException e)
			{
				e.printStackTrace();
			}
			
			playoutPolicy.closeAI();
			
			for (int p = 1; p <= numPlayers; ++p)
			{
				final List<PGExperience> experiences = epochExperiences[p];
				final int numExperiences = experiences.size();
				final FVector grads = new FVector(playoutPolicy.linearFunction(p).trainableParams().allWeights().dim());
				final double baseline = avgPlayerOutcomeTrackers[p].movingAvg();
				
				for (int i = 0; i < numExperiences; ++i)
				{
					final PGExperience exp = experiences.get(i);
					final FVector distribution = playoutPolicy.computeDistribution(exp.featureVectors, p);
					
					final FVector policyGradients = 
							computePolicyGradients
							(
								exp, grads.dim(), baseline, 
								trainingParams.entropyRegWeight, 
								distribution.get(exp.movePlayedIdx())
							);
					
					// Now just need to divide gradients by the number of experiences we have and then we can
					// add them to the average gradients (averaged over all experiences)
					policyGradients.div(numExperiences);
					grads.add(policyGradients);
				}

				// Take gradient step
				optimisers[p].maximiseObjective(playoutPolicy.linearFunction(p).trainableParams().allWeights(), grads);
			}

			if (!featureDiscoveryParams.noGrowFeatureSet && (epoch + 1) % 5 == 0)
			{
				// Now we want to try growing our feature set
				final BaseFeatureSet[] expandedFeatureSets = new BaseFeatureSet[numPlayers + 1];
				final ExecutorService threadPool = Executors.newFixedThreadPool(featureDiscoveryParams.numFeatureDiscoveryThreads);
				final CountDownLatch latch = new CountDownLatch(numPlayers);
				for (int pIdx = 1; pIdx <= numPlayers; ++pIdx)
				{
					final int p = pIdx;
					final BaseFeatureSet featureSetP = featureSets[p];
					threadPool.submit
					(
						() ->
						{
							try
							{
								// We'll sample a batch from our experiences, and grow feature set
								final int batchSize = trainingParams.batchSize;
								final List<PGExperience> batch = new ArrayList<PGExperience>(batchSize);
								while (batch.size() < batchSize && !epochExperiences[p].isEmpty())
								{
									final int r = ThreadLocalRandom.current().nextInt(epochExperiences[p].size());
									batch.add(epochExperiences[p].get(r));
									ListUtils.removeSwap(epochExperiences[p], r);
								}
	
								if (batch.size() > 0)
								{
									final long startTime = System.currentTimeMillis();
									final BaseFeatureSet expandedFeatureSet = 
											featureSetExpander.expandFeatureSet
											(
												batch,
												featureSetP,
												playoutPolicy,
												game,
												featureDiscoveryParams.combiningFeatureInstanceThreshold,
												objectiveParams, 
												featureDiscoveryParams,
												featureActiveRatios[p],
												logWriter,
												experiment
											);
	
									if (expandedFeatureSet != null)
									{
										expandedFeatureSets[p] = expandedFeatureSet;
										expandedFeatureSet.init(game, new int[]{p}, null);
										
										while (featureActiveRatios[p].size() < expandedFeatureSet.getNumSpatialFeatures())
										{
											featureLifetimes[p].add(0L);
											featureActiveRatios[p].add(0.0);
										}
									}
									else
									{
										expandedFeatureSets[p] = featureSetP;
									}
									
									// Previously cached feature sets likely useless / less useful now, so clear cache
									JITSPatterNetFeatureSet.clearFeatureSetCache();
									
									experiment.logLine
									(
										logWriter,
										"Expanded feature set in " + (System.currentTimeMillis() - startTime) + " ms for P" + p + "."
									);
									//System.out.println("Expanded feature set in " + (System.currentTimeMillis() - startTime) + " ms for P" + p + ".");
								}
								else
								{
									expandedFeatureSets[p] = featureSetP;
								}
							}
							catch (final Exception e)
							{
								e.printStackTrace();
							}
							finally
							{
								latch.countDown();
							}
						}
					);
							
				}
				
				try
				{
					latch.await();
				} 
				catch (final InterruptedException e)
				{
					e.printStackTrace();
				}
				threadPool.shutdown();

				selectionPolicy.updateFeatureSets(expandedFeatureSets);
				playoutPolicy.updateFeatureSets(expandedFeatureSets);
				tspgPolicy.updateFeatureSets(expandedFeatureSets);
				
				featureSets = expandedFeatureSets;
			}
			// TODO above will be lots of duplication with code in ExpertIteration, should refactor
			
			// TODO save checkpoints
		}
		
		trialsThreadPool.shutdownNow();
		
		return featureSets;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param exp
	 * @param dim Dimensionality we want for output vector
	 * @param valueBaseline
	 * @param entropyRegWeight Weight for entropy regularisation term
	 * @param playedMoveProb Probably with which our policy picks the move that we ended up picking
	 * @return Computes vector of policy gradients for given sample of experience
	 */
	private static FVector computePolicyGradients
	(
		final PGExperience exp, 
		final int dim, 
		final double valueBaseline,
		final double entropyRegWeight,
		final float playedMoveProb
	)
	{
		// Policy gradient giving the direction in which we should update parameters theta
		// can be estimated as:
		//
		// AVERAGE OVER ALL EXPERIENCE SAMPLES i with returns G_i:
		//	\nabla_{\theta} \log ( \pi_{\theta} (a_i | s_i) ) * G_i
		//
		// Assuming that \pi_{\theta} (a_i | s_i) is given by a softmax over the logits of
		// all the actions legal in s_i, we have:
		//
		// \nabla_{\theta} \log ( \pi_{\theta} (a_i | s_i) ) = \phi(s_i, a_i) - E_{\pi_{\theta}} [\phi(s_i, \cdot)]
		
		final FeatureVector[] featureVectors = exp.featureVectors();
		
		final FVector expectedPhi = new FVector(dim);
		final FVector gradLogPi = new FVector(dim);
		
		for (int moveIdx = 0; moveIdx < featureVectors.length; ++moveIdx)
		{
			final FeatureVector featureVector = featureVectors[moveIdx];
			
			// Dense representation for aspatial features
			final FVector aspatialFeatureVals = featureVector.aspatialFeatureValues();
			final int numAspatialFeatures = aspatialFeatureVals.dim();
			
			for (int k = 0; k < numAspatialFeatures; ++k)
			{
				expectedPhi.addToEntry(k, aspatialFeatureVals.get(k));
			}
			
			if (moveIdx == exp.movePlayedIdx())
			{
				for (int k = 0; k < numAspatialFeatures; ++k)
				{
					gradLogPi.addToEntry(k, aspatialFeatureVals.get(k));
				}
			}
			
			// Sparse representation for spatial features (num aspatial features as offset for indexing)
			final TIntArrayList sparseSpatialFeatures = featureVector.activeSpatialFeatureIndices();
			
			for (int k = 0; k < sparseSpatialFeatures.size(); ++k)
			{
				final int feature = sparseSpatialFeatures.getQuick(k);
				expectedPhi.addToEntry(feature + numAspatialFeatures, 1.f);
			}
			
			if (moveIdx == exp.movePlayedIdx())
			{
				for (int k = 0; k < sparseSpatialFeatures.size(); ++k)
				{
					final int feature = sparseSpatialFeatures.getQuick(k);
					gradLogPi.addToEntry(feature + numAspatialFeatures, 1.f);
				}
			}
		}
		
		expectedPhi.div(featureVectors.length);
		gradLogPi.subtract(expectedPhi);

		// Now we have the gradients of the log-probability of the action we played
		// We want to weight these by the returns of the episode
		gradLogPi.mult((float)(exp.discountMultiplier() * (exp.returns() - valueBaseline) - entropyRegWeight * Math.log(playedMoveProb)));
		
		return gradLogPi;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Update feature activity data. Unfortunately this needs to be synchronized,
	 * trial threads should only update this one-by-one
	 * @param featureVectors
	 * @param featureLifetimes
	 * @param featureActiveRatios
	 * @param mover
	 */
	private static synchronized void updateFeatureActivityData
	(
		final FeatureVector[] featureVectors,
		final TLongArrayList[] featureLifetimes,
		final TDoubleArrayList[] featureActiveRatios,
		final int mover
	)
	{
		// Update feature activity data
		for (final FeatureVector featureVector : featureVectors)
		{
			final TIntArrayList sparse = featureVector.activeSpatialFeatureIndices();
			
			if (sparse.isEmpty())
				continue;		// Probably a pass/swap/other special move, don't want these affecting our active ratios
			
			// Following code expects the indices in the sparse feature vector to be sorted
			sparse.sort();

			// Increase lifetime of all features by 1
			featureLifetimes[mover].transformValues((final long l) -> {return l + 1L;});

			// Incrementally update all average feature values
			final TDoubleArrayList list = featureActiveRatios[mover];
			int vectorIdx = 0;
			for (int i = 0; i < list.size(); ++i)
			{
				final double oldMean = list.getQuick(i);

				if (vectorIdx < sparse.size() && sparse.getQuick(vectorIdx) == i)
				{
					// ith feature is active
					list.setQuick(i, oldMean + ((1.0 - oldMean) / featureLifetimes[mover].getQuick(i)));
					++vectorIdx;
				}
				else
				{
					// ith feature is not active
					list.setQuick(i, oldMean + ((0.0 - oldMean) / featureLifetimes[mover].getQuick(i)));
				}
			}

			if (vectorIdx != sparse.size())
			{
				System.err.println("ERROR: expected vectorIdx == sparse.size()!");
				System.err.println("vectorIdx = " + vectorIdx);
				System.err.println("sparse.size() = " + sparse.size());
				System.err.println("sparse = " + sparse);
			}
		}
	}
	
	/**
	 * Record data collected from a trial. Needs to be synchronized, parallel trials
	 * have to do this one-by-one.
	 * 
	 * @param epochExperiences
	 * @param numPlayers
	 * @param encounteredGameStates
	 * @param lastDecisionMoves
	 * @param legalMovesLists
	 * @param featureVectorArrays
	 * @param playedMoveIndices
	 * @param utilities
	 * @param avgGameDurations
	 * @param avgPlayerOutcomeTrackers
	 * @param trainingParams
	 */
	private static synchronized void addTrialData
	(
		final List<PGExperience>[] epochExperiences,
		final int numPlayers,
		final List<State>[] encounteredGameStates,
		final List<Move>[] lastDecisionMoves,
		final List<FastArrayList<Move>>[] legalMovesLists,
		final List<FeatureVector[]>[] featureVectorArrays,
		final TIntArrayList[] playedMoveIndices,
		final double[] utilities,
		final ExponentialMovingAverage[] avgGameDurations,
		final ExponentialMovingAverage[] avgPlayerOutcomeTrackers,
		final TrainingParams trainingParams
	)
	{
		for (int p = 1; p <= numPlayers; ++p)
		{
			final List<State> gameStatesList = encounteredGameStates[p];
			final List<Move> lastDecisionMovesList = lastDecisionMoves[p];
			final List<FastArrayList<Move>> legalMovesList = legalMovesLists[p];
			final List<FeatureVector[]> featureVectorsList = featureVectorArrays[p];
			final TIntArrayList moveIndicesList = playedMoveIndices[p];
			
			// Note: not really game duration! Just from perspective of one player!
			final int gameDuration = gameStatesList.size();
			avgGameDurations[p].observe(gameDuration);
			avgPlayerOutcomeTrackers[p].observe(utilities[p]);
			
			double discountMultiplier = 1.0;
			
			final boolean[] skipData = new boolean[featureVectorsList.size()];
			if (trainingParams.pgGamma == 1.0)
			{
				int numSkipped = 0;
				while (skipData.length - numSkipped > DATA_PER_TRIAL_THRESHOLD)
				{
					final int skipIdx = ThreadLocalRandom.current().nextInt(skipData.length);
					if (!skipData[skipIdx])
					{
						skipData[skipIdx] = true;
						++numSkipped;
					}
				}
			}
			
			for (int i = featureVectorsList.size() - 1; i >= 0; --i)
			{
				if (!skipData[i] && legalMovesList.get(i).size() > 1)
				{
					epochExperiences[p].add
					(
						new PGExperience
						(
							gameStatesList.get(i),
							lastDecisionMovesList.get(i),
							legalMovesList.get(i),
							featureVectorsList.get(i), 
							moveIndicesList.getQuick(i), 
							(float)utilities[p],
							discountMultiplier
						)
					);
				}
				
				discountMultiplier *= trainingParams.pgGamma;
				
				if (discountMultiplier < EXPERIENCE_DISCOUNT_THRESHOLD)
					break;
			}
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Sample of experience for policy gradients
	 * 
	 * NOTE: since our experiences just collect feature vectors rather than contexts,
	 * we cannot reuse the same experiences after growing our feature sets
	 * 
	 * @author Dennis Soemers
	 */
	private static class PGExperience extends ExperienceSample
	{
		
		/** Game state */
		protected final State state;
		
		/** From-position of last decision move */
		protected final int lastFromPos;
		
		/** To-position of last decision move */
		protected final int lastToPos;
		
		/** List of legal moves */
		protected final FastArrayList<Move> legalMoves;
		
		/** Array of feature vectors (one per legal move) */
		protected final FeatureVector[] featureVectors;
		
		/** Index of move that we ended up playing */
		protected final int movePlayedIdx;
		
		/** Returns we got at the end of the trial that this experience was a part of */
		protected final float returns;
		
		/** Multiplier we should use due to discounting */
		protected final double discountMultiplier;
		
		/**
		 * Constructor
		 * @param state
		 * @param lastDecisionMove
		 * @param legalMoves
		 * @param featureVectors
		 * @param movePlayedIdx
		 * @param returns
		 * @param discountMultiplier
		 */
		public PGExperience
		(
			final State state,
			final Move lastDecisionMove,
			final FastArrayList<Move> legalMoves,
			final FeatureVector[] featureVectors, 
			final int movePlayedIdx, 
			final float returns,
			final double discountMultiplier
		)
		{
			this.state = state;
			this.lastFromPos = FeatureUtils.fromPos(lastDecisionMove);
			this.lastToPos = FeatureUtils.toPos(lastDecisionMove);
			this.legalMoves = legalMoves;
			this.featureVectors = featureVectors;
			this.movePlayedIdx = movePlayedIdx;
			this.returns = returns;
			this.discountMultiplier = discountMultiplier;
		}
		
		/**
		 * @return Array of feature vectors (one per legal move)
		 */
		public FeatureVector[] featureVectors()
		{
			return featureVectors;
		}
		
		/**
		 * @return The index of the move we played
		 */
		public int movePlayedIdx()
		{
			return movePlayedIdx;
		}
		
		/**
		 * @return The returns we got at end of trial that this experience was a part of
		 */
		public float returns()
		{
			return returns;
		}
		
		/**
		 * @return Discount multiplier for this sample of experience
		 */
		public double discountMultiplier()
		{
			return discountMultiplier;
		}
		
		@Override
		public FeatureVector[] generateFeatureVectors(final BaseFeatureSet featureSet)
		{
			return featureVectors;
		}
		
		@Override
		public FVector expertDistribution()
		{
			// As an estimation of a good expert distribution, we'll use the
			// discounted returns as logit for the played action, with logits of 0
			// everywhere else, and then use a softmax to turn it into
			// a distribution
			final FVector distribution = new FVector(featureVectors.length);
			distribution.set(movePlayedIdx, (float) (returns * discountMultiplier));
			distribution.softmax();
			return distribution;
		}
		
		@Override
		public State gameState()
		{
			return state;
		}
		
		@Override
		public int lastFromPos()
		{
			return lastFromPos;
		}
		
		@Override
		public int lastToPos()
		{
			return lastToPos;
		}
		
		@Override
		public FastArrayList<Move> moves()
		{
			return legalMoves;
		}
		
		@Override
		public BitSet winningMoves()
		{
			return new BitSet();	// TODO probably want to track these
		}
		
		@Override
		public BitSet losingMoves()
		{
			return new BitSet();	// TODO probably want to track these
		}
		
		@Override
		public BitSet antiDefeatingMoves()
		{
			return new BitSet();	// TODO probably want to track these
		}
		
	}
	
	//-------------------------------------------------------------------------

}
