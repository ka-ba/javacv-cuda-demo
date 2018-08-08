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
    private static Path swapdir=null;
    static {
        try {
            swapdir = Files.createTempDirectory( "electric." );
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
    private final static long maxPhys = Pointer.maxPhysicalBytes() * 94L / 100L;
    private static long usedPhys=0L;
    private Path swapfile=null;
    private boolean swapedOut=false, locked=false, released=false;
    private long size=-1L;
    protected int RETRIES=20;

    /** only use in exceptional cases when payload already exists */
    protected SwapObject( long s ) {
        this.size = s;
        usedPhys += this.size;
    }

    /** never use; only to satisfy default constructor requirement */
    protected SwapObject() {
    }

    protected abstract long allocatePayload_basic( ParamVariant v );

    protected void allocatePayloadWithFree( ParamVariant v, long required/*, Queue<WeakReference<? extends SwapObject<T>>> queue*/ ) {
        assert( ! released );
        occupyMemWithFree( required, () -> {
            final long ret = allocatePayload_basic( v );
            queue.add( new WeakReference<>(this) );
            return ret;
        } );
    }

    private void occupyMemWithFree( long required, LongSupplier activity ) {
        OutOfMemoryError lastOOME=null;
        if( required > maxPhys )
            throw new IllegalArgumentException("cannot allocate "+required+" bytes in native mem, as maximum is "+maxPhys);
//        long free_phy = maxPhys - Pointer.physicalBytes();
        retries_alloc:
        do {
            for( int i=0; i<RETRIES; i++ ) {
                long free_phy = maxPhys - usedPhys;
                try {
                if( required > free_phy )
                    freePhysical( required-free_phy );
                size = activity.getAsLong();
                usedPhys += this.size;
//                queue.add( new WeakReference<>(this) );
                //<editor-fold defaultstate="collapsed" desc="debug output on">
                if(true&&DEBUG)
                        System.out.println( toString()+" allocated "+size+" bytes of payload" );
                //</editor-fold>
                break retries_alloc;
                } catch(OutOfMemoryError e) {
                    lastOOME = e;
                    free_phy = 0L;  // trigger releasePayload of required from second try on
                }
            }
            throw lastOOME;
        } while(false); // one pass only
    }

    private void freePhysical( long amount ) {
        Queue<WeakReference<? extends SwapObject>> tmpq = new ArrayDeque<>(),  // FIXME: ... without <T> ... see above
                swap_q = new ArrayDeque<>();
        long to_free=0L, freed=0L;
        synchronized(queue) {
            try {
                liberation:
                while( (to_free<amount) && (!queue.isEmpty()) ) {
                    final WeakReference<? extends SwapObject> ref = queue.poll();
                    assert( ref != null );
                    final SwapObject<T> so = ref.get();
                    if( so==null )
                        continue liberation;  // drop polled ref, 'cause it doesn't reference nothin anymore
                    if( so.released )
                        continue liberation;  // drop polled ref, 'cause it doesn't reference nothin anymore
//                    synchronized(so) { // ATTENTION: possible source of deadlock if somewhere so and queue were synchronized other way around !!!
//                        if( so.released==true )
//                            continue liberation;  // drop polled ref, 'cause it doesn't reference nothin anymore
//                        tmpq.add( ref );  // remember to re-add later
//                        freed += so.swapOut();
//                    }
                    swap_q.add( ref );  // remember for swapping below
                    to_free += so.size;
                }
            } finally {
                queue.addAll( tmpq );  // re-add swapped out objects
            }
        }
        //<editor-fold defaultstate="collapsed" desc="debug output on">
        if(true&&DEBUG)
                System.out.println( toString()+" goin2 free "+to_free+" bytes of memory by swapping "+swap_q.size() );
        //</editor-fold>
        try {
            liberation2:
            while( ! swap_q.isEmpty() ) {
                final WeakReference<? extends SwapObject> ref = swap_q.poll();
                assert( ref != null );
                final SwapObject<T> so = ref.get();
                //<editor-fold defaultstate="collapsed" desc="debug output on">
                if(true&&DEBUG)
                        System.out.println( toString()+" got from swap_q: "+ref+" - "+so );
                //</editor-fold>
                if( so==null )
                    continue liberation2;  // silently drop ref, because nothing referenced anymore; FIXME: but still was referenced above!?
                synchronized(so) {
                    if( so.released )
                        continue liberation2;  // drop polled ref, 'cause it doesn't reference nothin anymore
                    tmpq.add( ref );  // remember to re-add later
                    freed += so.swapOut();
                }
            }
            if( freed < amount )
                throw new OutOfMemoryError("cannot free "+amount+", only released "+freed);
            //<editor-fold defaultstate="collapsed" desc="debug output on">
            if(true&&DEBUG)
                    System.out.println( toString()+" freed "+freed+" bytes of memory" );
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
                //<editor-fold defaultstate="collapsed" desc="debug output on">
                if(true&&DEBUG)
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

    protected abstract void swapOutPayload_basic( Path p ) throws IOException;

    private void swapIn() {
        assert( ! released );
        try {
            occupyMemWithFree(size, () -> {
                try {
                    swapInPayload_basic( swapfile );
                    swapedOut = false;
                    usedPhys += size;
                    //<editor-fold defaultstate="collapsed" desc="debug output on">
                    if(true&&DEBUG)
                            System.out.println( toString()+" swapped in from "+swapfile.toString() );
                    //</editor-fold>
                } catch( IOException e ) {
                    throw new RuntimeException(e);
                }
                return size;
            } );
        } catch( Exception e) {
            System.err.println("cannot swap in: "+e);
            e.printStackTrace(System.err);
        }
    }

    protected abstract void swapInPayload_basic( Path p ) throws IOException;

    /** when writing to the returned T-object, beware of race conditions if this object
     * got swapped out before written! */
    public T getPayload() {
        synchronized(this) {
            if( released )
                throw new IllegalStateException("payload already released");
            if( this.swapedOut ) {
                swapIn();
//                swapedOut = false;
                // keep swap file (?)
            }
            return getPayload_basic();
        }
    }

    protected abstract T getPayload_basic();

    public boolean releasePayload() {
        try {
            boolean basic_ret=true, del_ret=true;
            synchronized(this) {
                if( ! swapedOut )
                    basic_ret = releasePayload_basic();
                if( basic_ret ) {
                    released = true;
                    usedPhys -= size;
                    if( swapfile != null ) {
                        del_ret = swapfile.toFile().delete();
                        if( del_ret )
                            swapfile = null;
                    }
                }
            }
            //<editor-fold defaultstate="collapsed" desc="debug output on">
            if(true&&DEBUG)
                    System.out.println( toString()+(basic_ret?"":" not")+" released "+size+" bytes of memory, "+(del_ret?"":"not ")+"deleted "+swapfile );
            //</editor-fold>
            return basic_ret && del_ret;
        } catch(Throwable t) {
            System.out.println( "cannot release payload: "+t );
        } finally {
            return false; // somethin went wrong
        }
    }

    /** @return true if payload was release ok or release not necessary */
    protected abstract boolean releasePayload_basic();

    public static SwapObject releasePayloadNN( SwapObject so ) {
        if( so != null )
            so.releasePayload();
        return null;
    }

    protected interface ParamVariant
    {
        int getVariant();
    };

    protected class DecDeallocator implements Pointer.Deallocator {
        @Override
        public void deallocate() {
            usedPhys -= size;
        }
    }

    public static long getUsedMem() { return usedPhys; }

    public String toString( String subclass_spec ) {
        return "[ "+subclass_spec+", "+size+" "+(swapedOut?"S":"s")+(locked?"L":"l")+(released?"R":"r")+" "+(swapfile==null?"null":swapfile.toString())+"]";
    }

    /** ONLY FOR ACCESS BY TEST CLASSES! */
    protected long test_swapOut() {
        return swapOut();
    }
}
