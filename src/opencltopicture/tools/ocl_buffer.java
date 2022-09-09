/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opencltopicture.tools;


import static opencltopicture.tools.OpenCLBase.checkError;
import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_mem;

/**
 *
 * @author durands
 */
public class ocl_buffer {
    public final static int SINGLE_CHANNEL_TYPE = /*CL.CL_A;*/ CL.CL_LUMINANCE;
    
    public cl_mem mem;
    public int w;

    public ocl_buffer() {
    }

    public ocl_buffer(float[] data) {
        this.w = data.length;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_WRITE | CL.CL_MEM_COPY_HOST_PTR, w * Sizeof.cl_float, Pointer.to(data), err);
        checkError(err[0]);
    }

    public ocl_buffer(float[] data, boolean readOnly) {
        this.w = data.length;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer((readOnly ? CL.CL_MEM_READ_ONLY : CL.CL_MEM_READ_WRITE) | CL.CL_MEM_COPY_HOST_PTR, w * Sizeof.cl_float, Pointer.to(data), err);
        checkError(err[0]);
    }
    
    public ocl_buffer(byte[] data, boolean readOnly) {
        this.w = data.length;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer((readOnly ? CL.CL_MEM_READ_ONLY : CL.CL_MEM_READ_WRITE) | CL.CL_MEM_COPY_HOST_PTR, w * Sizeof.cl_char, Pointer.to(data), err);
        checkError(err[0]);
    }
    
    public ocl_buffer(int[] data, boolean readOnly) {
        this.w = data.length;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer((readOnly ? CL.CL_MEM_READ_ONLY : CL.CL_MEM_READ_WRITE) | CL.CL_MEM_COPY_HOST_PTR, w * Sizeof.cl_int, Pointer.to(data), err);
        checkError(err[0]);
    }

    public ocl_buffer(int length, long flags) {
        this.w = length;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer(flags, w * Sizeof.cl_float, null, err);
        checkError(err[0]);
    }
    
    public ocl_buffer(int channel_order, int data_type, int w) {
        this.w = w;
        int sz = 4;
        if (data_type == CL.CL_UNSIGNED_INT8) {
            if (channel_order == CL.CL_RGBA) {
                sz = Sizeof.cl_uchar * 4;
            } else {
                sz = Sizeof.cl_uchar;
            }
        } else if (data_type == CL.CL_SIGNED_INT16) {
            sz = Sizeof.cl_ushort;
        } else if (data_type == CL.CL_SIGNED_INT32 && channel_order == CL.CL_RGBA) {
            sz = Sizeof.cl_int * 4;
        } else if (data_type == CL.CL_FLOAT && channel_order == CL.CL_RGBA) {
            sz = Sizeof.cl_float * 4;
        } else if (data_type == CL.CL_FLOAT && channel_order == SINGLE_CHANNEL_TYPE) {
            sz = Sizeof.cl_float;
        } else {
            assert (false);
        }

        int size = w * sz;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_WRITE, size, null, err);
    }
    
        
    public int getWidth() {
        return w;
    }
    
    static public ocl_buffer create1DFloatInput(final float[] data) {
        return new ocl_buffer(data, true);
    }

    static public ocl_buffer create1DFloatInput(final double[] data) {
        return new ocl_buffer(OpenCLBase.toFloat(data), true);
    }

    static public ocl_buffer create1DFloatInput(final Double[] data) {
        return new ocl_buffer(OpenCLBase.toFloat(data), true);
    }

    static public ocl_buffer create1DFloatOutput(final int length) {
        return new ocl_buffer(length, CL.CL_MEM_WRITE_ONLY);
    }

    static public ocl_buffer create1DFloatInputOutput(final float[] data) {
        return new ocl_buffer(data, false);
    }

    static public ocl_buffer create1DIntOutput(final int length) {
        final ocl_buffer buff = new ocl_buffer();
        buff.w = length;
        int[] err = {0};
        buff.mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_WRITE_ONLY, length * Sizeof.cl_int, null, err);
        checkError(err[0]);
        return buff;
    }

    static public ocl_buffer create1DInt4InputOutput(final int length) {
        final ocl_buffer buff = new ocl_buffer();
        buff.w = length;
        int[] err = {0};
        buff.mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_WRITE, length * Sizeof.cl_int4, null, err);
        checkError(err[0]);
        return buff;
    }

    static public ocl_buffer create1DUShort4InputOutput(final int length) {
        final ocl_buffer buff = new ocl_buffer();
        buff.w = length;
        int[] err = {0};
        buff.mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_WRITE, length * Sizeof.cl_ushort4, null, err);
        checkError(err[0]);
        return buff;
    }

    public ocl_buffer(final ocl_image img, final long flags) {
        this.w = img.w;
        int[] err = {0};
        mem = OpenCLBase.clCreateBuffer(flags, w * Sizeof.cl_float, null, err);
        checkError(err[0]);
        OpenCLBase.copy_to_buf(img, this);
    }

    /**
     * Met a jour les donnees du buffer a partir de l'image
     *
     * @param img
     * @return
     */
    public boolean updateFromFloatImg(final ocl_image img) {
        if (img != null
                && img.image_format.image_channel_order == CL.CL_INTENSITY    // TODO s'adapter aux autres taille
                && img.image_format.image_channel_data_type == CL.CL_FLOAT) { // TODO s'adapter aux autres types
            if (mem != null && img.w * img.h == w) {
                return OpenCLBase.copy_to_buf(img, this);
            } else {
                if (mem != null) {
                    release();
                }
                this.w = img.w;
                int[] err = {0};
                mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_WRITE, w * Sizeof.cl_float, null, err);
                checkError(err[0]);
                return OpenCLBase.copy_to_buf(img, this);
            }
        }
        return false;
    }

    /**
     * Met a jour les donnees sur le GPU
     *
     * @param buff
     * @param sz
     * @return
     */
    public boolean updateFloat(final float[] buff) {
        // Si on peu on reutilise le meme cl_mem
        if (buff.length == w && mem != null) {
            // On remplace les donnees sans recreer le buffer
            int res = OpenCLBase.copy_to_buf(buff, mem);
        } else {
            // on va devoir le recreer car il n'est pas a la bonne dimension
            release();
            w = buff.length;
            int[] err = {0};
            mem = OpenCLBase.clCreateBuffer(CL.CL_MEM_READ_ONLY, w * Sizeof.cl_float, null, err);
            checkError(err[0]);
        }
        return true;
    }

    public void release() {
        if (mem != null) {
            try {
                int result = CL.clReleaseMemObject(mem);
            } catch (CLException e) {
                // Release deja effectué
                int error = 1;
            }
            mem = null;
        }
    }

}
