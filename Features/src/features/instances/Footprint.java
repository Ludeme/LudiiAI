package features.instances;

import main.collections.ChunkSet;

/**
 * Wrapper class for masks that represent the key-specific (specific to
 * player index / from-pos / to-pos) footprint of a complete Feature Set.
 * 
 * @author Dennis Soemers
 */
public final class Footprint
{
	
	//-------------------------------------------------------------------------
	
	/** Mask for all chunks that we run at least one "empty" cell test on */
	protected final ChunkSet emptyCell;
	/** Mask for all chunks that we run at least one "empty" vertex test on */
	protected final ChunkSet emptyVertex;
	/** Mask for all chunks that we run at least one "empty" edge test on */
	protected final ChunkSet emptyEdge;

	/** Mask for all chunks that we run at least one "who" cell test on */
	protected final ChunkSet whoCell;
	/** Mask for all chunks that we run at least one "who" vertex test on */
	protected final ChunkSet whoVertex;
	/** Mask for all chunks that we run at least one "who" edge test on */
	protected final ChunkSet whoEdge;

	/** Mask for all chunks that we run at least one "what" cell test on */
	protected final ChunkSet whatCell;
	/** Mask for all chunks that we run at least one "what" vertex test on */
	protected final ChunkSet whatVertex;
	/** Mask for all chunks that we run at least one "what" edge test on */
	protected final ChunkSet whatEdge;
	
	//-------------------------------------------------------------------------

	/**
	 * Constructor
	 * @param emptyCell 
	 * @param emptyVertex 
	 * @param emptyEdge 
	 * @param whoCell 
	 * @param whoVertex 
	 * @param whoEdge 
	 * @param whatCell 
	 * @param whatVertex 
	 * @param whatEdge 
	 */
	public Footprint
	(
		final ChunkSet emptyCell,
		final ChunkSet emptyVertex,
		final ChunkSet emptyEdge,
		final ChunkSet whoCell, 
		final ChunkSet whoVertex,
		final ChunkSet whoEdge,
		final ChunkSet whatCell,
		final ChunkSet whatVertex,
		final ChunkSet whatEdge
	)
	{
		this.emptyCell = emptyCell;
		this.emptyVertex = emptyVertex;
		this.emptyEdge = emptyEdge;
		this.whoCell = whoCell;
		this.whoVertex = whoVertex;
		this.whoEdge = whoEdge;
		this.whatCell = whatCell;
		this.whatVertex = whatVertex;
		this.whatEdge = whatEdge;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return Footprint on "empty" ChunkSet for cells
	 */
	public final ChunkSet emptyCell()
	{
		return emptyCell;
	}
	
	/**
	 * @return Footprint on "empty" ChunkSet for vertices
	 */
	public final ChunkSet emptyVertex()
	{
		return emptyVertex;
	}
	
	/**
	 * @return Footprint on "empty" ChunkSet for edges
	 */
	public final ChunkSet emptyEdge()
	{
		return emptyEdge;
	}
	
	/**
	 * @return Footprint on "who" ChunkSet for cells
	 */
	public final ChunkSet whoCell()
	{
		return whoCell;
	}
	
	/**
	 * @return Footprint on "who" ChunkSet for vertices
	 */
	public final ChunkSet whoVertex()
	{
		return whoVertex;
	}
	
	/**
	 * @return Footprint on "who" ChunkSet for edges
	 */
	public final ChunkSet whoEdge()
	{
		return whoEdge;
	}
	
	/**
	 * @return Footprint on "what" ChunkSet for cells
	 */
	public final ChunkSet whatCell()
	{
		return whatCell;
	}
	
	/**
	 * @return Footprint on "what" ChunkSet for vertices
	 */
	public final ChunkSet whatVertex()
	{
		return whatVertex;
	}
	
	/**
	 * @return Footprint on "what" ChunkSet for edges
	 */
	public final ChunkSet whatEdge()
	{
		return whatEdge;
	}
	
	//-------------------------------------------------------------------------

}
