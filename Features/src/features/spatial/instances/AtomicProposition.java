package features.spatial.instances;

import main.collections.ChunkSet;

/**
 * An atomic proposition is a test that checks for only a single specific
 * value (either absent or present) in a single specific chunk of a single
 * data vector.
 *
 * @author Dennis Soemers
 */
public abstract class AtomicProposition implements BitwiseTest
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Types of state vectors that atomic propositions can apply to
	 *
	 * @author Dennis Soemers
	 */
	public enum StateVectorTypes
	{
		/** For propositions that check the Empty chunkset */
		Empty,
		/** For propositions that check the Who chunkset */
		Who,
		/** For propositions that check the What chunkset */
		What
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public boolean hasNoTests()
	{
		return false;
	}
	
	/**
	 * Add mask for bits checked by this proposition to given chunkset
	 * @param chunkSet
	 */
	public abstract void addMaskTo(final ChunkSet chunkSet);
	
	/**
	 * @return State vector type this atomic proposition applies to.
	 */
	public abstract StateVectorTypes stateVectorType();
	
	/**
	 * @return Which site does this proposition look at?
	 */
	public abstract int testedSite();
	
	/**
	 * @return What value do we expect to (not) see?
	 */
	public abstract int value();
	
	/**
	 * @return Do we expect to NOT see the value returned by value()?
	 */
	public abstract boolean negated();
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param other
	 * @return Does this proposition being true also prove the given other prop?
	 */
	public abstract boolean provesIfTrue(final AtomicProposition other);
	
	/**
	 * @param other
	 * @return Does this proposition being true disprove the given other prop?
	 */
	public abstract boolean disprovesIfTrue(final AtomicProposition other);
	
	/**
	 * @param other
	 * @return Does this proposition being false prove the given other prop?
	 */
	public abstract boolean provesIfFalse(final AtomicProposition other);
	
	/**
	 * @param other
	 * @return Does this proposition being false disprove the given other prop?
	 */
	public abstract boolean disprovesIfFalse(final AtomicProposition other);
	
	//-------------------------------------------------------------------------

}
