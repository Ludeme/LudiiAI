package features.visualisation;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileFilter;

import features.feature_sets.FeatureSet;
import features.features.Feature;
import game.Game;

/**
 * This was once intended to be a frame showing visualisations of features.
 * Should be changed into something that just provides functionality for
 * printing the PDFs, no JFrame.
 *
 * @author Dennis Soemers
 */
public class FeaturesFrame extends JFrame
{
	
	/** */
	private static final long serialVersionUID = 1L;

	//-------------------------------------------------------------------------
	
	/** Our panel with images */
	protected JPanel imagePanel;
	
	/** File Chooser to choose output directory */
	protected static JFileChooser outDirChooser = null;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param featureSet Feature Set to visualise
	 * @param player Player for which to visualise features
	 * @param game Game for which to visualise
	 * @param featureSetName Name of feature set
	 */
	public FeaturesFrame
	(
		final FeatureSet featureSet, 
		final int player, 
		final Game game,
		final String featureSetName
	)
	{
		super("Ludii Features");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
		// Logo
		try
		{
			final URL resource = this.getClass().getResource("/ludii-logo-64x64.png");
			final BufferedImage image = ImageIO.read(resource);
			setIconImage(image);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		// create panel to contain images
		final int numFeatures = featureSet.getNumFeatures();
		final int numCols = 5;
		final int numRows = (int) Math.ceil((double) numFeatures / numCols);
		imagePanel = new JPanel(new GridLayout(numRows, numCols));
		
		// create main scroll pane
		final JScrollPane scrollPane = new JScrollPane(imagePanel);
		scrollPane.setPreferredSize(new Dimension(700, 500));
		setContentPane(scrollPane);
		
		if (outDirChooser == null)
		{
			outDirChooser = new JFileChooser("");
			outDirChooser.setFileSelectionMode(
					JFileChooser.FILES_AND_DIRECTORIES);

			final FileFilter dirFilter = new FileFilter()
			{
				@Override
				public boolean accept(File f)
				{
					return f.isDirectory();
				}

				@Override
				public String getDescription()
				{
					return "Directories";
				}

			};
			outDirChooser.setFileFilter(dirFilter);
			outDirChooser.setPreferredSize(new Dimension(1000, 600));
			
			// Automatically try to switch to details view in file chooser
			Action details = outDirChooser.getActionMap().get("viewTypeDetails");

			if (details != null)
			{
				details.actionPerformed(null);
			}
		}
		
		outDirChooser.setDialogTitle("Choose an empty output directory");
		final int fcReturnVal = outDirChooser.showOpenDialog(null);

		if (fcReturnVal == JFileChooser.APPROVE_OPTION)
		{
			final File outDir = outDirChooser.getSelectedFile();

			if (outDir != null && outDir.exists())
			{
				final File[] files = outDir.listFiles();
				
				if (files == null || files.length > 0)
				{
					JOptionPane.showMessageDialog(
							null, "This is not an empty output directory.");
				}
				else
				{
					createImages(featureSet, player, game, outDir, featureSetName);
				}
			}
		}
		
		pack();
		setLocationByPlatform(true);
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Creates all the images and adds them to the image panel
	 * @param featureSet
	 * @param player
	 * @param game
	 * @param outDir
	 * @param featureSetName
	 */
	private static void createImages
	(
		final FeatureSet featureSet, 
		final int player, 
		final Game game,
		final File outDir,
		final String featureSetName
	)
	{
		final Feature[] features = featureSet.features();
		
		try 
		(
			// we'll write a .tex file showing all the features
			final PrintWriter w = new PrintWriter
			(
				outDir.getAbsolutePath() + File.separator + featureSetName + ".tex", 
				"UTF-8"
			)
		)
		{
			w.println("\\documentclass[a4paper,12pt]{article}");
			w.println("");
			w.println("\\usepackage{graphicx}");
			w.println("\\usepackage{subcaption}");
			w.println("\\usepackage{seqsplit}");
			w.println("\\usepackage{float}");
			w.println("");
			w.println("\\begin{document}");
			w.println("");
			
			for (int i = 1; i <= features.length; ++i)
			{
				FeatureToEPS.createEPS
				(
					features[i - 1], 
					player, 
					game, 
					new File(outDir.getAbsolutePath() + File.separator + 
							String.format("Feature_%05d.eps", Integer.valueOf(i)))
				);
				
				boolean newFig = ((i - 1) % 3) == 0;
				
				if (i > 1 && newFig)
				{
					// first end the previous fig
					w.println("\\end{figure}");
				}
				
				if (newFig)
				{
					w.println("\\begin{figure}[H]");
					
					if (i > 1)
					{
						w.println("\\ContinuedFloat");
					}
					
					w.println("\\centering");
				}
				
				w.println("\\begin{subfigure}{.3\\textwidth}");
				w.println("\\centering");
				w.println("\\includegraphics[height=3.5cm]{" + String.format("Feature_%05d.eps", Integer.valueOf(i)) + "}");
				w.println
				(
					"\\caption*{\\scriptsize\\texttt{\\seqsplit{" +
					features[i - 1].toString()
					.replaceAll(Pattern.quote("#"), Matcher.quoteReplacement("\\#"))
					.replaceAll(Pattern.quote("{"), Matcher.quoteReplacement("\\{"))
					.replaceAll(Pattern.quote("}"), Matcher.quoteReplacement("\\}"))
					.replaceAll(Pattern.quote(" "), Matcher.quoteReplacement("~")) + 
					"}}}"
				);
				w.println("\\end{subfigure} \\hfill");
			}
			
			// end the last fig
			w.println("\\end{figure}");
			
			w.println("\\end{document}");
		}
		catch (final FileNotFoundException | UnsupportedEncodingException e)
		{
			e.printStackTrace();
		}
	}
	
	//-------------------------------------------------------------------------

}
