/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opencltopicture.tools;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.Charsets;
import org.jocl.CL;
import static org.jocl.CL.clGetDeviceInfo;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_event;
import org.jocl.cl_image_desc;
import org.jocl.cl_image_format;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author durands
 */
public class OpenCLBase {

    private static final Logger LOGGER = Logger.getLogger(OpenCLBase.class.getName());

    public final static int SINGLE_CHANNEL_ORDER = CL.CL_LUMINANCE; //CL_A; //LUMINANCE;

    private static cl_command_queue command_queue;
    private static cl_context context;

    // Lite des kernels chargés
    protected static Map<String, cl_kernel> mapKernels = new HashMap<>();

    // Indique si OpenCL est displonible sur la machine 
    private static final boolean IS_OPENCL_AVAILABLE = initIsOpenCLAvailable();

    public static cl_program buildProgramFromFile(final String source_path, final String options) throws IOException {
        URL url = OpenCLBase.class.getClassLoader().getResource(source_path);
        return buildProgramFromUrl(url, options);
    }

    public static cl_program buildProgramFromExtFile(final String source_path, final String options) throws IOException {
        FileInputStream inputStream = new FileInputStream(source_path);
        if (source_path.endsWith(".bin")) {
            return buildProgramFromBinary(IOUtils.toByteArray(inputStream), options);
        } else {
            return buildProgramFromSource(IOUtils.toString(inputStream), options);
        }
    }

    static byte[] toByteArray(URL url) throws IOException {
        byte[] imageBytes = null;
        InputStream is = null;
        try {
            is = url.openStream();
            imageBytes = IOUtils.toByteArray(is);
        } catch (IOException e) {
            System.err.printf("Failed while reading bytes from %s: %s", url.toExternalForm(), e.getMessage());
            // Perform any other exception handling that's appropriate.
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return imageBytes;
    }

    private static cl_program buildProgramFromUrl(final URL url, final String options) throws IOException {
        if (url.getFile().endsWith(".bin")) {
            return buildProgramFromBinary(toByteArray(url), options);
        } else {
            return buildProgramFromSource(new String(toByteArray(url), Charsets.UTF_8), options);
        }
    }

    private static cl_program buildProgramFromBinary(final byte[] source, final String options) {
        if (source == null) {
            return null;
        }
        int err = 0;
        int[] binary_status = {0};
        OpenCLDeviceQuery.Device device = getBestDevice();
        cl_program program = CL.clCreateProgramWithBinary(context, 1, new cl_device_id[]{device.device_id}, new long[]{source.length}, new byte[][]{source}, binary_status, new int[]{err});
        try {
            err = CL.clBuildProgram(program, 0, null, (options == null ? "" : options) + /*" -cl-nv-verbose*/ " -cl-mad-enable -cl-fast-relaxed-math", null, new int[]{err});
            if (err == CL.CL_BUILD_PROGRAM_FAILURE) {
                LOGGER.warning(OpenCLBase.getErrorMessage(program));
            }
            if (checkError(err)) {
                return null;
            }
        } catch (CLException e) {
            LOGGER.severe(e.getMessage());
            return null;
        }
        return program;
    }

    public static String getErrorMessage(final cl_program program) {
        // Determine the size of the log
        long[] log_size = {0};
        CL.clGetProgramBuildInfo(program, (null), CL.CL_PROGRAM_BUILD_LOG, 0, null, log_size);
        // Allocate memory for the log
        byte buffer[] = new byte[(int) log_size[0]];
        CL.clGetProgramBuildInfo(program, (null), CL.CL_PROGRAM_BUILD_LOG, log_size[0], Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length - 1);
    }

    private static cl_program buildProgramFromSource(final String source, final String options) {
        if (source == null) {
            return null;
        }
        int err = 0;
        // Create OpenCL program with source code
        cl_program program = CL.clCreateProgramWithSource(context, 1, new String[]{source}, null, new int[]{err});
        try {
            // Build the program (OpenCL JIT compilation)
            //   "-D "
            err = CL.clBuildProgram(program, 0, null, (options == null ? "" : options) + /*" -cl-nv-verbose*/ " -cl-mad-enable -cl-fast-relaxed-math", null, new int[]{err});
            if (err == CL.CL_BUILD_PROGRAM_FAILURE) {
                LOGGER.warning(OpenCLBase.getErrorMessage(program));
            }
            if (checkError(err)) {
                return null;
            }
        } catch (CLException e) {
            LOGGER.severe(e.getMessage());
            return null;
        }
        return program;
    }

    protected static double elapsedTimeInSeconds(cl_event event) {
        int err;
        long[] start = {0};
        long[] end = {0};
        double t = 0;
        //    err = CL.clGetEventProfilingInfo(event, CL.CL_PROFILING_COMMAND_START, Sizeof.cl_ulong, Pointer.to(start), null);
        //    err = CL.clGetEventProfilingInfo(event, CL.CL_PROFILING_COMMAND_END, Sizeof.cl_ulong, Pointer.to(start), null);
        // end & start are in nanoseconds
        t = ((double) end[0] - (double) start[0]) * (1.0e-9);
        return t;
    }

    public static void query_picture(ocl_image clTextureOutput, cl_event[] event, BufferedImage imgOut) {
        final int[] imgPixels = ((DataBufferInt) imgOut.getRaster().getDataBuffer()).getData();
        query_int(clTextureOutput, event, imgPixels);
    }

    public static cl_kernel getKernel(String kernelName) {
        return mapKernels.get(kernelName);
    }

    public static void releaseAll() {
        for (cl_kernel kernel : mapKernels.values()) {
            CL.clReleaseKernel(kernel);
        }
        CL.clReleaseCommandQueue(command_queue);
        CL.clReleaseContext(context);
    }

    public static byte[] query_byte(final ocl_buffer buff, final cl_event[] event) {
        int err = CL.CL_SUCCESS;
        byte[] data = new byte[buff.w];
        err = CL.clEnqueueReadBuffer(command_queue, buff.mem, CL.CL_TRUE, 0, buff.w * Sizeof.cl_char, Pointer.to(data), event == null ? 0 : event.length, event, null);
        return data;
    }

    public static float[] query_float(ocl_buffer buff, cl_event[] event) {
        int err = CL.CL_SUCCESS;
        float[] data = new float[buff.w];
        err = CL.clEnqueueReadBuffer(command_queue, buff.mem, CL.CL_TRUE, 0, buff.w * Sizeof.cl_float, Pointer.to(data), event == null ? 0 : event.length, event, null);
        return data;
    }

    public static float[] query_float(ocl_buffer buff, float[] data, cl_event[] event) {
        int err = CL.CL_SUCCESS;
        err = CL.clEnqueueReadBuffer(command_queue, buff.mem, CL.CL_TRUE, 0, buff.w * Sizeof.cl_float, Pointer.to(data), event == null ? 0 : event.length, event, null);
        return data;
    }

    public static int[] query_int(ocl_buffer buff, cl_event[] event) {
        int err = CL.CL_SUCCESS;
        int[] data = new int[buff.w];
        err = CL.clEnqueueReadBuffer(command_queue, buff.mem, CL.CL_TRUE, 0, buff.w * Sizeof.cl_int, Pointer.to(data), event == null ? 0 : event.length, event, null);
        return data;
    }

    public static int clEnqueueReadImage(cl_mem image, boolean blocking_read, long[] origin, long[] region, long row_pitch, long slice_pitch, Pointer ptr, int num_events_in_wait_list, cl_event[] event_wait_list, cl_event event) {
        return CL.clEnqueueReadImage(command_queue, image, blocking_read, origin, region, row_pitch, slice_pitch, ptr, num_events_in_wait_list, event_wait_list, event);
    }

    public static float[] query_float(ocl_image img, cl_event[] event) {
        float[] data = new float[img.w * img.h];
        int result = CL.clEnqueueReadImage(command_queue, img.mem, true, new long[]{0, 0, 0}, new long[]{img.w, img.h, 1}, img.w, 0, Pointer.to(data), event == null ? 0 : event.length, event, null);
        if (checkError(result)) {
            return null;
        }
        return data;
    }

    public static float[] query_float4(final ocl_image img, final cl_event[] event) {
        float[] data = new float[img.w * img.h * 4];
        int result = CL.clEnqueueReadImage(command_queue, img.mem, true, new long[]{0, 0, 0}, new long[]{img.w, img.h, 1}, img.w * Sizeof.cl_float4, 0, Pointer.to(data), event == null ? 0 : event.length, event, null);
        if (checkError(result)) {
            return null;
        }
        return data;
    }

    public static boolean query_int(final ocl_image img, final cl_event[] event, final int[] buffer) {
        int result = CL.clEnqueueReadImage(command_queue, img.mem, true, new long[]{0, 0, 0}, new long[]{img.w, img.h, 1}, img.w * Sizeof.cl_uint, 0, Pointer.to(buffer), event != null ? event.length : 0, event, null);
        return checkError(result);
    }

    public static boolean copy_to_buf(final ocl_buffer buf, final ocl_buffer dst, final cl_event[] event) {
        if (dst.w < buf.w) {
            dst.release();
            int[] err = {0};
            dst.mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_WRITE, buf.w * Sizeof.cl_float, null, err);
            dst.w = buf.w;
        }
        int result = CL.clEnqueueCopyBuffer(command_queue, buf.mem, dst.mem, 0, 0, buf.w * Sizeof.cl_float, event != null ? event.length : 0, event, null);
        return checkError(result);
    }

    public static boolean copy_to_img(final ocl_buffer buf, final ocl_image img, final cl_event[] event) {
        int result = CL.clEnqueueCopyBufferToImage(command_queue, buf.mem, img.mem, 0, new long[]{0, 0, 0}, new long[]{img.w, img.h, img.z}, event != null ? event.length : 0, event, null);
        return checkError(result);
    }

    public static boolean copy_to_buf(final ocl_image img, final ocl_buffer buf) {
        cl_event[] event = null;
        int result = CL.clEnqueueCopyImageToBuffer(command_queue, img.mem, buf.mem, new long[]{0, 0, 0}, new long[]{img.w, img.h, img.z}, 0, event != null ? event.length : 0, event, null);
        return checkError(result);
    }

    private static long[] getLongs(final cl_device_id device, final int paramName, final int numValues) {
        long values[] = new long[numValues];
        clGetDeviceInfo(device, paramName, Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }

    private static long getLong(final cl_device_id device, final int paramName) {
        return getLongs(device, paramName, 1)[0];
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(final cl_device_id device, final int paramName) {
        // Obtain the length of the string that will be queried
        long size[] = new long[1];
        CL.clGetDeviceInfo(device, paramName, 0, null, size);
        // Create a buffer of the appropriate size and fill it with the info
        byte buffer[] = new byte[(int) size[0]];
        CL.clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);
        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length - 1);
    }

    public static boolean createKernels(final String... clFileName) throws IOException {
        // TODO memorizer pour ne pas recompiler plusieurs fois le meme programme
        opencl_init();
        for (String file : clFileName) {
            cl_program program = OpenCLBase.buildProgramFromFile(file, "");
            if (program == null || !OpenCLBase.createKernels(program)) {
                return false;
            }
        }
        return true;
    }

    public static boolean createKernelsFromSource(final String source) {
        opencl_init(); // Au cas où
        cl_program program = buildProgramFromSource(source, "");
        return !(program == null || !OpenCLBase.createKernels(program));
    }

    public static boolean createKernels(final URL... urls) throws IOException {
        opencl_init();
        for (URL url : urls) {
            cl_program program = OpenCLBase.buildProgramFromUrl(url, "");
            if (program == null || !OpenCLBase.createKernels(program)) {
                return false;
            }
        }
        return true;
    }

    private static boolean createKernels(final cl_program... programs) {
        // Create the kernel
        // mapKernels.clear();
        cl_kernel[] allKernels = new cl_kernel[30];
        for (cl_program program : programs) {
            int[] nbKernelsArr = {0};
            int result = CL.clCreateKernelsInProgram(program, 30, allKernels, nbKernelsArr);
            if (result != CL.CL_SUCCESS) {
                return false;
            }
            int nbKernels = nbKernelsArr[0];

            for (int i = 0; i < nbKernels; i++) {
                long size[] = new long[1];
                CL.clGetKernelInfo(allKernels[i], CL.CL_KERNEL_FUNCTION_NAME, 0, null, size);
                byte buffer[] = new byte[(int) size[0]];
                CL.clGetKernelInfo(allKernels[i], CL.CL_KERNEL_FUNCTION_NAME, buffer.length, Pointer.to(buffer), null);
                String name = new String(buffer, 0, buffer.length - 1);
                mapKernels.put(name, allKernels[i]);
            }
        }

        //      kernel = mapKernels.get("render");
        return true;
    }

    public static void opencl_init() {
        if (command_queue != null) {
            return;
        }

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        //CL_DEVICE_TYPE_GPU
        OpenCLDeviceQuery.Device device = getBestDevice();
        if (device != null) {
            // Initialize the context properties
            cl_context_properties contextProperties = new cl_context_properties();
            contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM, device.platform_id);

            displayDevice(device.device_id);

            // Create a context for the selected device
            context = CL.clCreateContext(contextProperties, 1, new cl_device_id[]{device.device_id}, null, null, null);

            // Create a command-queue
            cl_queue_properties clProperties = new cl_queue_properties();
            //    clProperties.addProperty(CL.CL_QUEUE_PROFILING_ENABLE, 1l);
            //   clProperties.addProperty(CL.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, 1l);
            int err = 0;
            command_queue = CL.clCreateCommandQueueWithProperties(context, device.device_id, clProperties, new int[]{err});
        }
    }

    private static OpenCLDeviceQuery.Device getBestDevice() {
        OpenCLDeviceQuery.Device device = null;
        for (OpenCLDeviceQuery.Device dev : OpenCLDeviceQuery.getDevices()) {
            String deviceVendor = getString(dev.device_id, CL.CL_DEVICE_VENDOR);
            if (deviceVendor.contains("NVIDIA")) {
                device = dev;
                break;
            }

            if ((getLong(dev.device_id, CL.CL_DEVICE_TYPE) & CL.CL_DEVICE_TYPE_GPU) != 0) {
                device = dev;
            }
        }
        return device;
    }

    /**
     * Affiche les info d'un device
     *
     * @param device_id
     */
    private static void displayDevice(final cl_device_id device_id) {
        final String //
                deviceName = getString(device_id, CL.CL_DEVICE_NAME),
                deviceVendor = getString(device_id, CL.CL_DEVICE_VENDOR),
                driverVersion = getString(device_id, CL.CL_DRIVER_VERSION);

        LOGGER.log(Level.INFO, "--- Info for device {0}: ---", deviceName);
        LOGGER.log(Level.INFO, "CL_DEVICE_NAME: \t\t\t{0}", deviceName);
        LOGGER.log(Level.INFO, "CL_DEVICE_VENDOR: \t\t\t{0}", deviceVendor);
        LOGGER.log(Level.INFO, "CL_DRIVER_VERSION: \t\t\t{0}", driverVersion);
    }

    /**
     * Affecte une liste d'arguments a une fonction kernel
     *
     * @param k
     * @param params
     */
    public static void args(final cl_kernel k, final Object... params) {
        int id = 0;
        for (Object p : params) {
            //       System.out.println("setting param: " + id);
            if (p instanceof cl_mem) {
                CL.clSetKernelArg(k, id++, Sizeof.cl_mem, Pointer.to((cl_mem) p));
            } else if (p instanceof ocl_image) {
                CL.clSetKernelArg(k, id++, Sizeof.cl_mem, Pointer.to(((ocl_image) p).mem));
            } else if (p instanceof ocl_buffer) {
                if (((ocl_buffer) p).mem == null) {
                    int test = 1;
                }
                CL.clSetKernelArg(k, id++, Sizeof.cl_mem, Pointer.to(((ocl_buffer) p).mem));
            } else if (p instanceof cl_mem) {
                CL.clSetKernelArg(k, id++, Sizeof.cl_mem, Pointer.to((cl_mem) p));
            } else if (p instanceof Integer) {
                CL.clSetKernelArg(k, id++, Sizeof.cl_int, Pointer.to(new int[]{((Integer) p)}));
            } else if (p instanceof Float) {
                float[] fp = {(float) p};
                CL.clSetKernelArg(k, id++, Sizeof.cl_float, Pointer.to(fp));
            } else if (p instanceof Double) {
                float[] fp = {((Double) p).floatValue()};
                CL.clSetKernelArg(k, id++, Sizeof.cl_float, Pointer.to(fp));
            } else if (p instanceof double[]) {
                float[] fp = toFloat((double[]) p);
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_float * fp.length, Pointer.to(fp));
            } else if (p instanceof Double[]) {
                float[] fp = toFloat((Double[]) p);
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_float * fp.length, Pointer.to(fp));
            } else if (p instanceof float[]) {
                float[] fp = (float[]) p;
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_float * fp.length, Pointer.to(fp));
            } else if (p instanceof int[]) {
                int[] ip = (int[]) p;
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_int * ip.length, Pointer.to(ip));
            } else if (p instanceof Color) {
                Color c = (Color) p;
                float[] color = new float[]{(float) (c.getRed()) / 255.f, (float) (c.getGreen()) / 255.f, (float) (c.getBlue()) / 255.f, (float) (c.getAlpha()) / 255.f};
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_float * 4, Pointer.to(color));
            } else if (p instanceof Boolean) {
                int[] fp = {((Boolean) p) ? 1 : 0};
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_int, Pointer.to(fp));
            } else if (p instanceof Point2D) {
                Point2D pt = (Point2D) p;
                float[] fp = new float[]{(float) pt.getX(), (float) pt.getY()};
                int res = CL.clSetKernelArg(k, id++, Sizeof.cl_float * fp.length, Pointer.to(fp));
            }
        }
    }

    public static float[] toFloat(final double[] d) {
        final float[] f = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            f[i] = (float) (d[i]);
        }
        return f;
    }

    public static float[] toFloat(final Double[] d) {
        final float[] f = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            f[i] = d[i].floatValue();
        }
        return f;
    }

    public static boolean checkError(int err) {
        if (err != CL.CL_SUCCESS) {
            LOGGER.log(Level.SEVERE, "ERROR :{0} - {1}", new Object[]{err, CL.stringFor_errorCode(err)});
            return true;
        }
        return false;
    }

    public static boolean runWithArgs(String kernelName, int nbDim, long[] global_offset, long[] global_sz, long[] local_sz, cl_event[][] event, Object... args) {
        return runWithArgs(mapKernels.get(kernelName), nbDim, global_offset, global_sz, local_sz, event, args);
    }

    public static boolean runWithArgs(cl_kernel kernel, int nbDim, long[] global_offset, long[] global_sz, long[] local_sz, cl_event[][] event, Object... args) {
        if (kernel == null) {
            return false;
        }
        args(kernel, args);
        return run(kernel, nbDim, global_offset, global_sz, local_sz, event);
    }

    /**
     * Pour adapter facilement du code OpenCV
     *
     * @param kernel
     * @param nbDim
     * @param global_sz
     * @param local_sz
     * @param b
     * @param event
     * @return
     */
    private static boolean run(cl_kernel kernel, int nbDim, long[] global_offset, long[] global_sz, long[] local_sz, cl_event[][] event) {
        cl_event event2 = new cl_event();
        int err = CL.clEnqueueNDRangeKernel(command_queue, kernel, nbDim, global_offset, global_sz, local_sz, event[0] != null ? event[0].length : 0, event[0], event2);
        // TODO: voir comment faire un release de l'evenement precedent sans qu'il soit completement perdu pour les parents
        //if (event[0] != null) {
        //    for(cl_event evt:event[0]) {
        //        CL.clReleaseEvent(evt);
        //    }
        //}
        event[0] = new cl_event[]{event2};
        return !checkError(err);
    }

    private static boolean run(final cl_kernel kernel, final long[] global_sz, final cl_event[][] event) {
        return run(kernel, global_sz.length, null, global_sz, null, event);
    }

    private static boolean run(String kernelName, long[] global_sz, cl_event[][] event) {
        return run(mapKernels.get(kernelName), global_sz, event);
    }

    /**
     * Pour simplifier l'ecriture des fonctions d'appel
     *
     * @param kernelName
     * @param global_sz
     * @param local_sz
     * @param event
     * @param args
     * @return
     */
    public static boolean runWithArgsOnBBox(String kernelName, int[] global_bbox, cl_event[][] event, Object... args) {
        return runWithArgs(mapKernels.get(kernelName), new long[]{global_bbox[0], global_bbox[1]},
                new long[]{global_bbox[2] - global_bbox[0], global_bbox[3] - global_bbox[1]}, event, args);
    }

    public static boolean runWithArgs(String kernelName, long[] global_sz, cl_event[][] event, Object... args) {
        try {
            return runWithArgs(mapKernels.get(kernelName), null, global_sz, event, args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, kernelName + " runWithArgs: ", e);
            return false;
        }
    }

    public static cl_mem clCreateBuffer(long flags, long size, Pointer host_ptr, int[] errcode_ret) {
        return CL.clCreateBuffer(context, flags, size, host_ptr, errcode_ret);
    }

    public static cl_mem clCreateImage(long flags, cl_image_format image_format, cl_image_desc image_desc, Pointer host_ptr, int[] errcode_ret) {
        return CL.clCreateImage(context, flags, image_format, image_desc, host_ptr, errcode_ret);
    }

    public static int clEnqueueWriteImage(cl_mem image, boolean blocking_write, long[] origin, long[] region, long input_row_pitch, long input_slice_pitch, Pointer ptr, int num_events_in_wait_list, cl_event[] event_wait_list, cl_event event) {
        return CL.clEnqueueWriteImage(command_queue, image, blocking_write, origin, region, input_row_pitch, input_slice_pitch, ptr, num_events_in_wait_list, event_wait_list, event);
    }

    public static int copy_to_buf(final float[] buff, final cl_mem mem) {
        return CL.clEnqueueWriteBuffer(command_queue, mem, true, 0, buff.length * Sizeof.cl_float, Pointer.to(buff), 0, null, null);
    }

    private static boolean runWithArgs(cl_kernel kernel, long[] global_offset, long[] global_sz, cl_event[][] event, Object... args) {
        if (kernel == null) {
            return false;
        }
        args(kernel, args);
        return run(kernel, global_sz.length, global_offset, global_sz, null, event);
    }

    private static boolean initIsOpenCLAvailable() {
        try {
            String st = CL.stringFor_errorCode(CL.CL_BUILD_SUCCESS);
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    /**
     * Indique si OpenCL est displonible sur la machine
     *
     * @return
     */
    public static boolean isOpenCLAvailable() {
        return IS_OPENCL_AVAILABLE;
    }

    /**
     * Returns the binaries for this program in an ordered Map containing the
     * device as key and the program binaries as value.
     */
    public static String[] getBinaries(int numDevices, cl_program program) {

        // Obtain the length of the binary data that will be queried, for each device
        long binaryDataSizes[] = new long[numDevices];
        int ret = CL.clGetProgramInfo(program, CL.CL_PROGRAM_BINARY_SIZES, numDevices * Sizeof.size_t, Pointer.to(binaryDataSizes), null);
        if (checkError(ret)) {
            return null;
        }

        // Allocate arrays that will store the binary data, each
        // with the appropriate size
        byte binaryDatas[][] = new byte[numDevices][];
        for (int i = 0; i < numDevices; i++) {
            int binaryDataSize = (int) binaryDataSizes[i];
            binaryDatas[i] = new byte[binaryDataSize];
        }

        // Create a pointer to an array of pointers which are pointing
        // to the binary data arrays
        Pointer binaryDataPointers[] = new Pointer[numDevices];
        for (int i = 0; i < numDevices; i++) {
            binaryDataPointers[0] = Pointer.to(binaryDatas[i]);
        }

        // Query the binary data
        Pointer pointerToBinaryDataPointers = Pointer.to(binaryDataPointers);
        ret = CL.clGetProgramInfo(program, CL.CL_PROGRAM_BINARIES, numDevices * Sizeof.POINTER, pointerToBinaryDataPointers, null);
        if (checkError(ret)) {
            return null;
        }

        final String[] binaries = new String[numDevices];
        // Print the binary data (for NVIDIA, this is the PTX data)
        for (int i = 0; i < binaryDatas.length; i++) {
            binaries[i] = new String(binaryDatas[i]);
        }
        return binaries;
    }
}
