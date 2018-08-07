/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kaba.javacv.cuda.demo;

import java.io.IOException;
import java.nio.file.Path;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_imgcodecs;

/**
 *
 * @author kaba
 */
public class SwapMat // FIXME: think about implementing Mat methods on SwapMat with apropriate locking when writing!
    extends SwapObject<Mat> {
    private Mat mat=null;
    /** to save SwapMat parameter for use in allocate */
    private SwapMat paramSM;
    /** to save size parameter for use in allocate */
    private Size paramS;
    /** to save Rect parameter for use in allocate */
    private opencv_core.Rect paramRect;
    /** to save r-, c-, t- parameters for use in allocate */
    private int paramR, paramC, paramT;

    public static SwapMat ones( Size s, int t ) {
        return new SwapMat( MatParamVariant.ONE_SIZE_T, s, -1, -1, t );
    }

    private void allocatePayloadO( Size s, int t ) {
        this.mat = Mat.ones( s, t ).asMat();
    }

    public static SwapMat ones( int r, int c, int t ) {
        return new SwapMat( MatParamVariant.ONE_R_C_T, null, r, c, t );
    }

    private void allocatePayloadO( int r, int c, int t ) {
        this.mat = Mat.ones( r, c, t ).asMat();
    }

    public static SwapMat zeros( Size s, int t ) {
        return new SwapMat( MatParamVariant.ZERO_SIZE_T, s, -1, -1, t );
    }

    private void allocatePayloadZ( Size s, int t ) {
        this.mat = Mat.zeros( s, t ).asMat();
    }

    public static SwapMat zeros( int r, int c, int t ) {
        return new SwapMat( MatParamVariant.ZERO_R_C_T, null, r, c, t );
    }

    private void allocatePayloadZ( int r, int c, int t ) {
        this.mat = Mat.zeros( r, c, t ).asMat();
    }

    public SwapMat( Size s, int t ) {
        this.paramS = s;
        this.paramT = t;
        allocatePayloadWithFree(MatParamVariant.SIZE_T, CV_MAT_MEM_SIZE(s,t) );
    }

    private void allocatePayload( Size s, int t ) {
        this.mat = new Mat( s, t );
    }

    public SwapMat( int r, int c, int t ) {
        this.paramR = r;
        this.paramC = c;
        this.paramT = t;
        allocatePayloadWithFree(MatParamVariant.R_C_T, CV_MAT_MEM_SIZE(r,c,t) );
    }

    private void allocatePayload( int r, int c, int t ) {
        this.mat = new Mat( r, c, t );
    }

    public SwapMat( SwapMat sm, opencv_core.Rect rect ) {
        this.paramSM = sm;
        this.paramRect = rect;
        allocatePayloadWithFree(MatParamVariant.SM_R, CV_MAT_MEM_SIZE(rect.height(),rect.width(),sm.getPayload().type()) );
    }

    private void allocatePayload( SwapMat sm, opencv_core.Rect rect ) {
        final Mat view = new Mat( sm.getPayload(), rect );
        this.mat = new Mat( rect.height(), rect.width(), sm.getPayload().type() );
        view.copyTo( this.mat );
        view.release();  // FIXME: assuming this only decrements the usage count, does not really release memory here!
    }

    public SwapMat( Mat m ) {
        super( CV_MAT_MEM_SIZE(m) );
        this.mat = m;
//        this.mat.deallocator(null);
    }

    public SwapMat( long size ) {
        allocatePayloadWithFree(MatParamVariant.EMPTY, size );
    }

    private void allocatePayload() {
        this.mat = new Mat();  // empty dummy to be filled later
    }

    private SwapMat( MatParamVariant v, Size s, int r, int c, int t ) {
        this.paramS = s;
        this.paramR = r;
        this.paramC = c;
        this.paramT = t;
        allocatePayloadWithFree(v, (s==null?CV_MAT_MEM_SIZE(r,c,t):CV_MAT_MEM_SIZE(s,t)) );
    }

    @Override
    protected long allocatePayload_basic( ParamVariant v ) {
        if( v.getVariant() == MatParamVariant.SIZE_T.i)
            allocatePayload( paramS, paramT );
        else if( v.getVariant() == MatParamVariant.R_C_T.i)
            allocatePayload( paramR, paramC, paramT );
        else if( v.getVariant() == MatParamVariant.ZERO_SIZE_T.i)
            allocatePayloadZ( paramS, paramT );
        else if( v.getVariant() == MatParamVariant.ZERO_R_C_T.i)
            allocatePayloadZ( paramR, paramC, paramT );
        else if( v.getVariant() == MatParamVariant.ONE_SIZE_T.i)
            allocatePayloadO( paramS, paramT );
        else if( v.getVariant() == MatParamVariant.ONE_R_C_T.i)
            allocatePayloadO( paramR, paramC, paramT );
        else if( v.getVariant() == MatParamVariant.SM_R.i)
            allocatePayload( paramSM, paramRect );
        else if( v.getVariant() == MatParamVariant.EMPTY.i)
            allocatePayload();
        else
            throw new IllegalStateException("don't know ParamVariant "+v.getVariant()+" in SwapMat");
        return CV_MAT_MEM_SIZE( this.mat );
    }

    @Override
    protected void finalize()
        throws Throwable {
        if( this.mat != null )
            this.mat.release();
        this.mat = null;
        super.finalize();
    }

    @Override
    protected Mat getPayload_basic() {
        return mat;
    }

    @Override
    protected void swapOutPayload_basic( Path p )
        throws IOException {
        opencv_imgcodecs.imwrite( p.toString(), this.mat );
        this.mat.release();
        this.mat = null;
    }

    @Override
    protected void swapInPayload_basic( Path p )
        throws IOException {
        this.mat = opencv_imgcodecs.imread( p.toString(), opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE );
    }

    
    @Override
    protected String getTmpExt_basic() {
        return ".pgm";  // portable gray map
    }
    
    @Override
    protected boolean releasePayload_basic() {
        try {
            if( this.mat != null )
                this.mat.release();
            this.mat = null;
            return true;
        } finally {
//   FIXME: for debugging         return false; // somethin went wrong
        }
    }

    private enum MatParamVariant
        implements ParamVariant {
        SIZE_T(1), R_C_T(2), ZERO_SIZE_T(3), ZERO_R_C_T(4), ONE_SIZE_T(5), ONE_R_C_T(6), SM_R(7), EMPTY(8);
        public final int i;
        private MatParamVariant(int j) {i=j;}
        @Override
        public int getVariant() {
            return i;
        }
        
    };

    /** ONLY FOR ACCESS BY TEST CLASSES! */
    long test_swapOutMat() {
        return super.test_swapOut();
    }




    public static long CV_MAT_MEM_SIZE( Mat m ) {
        return CV_MAT_MEM_SIZE( m.step( 0 ), m.rows(), m.type() );
    }

    public static long CV_MAT_MEM_SIZE( Size s, int t ) {
        return CV_MAT_MEM_SIZE( s.height(), s.width(), t );
    }

    public static long CV_MAT_MEM_SIZE( int r, int c, int t ) {
        return r*c*opencv_core.CV_MAT_CN( t )*CV_MAT_DEPTH_BYTES( t );
    }

    private static final int[] CVMAT_DEPTH_BYTES = new int[opencv_core.CV_DEPTH_MAX];
    static {
        for( int d=0; d<opencv_core.CV_DEPTH_MAX; d++ ) {
            CVMAT_DEPTH_BYTES[d] = 2 ^ (d>>1);
        }
    }

    public static int CV_MAT_DEPTH_BYTES( int t ) {
        return CVMAT_DEPTH_BYTES[ t & opencv_core.CV_MAT_DEPTH_MASK ];
    }



}