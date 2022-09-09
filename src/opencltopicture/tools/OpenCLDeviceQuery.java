package opencltopicture.tools;

/*
 * JOCL - Java bindings for OpenCL
 *
 * Copyright 2010 Marco Hutter - http://www.jocl.org/
 */

import static org.jocl.CL.*;

import java.nio.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jocl.*;

/**
 * A JOCL program that queries and prints information about all
 * available devices.
 */
public class OpenCLDeviceQuery
{    
    public static class Logger {

        private void log(Level INFO, String txt, Integer args) {
            MessageFormat fmt = new MessageFormat(txt);
            System.out.println(fmt.format(new Object[] {new Long(args)}));
        }

        private void log(Level INFO, String txt, String args) {
            MessageFormat fmt = new MessageFormat(txt);
            System.out.println(fmt.format(new Object[] {args}));
        }

        private void log(Level INFO, String txt) {
            System.out.println(txt);
        }

        private void log(Level INFO, String txt, Object[] args) {
            MessageFormat fmt = new MessageFormat(txt);
            System.out.println(fmt.format(args));
        }

        private void log(Level INFO, String txt, Long args) {
            MessageFormat fmt = new MessageFormat(txt);
            System.out.println(fmt.format(new Object[] {args}));
        }
        
    }
    
    private static final Logger LOGGER = new Logger(); //Logger.getLogger(OpenCLDeviceQuery.class.getName());
    
    /**
     * The entry point of this program
     *
     * @param args Not used
     */
    public static void main(String args[])
    {
        printDevices();
    }
    
    public static class Device {
        Device(cl_platform_id platform_id, cl_device_id device_id) {
            this.platform_id = platform_id;
            this.device_id = device_id;
        }
        final public cl_platform_id platform_id;
        final public cl_device_id device_id;
    }
    
    public static List<Device> getDevices() {
        // Obtain the number of platforms
        int numPlatforms[] = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        LOGGER.log(Level.INFO, "Number of platforms: {0}", numPlatforms[0]);
        // Obtain the platform IDs
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);
        // Collect all devices of all platforms
        final List<Device> devices = new ArrayList<>();
        for (cl_platform_id platform : platforms) {
            // Obtain the number of devices for the current platform
            int numDevices[] = new int[1];
            try {
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevices);
                if (numDevices[0] > 0) {
                    cl_device_id devicesArray[] = new cl_device_id[numDevices[0]];
                    clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevices[0], devicesArray, null);
                    for (cl_device_id id : devicesArray) {
                        devices.add(new Device(platform, id));
                    }
                }
            } catch (CLException e) {
                // Sur linux on peu avoir des exceptions sur certaines plateforms
            }
        }
        return devices;
    }
    
    public static List<Device> filterDevicesByType(final List<Device> devices, final long deviceType) {
        final List<Device> devicesFiltered = new ArrayList<>();
        devices.stream().filter((device) -> (getLong(device.device_id, CL_DEVICE_TYPE) & deviceType) != 0).forEach((device) -> {
            devicesFiltered.add(device);
        });
        return devicesFiltered;
    }
    
    public static Device getBestDevice() {
        List<Device> devices = getDevices();
        
        List<Device> devicesGPU  = filterDevicesByType(devices, CL_DEVICE_TYPE_GPU);
        if (!devicesGPU.isEmpty()) return devicesGPU.get(0);
        
        List<Device> devicesCPU  = filterDevicesByType(devices, CL_DEVICE_TYPE_CPU);
        if (!devicesCPU.isEmpty()) return devicesCPU.get(0);
        
        List<Device> devicesACC  = filterDevicesByType(devices, CL_DEVICE_TYPE_ACCELERATOR);
        if (!devicesCPU.isEmpty()) return devicesACC.get(0);
        
        List<Device> devicesAll  = filterDevicesByType(devices, CL_DEVICE_TYPE_ALL);
        if (!devicesCPU.isEmpty()) return devicesAll.get(0);
        
        return null;
    }
    
    public static void printDevices() {
        // Obtain the number of platforms
        int numPlatforms[] = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);

        LOGGER.log(Level.INFO, "Number of platforms: {0}", numPlatforms[0]);

        // Obtain the platform IDs
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        // Collect all devices of all platforms
        List<cl_device_id> devices = new ArrayList<>();
        
        for (cl_platform_id platform : platforms) {
            String platformName = getString(platform, CL_PLATFORM_NAME);
            // Obtain the number of devices for the current platform
            int numDevices[] = new int[1];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevices);
            LOGGER.log(Level.INFO, "Number of devices in platform {0}: {1}", new Object[]{platformName, numDevices[0]});
            cl_device_id devicesArray[] = new cl_device_id[numDevices[0]];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevices[0], devicesArray, null);
            devices.addAll(Arrays.asList(devicesArray));
        }

        // Print the infos about all devices
        for (cl_device_id device : devices) {
            // CL_DEVICE_NAME
            String deviceName = getString(device, CL_DEVICE_NAME);
            LOGGER.log(Level.INFO, "--- Info for device "+deviceName+": ---");
            LOGGER.log(Level.INFO, "CL_DEVICE_NAME: \t\t\t{0}", deviceName);

            // CL_DEVICE_VENDOR
            String deviceVendor = getString(device, CL_DEVICE_VENDOR);
            LOGGER.log(Level.INFO, "CL_DEVICE_VENDOR: \t\t\t{0}", deviceVendor);

            // CL_DRIVER_VERSION
            String driverVersion = getString(device, CL_DRIVER_VERSION);
            LOGGER.log(Level.INFO, "CL_DRIVER_VERSION: \t\t\t{0}", driverVersion);

            // CL_DEVICE_TYPE
            long deviceType = getLong(device, CL_DEVICE_TYPE);
            if( (deviceType & CL_DEVICE_TYPE_CPU) != 0)
                LOGGER.log(Level.INFO, "CL_DEVICE_TYPE:\t\t\t\t{0}", "CL_DEVICE_TYPE_CPU");
            if( (deviceType & CL_DEVICE_TYPE_GPU) != 0)
                LOGGER.log(Level.INFO, "CL_DEVICE_TYPE:\t\t\t\t{0}", "CL_DEVICE_TYPE_GPU");
            if( (deviceType & CL_DEVICE_TYPE_ACCELERATOR) != 0)
                LOGGER.log(Level.INFO, "CL_DEVICE_TYPE:\t\t\t\t{0}", "CL_DEVICE_TYPE_ACCELERATOR");
            if( (deviceType & CL_DEVICE_TYPE_DEFAULT) != 0)
                LOGGER.log(Level.INFO, "CL_DEVICE_TYPE:\t\t\t\t{0}", "CL_DEVICE_TYPE_DEFAULT");

            // CL_DEVICE_MAX_COMPUTE_UNITS
            int maxComputeUnits = getInt(device, CL_DEVICE_MAX_COMPUTE_UNITS);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_COMPUTE_UNITS:\t\t{0}", maxComputeUnits);

            // CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS
            long maxWorkItemDimensions = getLong(device, CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS:\t{0}", maxWorkItemDimensions);

            // CL_DEVICE_MAX_WORK_ITEM_SIZES
            long maxWorkItemSizes[] = getSizes(device, CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_WORK_ITEM_SIZES:\t\t{0} / {1} / {2}", new Object[] {maxWorkItemSizes[0], maxWorkItemSizes[1], maxWorkItemSizes[2]});
            
            // CL_DEVICE_MAX_WORK_GROUP_SIZE
            long maxWorkGroupSize = getSize(device, CL_DEVICE_MAX_WORK_GROUP_SIZE);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_WORK_GROUP_SIZE:\t\t{0}", maxWorkGroupSize);

            // CL_DEVICE_MAX_CLOCK_FREQUENCY
            long maxClockFrequency = getLong(device, CL_DEVICE_MAX_CLOCK_FREQUENCY);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_CLOCK_FREQUENCY:\t\t{0} MHz", maxClockFrequency);

            // CL_DEVICE_ADDRESS_BITS
            int addressBits = getInt(device, CL_DEVICE_ADDRESS_BITS);
            LOGGER.log(Level.INFO, "CL_DEVICE_ADDRESS_BITS:\t\t\t{0}", addressBits);

            // CL_DEVICE_MAX_MEM_ALLOC_SIZE
            long maxMemAllocSize = getLong(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_MEM_ALLOC_SIZE:\t\t{0} MByte", (int)(maxMemAllocSize / (1024 * 1024)));

            // CL_DEVICE_GLOBAL_MEM_SIZE
            long globalMemSize = getLong(device, CL_DEVICE_GLOBAL_MEM_SIZE);
            LOGGER.log(Level.INFO, "CL_DEVICE_GLOBAL_MEM_SIZE:\t\t{0} MByte", (int)(globalMemSize / (1024 * 1024)));

            // CL_DEVICE_ERROR_CORRECTION_SUPPORT
            int errorCorrectionSupport = getInt(device, CL_DEVICE_ERROR_CORRECTION_SUPPORT);
            LOGGER.log(Level.INFO, "CL_DEVICE_ERROR_CORRECTION_SUPPORT:\t{0}", errorCorrectionSupport != 0 ? "yes" : "no");

            // CL_DEVICE_LOCAL_MEM_TYPE
            int localMemType = getInt(device, CL_DEVICE_LOCAL_MEM_TYPE);
            LOGGER.log(Level.INFO, "CL_DEVICE_LOCAL_MEM_TYPE:\t\t{0}", localMemType == 1 ? "local" : "global");

            // CL_DEVICE_LOCAL_MEM_SIZE
            long localMemSize = getLong(device, CL_DEVICE_LOCAL_MEM_SIZE);
            LOGGER.log(Level.INFO, "CL_DEVICE_LOCAL_MEM_SIZE:\t\t{0} KByte", (int)(localMemSize / 1024));

            // CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE
            long maxConstantBufferSize = getLong(device, CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE:\t{0} KByte", (int)(maxConstantBufferSize / 1024));

            // CL_DEVICE_QUEUE_PROPERTIES
            long queueProperties = getLong(device, CL_DEVICE_QUEUE_PROPERTIES);
            if(( queueProperties & CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE ) != 0)
                LOGGER.log(Level.INFO, "CL_DEVICE_QUEUE_PROPERTIES:\t\t{0}", "CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE");
            if(( queueProperties & CL_QUEUE_PROFILING_ENABLE ) != 0)
                LOGGER.log(Level.INFO, "CL_DEVICE_QUEUE_PROPERTIES:\t\t{0}", "CL_QUEUE_PROFILING_ENABLE");

            // CL_DEVICE_IMAGE_SUPPORT
            int imageSupport = getInt(device, CL_DEVICE_IMAGE_SUPPORT);
            LOGGER.log(Level.INFO, "CL_DEVICE_IMAGE_SUPPORT:\t\t{0}", imageSupport);

            // CL_DEVICE_MAX_READ_IMAGE_ARGS
            int maxReadImageArgs = getInt(device, CL_DEVICE_MAX_READ_IMAGE_ARGS);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_READ_IMAGE_ARGS:\t\t{0}", maxReadImageArgs);

            // CL_DEVICE_MAX_WRITE_IMAGE_ARGS
            int maxWriteImageArgs = getInt(device, CL_DEVICE_MAX_WRITE_IMAGE_ARGS);
            LOGGER.log(Level.INFO, "CL_DEVICE_MAX_WRITE_IMAGE_ARGS:\t\t{0}", maxWriteImageArgs);

            // CL_DEVICE_SINGLE_FP_CONFIG
            long singleFpConfig = getLong(device, CL_DEVICE_SINGLE_FP_CONFIG);
            LOGGER.log(Level.INFO, "CL_DEVICE_SINGLE_FP_CONFIG:\t\t{0}",
                    stringFor_cl_device_fp_config(singleFpConfig));
            
            // CL_DEVICE_IMAGE2D_MAX_WIDTH
            long image2dMaxWidth = getSize(device, CL_DEVICE_IMAGE2D_MAX_WIDTH);
            LOGGER.log(Level.INFO, "CL_DEVICE_2D_MAX_WIDTH\t\t\t{0}", image2dMaxWidth);

            // CL_DEVICE_IMAGE2D_MAX_HEIGHT
            long image2dMaxHeight = getSize(device, CL_DEVICE_IMAGE2D_MAX_HEIGHT);
            LOGGER.log(Level.INFO, "CL_DEVICE_2D_MAX_HEIGHT\t\t\t{0}", image2dMaxHeight);

            // CL_DEVICE_IMAGE3D_MAX_WIDTH
            long image3dMaxWidth = getSize(device, CL_DEVICE_IMAGE3D_MAX_WIDTH);
            LOGGER.log(Level.INFO, "CL_DEVICE_3D_MAX_WIDTH\t\t\t{0}", image3dMaxWidth);

            // CL_DEVICE_IMAGE3D_MAX_HEIGHT
            long image3dMaxHeight = getSize(device, CL_DEVICE_IMAGE3D_MAX_HEIGHT);
            LOGGER.log(Level.INFO, "CL_DEVICE_3D_MAX_HEIGHT\t\t\t{0}", image3dMaxHeight);

            // CL_DEVICE_IMAGE3D_MAX_DEPTH
            long image3dMaxDepth = getSize(device, CL_DEVICE_IMAGE3D_MAX_DEPTH);
            LOGGER.log(Level.INFO, "CL_DEVICE_3D_MAX_DEPTH\t\t\t{0}", image3dMaxDepth);

            // CL_DEVICE_PREFERRED_VECTOR_WIDTH_<type>
            LOGGER.log(Level.INFO, "CL_DEVICE_PREFERRED_VECTOR_WIDTH_<t>\t");
            int preferredVectorWidthChar = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR);
            int preferredVectorWidthShort = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT);
            int preferredVectorWidthInt = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT);
            int preferredVectorWidthLong = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG);
            int preferredVectorWidthFloat = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT);
            int preferredVectorWidthDouble = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE);
            
            LOGGER.log(Level.INFO, "CHAR {0}, SHORT {1}, INT {2}, LONG {3}, FLOAT {4}, DOUBLE {5}\n\n\n",
                    new Object[] {
                        preferredVectorWidthChar, preferredVectorWidthShort,
                        preferredVectorWidthInt, preferredVectorWidthLong,
                        preferredVectorWidthFloat, preferredVectorWidthDouble}
            );
        }
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static int getInt(cl_device_id device, int paramName)
    {
        return getInts(device, paramName, 1)[0];
    }

    /**
     * Returns the values of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @param numValues The number of values
     * @return The value
     */
    private static int[] getInts(cl_device_id device, int paramName, int numValues)
    {
        int values[] = new int[numValues];
        clGetDeviceInfo(device, paramName, Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static long getLong(cl_device_id device, int paramName)
    {
        return getLongs(device, paramName, 1)[0];
    }

    /**
     * Returns the values of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @param numValues The number of values
     * @return The value
     */
    private static long[] getLongs(cl_device_id device, int paramName, int numValues)
    {
        long values[] = new long[numValues];
        clGetDeviceInfo(device, paramName, Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(cl_device_id device, int paramName)
    {
        // Obtain the length of the string that will be queried
        long size[] = new long[1];
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte buffer[] = new byte[(int)size[0]];
        clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }

    /**
     * Returns the value of the platform info parameter with the given name
     *
     * @param platform The platform
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(cl_platform_id platform, int paramName)
    {
        // Obtain the length of the string that will be queried
        long size[] = new long[1];
        clGetPlatformInfo(platform, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte buffer[] = new byte[(int)size[0]];
        clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }
    
    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static long getSize(cl_device_id device, int paramName)
    {
        return getSizes(device, paramName, 1)[0];
    }
    
    /**
     * Returns the values of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @param numValues The number of values
     * @return The value
     */
    static long[] getSizes(cl_device_id device, int paramName, int numValues)
    {
        // The size of the returned data has to depend on 
        // the size of a size_t, which is handled here
        ByteBuffer buffer = ByteBuffer.allocate(
            numValues * Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues, 
            Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4)
        {
            for (int i=0; i<numValues; i++)
            {
                values[i] = buffer.getInt(i * Sizeof.size_t);
            }
        }
        else
        {
            for (int i=0; i<numValues; i++)
            {
                values[i] = buffer.getLong(i * Sizeof.size_t);
            }
        }
        return values;
    }
    
}