/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kaba.javacv.cuda.demo;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Queue;
import java.util.function.LongSupplier;
import org.bytedeco.javacpp.Pointer;

/** a wrapper for an object of type T that can swap itself in and out to and from a tmp folder;
 * observes javacpp native memory for now but may later be expanded to work with different memory regimes
 * @author kaba
 */
public abstract class SwapObject<T> extends Pointer
{
    private static final boolean DEBUG=true;
    //<editor-fold defaultstate="collapsed" desc="debug id">
    private static final boolean IDDEBUG=(true&&DEBUG);
    private static SafeContinousCounter debugIdCounter=null;  // debug
    static {
        if(IDDEBUG)
            debugIdCounter = new SafeContinousCounter(1L);
    }
    private final long debugid;
    //</editor-fold>
    private static Path swapdir=null;
    static {
        try {
            swapdir = Files.createTempDirectory( "SwapObject." );
            Runtime.getRuntime().addShutdownHook( new Thread(){
                @Override
                public void run() {
                    if( swapdir != null)
                        try {
                            Files.walk( swapdir )
                                .sorted( Comparator.reverseOrder() )
                                .map( Path::toFile)
                                .forEach( File::delete );
                            swapdir = null;
                    } catch( IOException e ) {
                        System.err.println("SwapObject cannot remove static temp dir in shutdown hook");
                    }
                }
            });
        } catch( IOException e ) {
            System.err.println("SwapObject cannot obtain static temp dir in class initialisation");
        }
    }
    private final static Queue<WeakReference<? extends SwapObject>> queue = new ArrayDeque<>();  // FIXME: ok without <T> ???????
    private final static long maxMaxPhys = Pointer.maxPhysicalBytes() * 95L / 100L;
    private final static long maxPhysStep = Pointer.maxPhysicalBytes() / 200L;  // .5% up-stepping
    private volatile static long maxPhys/*=maxMaxPhys*/, usedPhys/*=0L*/;  // dynamic maxPhys
    static {
        maxPhys = maxMaxPhys;
    }
    private volatile Path swapfile/*=null*/;
    private volatile boolean swapedOut/*=false*/, locked/*=false*/, released/*=false*/;
    private volatile long size/*=-1L*/;  // really volatile??
    protected int RETRIES=20;

    protected SwapObject( ParamVariant p ) {
        //<editor-fold defaultstate="collapsed" desc="debug id">
        debugid = (IDDEBUG?debugIdCounter.getNext():Long.MAX_VALUE);
        //</editor-fold>
        size = -1L;
        occupyMemWithFree( p.getRequired(), () -> {
            final long actual_size = allocatePayload_basic( p );
            return actual_size;
        } );
        synchronized(queue) {
            queue.add( new WeakReference<>(this) );  // ATTENTION: <? extends this> not completely constructed yet (this is, though)
        }
    }

    /** never use; only to satisfy default constructor requirement */
    private SwapObject() {
        //<editor-fold defaultstate="collapsed" desc="debug id">
        debugid = (IDDEBUG?debugIdCounter.getNext():Long.MAX_VALUE);
        //</editor-fold>
        size = -1L;
    }

    protected abstract long allocatePayload_basic( ParamVariant v );

    private void occupyMemWithFree( long required, LongSupplier allocator ) {
        OutOfMemoryError lastOOME=null;
        if( required > maxPhys )
            throw new IllegalArgumentException("cannot allocate "+required+" bytes in native mem, as maximum is "+maxPhys);
//        long free_phy = maxPhys - Pointer.physicalBytes();
        retries_alloc:
        do {
            for( int i=0; i<RETRIES; i++ ) {
                final long free_phy = maxPhys - usedPhys;
                try {
                    if( required > free_phy )
                        freePhysical( required-free_phy );
                    size = allocator.getAsLong();
                    usedPhys += this.size;
                    //<editor-fold defaultstate="collapsed" desc="debug output off">
                    if(false&&DEBUG)
                            System.out.println( toString()+" allocated "+size+" bytes of payload" );
                    //</editor-fold>
                    break retries_alloc;
                } catch(OutOfMemoryError e) {
                    lastOOME = e;
                    if( ! (e instanceof UnableToReleaseError) ) {  // this means javacpp physical memory full
                        maxPhys = usedPhys;  // reduce our idea of what we may use; gradually increased later on
                        //<editor-fold defaultstate="collapsed" desc="debug output on">
                        if(true&&DEBUG)
                            System.out.printf( "%s physical memory exhausted - lowering maxPhys to own current usage\n  -  used %11d of %11d (%3d%%)\n          %11d of %11d\n",
                                               toString(),Pointer.physicalBytes(),Pointer.maxPhysicalBytes(),(Pointer.physicalBytes()*100/Pointer.maxPhysicalBytes()),SwapMat.getUsedMem(),maxPhys );
                        //</editor-fold>
                    }
                }
            }
            throw lastOOME;
        } while(false); // one pass only
    }

    private void freePhysical( long amount )
            throws UnableToReleaseError {
        Queue<WeakReference<? extends SwapObject>> tmpq = new ArrayDeque<>(),  // FIXME: ... without <T> ... see above
                swap_q = new ArrayDeque<>();
        long to_free=0L, freed=0L;
        synchronized(queue) {
            try {
                liberation1:
                while( (to_free<amount) && (!queue.isEmpty()) ) {
                    final WeakReference<? extends SwapObject> ref1 = queue.poll();
                    assert( ref1 != null );
                    final SwapObject<T> so1 = ref1.get();
                    if( so1==null )
                        continue liberation1;  // drop polled ref, 'cause it doesn't reference nothin anymore
                    if( so1.released )
                        continue liberation1;  // drop polled ref, 'cause it doesn't reference nothin anymore
                    swap_q.add( ref1 );  // remember for swapping below
                    to_free += so1.size;
                }
            } catch(Throwable t) {
                synchronized(queue) {
                    queue.addAll( swap_q );  // re-add selected objects
                }
                throw t;
            }
        }
        //<editor-fold defaultstate="collapsed" desc="debug output off">
        if(false&&DEBUG)
                System.out.println( toString()+" goin2 free "+to_free+" bytes of memory by swapping "+swap_q.size() );
        //</editor-fold>
        try {
            liberation2:
            while( ! swap_q.isEmpty() ) {
                final WeakReference<? extends SwapObject> ref2 = swap_q.poll();
                assert( ref2 != null );
                final SwapObject<T> so2 = ref2.get();
                //<editor-fold defaultstate="collapsed" desc="debug output off">
                if(false&&DEBUG)
                        System.out.println( toString()+" got from swap_q: "+ref2+" - "+so2 );
                //</editor-fold>
                if( so2==null )
                    continue liberation2;  // silently drop ref, because nothing referenced anymore; FIXME: but still was referenced above!?
                assert( so2 != this );
                synchronized(so2) {
                    if( so2.released )
                        continue liberation2;  // drop polled ref, 'cause it doesn't reference nothin anymore
                    tmpq.add( ref2 );  // remember to re-add later
                    freed += so2.swapOut();  // usedPhys decremented in swapOut()
                }
            }
            if( freed < amount )
                throw new UnableToReleaseError("cannot free "+amount+", only released "+freed);
            //<editor-fold defaultstate="collapsed" desc="debug output on">
            if(false&&DEBUG)
                    System.out.println( toString()+" freed "+freed+" bytes of memory in freePhysical" );
            //</editor-fold>
        } finally {
            synchronized(queue) {
                queue.addAll( tmpq );
                queue.addAll( swap_q );
            }
        }
    }

    public void lock() {
        locked = true;
    }

    public void unlock() {
        locked = false;
    }

    private long swapOut() {
        assert( ! released );
        if( locked ) {
            //<editor-fold defaultstate="collapsed" desc="debug output on">
            if(true&&DEBUG)
                    System.out.println( toString()+" locked" );
            //</editor-fold>
            return 0L; // do not swap out when locked
        }
        synchronized(this) {
            try {
                if( swapedOut ) {
                    //<editor-fold defaultstate="collapsed" desc="debug output on">
                    if(true&&DEBUG)
                            System.out.println( toString()+" already swapped out" );
                    //</editor-fold>
                    return 0L;
                }
                if( swapfile == null )
                    swapfile = Files.createTempFile( swapdir, "", getTmpExt_basic() );
                swapOutPayload_basic( swapfile );
                swapedOut = true;
                usedPhys -= size;
                //<editor-fold defaultstate="collapsed" desc="debug output off">
                if(false&&DEBUG)
                        System.out.println( toString()+" swapped out "+size+" bytes to "+swapfile.toString() );
                //</editor-fold>
                return size;
            } catch( IOException e ) {
                System.err.println("cannot swap out: "+e);
                e.printStackTrace(System.err);
            } catch( Throwable t) {
                //<editor-fold defaultstate="collapsed" desc="debug output on">
                if(true&&DEBUG)
                        System.out.println( toString()+" seeing "+t );
                //</editor-fold>
                throw(t);
            }
        }
        return 0L;  // somethin went wrong
    }

    protected abstract String getTmpExt_basic();

    /** write payload to disc and release the native memory */
    protected abstract void swapOutPayload_basic( Path p ) throws IOException;

    /** count to to things once in a while, not every time */
    private static int swapInCounter=0;
    private void swapIn() {
        assert( ! released );
        try {
            occupyMemWithFree(size, () -> {
                try {
                    swapInPayload_basic( swapfile );
                    swapedOut = false;
//                    usedPhys += size;  already done in occupyMemWithFree, don't double here !!!!
                    //<editor-fold defaultstate="collapsed" desc="debug output off">
                    if(false&&DEBUG)
                            System.out.println( toString()+" swapped in from "+swapfile.toString() );
                    //</editor-fold>
                } catch( IOException e ) {
                    throw new RuntimeException(e);
                }
                return size;
            } ); // end occupy
        } catch( Exception e) {
            System.err.println("cannot swap in: "+e);
            e.printStackTrace(System.err);
        }
        if( (maxPhys<maxMaxPhys) && ( (swapInCounter++)%10 == 8 ) ) {
            maxPhys += maxPhysStep;
            //<editor-fold defaultstate="collapsed" desc="debug output on">
            if(true&&DEBUG)
                System.out.printf( "%s increasing maxPhys by one step\n  -  used %11d of %11d (%3d%%)\n          %11d of %11d\n",
                                   toString(),Pointer.physicalBytes(),Pointer.maxPhysicalBytes(),(Pointer.physicalBytes()*100/Pointer.maxPhysicalBytes()),SwapMat.getUsedMem(),maxPhys );
            //</editor-fold>
        }
    }

    /** read payload from disc and store */
    protected abstract void swapInPayload_basic( Path p ) throws IOException;

    /** when writing to the returned T-object, beware of race conditions if this object
     * got swapped out before written! */
    public T getPayload() {
        synchronized(this) {
            if( released )
                throw new IllegalStateException("payload already released");
            if( this.swapedOut ) {
                swapIn();
                // keep swap file (?)
            }
            return getPayload_basic();
        }
    }

    /** just return payload */
    protected abstract T getPayload_basic();

    public boolean releasePayload() {
        try {
            boolean del_ret=true;
            synchronized(this) {
                if( ! swapedOut ) {
                    releasePayload_basic();
                    usedPhys -= size;
                }
                released = true;
                if( swapfile != null ) {
                    del_ret = swapfile.toFile().delete();
                    if( del_ret )
                        swapfile = null;
                }
            }
            //<editor-fold defaultstate="collapsed" desc="debug output off">
            if(false&&DEBUG)
                    System.out.println( toString()+" released "+size+" bytes of memory, "+(del_ret?"":"not ")+"deleted "+swapfile );
            //</editor-fold>
            return del_ret;  // FIXME: needed anywhere??
        } catch(Throwable t) {
            System.out.println( "cannot release payload: "+t );
        } finally {
            return false; // somethin went wrong
        }
    }

    /** release the native memory */
    protected abstract void releasePayload_basic();

    public static SwapObject releasePayloadNN( SwapObject so ) {
        if( so != null )
            so.releasePayload();
        return null;
    }

    @Override
    protected void finalize()
            throws Throwable {
        releasePayload();
        super.finalize();
    }

    
    protected interface ParamVariant
    {
        long getRequired();
    };

    public static long getUsedMem() { return usedPhys; }

    public static long getMaxMem() { return maxPhys; }

    public String toString( String subclass_spec ) {
        //<editor-fold defaultstate="collapsed" desc="debug id">
        if(IDDEBUG)
            return "[#"+debugid+" "+subclass_spec+", "+size+" "+(swapedOut?"S":"s")+(locked?"L":"l")+(released?"R":"r")+" "+(swapfile==null?"null":swapfile.toString())+"]";
        else
        //</editor-fold>
            return "[ "+subclass_spec+", "+size+" "+(swapedOut?"S":"s")+(locked?"L":"l")+(released?"R":"r")+" "+(swapfile==null?"null":swapfile.toString())+"]";
    }

    /** ONLY FOR ACCESS BY TEST CLASSES! */
    protected long test_swapOut() {
        return swapOut();
    }
}
