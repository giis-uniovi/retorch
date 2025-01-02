package giis.retorch.profiling.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import giis.retorch.profiling.datasetgeneration.DataTuple;
import giis.retorch.profiling.datasetgeneration.DatasetGenerator;

import java.util.List;

/**
 {@code UsageProfilerToolBox gets the main methods to generate the different UsageProfiles.}
 */
public class UsageProfilerToolBox {
    DatasetGenerator generator;
    private static final Logger log = LoggerFactory.getLogger(UsageProfilerToolBox.class);
    public UsageProfilerToolBox(){
        generator= new DatasetGenerator();
    }
    /**
     * Get as input the path with the CSV files and provides as output in the directory selected the average duration
     * csv file with all the COI and TJobs lifecycles.
     * <p>
     * Example usage:
     * <pre>
     *     // Instantiates the class
     *     UsageProfilerToolBox usageProfiler = new UsageProfilerToolBox();
     *     usageProfiler.generateAverageDurationCSVFile("/home/datasets","/testFileGeneratorOutput4parallel.csv");
     *   </pre>
     * </p>
     * @param inputPath The path where CSV files are located.
     * @param outputPath the path where the average execution plan would be placed
     */
    public void generateAverageDurationCSVFile(String inputPath,String outputPath){
        log.debug("Generating the DataTuples");
        List<DataTuple> listTuples=generator.generateListTuplesAvgTimes(inputPath);
        log.debug("Creating the CSV file from the DataTuples");
        generator.createCSVAvgFromListDataTuplesOrdered(listTuples,outputPath);
    }
}
