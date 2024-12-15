package giis.retorch.orchestration.generator;

import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.orchestration.scheduler.NoTGroupsInTheSchedulerException;
import giis.retorch.orchestration.scheduler.NotValidSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
/**
 * The {@code OrchestationGeneratorMainClass} class provides only calls the orchestration generator method.
 */
public class OrchestationGeneratorMainClass {
    private static final Logger log = LoggerFactory.getLogger(OrchestationGeneratorMainClass.class);

    public static void main(String[] args) throws NoFinalActivitiesException, NoTGroupsInTheSchedulerException, EmptyInputException, IOException, URISyntaxException, NotValidSystemException, ClassNotFoundException {
        if (args.length == 3) {
            OrchestrationGenerator orch= new OrchestrationGenerator();
            orch.generateJenkinsfile(args[0],args[1],args[2]);
        } else {
            log.error("Usage: java retorch-orchestration <rootPackageNameTests> <systemName> <jenkinsFilePath>");
        }
    }
}