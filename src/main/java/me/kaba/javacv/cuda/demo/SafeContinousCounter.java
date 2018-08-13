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
public class SafeContinousCounter
{
    private long counter;
    
    public SafeContinousCounter(long startvalue) {
        counter=startvalue;
    }

    public SafeContinousCounter() {
        this(0L);
    }

    public synchronized long getNext() {
        return counter++;
    }
}
