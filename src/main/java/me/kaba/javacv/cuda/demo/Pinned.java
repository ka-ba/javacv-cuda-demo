/* goes with the lincense of org.bytedeco.javacpp
 * (c) kaba
 */
package me.kaba.javacv.cuda.demo;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.HostMem;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatAllocator;
import org.bytedeco.javacpp.opencv_core.Size;

/**
 *
 * @author kaba
 */
public class Pinned
{
    private static final int MAX_MATS=700;
    private static final Size matSize = new Size( 4000, 6000 );
//    private List<Mat> mats = new ArrayList<>();
    private Queue<SwapMat> mats = new ArrayDeque<>();

    public static void main(String[] args) throws IOException {
        new Pinned().go();
    }
    
    private void go() throws IOException {
//        showDepthsChannels();

        MatAllocator allocator = HostMem.getAllocator( HostMem.PAGE_LOCKED );

        Mat.setDefaultAllocator( allocator );

        try {
            IntStream.range( 0, MAX_MATS)./*parallel().*/forEach( i -> {
                SwapMat sm=null;
//            for( int i=0; i<MAX_MATS; i++ ) {
//                mats.add( new Mat(matSize) );
//                mats.add( Mat.ones(matSize,opencv_core.CV_8UC1).asMat() );
                if( i%3 == 2 ) {
                    synchronized(mats) {
                        sm = mats.poll();
                    }
                    if( sm != null )
                        sm.releasePayload();
                }
                sm = SwapMat.ones( matSize, opencv_core.CV_8UC1 );
                synchronized(mats) {
                    mats.add( sm );
                }
                if( i%100 == 0)
                    System.out.printf( "- %5d ... used %10d of %10d (%3d%%)\n                 %10d\n",mats.size(),Pointer.physicalBytes(),Pointer.maxPhysicalBytes(),(Pointer.physicalBytes()*100/Pointer.maxPhysicalBytes()),SwapMat.getUsedMem() );
//                    System.out.println( "- "+mats.size()+"... used "+Pointer.physicalBytes()+" of "+Pointer.maxPhysicalBytes() );
            } );
//            System.out.print( " 8UC1 - " ); mats.add( SwapMat.ones(matSize,opencv_core.CV_8UC1) );
//            System.out.print( " 8UC2 - " ); mats.add( SwapMat.ones(matSize,opencv_core.CV_8UC2) );
//            System.out.print( " 8UC3 - " ); mats.add( SwapMat.ones(matSize,opencv_core.CV_8UC3) );
//            System.out.print( "16SC1 - " ); mats.add( SwapMat.ones(matSize,opencv_core.CV_16SC1) );
//            System.out.print( "32FC4 - " ); mats.add( SwapMat.ones(matSize,opencv_core.CV_32FC4) );
            System.out.println( "\nreached MAX_MATS ("+MAX_MATS+") limit\npress <ret>" );
            System.in.read();
        } catch( Throwable t ) {
            System.out.println( "caught while allocating: "+t );
            t.printStackTrace(System.out);
        }

        System.out.println("allocated "+mats.size()+" Mats - in pinned mem? - getting&releasing");

//        for( Mat m: mats )
//            m.release();
        mats.parallelStream().forEach( (sm) -> {
            sm.getPayload(); // test multiple swap cycles
        } );
        mats.parallelStream().forEach( (sm) -> {
            sm.getPayload().release();
        } );

        System.gc();
        System.out.println( "\nreleased "+mats.size()+") mats\npress <ret>" );
        System.in.read();
    }


    private static void showDepthsChannels() {
        showDepthChannel( opencv_core.CV_8UC1,"CV_8UC1" );
        showDepthChannel( opencv_core.CV_8UC2,"CV_8UC2" );
        showDepthChannel( opencv_core.CV_8UC3,"CV_8UC3" );
        showDepthChannel( opencv_core.CV_8UC4,"CV_8UC4" );
        showDepthChannel( opencv_core.CV_8SC1,"CV_8SC1" );
        showDepthChannel( opencv_core.CV_8SC2,"CV_8SC2" );
        showDepthChannel( opencv_core.CV_8SC3,"CV_8SC3" );
        showDepthChannel( opencv_core.CV_8SC4,"CV_8SC4" );
        showDepthChannel( opencv_core.CV_16UC1,"CV_16UC1" );
        showDepthChannel( opencv_core.CV_16UC2,"CV_16UC2" );
        showDepthChannel( opencv_core.CV_16UC3,"CV_16UC3" );
        showDepthChannel( opencv_core.CV_16UC4,"CV_16UC4" );
        showDepthChannel( opencv_core.CV_16SC1,"CV_16SC1" );
        showDepthChannel( opencv_core.CV_16SC2,"CV_16SC2" );
        showDepthChannel( opencv_core.CV_16SC3,"CV_16SC3" );
        showDepthChannel( opencv_core.CV_16SC4,"CV_16SC4" );
        showDepthChannel( opencv_core.CV_32SC1,"CV_32SC1" );
        showDepthChannel( opencv_core.CV_32SC2,"CV_32SC2" );
        showDepthChannel( opencv_core.CV_32SC3,"CV_32SC3" );
        showDepthChannel( opencv_core.CV_32SC4,"CV_32SC4" );
        showDepthChannel( opencv_core.CV_32FC1,"CV_32FC1" );
        showDepthChannel( opencv_core.CV_32FC2,"CV_32FC2" );
        showDepthChannel( opencv_core.CV_32FC3,"CV_32FC3" );
        showDepthChannel( opencv_core.CV_32FC4,"CV_32FC4" );
        showDepthChannel( opencv_core.CV_64FC1,"CV_64FC1" );
        showDepthChannel( opencv_core.CV_64FC2,"CV_64FC2" );
        showDepthChannel( opencv_core.CV_64FC3,"CV_64FC3" );
        showDepthChannel( opencv_core.CV_64FC4,"CV_64FC4" );
    }

    private static void showDepthChannel( int t, String n ) {
        int func_depth = opencv_core.CV_MAT_DEPTH( t );
        int func_type = opencv_core.CV_MAT_TYPE(t );
        int func_cn = opencv_core.CV_MAT_CN( t );
//        int raw_depth = t & opencv_core.CV_MAT_DEPTH_MASK;
//        int man_depth = -1;
//        switch( raw_depth>>1 ) {
//            case 0: man_depth=8; break;
//            case 1: man_depth=16; break;
//            case 2: man_depth=32; break;
//            case 3: man_depth=64; break;
//            default: throw new IllegalArgumentException("depth "+raw_depth+" not possible");
//        }
        int man_depth = SwapMat.CV_MAT_DEPTH_BYTES( t );
        int man_cn = t >> opencv_core.CV_CN_SHIFT;
        System.out.printf( "%10s: func: d:%2d c:%2d t:%2d arg:%2d\n", n, func_depth, func_cn, func_type, t );
        System.out.printf( "             man: d:%2d c:%2d\n", man_depth, man_cn );
    }
}
