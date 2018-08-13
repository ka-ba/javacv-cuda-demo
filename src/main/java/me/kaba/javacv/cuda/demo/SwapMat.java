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
    private static final boolean DEBUG=true;
    private volatile Mat mat/*=null*/;  // does not work with initialiser!?
//    /** to save SwapMat parameter for use in allocate */
//    private SwapMat paramSM;
//    /** to save size parameter for use in allocate */
//    private Size paramS;
//    /** to save Rect parameter for use in allocate */
//    private opencv_core.Rect paramRect;
//    /** to save r-, c-, t- parameters for use in allocate */
//    private int paramR, paramC, paramT;

    public static SwapMat ones( Size s, int t ) {
        return new SwapMat( new Parameter(Parameter.Variants.ONE_SIZE_T, null, s, null, -1, -1, t) );
//        return new SwapMat( MatParamVariant.ONE_SIZE_T, s, -1, -1, t );
    }

    private synchronized void allocatePayloadO( Size s, int t ) {
        this.mat = Mat.ones( s, t ).asMat();
    }

    public static SwapMat ones( int r, int c, int t ) {
        return new SwapMat( new Parameter(Parameter.Variants.ONE_R_C_T, null, null, null, r, c, t) );
//        return new SwapMat( MatParamVariant.ONE_R_C_T, null, r, c, t );
    }

    private synchronized void allocatePayloadO( int r, int c, int t ) {
        this.mat = Mat.ones( r, c, t ).asMat();
    }

    public static SwapMat zeros( Size s, int t ) {
        return new SwapMat( new Parameter(Parameter.Variants.ZERO_SIZE_T, null, s, null, -1, -1, t) );
//        return new SwapMat( MatParamVariant.ZERO_SIZE_T, s, -1, -1, t );
    }

    private synchronized void allocatePayloadZ( Size s, int t ) {
        this.mat = Mat.zeros( s, t ).asMat();
    }

    public static SwapMat zeros( int r, int c, int t ) {
        return new SwapMat( new Parameter(Parameter.Variants.ZERO_R_C_T, null, null, null, r, c, t) );
//        return new SwapMat( MatParamVariant.ZERO_R_C_T, null, r, c, t );
    }

    private synchronized void allocatePayloadZ( int r, int c, int t ) {
        this.mat = Mat.zeros( r, c, t ).asMat();
    }

    private SwapMat( Parameter parameter ) {
        super( parameter );
    }

    public SwapMat( Size s, int t ) {
        super( new Parameter(Parameter.Variants.SIZE_T, null, s, null, -1, -1, t) );
//        this.paramS = s;
//        this.paramT = t;
//        allocatePayloadWithFree(MatParamVariant.SIZE_T, CV_MAT_MEM_SIZE(s,t) );
    }

    private synchronized void allocatePayload( Size s, int t ) {
        this.mat = new Mat( s, t );
    }

    public SwapMat( int r, int c, int t ) {
        super( new Parameter(Parameter.Variants.R_C_T, null, null, null, r, c, t) );
//        this.paramR = r;
//        this.paramC = c;
//        this.paramT = t;
//        allocatePayloadWithFree(MatParamVariant.R_C_T, CV_MAT_MEM_SIZE(r,c,t) );
    }

    private synchronized void allocatePayload( int r, int c, int t ) {
        this.mat = new Mat( r, c, t );
    }

    public SwapMat( SwapMat sm, opencv_core.Rect rect ) {
        super( new Parameter(Parameter.Variants.SM_R, sm, null, rect, -1, -1, -1) );
//        this.paramSM = sm;
//        this.paramRect = rect;
//        allocatePayloadWithFree(MatParamVariant.SM_R, CV_MAT_MEM_SIZE(rect.height(),rect.width(),sm.getPayload().type()) );
    }

    private synchronized void allocatePayload( SwapMat sm, opencv_core.Rect rect ) {
        final Mat view = new Mat( sm.getPayload(), rect );
        this.mat = new Mat( rect.height(), rect.width(), sm.getPayload().type() );
        view.copyTo( this.mat );
        view.release();  // FIXME: assuming this only decrements the usage count, does not really release memory here!
    }

//    public SwapMat( Mat m ) {
//        super( CV_MAT_MEM_SIZE(m) );
//        this.mat = m;
////        this.mat.deallocator(null);
//    }

    public SwapMat( long size ) {
        super( new Parameter(Parameter.Variants.EMPTY, null, null, null, -1, -1, -1) );  // FIXME: if needed find way to pass size
//        allocatePayloadWithFree(MatParamVariant.EMPTY, size );
    }

    private synchronized void allocatePayload() {
        this.mat = new Mat();  // empty dummy to be filled later
    }

//    private SwapMat( MatParamVariant v, Size s, int r, int c, int t ) {
//        this.paramS = s;
//        this.paramR = r;
//        this.paramC = c;
//        this.paramT = t;
//        allocatePayloadWithFree(v, (s==null?CV_MAT_MEM_SIZE(r,c,t):CV_MAT_MEM_SIZE(s,t)) );
//    }

    @Override
    protected long allocatePayload_basic( ParamVariant p ) {
        final Parameter param = (Parameter)p;
        switch( param.variant ) {
            case SIZE_T:
                allocatePayload( param.size, param.type );
                break;
            case R_C_T:
                allocatePayload( param.rows, param.cols, param.type );
                break;
            case ZERO_SIZE_T:
                allocatePayloadZ( param.size, param.type );
                break;
            case ZERO_R_C_T:
                allocatePayloadZ( param.rows, param.cols, param.type );
                break;
            case ONE_SIZE_T:
                allocatePayloadO( param.size, param.type );
                break;
            case ONE_R_C_T:
                allocatePayloadO( param.rows, param.cols, param.type );
                break;
            case SM_R:
                allocatePayload( param.swapMat, param.rect );
                break;
            case EMPTY:
                allocatePayload();
                break;
            default:
                throw new IllegalStateException("don't know Parameter.Variant "+param.variant+" in SwapMat");
        }
        //<editor-fold defaultstate="collapsed" desc="debug output off">
        if(false&&DEBUG)
            System.out.println( "paramsize:"+param.required+"mysize:"+CV_MAT_MEM_SIZE(this.mat)+", sizeof:"+this.mat.sizeof()+", buffersize:"+this.mat.arraySize()+", total:"+this.mat.total()
                                +" - cn:"+opencv_core.CV_MAT_CN(this.mat.type())+", d:"+CV_MAT_DEPTH_BYTES(this.mat.type())+", s:"+this.mat.step(0) );

        //</editor-fold>
        return this.mat.arraySize();
    }

//    @Override
//    protected long allocatePayload_basic( ParamVariant v ) {
//        //<editor-fold defaultstate="collapsed" desc="debug output prep">
//        long paramsize=-1L;
//        //</editor-fold>
//        if( v.getVariant() == MatParamVariant.SIZE_T.i) {
//            allocatePayload( paramS, paramT );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramS,paramT);
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.R_C_T.i) {
//            allocatePayload( paramR, paramC, paramT );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramR,paramC,paramT);
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.ZERO_SIZE_T.i) {
//            allocatePayloadZ( paramS, paramT );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramS,paramT);
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.ZERO_R_C_T.i) {
//            allocatePayloadZ( paramR, paramC, paramT );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramR,paramC,paramT);
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.ONE_SIZE_T.i) {
//            allocatePayloadO( paramS, paramT );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramS,paramT);
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.ONE_R_C_T.i) {
//            allocatePayloadO( paramR, paramC, paramT );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramR,paramC,paramT);
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.SM_R.i) {
//            allocatePayload( paramSM, paramRect );
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = CV_MAT_MEM_SIZE(paramRect.height(),paramRect.width(),paramSM.getPayload().type());
//            //</editor-fold>
//        } else if( v.getVariant() == MatParamVariant.EMPTY.i) {
//            allocatePayload();
//            //<editor-fold defaultstate="collapsed" desc="debug prep off">
//            if(false)
//                paramsize = 0L;
//            //</editor-fold>
//        } else
//            throw new IllegalStateException("don't know ParamVariant "+v.getVariant()+" in SwapMat");
//        //<editor-fold defaultstate="collapsed" desc="debug output off">
//        if(false)
//            System.out.println( "paramsize:"+paramsize+"mysize:"+CV_MAT_MEM_SIZE(this.mat)+", sizeof:"+this.mat.sizeof()+", buffersize:"+this.mat.arraySize()+", total:"+this.mat.total()
//                                +" - cn:"+opencv_core.CV_MAT_CN(this.mat.type())+", d:"+CV_MAT_DEPTH_BYTES(this.mat.type())+", s:"+this.mat.step(0) );
//
//        //</editor-fold>
////        return CV_MAT_MEM_SIZE( this.mat );
//        return this.mat.arraySize();
//    }

    @Override
    protected void finalize()
        throws Throwable {
        super.finalize();
        synchronized(this) {
            if( this.mat != null )
                this.mat.release();
            this.mat = null;
        }
    }

    @Override
    protected Mat getPayload_basic() {
        return mat;
    }

    @Override
    protected synchronized void swapOutPayload_basic( Path p )
        throws IOException {
        opencv_imgcodecs.imwrite( p.toString(), this.mat );
        this.mat.release();
        this.mat = null;
        //<editor-fold defaultstate="collapsed" desc="debug output off">
        if(false&&DEBUG)
            System.out.println( toString()+" nulled mat in swapOutPayload_basic" );
        //</editor-fold>
    }

    @Override
    protected synchronized void swapInPayload_basic( Path p )
        throws IOException {
        this.mat = opencv_imgcodecs.imread( p.toString(), opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE );
    }

    
    @Override
    protected String getTmpExt_basic() {
        return ".pgm";  // portable gray map
    }
    
    @Override
    protected synchronized void releasePayload_basic() {
//        try {
            if( this.mat != null )
                this.mat.release();
            this.mat = null;
        //<editor-fold defaultstate="collapsed" desc="debug output off">
        if(false&&DEBUG)
            System.out.println( toString()+" nulled mat in releasePayload_basic" );
        //</editor-fold>
//            return true;
//        } finally {
////   FIXME: for debugging         return false; // somethin went wrong
//        }
    }

    @Override
    public String toString() {
        return super.toString( "[SwapMat: "+(mat==null?"null":mat.toString())+"]" );
    }

    private static class Parameter
            implements ParamVariant {
        private enum Variants { SIZE_T, R_C_T, ZERO_SIZE_T, ZERO_R_C_T, ONE_SIZE_T, ONE_R_C_T, SM_R, EMPTY };
        private final Variants variant;
        private final SwapMat swapMat;
        private final Size size;
        private final opencv_core.Rect rect;
        private final int rows, cols, type;
        private final long required;

        public Parameter( Variants variant ,SwapMat swapMat, Size size, opencv_core.Rect rect, int rows, int cols, int type ) {
            this.variant = variant;
            this.swapMat = swapMat;
            this.size = size;
            this.rect = rect;
            this.rows = rows;
            this.cols = cols;
            this.type = type;
            long r=-1L;
            switch( variant ) {
                case SIZE_T:
                case ZERO_SIZE_T:
                case ONE_SIZE_T:
                    r = CV_MAT_MEM_SIZE( size, type );
                    break;
                case R_C_T:
                case ZERO_R_C_T:
                case ONE_R_C_T:
                    r = CV_MAT_MEM_SIZE( rows, cols, type );
                    break;
                case SM_R:
                    r = CV_MAT_MEM_SIZE( swapMat.mat );
                    break;
//                case EMPTY:   FIXME: not used? not meaningful?
                default:
                    throw new IllegalStateException("unknown Variant "+variant);
            }
            required = r;
        }

        @Override
        public long getRequired() {
            return required;
        }
    }

//    private enum MatParamVariant
//        implements ParamVariant {
//        SIZE_T(1), R_C_T(2), ZERO_SIZE_T(3), ZERO_R_C_T(4), ONE_SIZE_T(5), ONE_R_C_T(6), SM_R(7), EMPTY(8);
//        public final int i;
//        private MatParamVariant(int j) {i=j;}
//        @Override
//        public int getVariant() {
//            return i;
//        }
//        
//    };

    /** ONLY FOR ACCESS BY TEST CLASSES! */
    long test_swapOutMat() {
        return super.test_swapOut();
    }




    public static long CV_MAT_MEM_SIZE( Mat m ) {
//        return CV_MAT_MEM_SIZE( m.step( 0 ), m.rows(), m.type() );
        return (long)m.rows() * (long)m.step( 0 );
    }

    public static long CV_MAT_MEM_SIZE( Size s, int t ) {
        return CV_MAT_MEM_SIZE( s.height(), s.width(), t );
    }

    public static long CV_MAT_MEM_SIZE( int r, int c, int t ) {
        return (long)r * (long)c * (long)opencv_core.CV_MAT_CN(t) * (long)CV_MAT_DEPTH_BYTES(t);
    }

    private static final int[] CVMAT_DEPTH_BYTES = new int[opencv_core.CV_DEPTH_MAX];
    static {
        for( int d=0; d<opencv_core.CV_DEPTH_MAX; d++ ) {
            CVMAT_DEPTH_BYTES[d] = (int) Math.pow( 2, (d>>>1) );
        }
    }

    public static int CV_MAT_DEPTH_BYTES( int t ) {
        return CVMAT_DEPTH_BYTES[ t & opencv_core.CV_MAT_DEPTH_MASK ];
    }



}
