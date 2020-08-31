package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.equipment.container.Container;
import gnu.trove.list.array.TIntArrayList;
import main.Constants;
import main.collections.FastArrayList;
import util.Context;
import util.Move;
import util.Trial;
import util.state.State;
import util.state.containerStackingState.BaseContainerStateStacking;
import util.state.containerState.ContainerState;
import util.state.owned.Owned;

/**
 * Wrapper around a Ludii context (trial + state), with various extra methods required for
 * other frameworks that like to wrap around Ludii (e.g. OpenSpiel, Polygames)
 * 
 * @author Dennis Soemers
 */
public final class LudiiStateWrapper
{
	
	//-------------------------------------------------------------------------
	
	/** Reference back to our wrapped Ludii game */
	protected final LudiiGameWrapper game;
	
	/** Our wrapped context */
	protected final Context context;
	
	/** Our wrapped trial */
	protected final Trial trial;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param game
	 */
	public LudiiStateWrapper(final LudiiGameWrapper game)
	{
		this.game = game;
		trial = new Trial(game.game);
		context = new Context(game.game, trial);
		game.game.start(context);
	}
	
	/**
	 * Copy constructor
	 * @param other
	 */
	public LudiiStateWrapper(final LudiiStateWrapper other)
	{
		this.game = other.game;
		this.context = new Context(other.context);
		this.trial = this.context.trial();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param actionID
	 * @param player
	 * @return A string (with fairly detailed information) on the move(s) represented
	 * by the given actionID in the current game state.
	 */
	public String actionToString(final int actionID, final int player)
	{
		final FastArrayList<Move> legalMoves;
		
		if (game.isSimultaneousMoveGame())
			legalMoves = AIUtils.extractMovesForMover(game.game.moves(context).moves(), player + 1);
		else
			legalMoves = game.game.moves(context).moves();
			
		final List<Move> moves = new ArrayList<Move>();
		for (final Move move : legalMoves)
		{
			if (game.moveToInt(move) == actionID)
				moves.add(move);
		}
		
		if (moves.isEmpty())
		{
			return "[Ludii found no move for ID: " + actionID + "!]";
		}
		else if (moves.size() == 1)
		{
			return moves.get(0).toTrialFormat(context);
		}
		else
		{
			final StringBuilder sb = new StringBuilder();
			
			sb.append("[Multiple Ludii moves for ID=" + actionID + ": ");
			sb.append(moves);
			sb.append("]");
			
			return sb.toString();
		}
	}
	
	/**
	 * Applies the nth legal move in current game state
	 * 
	 * @param n Legal move index. NOTE: index in Ludii's list of legal move,
	 * 	not a move converted into an int for e.g. OpenSpiel representation
	 */
	public void applyNthMove(final int n)
	{
		final FastArrayList<Move> legalMoves = game.game.moves(context).moves();
		final Move moveToApply = legalMoves.get(n);
		game.game.apply(context, moveToApply);
	}
	
	/**
	 * Applies a move represented by given int in the single-int-action-representation.
	 * Note that this method may have to randomly select a move among multiple legal
	 * moves if multiple different legal moves are represented by the same int.
	 * 
	 * @param action
	 * @param player
	 */
	public void applyIntAction(final int action, final int player)
	{
		final FastArrayList<Move> legalMoves;
		
		if (game.isSimultaneousMoveGame())
			legalMoves = AIUtils.extractMovesForMover(game.game.moves(context).moves(), player + 1);
		else
			legalMoves = game.game.moves(context).moves();
			
		final List<Move> moves = new ArrayList<Move>();
		for (final Move move : legalMoves)
		{
			if (game.moveToInt(move) == action)
				moves.add(move);
		}
		
		game.game.apply(context, moves.get(ThreadLocalRandom.current().nextInt(moves.size())));
	}
	
	@Override
	public LudiiStateWrapper clone()
	{
		return new LudiiStateWrapper(this);
	}
	
	/**
	 * @return Current player to move (not accurate in simultaneous-move games).
	 * Returns a 0-based index.
	 */
	public int currentPlayer()
	{
		return context.state().mover() - 1;
	}
	
	/**
	 * @return True if and only if current trial is over (terminal game state reached)
	 */
	public boolean isTerminal()
	{
		return trial.over();
	}
	
	/**
	 * Resets this game state back to an initial game state
	 */
	public void reset()
	{
		game.game.start(context);
	}
	
	/**
	 * @return Array of indices for legal moves. For a game state with N legal moves,
	 * this will always simply be [0, 1, 2, ..., N-1]
	 */
	public int[] legalMoveIndices()
	{
		final FastArrayList<Move> moves = game.game.moves(context).moves();
		final int[] indices = new int[moves.size()];
		
		for (int i = 0; i < indices.length; ++i)
		{
			indices[i] = i;
		}
		
		return indices;
	}
	
	/**
	 * @return Number of legal moves in current state
	 */
	public int numLegalMoves()
	{
		return Math.max(1, game.game.moves(context).moves().size());
	}
	
	/**
	 * @return Array of integers corresponding to moves that are legal in current
	 * game state.
	 */
	public int[] legalMoveInts()
	{
		final FastArrayList<Move> moves = game.game.moves(context).moves();
		final TIntArrayList moveInts = new TIntArrayList(moves.size());
		
		// TODO could speed up this method by implementing an auto-sorting
		// class that extends TIntArrayList
		for (final Move move : moves)
		{
			final int toAdd = game.moveToInt(move);
			if (!moveInts.contains(toAdd))
				moveInts.add(toAdd);
		}
		
		moveInts.sort();

		return moveInts.toArray();
	}
	
	/**
	 * @param player
	 * @return Array of integers corresponding to moves that are legal in current
	 * game state for the given player.
	 */
	public int[] legalMoveIntsPlayer(final int player)
	{
		final FastArrayList<Move> legalMoves;
		
		if (game.isSimultaneousMoveGame())
			legalMoves = AIUtils.extractMovesForMover(game.game.moves(context).moves(), player + 1);
		else
			legalMoves = game.game.moves(context).moves();
		
		final TIntArrayList moveInts = new TIntArrayList(legalMoves.size());
		
		// TODO could speed up this method by implementing an auto-sorting
		// class that extends TIntArrayList
		for (final Move move : legalMoves)
		{
			final int toAdd = game.moveToInt(move);
			if (!moveInts.contains(toAdd))
				moveInts.add(toAdd);
		}
		
		moveInts.sort();
		return moveInts.toArray();
	}
	
	/**
	 * @return Array with a length equal to the number of legal moves in current state.
	 * Every element is an int array of size 3, containing [channel_idx, x, y] for 
	 * that move. This is used for action representation in Polygames.
	 */
	public int[][] legalMovesTensors()
	{
		final FastArrayList<Move> moves = game.game.moves(context).moves();
		final int[][] movesTensors;
		
		if (moves.isEmpty())
		{
			movesTensors = new int[1][];
			movesTensors[0] = new int[] {game.MOVE_PASS_CHANNEL_IDX, 0, 0};
		}
		else
		{
			movesTensors = new int[moves.size()][];

			for (int i = 0; i < moves.size(); ++i)
			{
				movesTensors[i] = game.moveToTensor(moves.get(i));
			}
		}

		return movesTensors;
	}
	
	/**
	 * NOTE: the returns are for the original player indices at the start of the episode;
	 * this can be different from player-to-colour assignments in the current game state
	 * if the Swap rule was used.
	 * 
	 * @return Array of utilities in [-1, 1] for all players. Player
	 * index assumed to be 0-based!
	 */
	public double[] returns()
	{
		if (!isTerminal())
			return new double[game.numPlayers()];
		
		final double[] returns = AIUtils.agentUtilities(context);
		return Arrays.copyOfRange(returns, 1, returns.length);
	}
	
	/**
	 * NOTE: the returns are for the original player indices at the start of the episode;
	 * this can be different from player-to-colour assignments in the current game state
	 * if the Swap rule was used.
	 * 
	 * @param player
	 * @return The returns for given player (index assumed to be 0-based!). Returns
	 * are always in [-1, 1]
	 */
	public double returns(final int player)
	{
		if (!isTerminal())
			return 0.0;
		
		final double[] returns = AIUtils.agentUtilities(context);
		return returns[player + 1];
	}
	
	/**
	 * Undo the last move.
	 * 
	 * NOTE: implementation is NOT efficient. It restarts to the initial game
	 * state, and re-applies all moves except for the last one
	 */
	public void undoLastMove()
	{
		final List<Move> moves = new ArrayList<Move>(context.trial().moves());
		reset();
		
		for (int i = context.trial().numInitialPlacementMoves(); i < moves.size() - 1; ++i)
		{
			game.game.apply(context, moves.get(i));
		}
	}
	
	/**
	 * @return A single tensor representation of the current game state
	 */
	public float[][][] toTensor()
	{
		// TODO we also want to support edges and faces for some games
		
		final int numPlayers = game.game.players().count();
		final int numPieceTypes = game.game.equipment().components().length - 1;
		final boolean stacking = game.game.isStacking();
		final boolean usesCount = game.game.requiresCount();
		final boolean usesAmount = game.game.requiresBet();
		final boolean usesState = game.game.requiresLocalState();
		
		final int[] xCoords = game.tensorCoordsX();
		final int[] yCoords = game.tensorCoordsY();
		final int tensorDimX = game.tensorDimX();
		final int tensorDimY = game.tensorDimY();
		
		final int numChannels = game.stateTensorNumChannels;
		
		final float[][][] tensor = new float[numChannels][tensorDimX][tensorDimY];
		
		int currentChannel = 0;
		
		if (!stacking)
		{
			// Just one channel per piece type
			for (int e = 1; e <= numPieceTypes; ++e)
			{
				final Owned owned = context.state().owned();
				for (int p = 1; p <= numPlayers + 1; ++p)
				{
					final TIntArrayList sites = owned.sites(p, e);
					
					for (int i = 0; i < sites.size(); ++i)
					{
						final int site = sites.getQuick(i);
						tensor[currentChannel][xCoords[site]][yCoords[site]] = 1.f;
					}
				}
				
				++currentChannel;
			}
		}
		else
		{
			// We have to deal with stacking
			for (int c = 0; c < game.game.equipment().containers().length; ++c)
			{
				final Container cont = game.game.equipment().containers()[c];
				final BaseContainerStateStacking cs = (BaseContainerStateStacking) context.state().containerStates()[c];
				final int contStartSite = game.game.equipment().sitesFrom()[c];
				
				for (int site = 0; site < cont.numSites(); ++site)
				{
					final int stackSize = cs.sizeStackCell(contStartSite + site);
					
					if (stackSize > 0)
					{
						// Store in channels for bottom 5 elements of stack
						for (int i = 0; i < LudiiGameWrapper.NUM_STACK_CHANNELS / 2; ++i)
						{
							if (i >= stackSize)
								break;
							
							final int what = cs.whatCell(contStartSite + site, i);
							final int channel = currentChannel + ((what - 1) * LudiiGameWrapper.NUM_STACK_CHANNELS + i);
							tensor[channel][xCoords[contStartSite + site]][yCoords[contStartSite + site]] = 1.f;
						}
						
						// And same for top 5 elements of stack
						for (int i = 0; i < LudiiGameWrapper.NUM_STACK_CHANNELS / 2; ++i)
						{
							if (i >= stackSize)
								break;
							
							final int what = cs.whatCell(contStartSite + site, stackSize - 1 - i);
							final int channel = 
									currentChannel + ((what - 1) * LudiiGameWrapper.NUM_STACK_CHANNELS + 
											(LudiiGameWrapper.NUM_STACK_CHANNELS / 2) + i);
							tensor[channel][xCoords[contStartSite + site]][yCoords[contStartSite + site]] = 1.f;
						}
						
						// Finally a non-binary channel storing the height of stack
						final int channel = currentChannel + LudiiGameWrapper.NUM_STACK_CHANNELS * numPieceTypes;
						tensor[channel][xCoords[contStartSite + site]][yCoords[contStartSite + site]] = stackSize;
					}
				}
			}
			
			// + 1 for stack size channel
			currentChannel += LudiiGameWrapper.NUM_STACK_CHANNELS * numPieceTypes + 1;
		}
		
		if (usesCount)
		{
			// non-binary channel for counts
			for (int c = 0; c < game.game.equipment().containers().length; ++c)
			{
				final Container cont = game.game.equipment().containers()[c];
				final ContainerState cs = context.state().containerStates()[c];
				final int contStartSite = game.game.equipment().sitesFrom()[c];
				
				for (int site = 0; site < cont.numSites(); ++site)
				{
					tensor[currentChannel][xCoords[contStartSite + site]][yCoords[contStartSite + site]] = 
							cs.countCell(contStartSite + site);
				}
			}
			
			++currentChannel;
		}
		
		if (usesAmount)
		{
			// One channel per player for their amount
			for (int p = 1; p <= numPlayers; ++p)
			{
				final int amount = context.state().amount(p);
				
				for (int x = 0; x < tensor[currentChannel].length; ++x)
				{
					Arrays.fill(tensor[currentChannel][x], amount);
				}
				
				++currentChannel;
			}
		}
		
		if (numPlayers > 1)
		{
			// One binary channel per player for whether or not they're current mover
			// (one will be all-1s, all the others will be all-0)
			final int mover = context.state().mover();
			for (int x = 0; x < tensor[currentChannel + mover - 1].length; ++x)
			{
				Arrays.fill(tensor[currentChannel + mover - 1][x], 1.f);
			}
			currentChannel += numPlayers;
		}
		
		if (usesState)
		{
			// Channels for local state: 0, 1, 2, 3, 4, or >= 5
			for (int c = 0; c < game.game.equipment().containers().length; ++c)
			{
				final Container cont = game.game.equipment().containers()[c];
				final int contStartSite = game.game.equipment().sitesFrom()[c];
				final ContainerState cs = context.state().containerStates()[c];
				
				for (int site = 0; site < cont.numSites(); ++site)
				{
					final int state = Math.min(cs.stateCell(contStartSite + site), LudiiGameWrapper.NUM_LOCAL_STATE_CHANNELS - 1);
					tensor[currentChannel + state][xCoords[contStartSite + site]][yCoords[contStartSite + site]] = 1.f;
				}
			}
			
			currentChannel += LudiiGameWrapper.NUM_LOCAL_STATE_CHANNELS;
		}
		
		// Channels for whether or not positions exist in containers
		for (int c = 0; c < game.game.equipment().containers().length; ++c)
		{
			final Container cont = game.game.equipment().containers()[c];
			final int contStartSite = game.game.equipment().sitesFrom()[c];
			
			for (int site = 0; site < cont.numSites(); ++site)
			{
				tensor[currentChannel][xCoords[contStartSite + site]][yCoords[contStartSite + site]] = 1.f;
			}
			
			++currentChannel;
		}
		
		// Channels marking from and to of last Move
		if (trial.moves().size() - trial.numInitialPlacementMoves() > 0)
		{
			final Move lastMove = trial.moves().get(trial.moves().size() - 1);
			final int from = lastMove.fromNonDecision();
			
			if (from != Constants.OFF)
				tensor[currentChannel][xCoords[from]][yCoords[from]] = 1.f;
			
			++currentChannel;
			final int to = lastMove.toNonDecision();
			
			if (to != Constants.OFF)
				tensor[currentChannel][xCoords[to]][yCoords[to]] = 1.f;
			
			++currentChannel;
		}
		else
		{
			currentChannel += 2;
		}
		
		// And the same for move before last move
		if (trial.moves().size() - trial.numInitialPlacementMoves() > 1)
		{
			final Move lastLastMove = trial.moves().get(trial.moves().size() - 2);
			final int from = lastLastMove.fromNonDecision();
			
			if (from != Constants.OFF)
				tensor[currentChannel][xCoords[from]][yCoords[from]] = 1.f;
			
			++currentChannel;
			final int to = lastLastMove.toNonDecision();
			
			if (to != Constants.OFF)
				tensor[currentChannel][xCoords[to]][yCoords[to]] = 1.f;
			
			++currentChannel;
		}
		else
		{
			currentChannel += 2;
		}
		
		// Assert that we correctly ran through all channels
		assert (currentChannel == numChannels);
		
		return tensor;
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder();
		final State state = context.state();
		
		sb.append("BEGIN LUDII STATE\n");
		
		sb.append("Mover = " + state.mover() + "\n");
		sb.append("Next = " + state.next() + "\n");
		sb.append("Previous = " + state.prev() + "\n");
		
		for (int p = 1; p <= state.numPlayers(); ++p)
		{
			sb.append("Player " + p + " active = " + context.active(p) + "\n");
		}
		
		sb.append("State hash = " + state.stateHash() + "\n");
		
		if (game.game.requiresScore())
		{
			for (int p = 1; p <= state.numPlayers(); ++p)
			{
				sb.append("Player " + p + " score = " + context.score(p) + "\n");
			}
		}
		
		for (int p = 1; p <= state.numPlayers(); ++p)
		{
			sb.append("Player " + p + " ranking = " + context.trial().ranking()[p] + "\n");
		}
		
		for (int i = 0; i < state.containerStates().length; ++i)
		{
			final ContainerState cs = state.containerStates()[i];
			sb.append("BEGIN CONTAINER STATE " + i + "\n");
			
			sb.append(cs.toString() + "\n");
			
			sb.append("END CONTAINER STATE " + i + "\n");
		}
		
		sb.append("END LUDII GAME STATE\n");
		
		return sb.toString();
	}

}
