package features;

import java.util.HashMap;
import java.util.Map;

import features.FeatureSet.ProactiveFeaturesKey;
import features.instances.Footprint;
import main.collections.ChunkSet;
import util.state.State;
import util.state.containerState.ContainerState;

/**
 * Given a list containing player index, from-pos, and to-pos as key, this cache
 * can look up a cached list of active features that were active the last time
 * that same index was used to compute a list of active features.
 * 
 * Given a current game state, the cache will first use the key-specific
 * footprint of the complete Feature Set to ensure that all parts of the game
 * state covered by the footprint are still identical to how they were when the
 * active features were computed and stored in the cache. The active features
 * will only be returned if all parts of the game state covered by the footprint
 * are indeed still identical.
 * 
 * Note that the cache is not linked to any specific Trial or Context, or even
 * to any specific full match of a game being played. Generally, we expect the
 * majority of the cache to remain valid throughout a single playout, with small
 * parts becoming invalid during a playout, and most of the cache having turned
 * invalid when we move on to the next playout. However, it is possible that
 * some parts of the cache remain valid over many different playouts (e.g. parts
 * of a board that trained policies are very unlikely to make moves in).
 * 
 * We keep a separate cache per Thread (using ThreadLocal), to make sure that
 * different playouts running in different Threads do not invalidate each
 * others' caches.
 * 
 * 
 * @author Dennis Soemers
 */
public class ActiveFeaturesCache
{

	//-------------------------------------------------------------------------

	/** Our caches (one per thread) */
	protected final ThreadLocal<Map<ProactiveFeaturesKey, CachedDataFootprint>> threadLocalCache;

	//-------------------------------------------------------------------------

	/**
	 * Constructor
	 */
	public ActiveFeaturesCache()
	{
		threadLocalCache = new ThreadLocal<Map<ProactiveFeaturesKey, CachedDataFootprint>>()
		{

			@Override
			protected Map<ProactiveFeaturesKey, CachedDataFootprint> initialValue()
			{
				return new HashMap<ProactiveFeaturesKey, CachedDataFootprint>();
			}

		};
	}

	//-------------------------------------------------------------------------
	
	/**
	 * Stores the given list of active feature indices in the cache, for the
	 * given state, from-, and to-positions
	 * 
	 * @param state
	 * @param from
	 * @param to
	 * @param activeFeaturesToCache
	 * @param player
	 */
	public void cache
	(
		final State state, 
		final int from, 
		final int to, 
		final int[] activeFeaturesToCache,
		final int player
	)
	{
		final ContainerState container = state.containerStates()[0];
		
		final ProactiveFeaturesKey key = new ProactiveFeaturesKey(player, from, to);
		final Map<ProactiveFeaturesKey, CachedDataFootprint> map = threadLocalCache.get();
		final CachedDataFootprint pair = map.get(key);
		final Footprint footprint = pair.footprint;
		
		final ChunkSet maskedEmptyCells;
		if (container.emptyChunkSetCell() != null && footprint.emptyCell() != null)
		{
			maskedEmptyCells = container.emptyChunkSetCell().clone();
			maskedEmptyCells.and(footprint.emptyCell());
		}
		else
		{
			maskedEmptyCells = null;
		}

		final ChunkSet maskedEmptyVertices;
		if (container.emptyChunkSetVertex() != null && footprint.emptyVertex() != null)
		{
			maskedEmptyVertices = container.emptyChunkSetVertex().clone();
			maskedEmptyVertices.and(footprint.emptyVertex());
		}
		else
		{
			maskedEmptyVertices = null;
		}

		final ChunkSet maskedEmptyEdges;
		if (container.emptyChunkSetEdge() != null && footprint.emptyEdge() != null)
		{
			maskedEmptyEdges = container.emptyChunkSetEdge().clone();
			maskedEmptyEdges.and(footprint.emptyEdge());
		}
		else
		{
			maskedEmptyEdges = null;
		}
		
		final ChunkSet maskedWhoCells = container.cloneWhoCell();
		if (maskedWhoCells != null && footprint.whoCell() != null)
			maskedWhoCells.and(footprint.whoCell());
		
		final ChunkSet maskedWhoVertices = container.cloneWhoVertex();
		if (maskedWhoVertices != null && footprint.whoVertex() != null)
			maskedWhoVertices.and(footprint.whoVertex());
		
		final ChunkSet maskedWhoEdges = container.cloneWhoEdge();
		if (maskedWhoEdges != null && footprint.whoEdge() != null)
			maskedWhoEdges.and(footprint.whoEdge());
		
		final ChunkSet maskedWhatCells = container.cloneWhatCell();
		if (maskedWhatCells != null && footprint.whatCell() != null)
			maskedWhatCells.and(footprint.whatCell());
		
		final ChunkSet maskedWhatVertices = container.cloneWhatVertex();
		if (maskedWhatVertices != null && footprint.whatVertex() != null)
			maskedWhatVertices.and(footprint.whatVertex());
		
		final ChunkSet maskedWhatEdges = container.cloneWhatEdge();
		if (maskedWhatEdges != null && footprint.whatEdge() != null)
			maskedWhatEdges.and(footprint.whatEdge());
		
		final CachedData data = new CachedData(
				activeFeaturesToCache, 
				maskedEmptyCells, 
				maskedEmptyVertices, 
				maskedEmptyEdges, 
				maskedWhoCells,
				maskedWhoVertices,
				maskedWhoEdges,
				maskedWhatCells,
				maskedWhatVertices,
				maskedWhatEdges);
		
		map.put(key, new CachedDataFootprint(data, footprint));
	}

	/**
	 * @param featureSet
	 * @param state
	 * @param from
	 * @param to
	 * @param player
	 * @return Cached list of indices of active features, or null if not in cache or if entry
	 * in cache is invalid.
	 */
	public int[] getCachedActiveFeatures
	(
		final FeatureSet featureSet, 
		final State state, 
		final int from, 
		final int to,
		final int player
	)
	{
		final ProactiveFeaturesKey key = new ProactiveFeaturesKey(player, from, to);
		final Map<ProactiveFeaturesKey, CachedDataFootprint> map = threadLocalCache.get();
		final CachedDataFootprint pair = map.get(key);
		
		if (pair == null)
		{
			// we need to compute and store footprint
			final Footprint footprint = featureSet.generateFootprint(state, from, to, player);
			map.put(key, new CachedDataFootprint(null, footprint));
		}
		else
		{
			final CachedData cachedData = pair.data;
			
			if (cachedData != null)
			{
				// we cached something, gotta make sure it's still valid
				final ContainerState container = state.containerStates()[0];
				final Footprint footprint = pair.footprint;
				
				//System.out.println("footprint empty = " + footprint.empty());
				//System.out.println("old empty state = " + cachedData.emptyState);
				//System.out.println("new empty state = " + container.empty().bitSet());
				
				if 
				(
					container.emptyChunkSetCell() != null && 
					!container.emptyChunkSetCell().matches(footprint.emptyCell(), cachedData.emptyStateCells)
				)
				{
					// part of "empty" state for Cells covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					container.emptyChunkSetVertex() != null && 
					!container.emptyChunkSetVertex().matches(footprint.emptyVertex(), cachedData.emptyStateVertices)
				)
				{
					// part of "empty" state for Vertices covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					container.emptyChunkSetEdge() != null && 
					!container.emptyChunkSetEdge().matches(footprint.emptyEdge(), cachedData.emptyStateEdges)
				)
				{
					// part of "empty" state for Edges covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					footprint.whoCell() != null &&
					!container.matchesWhoCell(footprint.whoCell(), cachedData.whoStateCells)
				)
				{
					// part of "who" state for Cells covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					footprint.whoVertex() != null &&
					!container.matchesWhoVertex(footprint.whoVertex(), cachedData.whoStateVertices)
				)
				{
					// part of "who" state for Vertices covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					footprint.whoEdge() != null &&
					!container.matchesWhoEdge(footprint.whoEdge(), cachedData.whoStateEdges)
				)
				{
					// part of "who" state for Edges covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					footprint.whatCell() != null &&
					!container.matchesWhatCell(footprint.whatCell(), cachedData.whatStateCells)
				)
				{
					// part of "what" state for Cells covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					footprint.whatVertex() != null &&
					!container.matchesWhatVertex(footprint.whatVertex(), cachedData.whatStateVertices)
				)
				{
					// part of "what" state for Vertices covered by footprint no longer matches, data invalid
					return null;
				}
				else if 
				(
					footprint.whatEdge() != null &&
					!container.matchesWhatEdge(footprint.whatEdge(), cachedData.whatStateEdges)
				)
				{
					// part of "what" state for Edges covered by footprint no longer matches, data invalid
					return null;
				}
				
				// cached data appears to be valid, so we can safely use it
				return cachedData.activeFeatureIndices;
			}
		}
		
		// no cached data, so return null
		//System.out.println("key not in cache at all");
		return null;
	}

	//-------------------------------------------------------------------------

	/**
	 * Wrapper for all the data we need to store for any given key in our cache
	 * 
	 * @author Dennis Soemers
	 */
	private class CachedData
	{

		/** Active features as previously computed and stored in cache */
		public final int[] activeFeatureIndices;

		/**
		 * masked "empty" ChunkSet in the game state for which we last cached active
		 * features (for cells)
		 */
		public final ChunkSet emptyStateCells;
		
		/**
		 * masked "empty" ChunkSet in the game state for which we last cached active
		 * features (for vertices)
		 */
		public final ChunkSet emptyStateVertices;
		
		/**
		 * masked "empty" ChunkSet in the game state for which we last cached active
		 * features (for edges)
		 */
		public final ChunkSet emptyStateEdges;

		/**
		 * masked "who" ChunkSet in the game state for which we last cached active
		 * features (for cells)
		 */
		public final ChunkSet whoStateCells;
		
		/**
		 * masked "who" ChunkSet in the game state for which we last cached active
		 * features (for vertices)
		 */
		public final ChunkSet whoStateVertices;
		
		/**
		 * masked "who" ChunkSet in the game state for which we last cached active
		 * features (for edges)
		 */
		public final ChunkSet whoStateEdges;

		/**
		 * masked "what" ChunkSet in the game state for which we last cached active
		 * features (for cells)
		 */
		public final ChunkSet whatStateCells;
		
		/**
		 * masked "what" ChunkSet in the game state for which we last cached active
		 * features (for vertices)
		 */
		public final ChunkSet whatStateVertices;
		
		/**
		 * masked "what" ChunkSet in the game state for which we last cached active
		 * features (for edges)
		 */
		public final ChunkSet whatStateEdges;

		/**
		 * Constructor
		 * 
		 * @param activeFeatureIndices
		 * @param emptyStateCells 
		 * @param emptyStateVertices 
		 * @param emptyStateEdges 
		 * @param whoStateCells 
		 * @param whoStateVertices 
		 * @param whoStateEdges 
		 * @param whatStateCells 
		 * @param whatStateVertices 
		 * @param whatStateEdges 
		 */
		public CachedData
		(
			final int[] activeFeatureIndices, 
			final ChunkSet emptyStateCells,
			final ChunkSet emptyStateVertices,
			final ChunkSet emptyStateEdges,
			final ChunkSet whoStateCells, 
			final ChunkSet whoStateVertices, 
			final ChunkSet whoStateEdges, 
			final ChunkSet whatStateCells,
			final ChunkSet whatStateVertices,
			final ChunkSet whatStateEdges
		)
		{
			this.activeFeatureIndices = activeFeatureIndices;
			this.emptyStateCells = emptyStateCells;
			this.emptyStateVertices = emptyStateEdges;
			this.emptyStateEdges = emptyStateEdges;
			this.whoStateCells = whoStateCells;
			this.whoStateVertices = whoStateVertices;
			this.whoStateEdges = whoStateEdges;
			this.whatStateCells = whatStateCells;
			this.whatStateVertices = whatStateVertices;
			this.whatStateEdges = whatStateEdges;
		}

	}

	//-------------------------------------------------------------------------
	
	/**
	 * Another wrapper, around the CachedData class above + a Footprint for the
	 * same key in HashMaps.
	 * 
	 * @author Dennis Soemers
	 */
	private class CachedDataFootprint
	{
		/** Data we want to cache (active features, old state vectors) */
		public final CachedData data;
		
		/** Footprint for the same key */
		public final Footprint footprint;
		
		/**
		 * Constructor
		 * @param data
		 * @param footprint
		 */
		public CachedDataFootprint(final CachedData data, final Footprint footprint)
		{
			this.data = data;
			this.footprint = footprint;
		}
	}
	
	//-------------------------------------------------------------------------

}
