package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import game.Game;
import game.equipment.component.Component;
import game.equipment.container.Container;
import game.types.state.GameType;
import main.Constants;
import main.FileHandling;
import main.math.MathRoutines;
import topology.TopologyElement;
import util.GameLoader;
import util.Move;

/**
 * Wrapper around a Ludii game, with various extra methods required for
 * other frameworks that like to wrap around Ludii (e.g. OpenSpiel, Polygames)
 * 
 * @author Dennis Soemers
 */
public final class LudiiGameWrapper
{
	
	//-------------------------------------------------------------------------
	
	/** Number of channels we use for stacks (just 1 for not-stacking-games) */
	protected static final int NUM_STACK_CHANNELS = 10;
	
	/** Number of channels for local state per site (in games that have local state per site) */
	protected static final int NUM_LOCAL_STATE_CHANNELS = 6;
	
	/** Maximum distance we consider between from and to x/y coords for move channels */
	protected static final int DEFAULT_MOVE_TENSOR_DIST_CLIP = 3;
	
	/** Clipping variable for levelMin / levelMax terms in move channel index computation */
	protected static final int MOVE_TENSOR_LEVEL_CLIP = 2;
	
	//-------------------------------------------------------------------------
	
	/** Our game object */
	protected final Game game;
	
	/** X-coordinates in state tensors for all sites in game */
	protected int[] xCoords;
	
	/** Y-coordinates in state tensors for all sites in game */
	protected int[] yCoords;
	
	/** X-dimension for state tensors */
	protected int tensorDimX;
	
	/** Y-dimension for state tensors */
	protected int tensorDimY;
	
	/** Number of channels we need for state tensors */
	protected int stateTensorNumChannels;
	
	/** Array of names for the channels in state tensors */
	protected String[] stateTensorChannelNames;
	
	/** Maximum absolute distance we consider between from and to positions for move tensors */
	protected final int moveTensorDistClip;
	
	/** Channel index for Pass move in move-tensor-representation */
	protected int MOVE_PASS_CHANNEL_IDX;
	
	/** Channel index for Swap move in move-tensor-representation */
	protected int MOVE_SWAP_CHANNEL_IDX;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param gameName
	 */
	public LudiiGameWrapper(final String gameName)
	{
		game = GameLoader.loadGameFromName(gameName);
		
		if ((game.stateFlags() & GameType.UsesFromPositions) == 0L)
			moveTensorDistClip = 0;		// no from-positions in any moves in this game
		else
			moveTensorDistClip = DEFAULT_MOVE_TENSOR_DIST_CLIP;
		
		computeTensorCoords();
	}
	
	/**
	 * Constructor with game Options
	 * @param gameName
	 * @param gameOptions
	 */
	public LudiiGameWrapper(final String gameName, final String... gameOptions)
	{
		game = GameLoader.loadGameFromName(gameName, Arrays.asList(gameOptions));
		
		if ((game.stateFlags() & GameType.UsesFromPositions) == 0L)
			moveTensorDistClip = 0;		// no from-positions in any moves in this game
		else
			moveTensorDistClip = DEFAULT_MOVE_TENSOR_DIST_CLIP;
		
		computeTensorCoords();
	}
	
	/**
	 * Constructor for already-instantiated game. NOTE: here we expect
	 * that game.create() has also already been called!
	 * @param game
	 */
	public LudiiGameWrapper(final Game game)
	{
		this.game = game;
		
		if ((game.stateFlags() & GameType.UsesFromPositions) == 0L)
			moveTensorDistClip = 0;		// no from-positions in any moves in this game
		else
			moveTensorDistClip = DEFAULT_MOVE_TENSOR_DIST_CLIP;
		
		computeTensorCoords();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return The version of Ludii that we're using (a String in 
	 * "x.y.z" format).
	 */
	public static String ludiiVersion()
	{
		return Constants.LUDEME_VERSION;
	}
	
	/**
	 * @return True if and only if the game is a simultaneous-move game
	 */
	public boolean isSimultaneousMoveGame()
	{
		return !game.isAlternatingMoveGame();
	}
	
	/**
	 * @return True if and only if the game is a stochastic game
	 */
	public boolean isStochasticGame()
	{
		return game.isStochasticGame();
	}
	
	/**
	 * @return True if and only if the game is an imperfect-information game
	 */
	public boolean isImperfectInformationGame()
	{
		return game.hiddenInformation();
	}
	
	/**
	 * @return Game's name
	 */
	public String name()
	{
		return game.name();
	}
	
	/**
	 * @return Number of players
	 */
	public int numPlayers()
	{
		return game.players().count();
	}
	
	/**
	 * @return X coordinates in state tensors for all sites
	 */
	public int[] tensorCoordsX()
	{
		return xCoords;
	}
	
	/**
	 * @return Y coordinates in state tensors for all sites
	 */
	public int[] tensorCoordsY()
	{
		return yCoords;
	}
	
	/**
	 * @return Size of x-dimension for state tensors
	 */
	public int tensorDimX()
	{
		return tensorDimX;
	}
	
	/**
	 * @return Size of y-dimension for state tensors
	 */
	public int tensorDimY()
	{
		return tensorDimY;
	}
	
	/**
	 * @return Shape of tensors for moves: [numChannels, size(x dimension), size(y dimension)]
	 */
	public int[] moveTensorsShape()
	{
		return new int[] {MOVE_SWAP_CHANNEL_IDX + 1, tensorDimX(), tensorDimY()};
	}
	
	/**
	 * @return Shape of tensors for states: [numChannels, size(x dimension), size(y dimension)]
	 */
	public int[] stateTensorsShape()
	{
		return new int[] {stateTensorNumChannels, tensorDimX(), tensorDimY()};
	}
	
	/**
	 * @return Array of names for all the channels in our state tensors
	 */
	public String[] stateTensorChannelNames()
	{
		return stateTensorChannelNames;
	}
	
	/**
	 * @param move
	 * @return A tensor representation of given move (shape = [3])
	 */
	public int[] moveToTensor(final Move move)
	{
		if (move.isPass())
		{
			return new int[] {MOVE_PASS_CHANNEL_IDX, 0, 0};
		}
		else if (move.isSwap())
		{
			return new int[] {MOVE_SWAP_CHANNEL_IDX, 0, 0};
		}
		else
		{
			final int from = move.fromNonDecision();
			final int to = move.toNonDecision();
			final int levelMin = move.levelMinNonDecision();
			final int levelMax = move.levelMaxNonDecision();

			assert (to >= 0);

			final int fromX = from != Constants.OFF ? xCoords[from] : -1;
			final int fromY = from != Constants.OFF ? yCoords[from] : -1;
			final int toX = xCoords[to];
			final int toY = yCoords[to];

			final int diffX = from != Constants.OFF ? toX - fromX : 0;
			final int diffY = from != Constants.OFF ? toY - fromY : 0;

			int channelIdx = MathRoutines.clip(
					diffX, 
					-moveTensorDistClip, 
					moveTensorDistClip) + moveTensorDistClip;

			channelIdx *= (moveTensorDistClip * 2 + 1);
			channelIdx += MathRoutines.clip(
					diffY, 
					-moveTensorDistClip, 
					moveTensorDistClip) + moveTensorDistClip;

			if (game.isStacking())
			{
				channelIdx *= (LudiiGameWrapper.MOVE_TENSOR_LEVEL_CLIP + 1);
				channelIdx += MathRoutines.clip(levelMin, 0, LudiiGameWrapper.MOVE_TENSOR_LEVEL_CLIP);

				channelIdx *= (LudiiGameWrapper.MOVE_TENSOR_LEVEL_CLIP + 1);
				channelIdx += MathRoutines.clip(levelMax - levelMin, 0, LudiiGameWrapper.MOVE_TENSOR_LEVEL_CLIP);
			}

			return new int[] {channelIdx, toX, toY};
		}
	}
	
	/**
	 * @param moveTensor
	 * @return A single int representation of a move (converted from its tensor representation)
	 */
	public int moveTensorToInt(final int[] moveTensor)
	{
		final int[] moveTensorsShape = moveTensorsShape();
		return moveTensorsShape[1] * moveTensorsShape[2] * moveTensor[0] + 
				moveTensorsShape[2] * moveTensor[1] + 
				moveTensor[2];
	}
	
	/**
	 * @param move
	 * @return A single int representation of a move
	 */
	public int moveToInt(final Move move)
	{
		return moveTensorToInt(moveToTensor(move));
	}
	
	/**
	 * @return Number of distinct actions that we can represent in our tensor-based
	 * 	representations for this game.
	 */
	public int numDistinctActions()
	{
		final int[] moveTensorsShape = moveTensorsShape();
		return moveTensorsShape[1] * moveTensorsShape[2] * moveTensorsShape[3];
	}
	
	/**
	 * @return Max duration of game (measured in moves)
	 */
	public int maxGameLength()
	{
		return game.getMaxMoveLimit();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Computes x and y coordinates in state tensors for all sites in the game.
	 */
	private void computeTensorCoords()
	{
		if (game.hasSubgames())
		{
			System.err.println("Computing tensors for Matches is not yet supported.");
			return;
		}
		
		final Container[] containers = game.equipment().containers();
		final List<? extends TopologyElement> graphElements = game.graphPlayElements();
		xCoords = new int[game.equipment().totalDefaultSites()];
		yCoords = new int[game.equipment().totalDefaultSites()];
		final int numBoardSites = graphElements.size();
		
		// first sort by X, to find x indices for vertices
		final List<? extends TopologyElement> sortedGraphElements = 
				new ArrayList<TopologyElement>(graphElements);
		sortedGraphElements.sort(new Comparator<TopologyElement>() 
		{

			@Override
			public int compare(final TopologyElement o1, final TopologyElement o2)
			{
				if (o1.centroid().getX() < o2.centroid().getX())
					return -1;
				else if (o1.centroid().getX() == o2.centroid().getX())
					return 0;
				else
					return 1;
			}
			
		});
		
		int currIdx = 0;
		double currXPos = sortedGraphElements.get(0).centroid().getX();
		for (final TopologyElement e : sortedGraphElements)
		{			
			final double xPos = e.centroid().getX();
			if (xPos > currXPos)
			{
				++currIdx;
				currXPos = xPos;
			}
			
			xCoords[e.index()] = currIdx;
		}
		
		final int maxBoardIndexX = currIdx;
		
		// now the same, but for y indices
		sortedGraphElements.sort(new Comparator<TopologyElement>() 
		{

			@Override
			public int compare(final TopologyElement o1, final TopologyElement o2)
			{
				if (o1.centroid().getY() < o2.centroid().getY())
					return -1;
				else if (o1.centroid().getY() == o2.centroid().getY())
					return 0;
				else
					return 1;
			}
			
		});
		
		currIdx = 0;
		double currYPos = sortedGraphElements.get(0).centroid().getY();
		for (final TopologyElement e : sortedGraphElements)
		{
			final double yPos = e.centroid().getY();
			if (yPos > currYPos)
			{
				++currIdx;
				currYPos = yPos;
			}
			
			yCoords[e.index()] = currIdx;
		}
		
		final int maxBoardIndexY = currIdx;
		
		tensorDimX = maxBoardIndexX + 1;
		tensorDimY = maxBoardIndexY + 1;
		
		// Maybe need to extend the board a bit for hands / other containers
		final int numContainers = game.numContainers();
		
		if (numContainers > 1)
		{
			int maxNonBoardContIdx = -1;
			for (int c = 1; c < numContainers; ++c)
			{
				maxNonBoardContIdx = Math.max(containers[c].numSites() - 1, maxNonBoardContIdx);
			}
			
			boolean handsAsRows = false;
			if (maxBoardIndexX < maxBoardIndexY && maxNonBoardContIdx <= maxBoardIndexX)
				handsAsRows = true;
			else if (maxNonBoardContIdx > maxBoardIndexX && maxBoardIndexX > maxBoardIndexY)
				handsAsRows = true;
			
			if (handsAsRows)
			{
				// We paste hands as extra rows for the board
				tensorDimY += 1;	// a dummy row to split board from other containers
				tensorDimY += (numContainers - 1);	// one extra row per container
				
				if (maxNonBoardContIdx > maxBoardIndexX)
				{
					// Hand rows are longer than the board's rows, so need extra cols too
					tensorDimX += (maxNonBoardContIdx - maxBoardIndexX);
				}
				
				// Compute coordinates for all vertices in extra containers
				int nextContStartIdx = numBoardSites;
				
				for (int c = 1; c < numContainers; ++c)
				{
					final Container cont = containers[c];
					
					for (int site = 0; site < cont.numSites(); ++site)
					{
						xCoords[site + nextContStartIdx] = site;
						yCoords[site + nextContStartIdx] = maxBoardIndexY + 1 + c;
					}
					
					nextContStartIdx += cont.numSites();
				}
			}
			else
			{
				// We paste hands as extra cols for the board
				tensorDimX += 1;	// a dummy col to split board from other containers
				tensorDimX += (numContainers - 1);	// one extra col per container
				
				if (maxNonBoardContIdx > maxBoardIndexY)
				{
					// Hand cols are longer than the board's cols, so need extra rows too
					tensorDimY += (maxNonBoardContIdx - maxBoardIndexY);
				}
				
				// Compute coordinates for all cells in extra containers
				for (int c = 1; c < numContainers; ++c)
				{
					final Container cont = containers[c];
					int nextContStartIdx = numBoardSites;
					
					for (int site = 0; site < cont.numSites(); ++site)
					{
						xCoords[site + nextContStartIdx] = maxBoardIndexX + 1 + c;
						yCoords[site + nextContStartIdx] = site;
					}
					
					nextContStartIdx += cont.numSites();
				}
			}
		}
		
		final Component[] components = game.equipment().components();
		final int numPlayers = game.players().count();
		final int numPieceTypes = components.length - 1;
		final boolean stacking = game.isStacking();
		final boolean usesCount = game.requiresCount();
		final boolean usesAmount = game.requiresBet();
		final boolean usesState = game.requiresLocalState();
		
		final List<String> channelNames = new ArrayList<String>();
		
		// Number of channels required for piece types
		stateTensorNumChannels = stacking ? NUM_STACK_CHANNELS * numPieceTypes : numPieceTypes;
		
		if (!stacking)
		{
			for (int e = 1; e <= numPieceTypes; ++e)
			{
				channelNames.add("Piece Type " + e + " (" + components[e].name() + ")");
			}
		}
		else
		{
			for (int e = 1; e <= numPieceTypes; ++e)
			{
				for (int i = 0; i < NUM_STACK_CHANNELS / 2; ++i)
				{
					channelNames.add("Piece Type " + e + " (" + components[e].name() + ") at level " + i + " from stack bottom.");
				}
				
				for (int i = 0; i < NUM_STACK_CHANNELS / 2; ++i)
				{
					channelNames.add("Piece Type " + e + " (" + components[e].name() + ") at level " + i + " from stack top.");
				}
			}
		}
		
		if (stacking)
		{
			stateTensorNumChannels += 1;	// one more channel for size of stack
			channelNames.add("Stack sizes (non-binary channel!");
		}
		
		if (usesCount)
		{
			stateTensorNumChannels += 1;	// channel for count
			channelNames.add("Counts (non-binary channel!");
		}
		
		if (usesAmount)
		{
			stateTensorNumChannels += numPlayers;	// channel for amount
			
			for (int p = 1; p <= numPlayers; ++p)
			{
				channelNames.add("Amount for Player " + p);
			}
		}
		
		if (numPlayers > 1)
		{
			stateTensorNumChannels += numPlayers;	// channels for current mover
			
			for (int p = 1; p <= numPlayers; ++p)
			{
				channelNames.add("Is Player " + p + " the current mover?");
			}
		}
		
		if (usesState)
		{
			stateTensorNumChannels += NUM_LOCAL_STATE_CHANNELS;
			
			for (int i = 0; i < NUM_LOCAL_STATE_CHANNELS; ++i)
			{
				if (i + 1 == NUM_LOCAL_STATE_CHANNELS)
					channelNames.add("Local state >= " + i);
				else
					channelNames.add("Local state == " + i);
			}
		}
		
		stateTensorNumChannels += numContainers;	// for maps of whether positions exist in containers
		
		for (int c = 0; c < numContainers; ++c)
		{
			channelNames.add("Does position exist in container " + c + " (" + containers[c].name() + ")?");
		}
		
		stateTensorNumChannels += 4;	// channels for last move and move before last move (from and to)
		
		channelNames.add("Last move's from-position");
		channelNames.add("Last move's to-position");
		channelNames.add("Second-to-last move's from-position");
		channelNames.add("Second-to-last move's to-position");
		
		assert (channelNames.size() == stateTensorNumChannels);
		stateTensorChannelNames = channelNames.toArray(new String[stateTensorNumChannels]);
		
		MOVE_PASS_CHANNEL_IDX = computeMovePassChannelIdx();
		MOVE_SWAP_CHANNEL_IDX = MOVE_PASS_CHANNEL_IDX + 1;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return Channel index for Pass move in move-tensor-representation
	 */
	private int computeMovePassChannelIdx()
	{
		// legal values for diff x = {-clip, ..., -2, -1, 0, 1, 2, ..., +clip}
		final int numValsDiffX = 2 * moveTensorDistClip + 1;

		// legal values for diff y = {-clip, ..., -2, -1, 0, 1, 2, ..., +clip} (mult with diff x)
		final int numValsDiffY = numValsDiffX * (2 * moveTensorDistClip + 1);

		if (!game.isStacking())
		{
			return numValsDiffY;
		}
		else
		{
			// legal values for clipped levelMin = {0, 1, 2, ..., clip} (mult with all the above)
			final int numValsLevelMin = numValsDiffY * (MOVE_TENSOR_LEVEL_CLIP + 1);

			// legal values for clipped levelMax - levelMin = {0, 1, 2, ..., clip} (mult with all the above)
			final int numValsLevelMax = numValsLevelMin * (MOVE_TENSOR_LEVEL_CLIP + 1);

			// The max index using variables mentioned above is 1 less than the number of values
			// we computed, since we start at 0.
			// So, the number we just computed can be used as the next index (for Pass moves)
			return numValsLevelMax;
		}
	}

	//-------------------------------------------------------------------------
	
	/**
	 * Main method prints some relevant information for these wrappers
	 * @param args
	 */
	public static void main(final String[] args)
	{
		final String[] gameNames = FileHandling.listGames();
		
		for (final String name : gameNames)
		{
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/wip/"))
				continue;
			
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/wishlist/"))
				continue;
			
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/test/"))
				continue;
			
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/bad_playout/"))
				continue;
			
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/bad/"))
				continue;
			
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/plex/"))
				continue;
			
			if (name.replaceAll(Pattern.quote("\\"), "/").contains("/math/graph/"))
				continue;

			System.out.println("name = " + name);
			final LudiiGameWrapper game = new LudiiGameWrapper(name);
			
			if (!game.game.hasSubgames())
			{
				System.out.println("State tensor shape = " + Arrays.toString(game.stateTensorsShape()));
				System.out.println("Moves tensor shape = " + Arrays.toString(game.moveTensorsShape()));
			}
				
//			System.out.println("Num distinct actions for " + game.name() + " = " + game.numDistinctActions());
//			System.out.println(game.game.moveIntMapper());
//			System.out.println();
		}
	}

}
