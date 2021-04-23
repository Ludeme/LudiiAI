package features.feature_sets.prop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import features.spatial.cache.footprints.BaseFootprint;
import features.spatial.cache.footprints.FullFootprint;
import features.spatial.instances.AtomicProposition;
import features.spatial.instances.FeatureInstance;
import gnu.trove.list.array.TIntArrayList;
import main.collections.ChunkSet;
import other.state.State;
import other.state.container.ContainerState;

/**
 * A set of propositions which can (dis)prove feature instances, which
 * in turn can prove features.
 *
 * @author Dennis Soemers
 */
public class PropSet
{
	
	//-------------------------------------------------------------------------
	
	/** Array of feature instances (appropriately sorted) */
	protected final FeatureInstance[] featureInstances;
	
	/** Array of propositions to test */
	protected final AtomicProposition[] propositions;
	
	/** For every proposition, a bitset of feature instances that depend on that proposition */
	protected final BitSet[] instancesPerProp;
	
	/** For every feature, a bitset containing the instances for that feature */
	protected final BitSet[] instancesPerFeature;
	
	/** For every feature instance, a bitset of the propositions required for that feature instance */
	protected final int[][] propsPerInstance;
	
	/** Array of feature indices that are always active */
	protected final int[] autoActiveFeatures;
	
	/** For every proposition, if it's true, a bitset of other propositions that are then proven */
	protected final int[][] provesIfTruePerProp;
	
	/** For every proposition, if it's true, a bitset of instances that are then disproven */
	protected final BitSet[] disproveInstancesIfTrue;
	
	/** For every proposition, if it's false, a bitset of other propositions that are then proven */
	protected final int[][] provesIfFalsePerProp;
	
	/** For every proposition, if it's false, a bitset of instances that are then disproven */
	protected final BitSet[] disproveInstancesIfFalse;
	
	/** Bitset with a 1 entry for every single proposition */
	protected final boolean[] ALL_PROPS_ACTIVE;
	
	/** Bitset with a 1 entry for every single instance, except those for features that are always active */
	protected final BitSet INIT_INSTANCES_ACTIVE;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param featureInstances
	 * @param propositions
	 * @param dependentFeatureInstances
	 * @param instancesPerFeature
	 * @param propsPerInstance
	 * @param autoActiveFeatures
	 * @param thresholdedFeatures
	 * @param provesIfTruePerProp
	 * @param disprovesIfTruePerProp
	 * @param provesIfFalsePerProp
	 * @param disprovesIfFalsePerProp
	 */
	public PropSet
	(
		final FeatureInstance[] featureInstances,
		final AtomicProposition[] propositions, 
		final BitSet[] dependentFeatureInstances,
		final BitSet[] instancesPerFeature,
		final BitSet[] propsPerInstance,
		final int[] autoActiveFeatures,
		final BitSet thresholdedFeatures,
		final BitSet[] provesIfTruePerProp,
		final BitSet[] disprovesIfTruePerProp,
		final BitSet[] provesIfFalsePerProp,
		final BitSet[] disprovesIfFalsePerProp
	)
	{
//		System.out.println();
//		for (int i = 0; i < propositions.length; ++i)
//		{
//			System.out.println("Prop " + i + " = " + propositions[i]);
//		}
//		System.out.println();
		
		this.featureInstances = featureInstances;
		this.propositions = propositions;
		this.instancesPerProp = dependentFeatureInstances;
		this.instancesPerFeature = instancesPerFeature;
		
		this.propsPerInstance = new int[propsPerInstance.length][];
		for (int i = 0; i < propsPerInstance.length; ++i)
		{
			this.propsPerInstance[i] = new int[propsPerInstance[i].cardinality()];
			int k = 0;
			for (int j = propsPerInstance[i].nextSetBit(0); j >= 0; j = propsPerInstance[i].nextSetBit(j + 1))
			{
				this.propsPerInstance[i][k++] = j;
			}
		}
		
		this.autoActiveFeatures = autoActiveFeatures;
		
		this.provesIfTruePerProp = new int[provesIfTruePerProp.length][];
		for (int i = 0; i < provesIfTruePerProp.length; ++i)
		{
			this.provesIfTruePerProp[i] = new int[provesIfTruePerProp[i].cardinality()];
			int k = 0;
			for (int j = provesIfTruePerProp[i].nextSetBit(0); j >= 0; j = provesIfTruePerProp[i].nextSetBit(j + 1))
			{
				this.provesIfTruePerProp[i][k++] = j;
			}
		}
		
		this.provesIfFalsePerProp = new int[provesIfFalsePerProp.length][];
		for (int i = 0; i < provesIfFalsePerProp.length; ++i)
		{
			this.provesIfFalsePerProp[i] = new int[provesIfFalsePerProp[i].cardinality()];
			int k = 0;
			for (int j = provesIfFalsePerProp[i].nextSetBit(0); j >= 0; j = provesIfFalsePerProp[i].nextSetBit(j + 1))
			{
				this.provesIfFalsePerProp[i][k++] = j;
			}
		}
		
		this.disproveInstancesIfTrue = new BitSet[disprovesIfTruePerProp.length];
		for (int i = 0; i < disproveInstancesIfTrue.length; ++i)
		{
			disproveInstancesIfTrue[i] = new BitSet();
			for (int j = disprovesIfTruePerProp[i].nextSetBit(0); j >= 0; j = disprovesIfTruePerProp[i].nextSetBit(j + 1))
			{
				disproveInstancesIfTrue[i].or(instancesPerProp[j]);
			}
		}
		
		this.disproveInstancesIfFalse = new BitSet[disprovesIfFalsePerProp.length];
		for (int i = 0; i < disproveInstancesIfFalse.length; ++i)
		{
			disproveInstancesIfFalse[i] = new BitSet();
			for (int j = disprovesIfFalsePerProp[i].nextSetBit(0); j >= 0; j = disprovesIfFalsePerProp[i].nextSetBit(j + 1))
			{
				disproveInstancesIfFalse[i].or(instancesPerProp[j]);
			}
			
			// Also incorporate any instances that require the proposition itself as disprove-if-false instances
			disproveInstancesIfFalse[i].or(instancesPerProp[i]);
		}
		
		ALL_PROPS_ACTIVE = new boolean[propositions.length];
		Arrays.fill(ALL_PROPS_ACTIVE, true);
		
		INIT_INSTANCES_ACTIVE = new BitSet(featureInstances.length);
		INIT_INSTANCES_ACTIVE.set(0, featureInstances.length);
		
		// TODO following two little loops should be unnecessary, all those instances should already be gone
		for (final int feature : autoActiveFeatures)
		{
			INIT_INSTANCES_ACTIVE.andNot(instancesPerFeature[feature]);
		}
		for (int i = thresholdedFeatures.nextSetBit(0); i >= 0; i = thresholdedFeatures.nextSetBit(i + 1))
		{
			INIT_INSTANCES_ACTIVE.andNot(instancesPerFeature[i]);
		}
		
		// Remove propositions for instances if those propositions also appear in earlier propositions,
		// and those other instances are guaranteed to get checked before the later instances.
		//
		// An earlier instance is guaranteed to get checked before a later instance if the earlier instance 
		// is the first instance of its feature
		//
		// The tracking of active props already would avoid re-evaluating these props, but this optimisation
		// step removes even more overhead by removing the bits altogether
		final boolean[] featuresObserved = new boolean[instancesPerFeature.length];
		for (int i = 0; i < featureInstances.length; ++i)
		{
			final int featureIdx = featureInstances[i].feature().featureSetIndex();

			if (!featuresObserved[featureIdx])
			{
				featuresObserved[featureIdx] = true;
				final BitSet instanceProps = propsPerInstance[i];
				
				for (int j = i + 1; j < featureInstances.length; ++j)
				{
					propsPerInstance[j].andNot(instanceProps);
				}
			}
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param state
	 * @return List of active instances for given state
	 */
	public List<FeatureInstance> getActiveInstances(final State state)
	{
		final List<FeatureInstance> active = new ArrayList<FeatureInstance>();
		
		final boolean[] activeProps = ALL_PROPS_ACTIVE.clone();
		final BitSet activeInstances = (BitSet) INIT_INSTANCES_ACTIVE.clone();
		
		for (int i = 0; i < activeProps.length; ++i)
		{
			if (!activeProps[i])
				continue;
			
			if (activeInstances.intersects(instancesPerProp[i]))		// If false, might as well not check anything
			{
				if (!propositions[i].matches(state))		// Requirement not satisfied
					activeInstances.andNot(instancesPerProp[i]);
			}
		}
		
		for (int i = activeInstances.nextSetBit(0); i >= 0; i = activeInstances.nextSetBit(i + 1))
		{
			active.add(featureInstances[i]);
		}
		
		return active;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param state
	 * @return List of active instances for given state
	 */
	public TIntArrayList getActiveFeatures(final State state)
	{
		final TIntArrayList activeFeatures = new TIntArrayList(instancesPerFeature.length);
		activeFeatures.add(autoActiveFeatures);
		
		final boolean[] activeProps = ALL_PROPS_ACTIVE.clone();
		final BitSet activeInstances = (BitSet) INIT_INSTANCES_ACTIVE.clone();
		//System.out.println();
		//System.out.println("Auto-active features: " + Arrays.toString(autoActiveFeatures));

		outer:
		for 
		(
			int instanceToCheck = activeInstances.nextSetBit(0); 
			instanceToCheck >= 0; 
			instanceToCheck = activeInstances.nextSetBit(instanceToCheck + 1))
		{
			final int[] instanceProps = propsPerInstance[instanceToCheck];
			//System.out.println("Checking feature instance " + instanceToCheck + ": " + featureInstances[instanceToCheck]);
			//System.out.println("instance props = " + Arrays.toString(instanceProps));
			
			// Keep checking props for this instance in order
			for (int i = 0; i < instanceProps.length; ++i)
			{
				final int propID = instanceProps[i];
				
				if (!activeProps[propID])
					continue;	// Prop already known to be true
				
				// We're checking propID now, so mark it as inactive
				activeProps[propID] = false;
				
				//System.out.println("evaluating prop " + propID + ": " + propositions[propID]);
				
				// Check the proposition
				if (!propositions[propID].matches(state))
				{
					// Proposition is false
					//System.out.println("evaluated to false!");
					
					// Prove other propositions that get proven by this one being false; simply switch them
					// off in the list of active props
					for (final int j : provesIfFalsePerProp[propID])
					{
						activeProps[j] = false;
					}
					
					// Disprove propositions that get disproven by this one being false; switch off any instances 
					// that require them
					activeInstances.andNot(disproveInstancesIfFalse[propID]);
					
					// No point in continuing with props for this instance, instance is false anyway
					continue outer;
				}
				else
				{
					// Proposition is true
					//System.out.println("evaluated to true");
					
					// Prove other propositions that get proven by this one being true; simply switch them
					// off in the list of active props
					for (final int j : provesIfTruePerProp[propID])
					{
						activeProps[j] = false;
					}
					
					// Disprove propositions that get disproven by this one being true; switch off any
					// instances that require them
					activeInstances.andNot(disproveInstancesIfTrue[propID]);
				}
			}
			
			// If we reach this point, the feature instance (and hence the feature) is active
			final int newActiveFeature = featureInstances[instanceToCheck].feature().featureSetIndex();
			activeFeatures.add(newActiveFeature);
			
			// This also means that we can skip any remaining instances for the same feature
			activeInstances.andNot(instancesPerFeature[newActiveFeature]);
		}
		
		//System.out.println();
		return activeFeatures;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param containerState
	 * @return Footprint of the state members that may be tested by this set.
	 */
	public BaseFootprint generateFootprint(final ContainerState containerState)
	{
		final ChunkSet footprintEmptyCells = 
				containerState.emptyChunkSetCell() != null ?
				new ChunkSet(containerState.emptyChunkSetCell().chunkSize(), 1) :
				null;
		final ChunkSet footprintEmptyVertices = 
				containerState.emptyChunkSetVertex() != null ?
				new ChunkSet(containerState.emptyChunkSetVertex().chunkSize(), 1) :
				null;
		final ChunkSet footprintEmptyEdges = 
				containerState.emptyChunkSetEdge() != null ?
				new ChunkSet(containerState.emptyChunkSetEdge().chunkSize(), 1) :
				null;
				
		final ChunkSet footprintWhoCells = 
				containerState.chunkSizeWhoCell() > 0 ? 
				new ChunkSet(containerState.chunkSizeWhoCell(), 1) : 
				null;
		final ChunkSet footprintWhoVertices = 
				containerState.chunkSizeWhoVertex() > 0 ? 
				new ChunkSet(containerState.chunkSizeWhoVertex(), 1) : 
				null;
		final ChunkSet footprintWhoEdges = 
				containerState.chunkSizeWhoEdge() > 0 ? 
				new ChunkSet(containerState.chunkSizeWhoEdge(), 1) : 
				null;
				
		final ChunkSet footprintWhatCells = 
				containerState.chunkSizeWhatCell() > 0 ?
				new ChunkSet(containerState.chunkSizeWhatCell(), 1) :
				null;
		final ChunkSet footprintWhatVertices = 
				containerState.chunkSizeWhatVertex() > 0 ?
				new ChunkSet(containerState.chunkSizeWhatVertex(), 1) :
				null;
		final ChunkSet footprintWhatEdges = 
				containerState.chunkSizeWhatEdge() > 0 ?
				new ChunkSet(containerState.chunkSizeWhatEdge(), 1) :
				null;
				
		for (final AtomicProposition prop : propositions)
		{
			switch (prop.graphElementType())
			{
			case Cell:
				switch (prop.stateVectorType())
				{
				case Empty:
					prop.addMaskTo(footprintEmptyCells);
					break;
				case Who:
					prop.addMaskTo(footprintWhoCells);
					break;
				case What:
					prop.addMaskTo(footprintWhatCells);
					break;
				}
				break;
			case Edge:
				switch (prop.stateVectorType())
				{
				case Empty:
					prop.addMaskTo(footprintEmptyEdges);
					break;
				case Who:
					prop.addMaskTo(footprintWhoEdges);
					break;
				case What:
					prop.addMaskTo(footprintWhatEdges);
					break;
				}
				break;
			case Vertex:
				switch (prop.stateVectorType())
				{
				case Empty:
					prop.addMaskTo(footprintEmptyVertices);
					break;
				case Who:
					prop.addMaskTo(footprintWhoVertices);
					break;
				case What:
					prop.addMaskTo(footprintWhatVertices);
					break;
				}
				break;
			}
		}
				
		return new FullFootprint
				(
					footprintEmptyCells, 
					footprintEmptyVertices, 
					footprintEmptyEdges, 
					footprintWhoCells, 
					footprintWhoVertices,
					footprintWhoEdges,
					footprintWhatCells,
					footprintWhatVertices,
					footprintWhatEdges
				);
	}
	
	//-------------------------------------------------------------------------

}
