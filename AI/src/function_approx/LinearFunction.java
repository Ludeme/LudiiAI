package function_approx;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import main.collections.FVector;

/**
 * A linear function approximator
 * 
 * @author Dennis Soemers
 */
public class LinearFunction 
{
	
	//-------------------------------------------------------------------------
	
	/** Our vector of parameters / weights */
	protected FVector theta;
	
	/** Filepath for feature set corresponding to our parameters */
	protected String featureSetFile = null;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * 
	 * @param theta
	 */
	public LinearFunction(final FVector theta)
	{
		this.theta = theta;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param denseFeatures
	 * @return Predicted value for a given feature vector (dense)
	 */
	public float predict(final FVector denseFeatures)
	{
		return effectiveParams().dot(denseFeatures);
	}
	
	/**
	 * @param sparseFeatures
	 * @return Predicted value for a given feature vector (binary, sparse)
	 */
	public float predict(final TIntArrayList sparseFeatures)
	{
		return effectiveParams().dotSparse(sparseFeatures);
	}
	
	/**
	 * @return Vector of effective parameters, used for making predictions. For this
	 *         class, a reference to theta.
	 */
	public FVector effectiveParams()
	{
		return theta;
	}

	/**
	 * @return Reference to parameters vector that we can train. For this class,
	 *         a reference to theta.
	 */
	public FVector trainableParams()
	{
		return theta;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Adjust our parameters theta using gradient descent
	 * 
	 * @param gradients
	 * @param stepSize
	 */
	public void gradientDescent(final FVector gradients, final float stepSize)
	{
		trainableParams().addScaled(gradients, -stepSize);
	}
	
	/**
	 * Adjust our parameters theta using gradient descent and weight decay (L2 regularization)
	 * 
	 * @param gradients Gradients of unregularized objective (loss) w.r.t. our params
	 * @param stepSize
	 * @param weightDecayParam Equivalent to lambda param in L2 regularization (if L2 regularization
	 * is described as (lambda / 2) * norm of params), or equivalent to (2 * lambda) (if L2
	 * regularization is described as lambda * norm of params).
	 */
	public void gradientDescent
	(
		final FVector gradients, 
		final float stepSize, 
		final float weightDecayParam
	)
	{
		// first regularize (no problem if we're directly modifying theta, has already
		// been used to compute gradients anyway)
		trainableParams().addScaled(trainableParams(), -stepSize * weightDecayParam);
		
		// and now we can finish with unregularized gradient descent on top of that
		// (with precomputed gradients, important that that was done before modifying theta)
		gradientDescent(gradients, stepSize);
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Replaces the linear function's param vector theta
	 * @param newTheta
	 */
	public void setTheta(final FVector newTheta)
	{
		theta = newTheta;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return Filename for corresponding Feature Set
	 */
	public String featureSetFile()
	{
		return featureSetFile;
	}
	
	/**
	 * Sets the filename for the corresponding Feature Set
	 * @param featureSetFile
	 */
	public void setFeatureSetFile(final String featureSetFile)
	{
		this.featureSetFile = featureSetFile;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Writes linear function to the given filepath
	 * @param filepath
	 * @param featureSetFiles
	 */
	public void writeToFile(final String filepath, final String[] featureSetFiles)
	{
		try (PrintWriter writer = new PrintWriter(filepath, "UTF-8"))
		{
			for (int i = 0; i < theta.dim(); ++i)
			{
				writer.println(theta.get(i));
			}
			
			for (final String fsf : featureSetFiles)
			{
				writer.println("FeatureSet=" + new File(fsf).getName());
			}
		} 
		catch (final IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * @param filepath
	 * @return Reads linear function from the given filepath
	 */
	public static LinearFunction fromFile(final String filepath)
	{
		try 
		(
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filepath), "UTF-8"))
		)
		{
			final TFloatArrayList readFloats = new TFloatArrayList();
			String featureSetFile = null;
			
			while (true)
			{
				final String line = reader.readLine();
				
				if (line == null)
					break;
				
				if (line.startsWith("FeatureSet="))
					featureSetFile = line.substring("FeatureSet=".length());
				else
					readFloats.add(Float.parseFloat(line));
			}
			
			float[] floats = new float[readFloats.size()];
			
			for (int i = 0; i < floats.length; ++i)
			{
				floats[i] = readFloats.getQuick(i);
			}
			
			final LinearFunction func = new LinearFunction(FVector.wrap(floats));
			func.setFeatureSetFile(featureSetFile);
			
			return func;
		} 
		catch (final IOException e) 
		{
			e.printStackTrace();
		}
		
		return null;
	}
	
	//-------------------------------------------------------------------------

}
