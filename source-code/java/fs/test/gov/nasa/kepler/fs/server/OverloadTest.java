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

package gov.nasa.kepler.fs.server;

import static gov.nasa.kepler.fs.FileStoreConstants.*;
import static org.junit.Assert.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import gov.nasa.kepler.fs.api.FileStoreClient;
import gov.nasa.kepler.fs.client.FileStoreClientFactory;
import gov.nasa.kepler.fs.transport.FileStoreServerTooManyClientsException;
import gov.nasa.kepler.hibernate.dbservice.ConfigurationServiceFactory;
import gov.nasa.spiffy.common.io.FileUtil;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Overload the server with too many client threads.
 * @author Sean McCauliff
 *
 */
public class OverloadTest {

    private static final Log log = LogFactory.getLog(OverloadTest.class);
    
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void overloadServer() throws Exception {
        final FileStoreClient fsClient = FileStoreClientFactory.getInstance();
        fsClient.ping();
        Configuration conf = ConfigurationServiceFactory.getInstance();
        int nthreads = conf.getInt(FS_SERVER_MAX_CLIENTS, FS_SERVER_MAX_CLIENTS_DEFAULT);
        nthreads += 2;
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        
        Runnable r = new Runnable() {
            public void run() {
                try {
                    fsClient.disassociateThread();
                    fsClient.ping();
                    Thread.sleep(500);
                    fsClient.rollbackLocalFsTransaction();
                } catch (Throwable t) {
                    log.error("Client thread log.", t);
                    error.compareAndSet(null, t);
                } finally {
                    try {
                        FileUtil.close(fsClient);
                    } catch (Throwable t) {
                        //nothing
                    }
                }
            }
        };
        
        List<Thread> threads = new ArrayList<Thread>();
        for (int i=0; i < nthreads; i++ ) {
            Thread t = new Thread(r, "Overload-" + i);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            t.join();
        }
        
        assertTrue(error.get() != null);
        
        assertTrue("Should have gotten FileStoreServerTooManyClientsException.", 
            error.get().getClass() == FileStoreServerTooManyClientsException.class);
        error.get().printStackTrace();
        Thread.sleep(1000);
    }

}