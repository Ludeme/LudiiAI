package features.visualisation;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import features.Walk;
import features.features.Feature;
import game.Game;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

/**
 * Utility class to create .eps images from features.
 * 
 * @author Dennis Soemers
 */
public class FeatureToEPS
{
	
	//-------------------------------------------------------------------------
	
//	private static final int J_HEX_RADIUS = 17;
//	private static final int J_HEX_WIDTH = (int) Math.ceil(Math.sqrt(3.0) * J_HEX_RADIUS);
//	private static final int J_HEX_HEIGHT = 2 * J_HEX_RADIUS;
//	
//	private static final int J_SQUARE_SIDE = 34;
//	
//	/** Radius of Hex tiles (circumradius = radius of outer circle) */
//	private static final String HEX_RADIUS = "" + J_HEX_RADIUS;
//	
//	/** Width of a Hex tile (with pointy parts going up/down) */
//	private static final String HEX_WIDTH = "3.0 sqrt " + HEX_RADIUS + " mul";
//	
//	/** Height of a Hex tile (with pointy parts going up/down) */
//	private static final String HEX_HEIGHT = "2 " + HEX_RADIUS + " mul";
//	
//	private static final String SQUARE_SIDE = "" + J_SQUARE_SIDE;
	
	//-------------------------------------------------------------------------
	
	/** */
	private FeatureToEPS()
	{
		// do not use
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Creates a .eps file visualising the given feature
	 * @param feature
	 * @param player
	 * @param game
	 * @param outputFile File to write output to (EPS program)
	 */
	public static void createEPS
	(
		final Feature feature, 
		final int player, 
		final Game game,
		final File outputFile
	)
	{
		// gather some info on game
//		final int numOrths = 
//				game.board().tiling().getSupportedOrthogonalDirectionTypes().length;
//		final Color friendColour = SettingsColour.getCustomPlayerColours()[player];
//		final Color enemyColour = 
//				player == 2 ? SettingsColour.getCustomPlayerColours()[1] : SettingsColour.getCustomPlayerColours()[2];
//				
//		final double friendR = friendColour.getRed() / 255.0;
//		final double friendG = friendColour.getGreen() / 255.0;
//		final double friendB = friendColour.getBlue() / 255.0;
//		final double enemyR = enemyColour.getRed() / 255.0;
//		final double enemyG = enemyColour.getGreen() / 255.0;
//		final double enemyB = enemyColour.getBlue() / 255.0;
//				
//		boolean squareTiling = (numOrths == 4);
//		boolean hexTiling = (numOrths == 6);
//		boolean playOnIntersections = (game.board() instanceof GoBoard);
//		
//		final int jCellHeight;
//		final int jCellWidth;
//		final String cellHeight;
//		final String cellWidth;
//		
//		if (squareTiling)
//		{
//			jCellHeight = J_SQUARE_SIDE;
//			jCellWidth = J_SQUARE_SIDE;
//			cellHeight = SQUARE_SIDE;
//			cellWidth = SQUARE_SIDE;
//		}
//		else if (hexTiling)
//		{
//			jCellHeight = J_HEX_HEIGHT;
//			jCellWidth = J_HEX_WIDTH;
//			cellHeight = HEX_HEIGHT;
//			cellWidth = HEX_WIDTH;
//		}
//		else
//		{
//			System.err.println("Unsupported tiling!");
//			return;
//		}
//				
//		// we'll update these as we draw things
//		int bboxLeftX = 0;
//		int bboxLowerY = 0;
//		int bboxRightX = 0;
//		int bboxTopY = 0;
//		
//		if (feature instanceof AbsoluteFeature)
//		{
//			// TODO ?
//			System.err.println("Cannot (yet?) visualise absolute feature");
//			return;
//		}
//
//		final RelativeFeature rel = (RelativeFeature) feature;
//		final Walk from = rel.fromPosition();
//		final Walk to = rel.toPosition();
//		final List<FeatureElement> elements = rel.pattern().featureElements();
//
//		// we may have multiple images within a single file when transferring
//		// features between games
//		int currImageColumn = 0;
//		int currImageRow = 0;
//
//		// keep track of sizes of images, used to make columns and rows
//		// big enough
//		int maxImageWidth = 0;
//		int maxImageHeight = 0;
//
//		// compute all the offsets for all the walks
//		final List<int[]> fromOffsets = computeOffsets(from, numOrths);
//		final List<int[]> toOffsets = computeOffsets(to, numOrths);
//		final List<List<int[]>> elementOffsets = new ArrayList<List<int[]>>();
//		final List<RelativeFeatureElement> relEls = new ArrayList<RelativeFeatureElement>();
//
//		for (final FeatureElement element : elements)
//		{
//			if (element instanceof RelativeFeatureElement)
//			{
//				final RelativeFeatureElement relEl = (RelativeFeatureElement) element;
//				elementOffsets.add(computeOffsets(relEl.walk(), numOrths));
//				relEls.add(relEl);
//			}
//		}
//
//		final List<List<int[]>> offsetCombos = new ArrayList<List<int[]>>();
//		enumerateOffsetCombos(elementOffsets, offsetCombos, new ArrayList<int[]>());
//
//		// draw image for every legal permutation
//		// we store programs per image such that we can later index
//		// them by "row" and "column"
//		final List<List<List<String>>> imagePrograms = new ArrayList<List<List<String>>>();
//
//		// for every image, also the lower bounds for cells
//		final List<List<int[]>> lowerCellBounds = new ArrayList<List<int[]>>();
//
//		for (final int[] fromOffset : fromOffsets)
//		{
//			for (final int[] toOffset : toOffsets)
//			{
//				for (final List<int[]> offsetCombo : offsetCombos)
//				{
//					// start writing code to draw this particular image
//					int minCellRow = 0;
//					int maxCellRow = 0;
//					int minCellCol = 0;
//					int maxCellCol = 0;
//
//					// this will contain the program for this image
//					final List<String> imageProgram = new ArrayList<String>();
//
//					if (currImageRow == imagePrograms.size())
//					{
//						imagePrograms.add(new ArrayList<List<String>>());
//						lowerCellBounds.add(new ArrayList<int[]>());
//					}
//					imagePrograms.get(currImageRow).add(imageProgram);
//					
//					final Set<Cell> cellsToDraw = new HashSet<Cell>();
//
//					if (fromOffset.length == 0)
//					{
//						// no from-position, just draw a plus at to-position
//						imageProgram.add(toOffset[0] + " " + toOffset[1] + " IncentiviseTo");
//
//						// update cell bounds
//						minCellRow = Math.min(minCellRow, toOffset[1]);
//						maxCellRow = Math.max(maxCellRow, toOffset[1]);
//						minCellCol = Math.min(minCellCol, toOffset[0]);
//						maxCellCol = Math.max(maxCellCol, toOffset[0]);
//						
//						// remember that we should draw this cell
//						cellsToDraw.add(new Cell(toOffset));
//					}
//					else if (toOffset.length == 0)
//					{
//						// TODO handle case where we only have a from-position
//					}
//					else
//					{
//						// TODO handle case where we have from and to positions
//					}
//
//					for (int elIdx = 0; elIdx < relEls.size(); ++elIdx)
//					{
//						final RelativeFeatureElement el = relEls.get(elIdx);
//						final ElementType type = el.type();
//						final int[] offset = offsetCombo.get(elIdx);
//						
//						if (el.not() && type != ElementType.Off)
//						{
//							imageProgram.add(0, offset[0] + " " + offset[1] + " Not");
//						}
//
//						if (type == ElementType.Empty)
//						{
//							// draw a little white circle to indicate empty position
//							imageProgram.add(0, offset[0] + " " + offset[1] + " Empty");
//						}
//						else if (type == ElementType.Friend)
//						{
//							// draw a friend
//							imageProgram.add(0, offset[0] + " " + offset[1] + " Friend");
//						}
//						else if (type == ElementType.Enemy)
//						{
//							// draw an enemy
//							imageProgram.add(0, offset[0] + " " + offset[1] + " Enemy");
//						}
//						else if (type == ElementType.Off)
//						{
//							if (!el.not())
//							{
//								// mark cell as off-board
//								imageProgram.add(0, offset[0] + " " + offset[1] + " OffBoard");
//							}
//						}
//						else if (type == ElementType.Any)	
//						{
//							// TODO
//							System.err.println("TODO: draw any element");
//						}
//						else if (type == ElementType.P1)
//						{
//							// TODO
//							System.err.println("TODO: draw P1 element");
//						}
//						else if (type == ElementType.P2)
//						{
//							// TODO
//							System.err.println("TODO: draw P2 element");
//						}
//						else if (type == ElementType.Item)	
//						{
//							// TODO
//							System.err.println("TODO: draw Item element");
//						}
//						else if (type == ElementType.IsPos)
//						{
//							// TODO
//							System.err.println("TODO: draw IsPos element");
//						}
//						
//						// update cell bounds
//						minCellRow = Math.min(minCellRow, offset[1]);
//						maxCellRow = Math.max(maxCellRow, offset[1]);
//						minCellCol = Math.min(minCellCol, offset[0]);
//						maxCellCol = Math.max(maxCellCol, offset[0]);
//						
//						// remember that we should draw this cell
//						cellsToDraw.add(new Cell(offset));
//					}
//
//					// update image bounds
//					maxImageWidth = Math.max(maxImageWidth, maxCellCol - minCellCol);
//					maxImageHeight = Math.max(maxImageHeight, maxCellRow - minCellRow);
//					lowerCellBounds.get(currImageRow).add(new int[]{minCellCol, minCellRow});
//					
//					// draw our cells
//					for (int dx = minCellCol; dx <= maxCellCol; ++dx)
//					{
//						for (int dy = minCellRow; dy <= maxCellRow; ++dy)
//						{
//							if (cellsToDraw.contains(new Cell(new int[]{dx, dy})))
//							{
//								imageProgram.add(0, dx + " " + dy + " Cell");
//							}
//						}
//					}
//					
//					// draw dashed versions of all the unused cells
//					for (int dx = minCellCol; dx <= maxCellCol; ++dx)
//					{
//						for (int dy = minCellRow; dy <= maxCellRow; ++dy)
//						{
//							if (!cellsToDraw.contains(new Cell(new int[]{dx, dy})))
//							{
//								imageProgram.add(0, dx + " " + dy + " CellDashed");
//							}
//						}
//					}
//
//					// update row / column indices for potential next image
//					++currImageColumn;
//
//					if (currImageColumn >= 5)
//					{
//						currImageColumn = 0;
//						++currImageRow;
//					}
//				}
//			}
//		}
//		
//		// compute bounding box
//		bboxRightX = (int) Math.ceil((maxImageWidth + 2) * jCellWidth);
//		bboxTopY = (int) Math.ceil((maxImageHeight + 2) * jCellHeight);
//		
//		// we'll collect and generate the feature-specific program lines in here
//		final List<String> program = new ArrayList<String>();
//		
//		program.add("% translate a bit to ensure we have some whitespace on left and bottom");
//		program.add(cellWidth + " " + cellHeight + " translate");
//		program.add("");
//		
//		// loop through all the rows of image programs
//		for (int imageRow = imagePrograms.size() - 1; imageRow >= 0; --imageRow)
//		{
//			final List<List<String>> rowPrograms = imagePrograms.get(imageRow);
//			
//			// loop through all the columns
//			for (int imageCol = 0; imageCol < rowPrograms.size(); ++imageCol)
//			{
//				final List<String> rowColProgram = rowPrograms.get(imageCol);
//				
//				// start writing program for this row + col
//				program.add("%------------- Row " + imageRow + ", Col " + imageCol + " -------------");
//				program.add("gsave");
//				program.add("% translate to origin of this particular image");
//				program.add(
//						(-lowerCellBounds.get(imageRow).get(imageCol)[0]) + 
//						" " + cellWidth + " mul " + 
//						(-lowerCellBounds.get(imageRow).get(imageCol)[1]) + 
//						" " + cellHeight + " mul translate");
//				program.add("");
//				
//				for (final String line : rowColProgram)
//				{
//					program.add(line);
//				}
//				
//				program.add("");
//				program.add("grestore");
//				
//				program.add("");
//				program.add("% translate back to lower-left of this image");
//				program.add(
//						(lowerCellBounds.get(imageRow).get(imageCol)[0]) + 
//						" " + cellWidth + " mul " + 
//						(lowerCellBounds.get(imageRow).get(imageCol)[1]) + 
//						" " + cellHeight + " mul translate");
//				program.add("% now translate to lower left of next column");
//				program.add((maxImageWidth + 1) + " " + cellWidth + " mul 0 translate");
//				program.add("");
//			}
//			
//			// reached end of row 
//			program.add("% translate all the way back to left of first column");
//			program.add((-rowPrograms.size() * (maxImageWidth + 1)) + " " + cellWidth + " mul 0 translate");
//			program.add("% translate up to next row");
//			program.add("0 " + (maxImageHeight + 1) + " " + cellHeight + " mul translate");
//			program.add("");
//		}
//		
//		// start writing
//		try (final PrintWriter w = new PrintWriter(outputFile.getAbsolutePath(), "ASCII"))
//		{	
//			// write some general stuff at start of file
//			w.println("%!PS");
//			w.println("%%LanguageLevel: 3");
//			w.println("%%Creator: Ludii");
//			w.println("%%CreationDate: " + LocalDate.now().toString());
//			w.println("%%BoundingBox: " + bboxLeftX + " " + bboxLowerY + " " + bboxRightX + " " + bboxTopY);
//			w.println("%%EndComments");
//			w.println("%%BeginProlog");
//			w.println("%%EndProlog");
//			w.println("");
//			// write page size
//			w.println("<< /PageSize [" + (bboxRightX - bboxLeftX) + " " + (bboxTopY - bboxLowerY) + "] >> setpagedevice");
//			w.println("");
//			
//			// write some constants
//			w.println("%---------------- Constants -------------------");
//			w.println("");
//			w.println("/Root3 3.0 sqrt def");
//			w.println("");
//			
//			// write useful variables for our pieces / cells / shapes / etc.
//			w.println("%--------------- Variables ------------------");
//			w.println("");
//			w.println("/HexRadius " + HEX_RADIUS + " def");
//			w.println("/HexDiameter { HexRadius 2 mul } def");
//			w.println("");
//			w.println("/SquareSide " + SQUARE_SIDE + " def");
//			w.println("");
//			w.println("/CircleRadius { 17 .75 mul }  def");
//			w.println("/CircleLineWidth 2 def");
//			w.println("");
//			w.println("/EmptyRadius { 17 .4 mul } def");
//			w.println("/EmptyLineWidth 0.75 def");
//			w.println("");
//			w.println("");
//			
//			// write useful functions
//			w.println("% ----------- Functions -------------");
//			w.println("");
//			w.println("/inch {72 mul} def");
//			w.println("/cm {182.88 mul} def");
//			w.println("");
//			
//			if (squareTiling)
//			{
//				// functions for square tilings
//				w.println("/X");
//				w.println("{   % call: i j X");
//				w.println("    2 dict begin ");
//				w.println("    /j exch def");
//				w.println("    /i exch def");
//				w.println("    i SquareSide mul ");
//				w.println("    end");
//				w.println("} def");
//				w.println("");
//				
//				w.println("/Y");
//				w.println("{   % call: i j Y");
//				w.println("    2 dict begin ");
//				w.println("    /j exch def");
//				w.println("    /i exch def");
//				w.println("    j SquareSide mul ");
//				w.println("    end");
//				w.println("} def");
//				w.println("");
//				
//				w.println("/XY");
//				w.println("{   % call: i j XY");
//				w.println("    2 dict begin ");
//				w.println("    /j exch def");
//				w.println("    /i exch def");
//				w.println("    i j X i j Y");
//				w.println("    end");
//				w.println("} def");
//				
//				if (playOnIntersections)
//				{
//					w.println("");
//					w.println("/Cell");
//					w.println("{	% call: i j Cell");
//					w.println("	2 dict begin");
//					w.println("    /j exch def");
//					w.println("    /i exch def");
//					w.println("    ");
//					w.println("    gsave");
//					w.println("    ");
//					w.println("	% fill squares");
//					// TODO
//					w.println("	");
//					w.println("	% draw lines");
//					w.println("	0 setgray");
//					w.println("	.1 setlinewidth");
//					w.println("	");
//					w.println("	newpath i j XY moveto SquareSide 0 rlineto stroke");
//					w.println("	newpath i j XY moveto SquareSide neg 0 rlineto stroke");
//					w.println("	newpath i j XY moveto 0 SquareSide rlineto stroke");
//					w.println("	newpath i j XY moveto 0 SquareSide neg rlineto stroke");
//					w.println("	");
//					w.println("	grestore");
//					w.println("    end");
//					w.println("} def");
//					w.println("");
//					
//					w.println("/CellDashed");
//					w.println("{	% call: i j CellDashed");
//					w.println("	2 dict begin");
//					w.println("    /j exch def");
//					w.println("    /i exch def");
//					w.println("    ");
//					w.println("    gsave");
//					w.println("    ");
//					w.println("	% fill squares");
//					// TODO
//					w.println("	");
//					w.println("	% draw lines");
//					w.println("	0 setgray");
//					w.println("	.1 setlinewidth");
//					w.println("	[4 4] 0 setdash");
//					w.println("	");
//					w.println("	newpath i j XY moveto SquareSide 0 rlineto stroke");
//					w.println("	newpath i j XY moveto SquareSide neg 0 rlineto stroke");
//					w.println("	newpath i j XY moveto 0 SquareSide rlineto stroke");
//					w.println("	newpath i j XY moveto 0 SquareSide neg rlineto stroke");
//					w.println("	");
//					w.println("	[] 0 setdash");
//					w.println("	grestore");
//					w.println("    end");
//					w.println("} def");
//					w.println("");
//					
//					w.println("/OffBoard");
//					w.println("{	% call: i j OffBoard");
//					w.println("	2 dict begin");
//					w.println("    /j exch def");
//					w.println("    /i exch def");
//					w.println("    ");
//					w.println("    gsave");
//					w.println("    ");
//					w.println("	% fill squares");
//					w.println("	0.75 setgray");
//					w.println("	newpath i j XY moveto");
//					w.println("	SquareSide 1 sub 0 rmoveto");
//					w.println("	0 SquareSide 1 sub rlineto");
//					w.println("	2 SquareSide 1 sub mul neg 0 rlineto");
//					w.println("	0 2 SquareSide 1 sub mul neg rlineto");
//					w.println("	2 SquareSide 1 sub mul 0 rlineto");
//					w.println("	closepath fill");
//					w.println("	");
//					w.println("	% draw lines");
//					w.println("	0 setgray");
//					w.println("	.1 setlinewidth");
//					w.println("	[4 4] 0 setdash");
//					w.println("	");
//					w.println("	newpath i j XY moveto SquareSide 0 rlineto stroke");
//					w.println("	newpath i j XY moveto SquareSide neg 0 rlineto stroke");
//					w.println("	newpath i j XY moveto 0 SquareSide rlineto stroke");
//					w.println("	newpath i j XY moveto 0 SquareSide neg rlineto stroke");
//					w.println("	");
//					w.println("	[] 0 setdash");
//					w.println("	grestore");
//					w.println("	end");
//					w.println("} def");
//				}
//				else
//				{
//					// TODO cells for square tilings where we play inside the cells
//				}
//			}
//			else if (hexTiling)
//			{
//				// functions for hex tilings
//				w.println("/X");
//				w.println("{   % call: i j X");
//				w.println("    2 dict begin ");
//				w.println("    /j exch def");
//				w.println("    /i exch def");
//				w.println("    i j -.5 mul add HexRadius mul Root3 mul ");
//				w.println("    end");
//				w.println("} def");
//				w.println("");
//				
//				w.println("/Y");
//				w.println("{   % call: i j Y");
//				w.println("    2 dict begin ");
//				w.println("    /j exch def");
//				w.println("    /i exch def");
//				w.println("    j HexRadius mul 3 mul 2 div ");
//				w.println("    end");
//				w.println("} def");
//				w.println("");
//				
//				w.println("/XY");
//				w.println("{   % call: i j XY");
//				w.println("    2 dict begin ");
//				w.println("    /j exch def");
//				w.println("    /i exch def");
//				w.println("    i j X i j Y");
//				w.println("    end");
//				w.println("} def");
//			}
//			
//			w.println("");
//			w.println("/Friend");
//			w.println("{   % call: i j Friend");
//			w.println("    2 dict begin ");
//			w.println("    /j exch def");
//			w.println("    /i exch def");
//			w.println("");
//			w.println("    CircleLineWidth setlinewidth");
//			w.println("");
//			w.println("    " + friendR + " " + friendG + " " + friendB + " setrgbcolor");
//			w.println("    newpath i j XY CircleRadius 0 360 arc fill");
//			w.println("");
//			w.println("    0 setgray");
//			w.println("    newpath i j XY CircleRadius 0 360 arc stroke");
//			w.println("    end");
//			w.println("} def");
//			w.println("");
//			
//			w.println("/Enemy");
//			w.println("{   % call: i j Enemy");
//			w.println("    2 dict begin ");
//			w.println("    /j exch def");
//			w.println("    /i exch def");
//			w.println("");
//			w.println("    CircleLineWidth setlinewidth");
//			w.println("");
//			w.println("    " + enemyR + " " + enemyG + " " + enemyB + " setrgbcolor");
//			w.println("    newpath i j XY CircleRadius 0 360 arc fill");
//			w.println("");
//			w.println("    0 setgray");
//			w.println("    newpath i j XY CircleRadius 0 360 arc stroke");
//			w.println("    end");
//			w.println("} def");
//			w.println("");
//			
//			w.println("/Empty");
//			w.println("{   % call: i j Empty");
//			w.println("    2 dict begin ");
//			w.println("    /j exch def");
//			w.println("    /i exch def");
//			w.println("");
//			w.println("    EmptyLineWidth setlinewidth");
//			w.println("	[2 2] 0 setdash");
//			w.println("");
//			w.println("    1 setgray");
//			w.println("    newpath i j XY EmptyRadius 0 360 arc fill");
//			w.println("");
//			w.println("    0 setgray");
//			w.println("    newpath i j XY EmptyRadius 0 360 arc stroke");
//			w.println("	[] 0 setdash");
//			w.println("    end");
//			w.println("} def");
//			w.println("");
//			
//			w.println("/IncentiviseTo");
//			w.println("{   % call: i j IncentiviseTo");
//			w.println("    2 dict begin ");
//			w.println("    /j exch def");
//			w.println("    /i exch def");
//			w.println("");
//			w.println("	gsave");
//			w.println("    CircleLineWidth setlinewidth");
//			w.println("");
//			w.println("	0 0 1 setrgbcolor");
//			w.println("");
//			w.println("    newpath i j XY EmptyRadius 0 360 arc clip");
//			w.println("    newpath i j XY moveto SquareSide 0 rmoveto SquareSide -2 mul 0 rlineto stroke");
//			w.println("    newpath i j XY moveto 0 SquareSide rmoveto 0 SquareSide -2 mul rlineto stroke");
//			w.println("	grestore");
//			w.println("    end");
//			w.println("} def");
//			w.println("");
//			
//			w.println("/Not");
//			w.println("{   % call: i j Not");
//			w.println("    2 dict begin ");
//			w.println("    /j exch def");
//			w.println("    /i exch def");
//			w.println("");
//			w.println("	gsave");
//			w.println("    CircleLineWidth setlinewidth");
//			w.println("");
//			w.println("	1 0 0 setrgbcolor");
//			w.println("");
//			w.println("    newpath i j XY EmptyRadius 0 360 arc clip");
//			w.println("    newpath i j XY moveto SquareSide -2 div SquareSide 2 div rmoveto SquareSide SquareSide neg rlineto stroke");
//			w.println("    newpath i j XY moveto SquareSide -2 div SquareSide -2 div rmoveto SquareSide SquareSide rlineto stroke");
//			w.println("	grestore");
//			w.println("    end");
//			w.println("} def");
//			w.println("");
//			
//			w.println("/textheight");
//			w.println("{ 	% based on: https://stackoverflow.com/a/7122326/6735980");
//			w.println("    gsave                                  % save graphic context");
//			w.println("    {");
//			w.println("        100 100 moveto                     % move to some point");
//			w.println("        (HÃ�pg) true charpath pathbbox      % gets text path bounding box (LLx LLy URx URy)");
//			w.println("        exch pop 3 -1 roll pop             % keeps LLy and URy");
//			w.println("        exch sub                           % URy - LLy");
//			w.println("    }");
//			w.println("    stopped                                % did the last block fail?");
//			w.println("    {");
//			w.println("        pop pop                            % get rid of \"stopped\" junk");
//			w.println("        currentfont /FontMatrix get 3 get  % gets alternative text height");
//			w.println("    }");
//			w.println("    if");
//			w.println("    grestore                               % restore graphic context");
//			w.println("} bind def");
//			w.println("");
//			
//			w.println("/StringAroundPoint");
//			w.println("{	% call: newpath i j XY moveto (string) StringAroundPoint");
//			w.println("	dup stringwidth pop		% get width of string");
//			w.println("	-2 div					% negate, and divide by 2");
//			w.println("	textheight -2.9 div		% dont know why, but div by 3 seems to work better than 2");
//			w.println("	rmoveto					% move to left and down by half width and height");
//			w.println("	show					% show the string");
//			w.println("} def");
//			w.println("");
//			
//			// write the actual feature-specific program
//			w.println("%-------------- Program --------------");
//			w.println("");
//			w.println("/Times-Bold findfont 24 scalefont setfont");
//			w.println("");
//			for (final String line : program)
//			{
//				w.println(line);
//			}
//			w.println("");
//			
//			// write final parts of file
//			w.println("");
//			w.println("%------------------------------------------");
//			w.println("");
//			w.println("showpage");
//			w.println("");
//			w.println("%%Trailer");
//			w.println("%%EOF");
//		}
//		catch (final FileNotFoundException | UnsupportedEncodingException e)
//		{
//			e.printStackTrace();
//		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param walk
	 * @param numOrths
	 * @return List of possible offsets. 
	 * Every offset is an array with x and y offsets, measured in "cell-units", 
	 * corresponding to given walk. If the given Walk is null, we'll return
	 * a list containing a single empty array
	 */
	public static List<int[]> computeOffsets(final Walk walk, final int numOrths)
	{
		if (walk == null)
		{
			return Arrays.asList(new int[]{});
		}
		
		final int[][] dirOffsets;
		
		if (numOrths == 4)
		{
			dirOffsets = new int[][]{
					{0, 1},
					{1, 0},
					{0, -1},
					{-1, 0}
			};
		}
		else if (numOrths == 6)
		{
			dirOffsets = new int[][]{
				{1, 1},
				{1, 0},
				{1, -1},
				{-1, -1},
				{-1, 0},
				{-1, 1}
			};
		}
		else
		{
			return null;
		}
		
		final List<int[]> offsets = new ArrayList<int[]>();
		final TFloatArrayList steps = walk.steps();
		
		if (steps.size() > 0)
		{
			final TIntArrayList connectionIndices = new TIntArrayList(2);

			float connectionIdxFloat = steps.get(0) * dirOffsets.length;	
			float connectionIdxFractionalPart = connectionIdxFloat - (int) connectionIdxFloat;
			
			if 
			(
				Math.abs(0.5f - connectionIdxFractionalPart) < 0.02f ||
				Math.abs(0.5f + connectionIdxFractionalPart) < 0.02f
			)
			{
				// we're (almost) exactly halfway between two integer indices, so we'll use both
				connectionIndices.add((int) Math.floor(connectionIdxFloat));
				connectionIndices.add((int) Math.ceil(connectionIdxFloat));
			}
			else
			{
				// not almost exactly halfway, so just round and use a single integer index
				connectionIndices.add(Math.round(connectionIdxFloat));
			}
			
			for (int c = 0; c < connectionIndices.size(); ++c)
			{
				int connectionIdx = connectionIndices.getQuick(c);
				
				// wrap around... (thanks https://stackoverflow.com/a/4412200/6735980)
				connectionIdx = (connectionIdx % dirOffsets.length + dirOffsets.length) % dirOffsets.length;
				
				List<List<int[]>> offsetPaths = new ArrayList<List<int[]>>();
				List<int[]> currPath = new ArrayList<int[]>();
				currPath.add(dirOffsets[connectionIdx]);
				offsetPaths.add(currPath);
				
				for (int step = 1; step < steps.size(); ++step)
				{
					// apply step to all possible "offset paths"
					
					final List<List<int[]>> newOffsetPaths = new ArrayList<List<int[]>>();
					
					for (int i = 0; i < offsetPaths.size(); ++i)
					{
						currPath = offsetPaths.get(i);
						
						// compute the directions that count as "continuing straight ahead"
						// (will be only one in the case of cells with even number of edges, but two otherwise)
						final TIntArrayList contDirs = new TIntArrayList(2);
						int fromDir = -1;
						final int[] lastOffset = currPath.get(currPath.size() - 1);
						
						for (int dirIdx = 0; dirIdx < dirOffsets.length; ++dirIdx)
						{
							if 
							(
								dirOffsets[dirIdx][0] + lastOffset[0] == 0 && 
								dirOffsets[dirIdx][1] + lastOffset[1] == 0
							)
							{
								fromDir = dirIdx;
								break;
							}
						}
						
						if (fromDir == -1)
						{
							System.err.println("Warning! FeatureToEPS.computeOffsets() could not find fromDir!");
						}
						
						if (dirOffsets.length % 2 == 0)
						{
							contDirs.add(fromDir + dirOffsets.length / 2);
						}
						else
						{
							contDirs.add(fromDir + dirOffsets.length / 2);
							contDirs.add(fromDir + 1 + dirOffsets.length / 2);
						}
						
						// for each of these "continue directions", we apply the next rotation 
						// specified in the Walk and move on
						for (int contDirIdx = 0; contDirIdx < contDirs.size(); ++contDirIdx)
						{
							final int contDir = contDirs.getQuick(contDirIdx);
							
							final TIntArrayList nextConnectionIndices = new TIntArrayList(2);
							connectionIdxFloat = contDir + steps.get(step) * dirOffsets.length;
							connectionIdxFractionalPart = connectionIdxFloat - (int) connectionIdxFloat;
							
							if 
							(
								Math.abs(0.5f - connectionIdxFractionalPart) < 0.02f ||
								Math.abs(0.5f + connectionIdxFractionalPart) < 0.02f
							)
							{
								// we're (almost) exactly halfway between two integer indices, so we'll use both
								nextConnectionIndices.add((int) Math.floor(connectionIdxFloat));
								nextConnectionIndices.add((int) Math.ceil(connectionIdxFloat));
							}
							else
							{
								// not almost exactly halfway, so just round and use a single integer index
								nextConnectionIndices.add(Math.round(connectionIdxFloat));
							}
							
							for (int n = 0; n < nextConnectionIndices.size(); ++n)
							{
								// wrap around...
								connectionIdx = 
										(nextConnectionIndices.getQuick(n) % dirOffsets.length + 
												dirOffsets.length) % dirOffsets.length;
								
								final List<int[]> newCurrPath = new ArrayList<int[]>();
								newCurrPath.addAll(currPath);
								newCurrPath.add(dirOffsets[connectionIdx]);
								newOffsetPaths.add(newCurrPath);
							}
						}
					}
					
					offsetPaths = newOffsetPaths;
				}
				
				for (final List<int[]> offsetPath : offsetPaths)
				{
					final int[] sumOffsets = new int[]{0, 0};
					
					for (final int[] offset : offsetPath)
					{
						sumOffsets[0] += offset[0];
						sumOffsets[1] += offset[1];
					}
					
					offsets.add(sumOffsets);
				}
			}
		}
		else	// no real Walk
		{
			offsets.add(new int[]{0, 0});
		}
		
		return offsets;
	}
	
	//-------------------------------------------------------------------------
	
//	/**
//	 * Enumerates all possible combinations of offsets for multiple
//	 * feature elements.
//	 * 
//	 * @param elementOffsets For every element, a list of allowed offsets
//	 * @param outCombos List to populate with all possible solutions, where
//	 * 	a solution consists of one legal offset per feature element
//	 * @param partialSolution A partial solution constructed so far
//	 */
//	private static void enumerateOffsetCombos
//	(
//		final List<List<int[]>> elementOffsets,
//		final List<List<int[]>> outCombos,
//		final List<int[]> partialSolution
//	)
//	{
//		if (partialSolution.size() == elementOffsets.size())
//		{
//			// solution complete; copy and store it
//			outCombos.add(new ArrayList<int[]>(partialSolution));
//			return;
//		}
//		
//		final int nextElement = partialSolution.size();
//		final List<int[]> legalElementOffsets = elementOffsets.get(nextElement);
//		
//		for (int i = 0; i < legalElementOffsets.size(); ++i)
//		{
//			partialSolution.add(legalElementOffsets.get(i));
//			enumerateOffsetCombos(elementOffsets, outCombos, partialSolution);
//			partialSolution.remove(partialSolution.size() - 1);
//		}
//	}
	
	//-------------------------------------------------------------------------
	
//	/**
//	 * A cell on our "board", specified by dx and dy from feature's anchor position.
//	 * 
//	 * @author Dennis Soemers
//	 */
//	private static class Cell
//	{
//		/** dx */
//		public final int dx;
//		
//		/** dy */
//		public final int dy;
//		
//		/**
//		 * Constructor
//		 * @param offset
//		 */
//		public Cell(final int[] offset)
//		{
//			dx = offset[0];
//			dy = offset[1];
//		}
//
//		@Override
//		public int hashCode()
//		{
//			final int prime = 31;
//			int result = 1;
//			result = prime * result + dx;
//			result = prime * result + dy;
//			return result;
//		}
//
//		@Override
//		public boolean equals(final Object obj)
//		{
//			if (!(obj instanceof Cell))
//				return false;
//			
//			final Cell other = (Cell) obj;
//
//			return (dx == other.dx && dy == other.dy);
//		}
//	}
	
	//-------------------------------------------------------------------------

}
