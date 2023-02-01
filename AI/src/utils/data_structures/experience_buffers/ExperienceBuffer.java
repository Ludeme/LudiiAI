package utils.data_structures.experience_buffers;

import java.util.List;

import training.expert_iteration.ExItExperience;

/**
 * Interface for experience buffers. Declares common methods
 * that we expect in uniform as well as Prioritized Experience Replay
 * buffers.
 * 
 * @author Dennis Soemers
 */
public interface ExperienceBuffer
{
	
	/**
	 * Adds a new sample of experience.
	 * Defaulting to the max observed priority level in the case of PER.
	 * 
	 * @param experience
	 */
	public void add(final ExItExperience experience);
	
	/**
	 * @param batchSize
	 * @return A batch of the given batch size, sampled uniformly with 
	 * replacement.
	 */
	public List<ExItExperience> sampleExperienceBatch(final int batchSize);
	
	/**
	 * @param batchSize
	 * @return Sample of batchSize tuples of experience, sampled uniformly
	 */
	public List<ExItExperience> sampleExperienceBatchUniformly(final int batchSize);
	
	/**
	 * @return Return the backing array containing ALL experience (including likely
	 * null entries if the buffer was not completely filled).
	 */
	public ExItExperience[] allExperience();
	
	/**
	 * Writes this complete buffer to a binary file
	 * @param filepath
	 */
	public void writeToFile(final String filepath);

}
