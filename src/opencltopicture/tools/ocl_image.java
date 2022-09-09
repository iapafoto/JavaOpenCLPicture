/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opencltopicture.tools;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.lang.ref.Cleaner;
import static opencltopicture.tools.OpenCLBase.SINGLE_CHANNEL_ORDER;
import static opencltopicture.tools.OpenCLBase.checkError;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_event;
import org.jocl.cl_image_desc;
import org.jocl.cl_image_format;
import org.jocl.cl_mem;

/**
 *
 * @author durands
 */
public class ocl_image {

    private final static Cleaner cleaner = Cleaner.create();

    protected int w, h, z;
    protected cl_mem mem;
    
    protected cl_image_desc image_desc = new cl_image_desc();
    public cl_image_format image_format = new cl_image_format();

    public ocl_image(long memflag, int data_type, int channel_order, int w, int h, Pointer ptr) {
        init(w, h, data_type, channel_order, ptr, memflag);
    }

    public ocl_image(float[] data, int... size) {
        init(data, size);
    }

    public ocl_image(BufferedImage img, boolean readOnly) {
        w = img.getWidth();
        h = img.getHeight();
        z = 1;
        image_format = Images2D.createImageFormatFrom(img);
        image_desc = Images2D.createImageDescFrom(img);
        createImage((readOnly ? CL.CL_MEM_READ_ONLY : CL.CL_MEM_READ_WRITE) | CL.CL_MEM_COPY_HOST_PTR, image_format, image_desc, Images2D.createRasterDataPointer(img));
    }

    private void init(float[] data, int... size) {

        image_format = new cl_image_format();
        image_format.image_channel_order = CL.CL_INTENSITY;
        image_format.image_channel_data_type = CL.CL_FLOAT;

        w = size[0];
        h = size[1];

        // Create the memory object for the input image
        image_desc = new cl_image_desc();

        if (size.length == 3) { // 3D
            z = size[2];
            image_desc.image_type = CL.CL_MEM_OBJECT_IMAGE3D;
            image_desc.image_width = w;
            image_desc.image_height = h;
            image_desc.image_depth = z;
            image_desc.image_array_size = 1;
            image_desc.image_row_pitch = w * Sizeof.cl_float;
            image_desc.image_slice_pitch = w * h * Sizeof.cl_float;
            image_desc.num_mip_levels = 0;
            image_desc.num_samples = 0;
            image_desc.buffer = null;
        } else { // 2D
            z = 1;
            image_desc.image_type = CL.CL_MEM_OBJECT_IMAGE2D;
            image_desc.image_width = w;
            image_desc.image_height = h;
            image_desc.image_array_size = 1;
            image_desc.image_row_pitch = 0;
        }
        createImage(CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR/*CL_MEM_USE_HOST_PTR*/, image_format, image_desc, Pointer.to(data));
    }

    protected void init(int w1, int h1, int data_type, int channel_order, Pointer ptr, long memflag) {
        this.w = w1;
        this.h = h1;
        this.z = 1;
        int sz = 4;

        // TODO voir si pas deja dans Image2D
        if (data_type == CL.CL_UNSIGNED_INT8) {
            if (channel_order == CL.CL_RGBA) {
                sz = Sizeof.cl_uint8 * 4;
            } else {
                sz = Sizeof.cl_uint8;
            }
        } else if (data_type == CL.CL_SIGNED_INT16) {
            sz = Sizeof.cl_ushort;
        } else if (data_type == CL.CL_SIGNED_INT32 && channel_order == CL.CL_RGBA) {
            sz = Sizeof.cl_int * 4;
        } else if (data_type == CL.CL_FLOAT && channel_order == CL.CL_RGBA) {
            sz = Sizeof.cl_float * 4;
        } else if (data_type == CL.CL_FLOAT && (channel_order == OpenCLBase.SINGLE_CHANNEL_ORDER || channel_order == CL.CL_INTENSITY)) {
            sz = Sizeof.cl_float;
        } else {
            assert (false);
        }

        image_format.image_channel_data_type = data_type;
        image_format.image_channel_order = channel_order;
        image_desc.image_type = CL.CL_MEM_OBJECT_IMAGE2D;
        image_desc.image_width = w1;
        image_desc.image_height = h1;
        image_desc.image_array_size = 1;
        image_desc.image_row_pitch = ptr == null ? 0 : w1 * sz;
        image_desc.buffer = null;

        createImage(memflag, image_format, image_desc, ptr);
    }

    public cl_mem createImage(long flags, cl_image_format image_format, cl_image_desc image_desc, Pointer host_ptr) {
        if (mem != null) {
            release();
        }
        try {
            int[] err = {0};
            mem = OpenCLBase.clCreateImage(flags, image_format, image_desc, host_ptr, err);
            if (checkError(err[0])) {
                int error = 1;
                mem = null;
            } else {
                cleaner.register(this, () -> {
                    if (mem != null) {
                        release();
                    }
                });
            }
        } catch (Exception e) {
            mem = null;
        }
        return mem;
    }

    public cl_mem getMem() {
        return mem;
    }
    
    
    /**
     * Met a jour les donnees
     *
     * @param buff
     * @param sz
     * @return
     */
    public boolean updateFloat(final float[] buff, final int... sz) {
        if (image_format.image_channel_order != CL.CL_INTENSITY || image_format.image_channel_data_type != CL.CL_FLOAT) {
            return false;
        }
        // Si on peu on reutilise le meme cl_mem
        if (sz.length == 3 && sz[0] == w && sz[1] == h && sz[2] == z) {
            // On remplace les donnees sans recreer le buffer
            OpenCLBase.clEnqueueWriteImage(mem, true, new long[]{0,0,0}, new long[]{sz[0], sz[1], sz[2]}, sz[0] * Sizeof.cl_float, sz[0] * sz[1] * Sizeof.cl_float, Pointer.to(buff), 0, null, null);

        } else if (sz.length == 2 && sz[0] == w && sz[1] == h) {
            // On remplace les donnees sans recreer le buffer
            OpenCLBase.clEnqueueWriteImage(mem, true, new long[]{0,0,0}, new long[]{sz[0],sz[1],1}, sz[0] * Sizeof.cl_float, 0, Pointer.to(buff), 0, null, null);
        
        } else {
            // on va devoir le recreer car il n'est pas a la bonne dimension
            release();
            init(buff, sz);
        }
        return true;
    }

    // libere la memoire sur le GPU
    public void release() {
        if (mem != null) {
            try {
                CL.clReleaseMemObject(mem);
                mem = null; 
            } catch (Exception e) {
            }
        }
    }

    public static ocl_image create2DInputInt32(final int w, final int h) {
        return new ocl_image(CL.CL_MEM_WRITE_ONLY, CL.CL_UNSIGNED_INT32, CL.CL_INTENSITY, w, h, null);
    }

    public static ocl_image create2DOutputPicture(final int w, final int h) {
        return new ocl_image(CL.CL_MEM_WRITE_ONLY, CL.CL_UNSIGNED_INT8, CL.CL_RGBA, w, h, null);
    }

    public static ocl_image create2DInputOutputPicure(final int w, final int h) {
        return new ocl_image(CL.CL_MEM_READ_WRITE, CL.CL_UNSIGNED_INT8, CL.CL_RGBA, w, h, null);
    }

    public static ocl_image create2DInputOutputFloat(final int w, final int h) {
        return new ocl_image(CL.CL_MEM_READ_WRITE, CL.CL_FLOAT, CL.CL_INTENSITY, w, h, null);
    }

    public static ocl_image create2DInputOutputPicure(final BufferedImage img) {
        return new ocl_image(img, false);
    }

    public static ocl_image create2DInputPicure(final BufferedImage img) {
        return new ocl_image(img, true);
    }

    public int getWidth() {
        return w;
    }
    
    public int getHeight() {
        return h;
    }

    public BufferedImage query_img() {
        if (image_format.image_channel_order == SINGLE_CHANNEL_ORDER) {
            switch (image_format.image_channel_data_type) {
                case CL.CL_FLOAT:{
                    cl_event[][] event = {null};
                    ocl_image img_out = new ocl_image(CL.CL_MEM_READ_WRITE, CL.CL_UNSIGNED_INT8, SINGLE_CHANNEL_ORDER, w, h, null);
                    //query_float(ocl_image img, cl_event[] event)
                    int res = convertToByte(mem, img_out.mem, w, h, 0.f, 1.f, event);
                   /// img_out.release();
                    BufferedImage result = img_out.query_img();
                    img_out.release();
                    return result;
                }
                case CL.CL_UNSIGNED_INT8: {
                    BufferedImage imgout = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
                    DataBufferByte dataBufferDst = (DataBufferByte) imgout.getRaster().getDataBuffer();
                    byte dataDst[] = dataBufferDst.getData();
                    int result = OpenCLBase.clEnqueueReadImage(mem, true, new long[]{0, 0, 0}, new long[]{w, h, 1}, w, 0, Pointer.to(dataDst), 0, null, null);
                    return imgout;
                }
                case CL.CL_UNSIGNED_INT16: {
                    BufferedImage imgout = new BufferedImage(w * 2, h, BufferedImage.TYPE_BYTE_GRAY);
                    DataBufferByte dataBufferDst = (DataBufferByte) imgout.getRaster().getDataBuffer();
                    byte dataDst[] = dataBufferDst.getData();
                    int result = OpenCLBase.clEnqueueReadImage(mem, true, new long[]{0, 0, 0}, new long[]{w, h, 1}, w * 2, 0, Pointer.to(dataDst), 0, null, null);
                    return imgout;
                }
                default:
                    break;
            }
        } else {
            BufferedImage imgout = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            DataBufferInt dataBufferDst = (DataBufferInt) imgout.getRaster().getDataBuffer();
            int dataDst[] = dataBufferDst.getData();
            int err = OpenCLBase.clEnqueueReadImage(mem, true, new long[]{0, 0, 0}, new long[]{w, h, 1}, w * Sizeof.cl_int, 0, Pointer.to(dataDst), 0, null, null);
            return imgout;
        }
        return null;
    }
    
    private int convertToByte(cl_mem img_in, cl_mem img_out, int w, int h, float add, float mult, cl_event[][] event) {
        if (!OpenCLBase.runWithArgs("convert_to_byte", new long[]{w, h}, null, event, img_in, img_out, add, mult)) {
            return -1;
        }
        return CL.CL_SUCCESS;
    }

}
