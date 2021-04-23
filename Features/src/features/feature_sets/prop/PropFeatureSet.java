package features.feature_sets.prop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import features.aspatial.AspatialFeature;
import features.feature_sets.BaseFeatureSet;
import features.spatial.SpatialFeature;
import features.spatial.Walk;
import features.spatial.cache.ActiveFeaturesCache;
import features.spatial.cache.footprints.BaseFootprint;
import features.spatial.instances.AtomicProposition;
import features.spatial.instances.FeatureInstance;
import game.Game;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import other.context.Context;
import other.state.State;
import other.state.container.ContainerState;
import other.trial.Trial;

/**
 * Implementation of Feature Set that organises its feature instances in
 * an array of proposition and instance nodes		TODO better description
 *
 * @author Dennis Soemers
 */
public class PropFeatureSet extends BaseFeatureSet
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Reactive instance sets, indexed by:
	 * 	player index,
	 * 	last-from-pos
	 * 	last-to-pos,
	 * 	from-pos
	 * 	to-pos
	 * 
	 * When indexed according to all of the above, we're left with a feature instance set.
	 */
	protected HashMap<ReactiveFeaturesKey, PropFeatureInstanceSet> reactiveInstances;
	
	/**
	 * Proactive instances, indexed by:
	 * 	player index,
	 * 	from-pos
	 * 	to-pos
	 * 
	 * When indexed according to all of the above, we're left with a feature instance set.
	 */
	protected HashMap<ProactiveFeaturesKey, PropFeatureInstanceSet> proactiveInstances;
	
	/**
	 * Reactive features, indexed by:
	 * 	player index,
	 * 	last-from-pos
	 * 	last-to-pos,
	 * 	from-pos
	 * 	to-pos
	 * 
	 * When indexed according to all of the above, we're left with a feature set.
	 */
	protected HashMap<ReactiveFeaturesKey, PropSet> reactiveFeatures;
	
	/**
	 * Proactive features, indexed by:
	 * 	player index,
	 * 	from-pos
	 * 	to-pos
	 * 
	 * When indexed according to all of the above, we're left with a feature set.
	 */
	protected HashMap<ProactiveFeaturesKey, PropSet> proactiveFeatures;
	
	/**
	 * Same as reactive features above, but only retaining features with absolute
	 * weights that exceed the threshold defined by BaseFeatureSet.
	 */
	protected HashMap<ReactiveFeaturesKey, PropSet> reactiveFeaturesThresholded;
	
	/**
	 * Same as proactive features above, but only retaining features with absolute
	 * weights that exceed the threshold defined by BaseFeatureSet.
	 */
	protected HashMap<ProactiveFeaturesKey, PropSet> proactiveFeaturesThresholded;
	
	/** Cache with indices of active proactive features previously computed */
	protected ActiveFeaturesCache activeProactiveFeaturesCache;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Construct feature set from list of features
	 * @param features
	 */
	public PropFeatureSet(final List<SpatialFeature> features)
	{
		this.spatialFeatures = new SpatialFeature[features.size()];
		
		for (int i = 0; i < this.spatialFeatures.length; ++i)
		{
			this.spatialFeatures[i] = features.get(i);
			this.spatialFeatures[i].setFeatureSetIndex(i);
		}
		
		this.aspatialFeatures = new AspatialFeature[0];		// TODO also include aspatial features
		
		reactiveInstances = null;
		proactiveInstances = null;
		
		reactiveFeatures = null;
		proactiveFeatures = null;
		
		reactiveFeaturesThresholded = null;
		proactiveFeaturesThresholded = null;
	}
	
	/**
	 * Loads a feature set from a given filename
	 * @param filename
	 */
	public PropFeatureSet(final String filename)
	{
		SpatialFeature[] tempFeatures;
		
		//System.out.println("loading feature set from " + filename);
		try (Stream<String> stream = Files.lines(Paths.get(filename)))
		{
			tempFeatures = stream.map(s -> SpatialFeature.fromString(s)).toArray(SpatialFeature[]::new);
		} 
		catch (final IOException exception) 
		{
			tempFeatures = null;
		}
		
		this.spatialFeatures = tempFeatures;
		
		for (int i = 0; i < spatialFeatures.length; ++i)
		{
			spatialFeatures[i].setFeatureSetIndex(i);
		}
		
		this.aspatialFeatures = new AspatialFeature[0];		// TODO also include aspatial features
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	protected void instantiateFeatures(final int[] supportedPlayers)
	{
		activeProactiveFeaturesCache = new ActiveFeaturesCache();
		
		// Start out creating feature (instance) sets represented as bipartite graphs
		Map<ReactiveFeaturesKey, BipartiteGraphFeatureInstanceSet> reactiveInstancesSet = 
				new HashMap<ReactiveFeaturesKey, BipartiteGraphFeatureInstanceSet>();
		Map<ProactiveFeaturesKey, BipartiteGraphFeatureInstanceSet> proactiveInstancesSet = 
				new HashMap<ProactiveFeaturesKey, BipartiteGraphFeatureInstanceSet>();
		
		// Create a dummy context because we need some context for 
		// feature generation
		final Context featureGenContext = new Context(game.get(), new Trial(game.get()));
		
		// Collect features that should be ignored when running in thresholded mode
		final BitSet thresholdedFeatures = new BitSet();
		if (spatialFeatureInitWeights != null)
		{
			for (int i = 0; i < spatialFeatures.length; ++i)
			{
				if (Math.abs(spatialFeatureInitWeights.get(i)) < SPATIAL_FEATURE_WEIGHT_THRESHOLD)
					thresholdedFeatures.set(i);
			}
		}
		
		for (int i = 0; i < supportedPlayers.length; ++i)
		{
			final int player = supportedPlayers[i];
			
			for (final SpatialFeature feature : spatialFeatures)
			{
				final List<FeatureInstance> newInstances = 
						feature.instantiateFeature
						(
							game.get(), 
							featureGenContext.state().containerStates()[0], 
							player, 
							-1, 
							-1
						);
				
				for (final FeatureInstance instance : newInstances)
				{
					final int lastFrom = instance.lastFrom();
					final int lastTo = instance.lastTo();
					final int from = instance.from();
					final int to = instance.to();
					
					if (lastFrom >= 0 || lastTo >= 0)	// Reactive feature
					{
						final ReactiveFeaturesKey key = new ReactiveFeaturesKey(player, lastFrom, lastTo, from, to);
						BipartiteGraphFeatureInstanceSet instancesSet = reactiveInstancesSet.get(key);
						
						if (instancesSet == null)
						{
							instancesSet = new BipartiteGraphFeatureInstanceSet();
							reactiveInstancesSet.put(key, instancesSet);
						}
						
						instancesSet.insertInstance(instance);
					}
					else								// Proactive feature
					{
						final ProactiveFeaturesKey key = new ProactiveFeaturesKey(player, from, to);
						BipartiteGraphFeatureInstanceSet instancesSet = proactiveInstancesSet.get(key);
						
						if (instancesSet == null)
						{
							instancesSet = new BipartiteGraphFeatureInstanceSet();
							proactiveInstancesSet.put(key, instancesSet);
						}
						
						instancesSet.insertInstance(instance);
					}
				}
			}
		}
		
		reactiveInstances = new HashMap<ReactiveFeaturesKey, PropFeatureInstanceSet>();
		reactiveFeatures = new HashMap<ReactiveFeaturesKey, PropSet>();
		reactiveFeaturesThresholded = new HashMap<ReactiveFeaturesKey, PropSet>();
		for (final Entry<ReactiveFeaturesKey, BipartiteGraphFeatureInstanceSet> entry : reactiveInstancesSet.entrySet())
		{
			reactiveInstances.put(entry.getKey(), entry.getValue().toPropFeatureInstanceSet());
			reactiveFeatures.put(entry.getKey(), entry.getValue().toClauseFeatureSet(getNumSpatialFeatures(), new BitSet()));
			reactiveFeaturesThresholded.put(entry.getKey(), entry.getValue().toClauseFeatureSet(getNumSpatialFeatures(), thresholdedFeatures));
		}
		
		proactiveInstances = new HashMap<ProactiveFeaturesKey, PropFeatureInstanceSet>();
		proactiveFeatures = new HashMap<ProactiveFeaturesKey, PropSet>();
		proactiveFeaturesThresholded = new HashMap<ProactiveFeaturesKey, PropSet>();
		for (final Entry<ProactiveFeaturesKey, BipartiteGraphFeatureInstanceSet> entry : proactiveInstancesSet.entrySet())
		{
			proactiveInstances.put(entry.getKey(), entry.getValue().toPropFeatureInstanceSet());
			proactiveFeatures.put(entry.getKey(), entry.getValue().toClauseFeatureSet(getNumSpatialFeatures(), new BitSet()));
			proactiveFeaturesThresholded.put(entry.getKey(), entry.getValue().toClauseFeatureSet(getNumSpatialFeatures(), thresholdedFeatures));
		}
	}
	
	//-------------------------------------------------------------------------

	@Override
	public TIntArrayList getActiveFeatureIndices
	(
		final State state,
		final int lastFrom,
		final int lastTo,
		final int from,
		final int to,
		final int player,
		final boolean thresholded
	)
	{
		final HashMap<ReactiveFeaturesKey, PropSet> reactiveFeaturesMap;
		final HashMap<ProactiveFeaturesKey, PropSet> proactiveFeaturesMap;
		if (thresholded)
		{
			reactiveFeaturesMap = reactiveFeaturesThresholded;
			proactiveFeaturesMap = proactiveFeaturesThresholded;
		}
		else
		{
			reactiveFeaturesMap = reactiveFeatures;
			proactiveFeaturesMap = proactiveFeatures;
		}
		
		final TIntArrayList featureIndices = new TIntArrayList();
		
//		System.out.println("lastFrom = " + lastFrom);
//		System.out.println("lastTo = " + lastTo);
//		System.out.println("from = " + from);
//		System.out.println("to = " + to);
//		System.out.println("player = " + player);
//		final List<FeatureInstance> instances = getActiveFeatureInstances(state, lastFrom, lastTo, from, to, player);
//		for (final FeatureInstance instance : instances)
//		{
//			if (!featureIndices.contains(instance.feature().featureSetIndex()))
//				featureIndices.add(instance.feature().featureSetIndex());
//		}
		
		final int[] froms = from >= 0 ? new int[]{-1, from} : new int[]{-1};
		final int[] tos = to >= 0 ? new int[]{-1, to} : new int[]{-1};
		final int[] lastFroms = lastFrom >= 0 ? new int[]{-1, lastFrom} : new int[]{-1};
		final int[] lastTos = lastTo >= 0 ? new int[]{-1, lastTo} : new int[]{-1};
		
		if (!proactiveFeaturesMap.isEmpty())
		{
			final int[] cachedActiveFeatureIndices;
			
			if (thresholded)
				cachedActiveFeatureIndices = activeProactiveFeaturesCache.getCachedActiveFeatures(this, state, from, to, player);
			else
				cachedActiveFeatureIndices = null;
		
			if (cachedActiveFeatureIndices != null)
			{
				// Successfully retrieved from cache
				featureIndices.add(cachedActiveFeatureIndices);
				//System.out.println("cache hit!");
			}
			else
			{
				for (int k = 0; k < froms.length; ++k)
				{
					final int fromPos = froms[k];
		
					for (int l = 0; l < tos.length; ++l)
					{
						final int toPos = tos[l];
		
						if (toPos >= 0 || fromPos >= 0)
						{
							// Proactive instances
							final PropSet set = 
									proactiveFeaturesMap.get
									(
										new ProactiveFeaturesKey
										(
											player, 
											fromPos, 
											toPos
										)
									);
							
							if (set != null)
								featureIndices.addAll(set.getActiveFeatures(state));
						}
					}
				}
				
				if (thresholded && !featureIndices.isEmpty())
					activeProactiveFeaturesCache.cache(state, from, to, featureIndices.toArray(), player);
			}
		}
		
		if (!reactiveFeatures.isEmpty())
		{
			if (lastFrom >= 0 || lastTo >= 0)
			{
				for (int i = 0; i < lastFroms.length; ++i)
				{
					final int lastFromPos = lastFroms[i];
					
					for (int j = 0; j < lastTos.length; ++j)
					{
						final int lastToPos = lastTos[j];
						
						for (int k = 0; k < froms.length; ++k)
						{
							final int fromPos = froms[k];
							
							for (int l = 0; l < tos.length; ++l)
							{
								final int toPos = tos[l];
								
								if (lastToPos >= 0 || lastFromPos >= 0)
								{
									// Reactive instances
									final PropSet set =
											reactiveFeaturesMap.get
											(
												new ReactiveFeaturesKey
												(
													player, 
													lastFromPos,
													lastToPos,
													fromPos, 
													toPos
												)
											);
									
									if (set != null)
										featureIndices.addAll(set.getActiveFeatures(state));
								}
							}
						}
					}
				}
			}
		}
		
		return featureIndices;
	}

	@Override
	public List<FeatureInstance> getActiveFeatureInstances
	(
		final State state,
		final int lastFrom,
		final int lastTo,
		final int from,
		final int to,
		final int player
	)
	{
		final List<FeatureInstance> instances = new ArrayList<FeatureInstance>();
		final int[] froms = from >= 0 ? new int[]{-1, from} : new int[]{-1};
		final int[] tos = to >= 0 ? new int[]{-1, to} : new int[]{-1};
		final int[] lastFroms = lastFrom >= 0 ? new int[]{-1, lastFrom} : new int[]{-1};
		final int[] lastTos = lastTo >= 0 ? new int[]{-1, lastTo} : new int[]{-1};
		
		if (lastFrom >= 0 || lastTo >= 0)
		{
			for (int i = 0; i < lastFroms.length; ++i)
			{
				final int lastFromPos = lastFroms[i];
				
				for (int j = 0; j < lastTos.length; ++j)
				{
					final int lastToPos = lastTos[j];
					
					for (int k = 0; k < froms.length; ++k)
					{
						final int fromPos = froms[k];
						
						for (int l = 0; l < tos.length; ++l)
						{
							final int toPos = tos[l];
							
							if (lastToPos >= 0 || lastFromPos >= 0)
							{
								// Reactive instances
								final PropFeatureInstanceSet set =
										reactiveInstances.get
										(
											new ReactiveFeaturesKey
											(
												player, 
												lastFromPos,
												lastToPos,
												fromPos, 
												toPos
											)
										);
								
								if (set != null)
									instances.addAll(set.getActiveInstances(state));
							}
						}
					}
				}
			}
		}
		
		for (int k = 0; k < froms.length; ++k)
		{
			final int fromPos = froms[k];

			for (int l = 0; l < tos.length; ++l)
			{
				final int toPos = tos[l];

				if (toPos >= 0 || fromPos >= 0)
				{
					// Proactive instances
					final PropFeatureInstanceSet set = 
							proactiveInstances.get
							(
								new ProactiveFeaturesKey
								(
									player, 
									fromPos, 
									toPos
								)
							);
					
					if (set != null)
						instances.addAll(set.getActiveInstances(state));
				}
			}
		}
		
		return instances;
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public BaseFootprint generateFootprint
	(
		final State state, 
		final int from, 
		final int to,
		final int player
	)
	{
		final ContainerState container = state.containerStates()[0];
				
		// NOTE: only using caching with thresholding
		PropSet set = proactiveFeaturesThresholded.get
							(
								new ProactiveFeaturesKey
								(
									player, 
									from, 
									to
								)
							);
		
		if (set == null)
		{
			set = new PropSet(
					new FeatureInstance[0], 
					new AtomicProposition[0], 
					new BitSet[0], 
					new BitSet[0], 
					new BitSet[0], 
					new int[0], 
					new BitSet(),
					new BitSet[0], 
					new BitSet[0], 
					new BitSet[0], 
					new BitSet[0]
				);
		}
					
		return set.generateFootprint(container);
	}
	
	//-------------------------------------------------------------------------

	@Override
	public PropFeatureSet createExpandedFeatureSet
	(
		final Game targetGame,
		final SpatialFeature newFeature
	)
	{
		boolean featureAlreadyExists = false;
		for (final SpatialFeature oldFeature : spatialFeatures)
		{
			if (newFeature.equals(oldFeature))
			{
				featureAlreadyExists = true;
				break;
			}
			
			// also try all legal rotations of the generated feature, see
			// if any of those turn out to be equal to an old feature
			TFloatArrayList allowedRotations = newFeature.pattern().allowedRotations();
			
			if (allowedRotations == null)
			{
				allowedRotations = Walk.allGameRotations(targetGame);
			}
			
			for (int i = 0; i < allowedRotations.size(); ++i)
			{
				final SpatialFeature rotatedCopy = 
						newFeature.rotatedCopy(allowedRotations.getQuick(i));
				
				if (rotatedCopy.equals(oldFeature))
				{
					// TODO only break if for every possible anchor this works?
					featureAlreadyExists = true;
					break;
				}
			}
		}
		
		if (!featureAlreadyExists)
		{
			// create new feature set with this feature, and return it
			final List<SpatialFeature> newFeatureList = new ArrayList<SpatialFeature>(spatialFeatures.length + 1);
			
			// all old features...
			for (final SpatialFeature feature : spatialFeatures)
			{
				newFeatureList.add(feature);
			}
			
			// and our new feature
			newFeatureList.add(newFeature);
			
			return new PropFeatureSet(newFeatureList);
		}
		
		return null;
	}
	
	//-------------------------------------------------------------------------

}
