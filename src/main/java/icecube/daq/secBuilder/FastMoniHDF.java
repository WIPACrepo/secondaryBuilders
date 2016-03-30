package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;

import java.io.File;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FastMoniHDF
{
    // FAST monitoring record contains 4 values
    public static final int WIDTH = 4;

    private static final Log LOG = LogFactory.getLog(FastMoniHDF.class);

    // internal dataset name
    private static final String DATASETNAME = "fastMoni";
    // we must write one row at a time
    private static final int LENGTH = 1;
    // we always build 2-dimension datasets
    private static final int NUM_DIMS = 2;

    // allow for up to 10 million rows
    private static final long MAX_DATASET_ROWS = 100000000L;

    // DAQ file dispatcher
    private Dispatcher dispatcher;

    // object used to synchronize access to the run/sequence number pair
    private Object fileLock = new Object();
    // DAQ run number
    private int runNumber;
    // sequence number
    private int seqNumber;

    // temporary file name
    private File tempFile;

    // internal HDF5 IDs
    private int spaceId;
    private int fileId;
    private int createPropId;
    private int xferPropId;
    private int datasetId;
    private int memspaceId;

    // arrays used to track the dataset size and offset for next row
    private long[] datasetSize;
    private long[] offset;

    // array which holds the single row of values
    private int[][] wrapper;

    // if the initial operation fails, write a log message with a possible fix
    private boolean loggedHelp;

    /**
     * Create an empty HDF5 file
     *
     * @param dispatcher DAQ file dispatcher
     *
     * @throws I3HDFException if there is a problem
     */
    public FastMoniHDF(Dispatcher dispatcher, int runNumber)
        throws I3HDFException
    {
        this.dispatcher = dispatcher;
        this.runNumber = runNumber;

        if (dispatcher.getDispatchDestStorage() == null) {
            throw new I3HDFException("Dispatch directory cannot be null");
        }

        // build the temporary file name
        tempFile = new File(dispatcher.getDispatchDestStorage(), "temp.h5");
        if (tempFile.exists()) {
            LOG.error("The last temp-HDF5 file was not moved" +
                      " to the dispatch storage!!!");
        }

        // preallocate offset dimension array
        offset = new long[2];

        openTempFile();
    }

    /**
     * Check that we can access the JHDF library.
     *
     * @return <tt>false</tt> if the library is not available
     */
    public static final boolean checkForLibrary()
    {
        try {
            // this should always return true
            return HDF5Constants.H5P_DATASET_CREATE != Integer.MIN_VALUE;
        } catch (Throwable thr) {
            return false;
        }
    }

    /**
     * Release resources
     *
     * @throws I3HDFException if there is a problem
     */
    public void close()
        throws I3HDFException
    {
        try {
            final int status = H5.H5Sclose(memspaceId);
            if (status != 0) {
                throw new I3HDFException("H5Sclose(memspaceId) failed");
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        try {
            final int status = H5.H5Dclose(datasetId);
            if (status != 0) {
                throw new I3HDFException("H5Dclose(datasetId) failed");
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        try {
            final int status = H5.H5Fclose(fileId);
            if (status != 0) {
                throw new I3HDFException("H5Fclose(fileId) failed");
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        try {
            final int status = H5.H5Pclose(xferPropId);
            if (status != 0) {
                throw new I3HDFException("H5Pclose(xferPropId) failed");
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        try {
            final int status = H5.H5Sclose(spaceId);
            if (status != 0) {
                throw new I3HDFException("H5Sclose(spaceId) failed");
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        try {
            final int status = H5.H5Pclose(createPropId);
            if (status != 0) {
                throw new I3HDFException("H5Pclose(createPropId) failed");
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        if (getLength() > 0) {
            renameTempFile();
        }
    }

    /**
     * Return the current number of rows.
     *
     * @return number of rows
     */
    public long getLength()
    {
        if (datasetSize == null) {
            return 0;
        }

        return datasetSize[0];
    }

    /**
     * Open the HDF5 file
     */
    private void openTempFile()
        throws I3HDFException
    {
        // create dataset property list
        try {
            createPropId = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
        } catch (HDF5Exception hex) {
            if (!loggedHelp) {
                LOG.error("Cannot create initial dataset property; have" +
                          " you added -Djava.library.path=/path/to/jhdf5" +
                          " to the SecondaryBuilders cluster config entry?");
                loggedHelp = true;
            }

            throw new I3HDFException(hex);
        } catch (NoClassDefFoundError ncdf) {
            if (!loggedHelp) {
                LOG.error("Cannot create initial dataset property; have" +
                          " you added -Djava.library.path=/path/to/jhdf5" +
                          " to the SecondaryBuilders cluster config entry?");
                loggedHelp = true;
            }

            throw new I3HDFException(ncdf);
        }

        final long[] dim = {LENGTH, WIDTH};
        final long[] maxdim = {HDF5Constants.H5S_UNLIMITED, WIDTH};

        // Create the 2-dimensional data space
        try {
            spaceId = H5.H5Screate_simple(NUM_DIMS, dim, maxdim);
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        // create transfer buffer property list
        try {
            xferPropId = H5.H5Pcreate(HDF5Constants.H5P_DATASET_XFER);
            // allow up to MAX_DATASET_ROWS rows of 4-byte values
            H5.H5Pset_buffer_size(xferPropId, MAX_DATASET_ROWS * WIDTH * 4);
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        // Create the file
        try {
            fileId = H5.H5Fcreate(tempFile.getPath(),
                                  HDF5Constants.H5F_ACC_TRUNC,
                                  HDF5Constants.H5P_DEFAULT,
                                  HDF5Constants.H5P_DEFAULT);
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        // create property with chunk size to a single row
        try {
            final long[] chunkdim = {LENGTH, WIDTH};

            final int status = H5.H5Pset_chunk(createPropId, NUM_DIMS,
                                               chunkdim);
            if (status != 0) {
                final String fmtStr = "Cannot set chunk size to [%d, %d]";
                throw new I3HDFException(String.format(fmtStr, chunkdim[0],
                                                       chunkdim[1]));
            }
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        // Create the dataset
        try {
            datasetId = H5.H5Dcreate(fileId, DATASETNAME,
                                     HDF5Constants.H5T_STD_I32BE, spaceId,
                                     createPropId);
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }

        // Create memory space for slab writes
        try {
            memspaceId = H5.H5Dget_space(datasetId);
        } catch (HDF5Exception hex) {
            throw new I3HDFException(hex);
        }
    }

    /**
     * Rename the temporary file to something which SPADE understands
     */
    private void renameTempFile()
    {
        final String nextName;
        synchronized (fileLock) {
            nextName =
                String.format("ITFastMoni-%d-%d.h5", runNumber, seqNumber++);
        }

        final File nextFile = new File(tempFile.getParent(), nextName);

        tempFile.renameTo(nextFile);
    }

    public void switchToNewRun(int newNumber)
        throws I3HDFException
    {
        synchronized (fileLock) {
            try {
                close();
            } catch (I3HDFException ex) {
                LOG.error("Cannot close HDF5 file #" + seqNumber +
                          " for run " + runNumber);
            }

            runNumber = newNumber;
            seqNumber = 0;

            openTempFile();
        }
    }

    /**
     * Write next row of FAST monitoring values.
     *
     * @param data FAST values
     *
     * @throws I3HDFException if there is a problem
     */
    public void write(int[] data)
        throws I3HDFException
    {
        synchronized (fileLock) {
            final int tmpMemId, tmpSpaceId;
            if (datasetSize == null) {
                // first row is entire dataset
                tmpMemId = HDF5Constants.H5S_ALL;
                tmpSpaceId = HDF5Constants.H5S_ALL;

                datasetSize = new long[] {LENGTH, WIDTH};
                wrapper = new int[1][WIDTH];
            } else {
                // update offset and size
                offset[0] = datasetSize[0];
                datasetSize[0] = datasetSize[0] + 1;

                // extend dataset
                try {
                    final int status = H5.H5Dextend(datasetId, datasetSize);
                    if (status != 0) {
                        throw new I3HDFException("Extend failed");
                    }
                } catch (HDF5Exception hex) {
                    throw new I3HDFException(hex);
                }

                // get the new workspace
                try {
                    tmpSpaceId = H5.H5Dget_space(datasetId);
                } catch (HDF5Exception hex) {
                    throw new I3HDFException(hex);
                }

                // select the new row
                try {
                    final long[] count = {LENGTH, WIDTH};

                    final int status =
                        H5.H5Sselect_hyperslab(tmpSpaceId,
                                               HDF5Constants.H5S_SELECT_SET,
                                               offset, null, count, null);
                    if (status != 0) {
                        throw new I3HDFException("Select failed");
                    }
                } catch (HDF5Exception hex) {
                    throw new I3HDFException(hex);
                }

                tmpMemId = memspaceId;
            }

            // write the new row
            try {
                wrapper[0] = data;
                final int status = H5.H5Dwrite(datasetId,
                                               HDF5Constants.H5T_NATIVE_INT,
                                               tmpMemId, tmpSpaceId,
                                               xferPropId, wrapper);
                if (status != 0) {
                    throw new I3HDFException("Write failed");
                }
            } catch (HDF5Exception hex) {
                throw new I3HDFException(hex);
            }
        }
    }
}
