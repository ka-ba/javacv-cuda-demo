/* goes with the lincense of org.bytedeco.javacpp
 * (c) kaba
 */
package me.kaba.javacv.cuda.demo;

import java.util.stream.IntStream;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.GpuMat;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_cudaimgproc;
import org.bytedeco.javacpp.opencv_imgproc;

/**
 *
 * @author kaba
 */
public class CudaMatch
{
    private static final int countS = 150, countP = 300, matType=opencv_core.CV_8UC1/*opencv_core.CV_32FC1*/;
    private static Size imgSize = new Size( 2048, 2048 ),
            tmpltSize = new Size( 128, 128 );

    public static void main(String[] args) {
        new CudaMatch().go();
    }
    
    private void go() {
        Mat img = new Mat( imgSize, matType );
        if(true)
            System.out.println("type: "+img.type()+", mat: "+img.toString());
        Mat tmplt = new Mat( tmpltSize, matType );
        opencv_cudaimgproc.TemplateMatching gtm = opencv_cudaimgproc.createTemplateMatching( img.type(), opencv_imgproc.CV_TM_CCORR );
        for( int i=0; i<countS; i++ ) {
            System.out.print( ""+i+".. " );
            match( img, tmplt, gtm );
        }
        System.out.println("ok");
        System.setProperty( "java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString( semaphore ) );
        IntStream.range( 0, countP ).parallel()
                .peek( i -> System.out.print( ""+i+".. " ) )
                .forEach( i -> semaMatch( img, tmplt, gtm ) );
        System.out.println("ok");
        tmplt.release();
        img.release();
    }

    private void match( Mat img, Mat tmplt, opencv_cudaimgproc.TemplateMatching gtm ) {
        try( PointerScope ps = new PointerScope() ) {
//            Mat img = new Mat( imgSize, matType );
            img = new Mat( imgSize, matType );
//            if(false)
//                System.out.println("type: "+img.type()+", mat: "+img.toString());
//            Mat tmplt = new Mat( tmpltSize, matType );
            GpuMat g_img = new GpuMat();
            GpuMat g_tmplt = new GpuMat();
            g_img.upload( img );
            g_tmplt.upload( tmplt );
//            opencv_cudaimgproc.TemplateMatching gtm = opencv_cudaimgproc.createTemplateMatching( img.type(), opencv_imgproc.CV_TM_CCORR );
    //        opencv_core.GpuMat gtemp = new opencv_core.GpuMat( gtile.rows()-tmplt.rows()+1, gtile.cols()-tmplt.cols()+1, opencv_core.CV_32FC1 );
            GpuMat g_res = new GpuMat();
            gtm.match( g_img, g_tmplt, g_res );
            Mat res = new Mat();
            g_res.download( res );
            g_tmplt.release();
            g_img.release();
            g_res.release();
//            tmplt.release();
            img.release();
            res.release();
        }
    }

    private static int semaphore=6;

    private void semaMatch( Mat img, Mat tmplt, opencv_cudaimgproc.TemplateMatching gtm ) {
        synchronized(this) {
            while( semaphore < 1 ) {
                try {
                    wait();
                } catch( InterruptedException e ) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            semaphore--;
//            System.out.println("semaphore v "+semaphore);
        }
        try {
            match( img, tmplt, gtm );
        } finally {
            synchronized(this) {
                semaphore++;
//                System.out.println("semaphore ^ "+semaphore);
                notifyAll();
            }
        }
    }
}
