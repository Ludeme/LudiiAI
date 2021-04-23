package features.spatial.instances;

import game.types.board.SiteType;
import main.collections.ChunkSet;
import other.state.State;

/**
 * A test that check for a single specific vertex that must NOT contain a specific value
 *
 * @author Dennis Soemers
 */
public class SingleMustNotWhatVertex extends AtomicProposition
{
	
	//-------------------------------------------------------------------------
	
	/** The index of the word that we want to match */
	protected final int wordIdx;
	
	/** The mask that we want to apply to the word when matching */
	protected final long mask;
	
	/** The word that we should match after masking */
	protected final long matchingWord;
	
	/** The site we look at */
	protected final int site;
	
	/** The value we look for in chunkset */
	protected final int value;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param mustNotWhatSite
	 * @param mustNotWhatValue
	 * @param chunkSize
	 */
	public SingleMustNotWhatVertex(final int mustNotWhatSite, final int mustNotWhatValue, final int chunkSize)
	{
		// Using same logic as ChunkSet.setChunk() here to determine wordIdx, mask, and matchingWord
		final int bitIndex  = mustNotWhatSite * chunkSize;
		wordIdx = bitIndex >> 6;
		
		final int up = bitIndex & 63;
		mask = ((0x1L << chunkSize) - 1) << up;
		matchingWord = (((long)mustNotWhatValue) << up);
		
		this.site = mustNotWhatSite;
		this.value = mustNotWhatValue;
	}
	
	//-------------------------------------------------------------------------

	@Override
	public boolean matches(final State state)
	{
		return !state.containerStates()[0].matchesWhatVertex(wordIdx, mask, matchingWord);
	}
	
	@Override
	public boolean onlyRequiresSingleMustEmpty()
	{
		return false;
	}

	@Override
	public boolean onlyRequiresSingleMustWho()
	{
		return false;
	}

	@Override
	public boolean onlyRequiresSingleMustWhat()
	{
		return false;
	}

	@Override
	public SiteType graphElementType()
	{
		return SiteType.Vertex;
	}
	
	@Override
	public void addMaskTo(final ChunkSet chunkSet)
	{
		chunkSet.addMask(wordIdx, mask);
	}
	
	@Override
	public StateVectorTypes stateVectorType()
	{
		return StateVectorTypes.What;
	}
	
	@Override
	public int testedSite()
	{
		return site;
	}
	
	@Override
	public int value()
	{
		return value;
	}
	
	@Override
	public boolean negated()
	{
		return true;
	}
	
	//-------------------------------------------------------------------------

	@Override
	public boolean provesIfTrue(final AtomicProposition other)
	{
		if (graphElementType() != other.graphElementType())
			return false;
		
		if (testedSite() != other.testedSite())
			return false;
		
		// Knowing that a site does not contain a specific piece does not let us infer anything else
		
		return false;
	}

	@Override
	public boolean disprovesIfTrue(final AtomicProposition other)
	{
		if (graphElementType() != other.graphElementType())
			return false;
		
		if (testedSite() != other.testedSite())
			return false;
		
		// Knowing that a site does not contain a specific piece does not let us infer anything else
		
		return false;
	}

	@Override
	public boolean provesIfFalse(final AtomicProposition other)
	{
		if (graphElementType() != other.graphElementType())
			return false;
		
		if (testedSite() != other.testedSite())
			return false;
		
		// TODO with game-specific knowledge of owner of piece type, we can also make inferences
		// about friend/enemy
		
		// False means we DO contain a specific piece, so we prove that we contain it
		if (other.stateVectorType() == StateVectorTypes.What)
			return (!other.negated() && value() == other.value());
		
		// False means we DO contain a specific piece, so we prove not empty
		return (value() > 0 && other.stateVectorType() == StateVectorTypes.Empty && other.negated());
	}

	@Override
	public boolean disprovesIfFalse(final AtomicProposition other)
	{
		if (graphElementType() != other.graphElementType())
			return false;
		
		if (testedSite() != other.testedSite())
			return false;
		
		// TODO with game-specific knowledge of owner of piece type, we can also make inferences
		// about friend/enemy
		
		// False means we DO contain a specific piece, so we disprove that we don't contain it
		if (other.stateVectorType() == StateVectorTypes.What)
			return (other.negated() && value() == other.value());
		
		// False means we DO contain a specific piece, so we disprove empty
		return (value() > 0 && other.stateVectorType() == StateVectorTypes.Empty && !other.negated());
	}
	
	//-------------------------------------------------------------------------

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (mask ^ (mask >>> 32));
		result = prime * result + (int) (matchingWord ^ (matchingWord >>> 32));
		result = prime * result + wordIdx;
		return result;
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (this == obj)
			return true;

		if (!(obj instanceof SingleMustNotWhatVertex))
			return false;
		
		final SingleMustNotWhatVertex other = (SingleMustNotWhatVertex) obj;
		return (mask == other.mask && matchingWord == other.matchingWord && wordIdx == other.wordIdx);
	}
	
	@Override
	public String toString()
	{
		return "[Vertex " + site + " must NOT contain " + value + "]";
	}
	
	//-------------------------------------------------------------------------

}
