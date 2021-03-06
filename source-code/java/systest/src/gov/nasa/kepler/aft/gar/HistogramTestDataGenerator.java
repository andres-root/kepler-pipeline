/*
 * Copyright 2017 United States Government as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All Rights Reserved.
 * 
 * This file is available under the terms of the NASA Open Source Agreement
 * (NOSA). You should have received a copy of this agreement with the
 * Kepler source code; see the file NASA-OPEN-SOURCE-AGREEMENT.doc.
 * 
 * No Warranty: THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY
 * WARRANTY OF ANY KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY,
 * INCLUDING, BUT NOT LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE
 * WILL CONFORM TO SPECIFICATIONS, ANY IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR FREEDOM FROM
 * INFRINGEMENT, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL BE ERROR
 * FREE, OR ANY WARRANTY THAT DOCUMENTATION, IF PROVIDED, WILL CONFORM
 * TO THE SUBJECT SOFTWARE. THIS AGREEMENT DOES NOT, IN ANY MANNER,
 * CONSTITUTE AN ENDORSEMENT BY GOVERNMENT AGENCY OR ANY PRIOR RECIPIENT
 * OF ANY RESULTS, RESULTING DESIGNS, HARDWARE, SOFTWARE PRODUCTS OR ANY
 * OTHER APPLICATIONS RESULTING FROM USE OF THE SUBJECT SOFTWARE.
 * FURTHER, GOVERNMENT AGENCY DISCLAIMS ALL WARRANTIES AND LIABILITIES
 * REGARDING THIRD-PARTY SOFTWARE, IF PRESENT IN THE ORIGINAL SOFTWARE,
 * AND DISTRIBUTES IT "AS IS."
 * 
 * Waiver and Indemnity: RECIPIENT AGREES TO WAIVE ANY AND ALL CLAIMS
 * AGAINST THE UNITED STATES GOVERNMENT, ITS CONTRACTORS AND
 * SUBCONTRACTORS, AS WELL AS ANY PRIOR RECIPIENT. IF RECIPIENT'S USE OF
 * THE SUBJECT SOFTWARE RESULTS IN ANY LIABILITIES, DEMANDS, DAMAGES,
 * EXPENSES OR LOSSES ARISING FROM SUCH USE, INCLUDING ANY DAMAGES FROM
 * PRODUCTS BASED ON, OR RESULTING FROM, RECIPIENT'S USE OF THE SUBJECT
 * SOFTWARE, RECIPIENT SHALL INDEMNIFY AND HOLD HARMLESS THE UNITED
 * STATES GOVERNMENT, ITS CONTRACTORS AND SUBCONTRACTORS, AS WELL AS ANY
 * PRIOR RECIPIENT, TO THE EXTENT PERMITTED BY LAW. RECIPIENT'S SOLE
 * REMEDY FOR ANY SUCH MATTER SHALL BE THE IMMEDIATE, UNILATERAL
 * TERMINATION OF THIS AGREEMENT.
 */

package gov.nasa.kepler.aft.gar;

import gov.nasa.kepler.aft.AbstractTestDataGenerator;
import gov.nasa.kepler.common.SocEnvVars;
import gov.nasa.kepler.hibernate.dbservice.TransactionService;
import gov.nasa.kepler.hibernate.dbservice.TransactionServiceFactory;
import gov.nasa.kepler.hibernate.gar.CompressionCrud;
import gov.nasa.kepler.hibernate.gar.Histogram;
import gov.nasa.kepler.hibernate.gar.HistogramGroup;
import gov.nasa.kepler.pi.configuration.PipelineConfigurationOperations;

import java.io.File;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Creates an HSQLDB database seeded with one {@code GAR_HISTOGRAM_GROUP}, 4
 * {@code GAR_HISTOGRAM_GROUP_HISTOGRAMS}, 4 {@code GAR_HISTOGRAM}s, and 524284
 * {@code GAR_HISTOGRAM_VALUES} seeded by running the relevant pipeline modules.
 * 
 * @author Forrest Girouard
 * @author Bill Wohler
 */
public class HistogramTestDataGenerator extends AbstractTestDataGenerator {

    private static final Log log = LogFactory.getLog(HistogramTestDataGenerator.class);

    private static final String GENERATOR_NAME = "histogram";

    private static final String HISTOGRAM_TRIGGER_NAME = "HISTOGRAM";

    public HistogramTestDataGenerator() {
        super(GENERATOR_NAME);
        setImportFcModels(false);
    }

    @Override
    protected void createDatabaseContents() throws Exception {

        log.info(getLogName() + ": Importing pipeline configuration");
        new PipelineConfigurationOperations().importPipelineConfiguration(new File(
            SocEnvVars.getLocalDataDir(), AFT_PIPELINE_CONFIGURATION_ROOT
                + GENERATOR_NAME + ".xml"));

        updateCadenceRangeParameters();
    }

    @Override
    protected void process() throws Exception {
        runPipeline(HISTOGRAM_TRIGGER_NAME);

        TransactionService transactionService = TransactionServiceFactory.getInstance();
        transactionService.beginTransaction();
        log.info(getLogName() + ": Modifying database objects");
        readyHistogramsForExport();
        transactionService.commitTransaction();
    }

    private void readyHistogramsForExport() {
        CompressionCrud compressionCrud = new CompressionCrud();

        List<HistogramGroup> histogramGroups = compressionCrud.retrieveAllHistogramGroups();

        // Tweak the histograms in the database.
        for (HistogramGroup histogramGroup : histogramGroups) {
            if (histogramGroup.getCcdModule() != HistogramGroup.CCD_MOD_OUT_ALL) {
                // Delete histograms from hgn run. We're only interested in the
                // final histogram generated by hac.
                log.info(getLogName() + ": Pruning histogram group for "
                    + histogramGroup.getCcdModule() + "/"
                    + histogramGroup.getCcdOutput());

                // First delete all of the histograms starting at the end.
                List<Histogram> histograms = histogramGroup.getHistograms();
                for (int i = histograms.size() - 1; i >= 0; i--) {
                    compressionCrud.delete(histograms.remove(i));
                }

                // Then remove the histogram group itself.
                compressionCrud.delete(histogramGroup);
            }
        }
    }

    // See also HistogramTestDataGeneratorTest harness.
    public static void main(String[] args) {
        try {
            new HistogramTestDataGenerator().generate();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.err.println(e.getMessage());
            System.exit(1);
        }
        System.exit(0);
    }
}
