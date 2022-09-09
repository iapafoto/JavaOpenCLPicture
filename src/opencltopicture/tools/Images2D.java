/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opencltopicture.tools;

import java.awt.Graphics2D;
import java.awt.Image;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;

import java.awt.image.BufferedImage;
import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.ImageObserver;
import static java.awt.image.ImageObserver.ALLBITS;
import java.awt.image.VolatileImage;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_context;
import org.jocl.cl_image_desc;
import org.jocl.cl_image_format;
import org.jocl.cl_mem;

/**
 * Utility methods related to cl_mem image objects.
 */
public class Images2D {

    /**
     * Create a new cl_image_format struct in correlation to a given
     * BufferedImage. This method return the new cl_image_format or null if the
     * image type is unsupported.
     *
     * @param image
     * @return (the cl_image_format or null)
     *
     * @author Luc Bruninx
     * @since 2014-09-01
     */
    public static cl_image_format createImageFormatFrom(BufferedImage image) {

        switch (image.getType()) {
            case TYPE_4BYTE_ABGR: {
                cl_image_format format = new cl_image_format();
                format.image_channel_order = CL.CL_ABGR;
                format.image_channel_data_type = CL.CL_UNSIGNED_INT8;
                return format;
            }
            case TYPE_3BYTE_BGR: {
                cl_image_format format = new cl_image_format();
                format.image_channel_order = CL.CL_RGB;
                format.image_channel_data_type = CL.CL_UNSIGNED_INT8;
                return format;
            }
            case TYPE_INT_ARGB:
            case TYPE_INT_ARGB_PRE: {
                cl_image_format format = new cl_image_format();
                format.image_channel_order = CL.CL_RGBA;
                format.image_channel_data_type = CL.CL_UNSIGNED_INT8;
                return format;
            }
            case TYPE_INT_RGB: {
                cl_image_format format = new cl_image_format();
                format.image_channel_order = CL.CL_RGB;
                format.image_channel_data_type = CL.CL_UNSIGNED_INT8;
                return format;
            }
            case TYPE_BYTE_GRAY: {
                cl_image_format format = new cl_image_format();
                format.image_channel_order = CL.CL_LUMINANCE;
                format.image_channel_data_type = CL.CL_UNSIGNED_INT8;
                return format;
            }
            case TYPE_USHORT_GRAY: {
                cl_image_format format = new cl_image_format();
                format.image_channel_order = CL.CL_LUMINANCE;
                format.image_channel_data_type = CL.CL_UNSIGNED_INT16;
                return format;
            }
            default:
                return null;
        }

    }

    /**
     * Give the pitch size (Sizeof.?) of one pixel of the image
     *
     * The supported sizes are : Sizeof.cl_uchar for bytes, Sizeof.cl_ushort for
     * 16 bits integers, Sizeof.cl_uint for 32 bits integers.
     *
     * If the data source of the image is not supported, this method return 0.
     *
     * @param image
     * @return (pitch size of one pixel or 0)
     *
     * @author Luc Bruninx
     * @since 2014-09-01
     */
    public static int pitchSizeOf(BufferedImage image) {

        switch (image.getType()) {

            case TYPE_INT_ARGB:
            case TYPE_INT_ARGB_PRE:
            case TYPE_INT_RGB:
                return Sizeof.cl_uint;
            case TYPE_4BYTE_ABGR:
                return Sizeof.cl_uchar;
            case TYPE_3BYTE_BGR:
                return Sizeof.cl_uchar;
            case TYPE_BYTE_GRAY:
                return Sizeof.cl_uchar;
            case TYPE_USHORT_GRAY:
                return Sizeof.cl_ushort;
            default:
                return 0;
        }
    }

    /**
     * Create a new cl_image_desc struct in correlation to a given
     * BufferedImage. This method return the new cl_image_desc or null if the
     * image type is unsupported.
     *
     * @param image
     * @return (the cl_image_desc or null)
     *
     * @author Luc Bruninx
     * @since 2014-09-01
     */
    public static cl_image_desc createImageDescFrom(BufferedImage image) {
        int pitchSizeof = pitchSizeOf(image);
        if (pitchSizeof != 0) {
            cl_image_desc desc = new cl_image_desc();
            desc.image_type = CL.CL_MEM_OBJECT_IMAGE2D;
            desc.image_width = image.getWidth();
            desc.image_height = image.getHeight();
            desc.image_row_pitch = desc.image_width * pitchSizeof;
            return desc;
        } else {
            return null;
        }
    }

    /**
     * Create a new Pointer to the raster datas of the BufferedImage. This
     * method return the new Pointer or null if the image type is unsupported.
     *
     * @param image
     * @return (the new Pointer or null)
     *
     * @author Luc Bruninx
     * @since 2014-09-01
     */
    public static Pointer createRasterDataPointer(BufferedImage image) {
        int pitchSizeof = pitchSizeOf(image);
        switch (pitchSizeof) {
            case Sizeof.cl_uint: {
                DataBufferInt dataBufferSrc = (DataBufferInt) image.getRaster().getDataBuffer();
                int dataSrc[] = dataBufferSrc.getData();
                return Pointer.to(dataSrc);
            }
            case Sizeof.cl_uchar: {
                DataBufferByte dataBufferSrc = (DataBufferByte) image.getRaster().getDataBuffer();
                byte dataSrc[] = dataBufferSrc.getData();
                return Pointer.to(dataSrc);
            }
            case Sizeof.cl_ushort: {
                DataBufferShort dataBufferSrc = (DataBufferShort) image.getRaster().getDataBuffer();
                short dataSrc[] = dataBufferSrc.getData();
                return Pointer.to(dataSrc);
            }
            default:
                return null;
        }
    }

    /**
     * Create a new 2D image cl_mem by using fine parameters.
     *
     * This method returns a new cl_mem object or null if the object cannot be
     * created. If the err parameter is given, you can get the returned error
     * code by reading err[0].
     *
     * This method calls the new OpenCL 1.2 CL.clCreateImage() function if all
     * device of the context are compatibles. If not, the old
     * CL.clCreateImage2D() is used.
     *
     * @param context
     * @param ptr (ptr to the raster datas)
     * @param rw_flags (rw_flags Read/Write flags)
     * @param format
     * @param desc
     * @param err (err int array or null)
     * @return cl_mem of the new 2D image buffer
     *
     * @author Luc Bruninx
     * @since 2014-09-01
     */
    @SuppressWarnings("deprecation")
    public static cl_mem create(cl_context context, Pointer ptr, long rw_flags, cl_image_format format, cl_image_desc desc, int[] err) {

        //if (ContextInfos.getBestOpenclCVersion(context).compareTo("OPENCL C 1.2") >= 0) {
        return CL.clCreateImage(
                context,
                rw_flags,
                format,
                desc,
                ptr,
                err
        );
    }

    public static cl_mem createTexture2D(cl_context context, long flags, BufferedImage image) {
        cl_image_format format = Images2D.createImageFormatFrom(image);
        //
        if (format == null) {
            //  throw new InterpreterException(StdErrors.Type_mismatch.extend("image format currently unsupported"));
        }
        //
        cl_image_desc desc = Images2D.createImageDescFrom(image);
        if (desc == null) {
            //   throw new InterpreterException(StdErrors.Type_mismatch.extend("image description currently unsupported"));
        }
        //
        Pointer ptr = Images2D.createRasterDataPointer(image);
        if (ptr == null) {
            //   throw new InterpreterException(StdErrors.Type_mismatch.extend("image data type currently unsupported"));
        }
        int[] err = {0};
        return CL.clCreateImage(context, flags, format, desc, ptr, err);

    }

    
    private static void loadImage(final Image image) {
        class StatusObserver implements ImageObserver {
            boolean imageLoaded = false;
            @Override
            public boolean imageUpdate(final Image img, final int infoflags, final int x, final int y, final int width, final int height) {
                if (infoflags == ALLBITS) {
                    synchronized (this) {
                        imageLoaded = true;
                        notify();
                    }
                    return true;
                }
                return false;
            }
        }
        
        final StatusObserver imageStatus = new StatusObserver();
        synchronized (imageStatus) {
            if (image.getWidth(imageStatus) == -1 || image.getHeight(imageStatus) == -1) {
                while (!imageStatus.imageLoaded) {
                    try {
                        imageStatus.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
    }
    
    
    public static BufferedImage toBufferedImage(final Image image, final int type) {
        if (image == null) {
            return null;
        }
        if (image instanceof BufferedImage && ((BufferedImage)image).getType() == type) {
            return (BufferedImage) image;
        }
        if (image instanceof VolatileImage) {
            return ((VolatileImage) image).getSnapshot();
        }
        loadImage(image);
        final BufferedImage buffImg = new BufferedImage(image.getWidth(null), image.getHeight(null), type);
        final Graphics2D g2 = buffImg.createGraphics();
        g2.drawImage(image, null, null);
        g2.dispose();
        return buffImg;
    }
    

    /**
     * Private constructor to prevent instantiation
     */
    private Images2D() {

    }
}
