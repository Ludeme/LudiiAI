package expert_iteration.feature_discovery;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import expert_iteration.ExItExperience;
import expert_iteration.params.ObjectiveParams;
import features.FeatureUtils;
import features.feature_sets.BaseFeatureSet;
import features.features.Feature;
import features.instances.FeatureInstance;
import game.Game;
import gnu.trove.impl.Constants;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import main.collections.FVector;
import main.collections.FastArrayList;
import policies.softmax.SoftmaxPolicy;
import util.Move;
import utils.experiments.InterruptableExperiment;

/**
 * Correlation-based Feature Set Expander. Mostly the same as the one proposed in our
 * CEC 2019 paper (https://arxiv.org/abs/1903.08942), possibly with some small
 * changes implemented since then.
 *
 * @author Dennis Soemers
 */
public class CorrelationBasedExpander implements FeatureSetExpander
{

	@Override
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

		// Create a Hash Set of features already in Feature Set; we won't
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

		// Set of feature instances that we have already preserved (and hence must continue to preserve)
		final Set<CombinableFeatureInstancePair> preservedInstances = new HashSet<CombinableFeatureInstancePair>();

		// Set of feature instances that we have already chosen to discard once (and hence must continue to discard)
		final Set<CombinableFeatureInstancePair> discardedInstances = new HashSet<CombinableFeatureInstancePair>();

		// For every sample in batch, first compute apprentice policies, errors, and sum of absolute errors
		final FVector[] apprenticePolicies = new FVector[batch.length];
		final FVector[] errorVectors = new FVector[batch.length];
		final float[] absErrorSums = new float[batch.length];

		for (int i = 0; i < batch.length; ++i)
		{
			final ExItExperience sample = batch[i];

			final List<TIntArrayList> sparseFeatureVectors = 
					featureSet.computeSparseFeatureVectors
					(
						sample.state().state(), 
						sample.state().lastDecisionMove(), 
						sample.moves(), 
						false
					);

			final FVector apprenticePolicy = 
					policy.computeDistribution(sparseFeatureVectors, sample.state().state().mover());
			final FVector errors = 
					policy.computeDistributionErrors
					(
						apprenticePolicy,
						sample.expertDistribution()
					);
			
			if (objectiveParams.expDeltaValWeighting)
			{
				// Compute expected values of expert and apprentice policies
				double expValueExpert = 0.0;
				double expValueApprentice = 0.0;
				final FVector expertQs = sample.expertValueEstimates();

				for (int a = 0; a < expertQs.dim(); ++a)
				{
					expValueExpert += expertQs.get(a) * sample.expertDistribution().get(a);
					expValueApprentice += expertQs.get(a) * apprenticePolicy.get(a);
				}

				// Scale the errors
				final double expDeltaValWeight = Math.max(
						objectiveParams.expDeltaValWeightingLowerClip, 
						(expValueExpert - expValueApprentice));
				errors.mult((float)expDeltaValWeight);
			}

			final FVector absErrors = errors.copy();
			absErrors.abs();

			apprenticePolicies[i] = apprenticePolicy;
			errorVectors[i] = errors;
			absErrorSums[i] = absErrors.sum();
		}

		// Create list of indices that we can use to index into batch, sorted in descending order
		// of sums of absolute errors.
		// This means that we prioritise looking at samples in the batch for which we have big policy
		// errors, and hence also focus on them when dealing with a cap in the number of active feature
		// instances we can look at
		final List<Integer> batchIndices = new ArrayList<Integer>(batch.length);
		for (int i = 0; i < batch.length; ++i)
		{
			batchIndices.add(Integer.valueOf(i));
		}
		Collections.sort(batchIndices, new Comparator<Integer>()
		{

			@Override
			public int compare(final Integer o1, final Integer o2)
			{
				final float deltaAbsErrorSums = absErrorSums[o1.intValue()] - absErrorSums[o2.intValue()];
				if (deltaAbsErrorSums > 0.f)
					return -1;
				else if (deltaAbsErrorSums < 0.f)
					return 1;
				else
					return 0;
			}

		});

		// Loop through all samples in batch
		for (int bi = 0; bi < batchIndices.size(); ++bi)
		{
			final int batchIndex = batchIndices.get(bi).intValue();
			final ExItExperience sample = batch[batchIndex];
			final FVector errors = errorVectors[batchIndex];
			final FastArrayList<Move> moves = sample.moves();

			// Every action in the sample is a new "case" (state-action pair)
			for (int a = 0; a < moves.size(); ++a)
			{
				++numCases;

				// keep track of pairs we've already seen in this "case"
				final Set<CombinableFeatureInstancePair> observedCasePairs = 
						new HashSet<CombinableFeatureInstancePair>(256, .75f);

				// list --> set --> list to get rid of duplicates
				final List<FeatureInstance> activeInstances = new ArrayList<FeatureInstance>(new HashSet<FeatureInstance>(
						featureSet.getActiveFeatureInstances
						(
							sample.state().state(), 
							FeatureUtils.fromPos(sample.state().lastDecisionMove()), 
							FeatureUtils.toPos(sample.state().lastDecisionMove()), 
							FeatureUtils.fromPos(moves.get(a)), 
							FeatureUtils.toPos(moves.get(a)),
							moves.get(a).mover()
						)));

				// Start out by keeping all feature instances that have already been marked as having to be
				// preserved, and discarding those that have already been discarded before
				final List<FeatureInstance> instancesToKeep = new ArrayList<FeatureInstance>();

				for (int i = 0; i < activeInstances.size(); /**/)
				{
					final FeatureInstance instance = activeInstances.get(i);
					final CombinableFeatureInstancePair combinedSelf = new CombinableFeatureInstancePair(game, instance, instance);
					if (preservedInstances.contains(combinedSelf))
					{
						instancesToKeep.add(instance);
						activeInstances.remove(i);
					}
					else if (discardedInstances.contains(combinedSelf))
					{
						activeInstances.remove(i);
					}
					else
					{
						++i;
					}
				}

				// This action is allowed to pick at most this many extra instances
				int numInstancesAllowedThisAction = Math.min(Math.min(Math.max(
						5, 		// TODO make this a param
						featureDiscoveryMaxNumFeatureInstances - preservedInstances.size() / (moves.size() - a)),
						featureDiscoveryMaxNumFeatureInstances - preservedInstances.size()),
						activeInstances.size());

				// Create distribution over active instances using softmax over logits inversely proportional to
				// how commonly the instances' features are active
				final FVector distr = new FVector(activeInstances.size());
				for (int i = 0; i < activeInstances.size(); ++i)
				{
					distr.set(i, (float) (2.0 * (1.0 - fActiveRatios.getQuick(activeInstances.get(i).feature().featureSetIndex()))));
				}
				distr.softmax();

				while (numInstancesAllowedThisAction > 0)
				{
					// Sample another instance
					final int sampledIdx = distr.sampleFromDistribution();
					final FeatureInstance keepInstance = activeInstances.get(sampledIdx);
					instancesToKeep.add(keepInstance);
					final CombinableFeatureInstancePair combinedSelf = new CombinableFeatureInstancePair(game, keepInstance, keepInstance);
					preservedInstances.add(combinedSelf);		// Remember to preserve this one forever now
					distr.updateSoftmaxInvalidate(sampledIdx);	// Don't want to pick the same index again
					--numInstancesAllowedThisAction;
				}

				// Mark all the instances that haven't been marked as preserved yet as discarded instead
				for (final FeatureInstance instance : activeInstances)
				{
					final CombinableFeatureInstancePair combinedSelf = new CombinableFeatureInstancePair(game, instance, instance);
					if (!preservedInstances.contains(combinedSelf))
						discardedInstances.add(combinedSelf);
				}

				final int numActiveInstances = instancesToKeep.size();

				final float error = errors.get(a);

				sumErrors += error;
				sumSquaredErrors += error * error;

				for (int i = 0; i < numActiveInstances; ++i)
				{
					final FeatureInstance instanceI = instancesToKeep.get(i);

					// increment entries on ''main diagonals''
					final CombinableFeatureInstancePair combinedSelf = 
							new CombinableFeatureInstancePair(game, instanceI, instanceI);

					if (observedCasePairs.add(combinedSelf))
					{
						featurePairActivations.adjustOrPutValue(combinedSelf, 1, 1);
						errorSums.adjustOrPutValue(combinedSelf, error, error);
					}

					for (int j = i + 1; j < numActiveInstances; ++j)
					{
						final FeatureInstance instanceJ = instancesToKeep.get(j);

						// increment off-diagonal entries
						final CombinableFeatureInstancePair combined = 
								new CombinableFeatureInstancePair(game, instanceI, instanceJ);

						if (!existingFeatures.contains(combined.combinedFeature))
						{
							if (observedCasePairs.add(combined))
							{
								featurePairActivations.adjustOrPutValue(combined, 1, 1);
								errorSums.adjustOrPutValue(combined, error, error);
							}
						}
					}
				}
			}
		}

		if (sumErrors == 0.0 || sumSquaredErrors == 0.0)
		{
			// incredibly rare case but appears to be possible sometimes
			// we have nothing to guide our feature growing, so let's 
			// just refuse to add a feature
			return null;
		}

		// construct all possible pairs and scores
		// as we go, keep track of best score and index at which we can find it
		final List<ScoredFeatureInstancePair> scoredPairs = new ArrayList<ScoredFeatureInstancePair>(featurePairActivations.size());
		double bestScore = Double.NEGATIVE_INFINITY;
		int bestPairIdx = -1;

		for (final CombinableFeatureInstancePair pair : featurePairActivations.keySet())
		{
			if (! pair.a.equals(pair.b))	// Only interested in combinations of different instances
			{
				final int pairActs = featurePairActivations.get(pair);
				if (pairActs == numCases || pairActs < 4)
				{
					// Perfect correlation, so we should just skip this one
					continue;
				}

				final int actsI = featurePairActivations.get(new CombinableFeatureInstancePair(game, pair.a, pair.a));
				final int actsJ = featurePairActivations.get(new CombinableFeatureInstancePair(game, pair.b, pair.b));

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
				
				// Fisher's r-to-z transformation
				final double errorCorrZ = 0.5 * Math.log((1.0 + errorCorr) / (1.0 - errorCorr));
				// Standard deviation of the z
				final double stdErrorCorrZ = Math.sqrt(1.0 / (numCases - 3));
				// Lower bound of 90% confidence interval on z
				final double lbErrorCorrZ = errorCorrZ - 1.64 * stdErrorCorrZ;
				// Transform lower bound on z back to r
				final double lbErrorCorr = (Math.exp(2.0 * lbErrorCorrZ) - 1.0) / (Math.exp(2.0 * lbErrorCorrZ) + 1.0);
				// Upper bound of 90% confidence interval on z
				final double ubErrorCorrZ = errorCorrZ + 1.64 * stdErrorCorrZ;
				// Transform upper bound on z back to r
				final double ubErrorCorr = (Math.exp(2.0 * ubErrorCorrZ) - 1.0) / (Math.exp(2.0 * ubErrorCorrZ) + 1.0);

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

				final double score;
				if (errorCorr >= 0.0)
					score = Math.max(0.0, lbErrorCorr) * (1.0 - worstFeatureCorr);
				else
					score = -Math.min(0.0, ubErrorCorr) * (1.0 - worstFeatureCorr);

				if (Double.isNaN(score))
				{
					// System.err.println("numCases = " + numCases);
					// System.err.println("pairActs = " + pairActs);
					// System.err.println("actsI = " + actsI);
					// System.err.println("actsJ = " + actsJ);
					// System.err.println("sumErrors = " + sumErrors);
					// System.err.println("sumSquaredErrors = " + sumSquaredErrors);
					continue;
				}

				scoredPairs.add(new ScoredFeatureInstancePair(pair, score));
				if (score > bestScore)
				{
					bestScore = score;
					bestPairIdx = scoredPairs.size() - 1;
				}
			}
		}

//		scoredPairs.sort(new Comparator<ScoredPair>(){
//
//			@Override
//			public int compare(ScoredPair o1, ScoredPair o2)
//			{
//				if (o1.score > o2.score)
//					return 1;
//				else if (o1.score < o2.score)
//					return -1;
//				else
//					return 0;
//			}
//
//		});
//
//		System.out.println("--------------------------------------------------------");
//		for (int i = 0; i < scoredPairs.size(); ++i)
//		{
//			final ScoredPair pair = scoredPairs.get(i);
//
//			final int actsI = 
//					featurePairActivations.get
//					(
//						new CombinableFeatureInstancePair(g, pair.pair.a, pair.pair.a)
//					);
//
//			final int actsJ = 
//					featurePairActivations.get
//					(
//						new CombinableFeatureInstancePair(g, pair.pair.b, pair.pair.b)
//					);
//
//			final int pairActs = 
//					featurePairActivations.get
//					(
//						new CombinableFeatureInstancePair(g, pair.pair.a, pair.pair.b)
//					);
//
//			final double pairErrorSum = 
//					errorSums.get
//					(
//						new CombinableFeatureInstancePair(g, pair.pair.a, pair.pair.b)
//					);
//
//			final double errorCorr = 
//					(
//						(numCases * pairErrorSum - pairActs * sumErrors) 
//						/ 
//						(
//							Math.sqrt(numCases * pairActs - pairActs * pairActs) * 
//							Math.sqrt(numCases * sumSquaredErrors - sumErrors * sumErrors)
//						)
//					);
//
//			final double featureCorrI = 
//					(
//						(numCases * pairActs - pairActs * actsI) 
//						/ 
//						(
//							Math.sqrt(numCases * pairActs - pairActs * pairActs) * 
//							Math.sqrt(numCases * actsI - actsI * actsI)
//						)
//					);
//
//			final double featureCorrJ = 
//					(
//						(numCases * pairActs - pairActs * actsJ) 
//						/ 
//						(
//							Math.sqrt(numCases * pairActs - pairActs * pairActs) * 
//							Math.sqrt(numCases * actsJ - actsJ * actsJ)
//						)
//					);
//
//			System.out.println("score = " + pair.score);
//			System.out.println("correlation with errors = " + errorCorr);
//			System.out.println("correlation with first constituent = " + featureCorrI);
//			System.out.println("correlation with second constituent = " + featureCorrJ);
//			System.out.println("active feature A = " + pair.pair.a.feature());
//			System.out.println("rot A = " + pair.pair.a.rotation());
//			System.out.println("ref A = " + pair.pair.a.reflection());
//			System.out.println("anchor A = " + pair.pair.a.anchorSite());
//			System.out.println("active feature B = " + pair.pair.b.feature());
//			System.out.println("rot B = " + pair.pair.b.rotation());
//			System.out.println("ref B = " + pair.pair.b.reflection());
//			System.out.println("anchor B = " + pair.pair.b.anchorSite());
//			System.out.println("observed pair of instances " + pairActs + " times");
//			System.out.println("observed first constituent " + actsI + " times");
//			System.out.println("observed second constituent " + actsJ + " times");
//			System.out.println();
//		}
//		System.out.println("--------------------------------------------------------");

		// keep trying to generate an expanded (by one) feature set, until
		// we succeed (almost always this should be on the very first iteration)
		while (scoredPairs.size() > 0)
		{
			// extract pair of feature instances we want to try combining
			final ScoredFeatureInstancePair bestPair = scoredPairs.remove(bestPairIdx);

			final BaseFeatureSet newFeatureSet = 
					featureSet.createExpandedFeatureSet(game, bestPair.pair.a, bestPair.pair.b);

			if (newFeatureSet != null)
			{
				final int actsI = 
						featurePairActivations.get
						(
							new CombinableFeatureInstancePair(game, bestPair.pair.a, bestPair.pair.a)
						);

				final int actsJ = 
						featurePairActivations.get
						(
							new CombinableFeatureInstancePair(game, bestPair.pair.b, bestPair.pair.b)
						);

				final int pairActs = 
						featurePairActivations.get
						(
							new CombinableFeatureInstancePair(game, bestPair.pair.a, bestPair.pair.b)
						);

				final double pairErrorSum = 
						errorSums.get
						(
							new CombinableFeatureInstancePair(game, bestPair.pair.a, bestPair.pair.b)
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
				
				// Fisher's r-to-z transformation
				final double errorCorrZ = 0.5 * Math.log((1.0 + errorCorr) / (1.0 - errorCorr));
				// Standard deviation of the z
				final double stdErrorCorrZ = Math.sqrt(1.0 / (numCases - 3));
				// Lower bound of 90% confidence interval on z
				final double lbErrorCorrZ = errorCorrZ - 1.64 * stdErrorCorrZ;
				// Transform lower bound on z back to r
				final double lbErrorCorr = (Math.exp(2.0 * lbErrorCorrZ) - 1.0) / (Math.exp(2.0 * lbErrorCorrZ) + 1.0);
				// Upper bound of 90% confidence interval on z
				final double ubErrorCorrZ = errorCorrZ + 1.64 * stdErrorCorrZ;
				// Transform upper bound on z back to r
				final double ubErrorCorr = (Math.exp(2.0 * ubErrorCorrZ) - 1.0) / (Math.exp(2.0 * ubErrorCorrZ) + 1.0);

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

				experiment.logLine(logWriter, "New feature added!");
				experiment.logLine(logWriter, "new feature = " + newFeatureSet.features()[newFeatureSet.getNumFeatures() - 1]);
				experiment.logLine(logWriter, "active feature A = " + bestPair.pair.a.feature());
				experiment.logLine(logWriter, "rot A = " + bestPair.pair.a.rotation());
				experiment.logLine(logWriter, "ref A = " + bestPair.pair.a.reflection());
				experiment.logLine(logWriter, "anchor A = " + bestPair.pair.a.anchorSite());
				experiment.logLine(logWriter, "active feature B = " + bestPair.pair.b.feature());
				experiment.logLine(logWriter, "rot B = " + bestPair.pair.b.rotation());
				experiment.logLine(logWriter, "ref B = " + bestPair.pair.b.reflection());
				experiment.logLine(logWriter, "anchor B = " + bestPair.pair.b.anchorSite());
				experiment.logLine(logWriter, "score = " + bestPair.score);
				experiment.logLine(logWriter, "correlation with errors = " + errorCorr);
				experiment.logLine(logWriter, "lower bound correlation with errors = " + lbErrorCorr);
				experiment.logLine(logWriter, "upper bound correlation with errors = " + ubErrorCorr);
				experiment.logLine(logWriter, "correlation with first constituent = " + featureCorrI);
				experiment.logLine(logWriter, "correlation with second constituent = " + featureCorrJ);
				experiment.logLine(logWriter, "observed pair of instances " + pairActs + " times");
				experiment.logLine(logWriter, "observed first constituent " + actsI + " times");
				experiment.logLine(logWriter, "observed second constituent " + actsJ + " times");

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

}
