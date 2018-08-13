/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kaba.javacv.cuda.demo;

/**
 *
 * @author kaba
 */
public class UnableToReleaseError
        extends OutOfMemoryError
{
    private static final long serialVersionUID = 1533714529L;
    /**
     * Creates a new instance of <code>UnableToReleaseError</code> without
     * detail message.
     */
    public UnableToReleaseError() {
        super();
    }

    /**
     * Constructs an instance of <code>UnableToReleaseError</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public UnableToReleaseError( String msg ) {
        super( msg );
    }
}
