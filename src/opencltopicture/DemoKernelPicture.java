package opencltopicture;


import opencltopicture.tools.ocl_image;
import opencltopicture.tools.OpenCLBase;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;
import opencltopicture.tools.Images2D;
import org.jocl.CL;
import static org.jocl.CL.CL_RGBA;
import static org.jocl.CL.CL_UNSIGNED_INT8;
import org.jocl.cl_event;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author durands
 */
public class DemoKernelPicture {

        
    private static JFrame frame;
    private static JLabel label;

    public static void display(BufferedImage image) {
        if (frame == null) {
            frame = new JFrame();
            frame.setTitle("Result");
            frame.setSize(image.getWidth(), image.getHeight());
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            label = new JLabel();
            label.setIcon(new ImageIcon(image));
            frame.getContentPane().add(label, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.pack();
            frame.setVisible(true);
        } else {
            label.setIcon(new ImageIcon(image));
        }
    }
    
    private static final String RESOURCES_PATH = "opencltopicture/";
        
    public static void main(String args[]) throws IOException {
        final URL urlImg = DemoKernelPicture.class.getClassLoader().getResource(RESOURCES_PATH + "mars.jpg");
        BufferedImage img = ImageIO.read(urlImg);
        img = Images2D.toBufferedImage(img, BufferedImage.TYPE_INT_ARGB);

        // ---------------------------------------------------------------------
        //                  Chargement du Programme OpenCL 
        // ---------------------------------------------------------------------
        OpenCLBase.createKernels(DemoKernelPicture.class.getClassLoader().getResource(RESOURCES_PATH + "DemoKernelPicture.cl")); // => [clCreateKernelsInProgram]

        // ---------------------------------------------------------------------
        //                  Creation des textures GPU
        // ---------------------------------------------------------------------
        // Creation de l'image en tant que texture sur le GPU
        ocl_image clTextureInput = new ocl_image(img, true);      // => [clCreateImage]

        // Creation d'une texture GPU pour le resultat
        ocl_image clTextureOutput = new ocl_image(CL.CL_MEM_WRITE_ONLY, CL_UNSIGNED_INT8, CL_RGBA, img.getWidth(), img.getHeight(), null);


        // ---------------------------------------------------------------------
        //             Appel du Kernel (de notre fonction)
        // ---------------------------------------------------------------------
        cl_event[][] event = {null};
        if (OpenCLBase.runWithArgs(                               // => [clEnqueueNDRangeKernel]
                "doVigneting",                                    // Le nom de la fonction (Kernel)
                new long[]{img.getWidth(), img.getHeight()},      // Les indexes vont aller de 0 a w pour gix et de 0 a h pour giy (2 boucles for imbiquees) 
                event,                                            // Evenement de synchronisation
                clTextureInput, clTextureOutput)) {               // Les arguments de la fonction

        // ---------------------------------------------------------------------
        //                  Recuperation du resultat
        // ---------------------------------------------------------------------
            // On prepare une image Java classique
            final BufferedImage imgOut = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            // On transfert les pixels resultat du GPU vers le tableau des pixels de l'image
            OpenCLBase.query_picture(clTextureOutput, null, imgOut); // => [clEnqueueReadImage]

        // ---------------------------------------------------------------------
        //         On libere l'espace GPU si on a finit de s'en servir
        // ---------------------------------------------------------------------
            clTextureInput.release();   // => [clReleaseMemObject]
            clTextureOutput.release();  // => [clReleaseMemObject]
            
        // ---------------------------------------------------------------------    
        //                      On affiche l'image resultat
        // ---------------------------------------------------------------------
            display(imgOut);
        }
    }

    

}
