package com.darkyen.worldSim.util;

import com.badlogic.gdx.math.MathUtils;

/**
 * Mutable string with minimal allocations.
 */
@SuppressWarnings("unused")
public final class Text implements CharSequence {

    private char[] arr;
    private int size;

    public Text(int size) {
        this.arr = new char[size];
        this.size = 0;
    }

    public Text(CharSequence content){
        this(content.length());
        set(content);
    }

    public Text(){
        this(64);
    }

    public Text insert(CharSequence sequence, int at){
        if(sequence == null || sequence.length() == 0)return this;
        final int len = sequence.length(), size = this.size, totalLen = size+len;
        if(at > size)at = size;//Cannot insert past the end
        ensureSize(totalLen);
        //Move everything after the insert point further down the line
        final char[] arr = this.arr;
        final int moving = size - at;
        System.arraycopy(arr, at, arr, at + len, moving);
        this.size = totalLen;
        set(sequence, at);
        return this;
    }

    public Text insert(char character,int at){
        final int size = this.size, totalLen = size + 1;
        if(at > size)at = size;//Cannot insert past the end
        ensureSize(totalLen);
        //Move everything after the insert point further down the line
        final char[] arr = this.arr;
        final int moving = size - at;
        System.arraycopy(arr, at, arr, at + 1, moving);
        this.size = totalLen;
        arr[at] = character;
        return this;
    }

    public Text delete(int at, int length){
        if(length == 0)return this;
        final int size = this.size;
        if(at >= size)return this;//Cannot delete anything past the end, there is already nothing.
        if(at + length > size){
            this.size = at;
        }else{
            //Move everything after delete point closer
            final char[] arr = this.arr;
            final int moving = size - (at + length);
            System.arraycopy(arr, at + length, arr, at, moving);
            this.size -= length;
        }
        return this;
    }

    public Text append(CharSequence text){
        return set(text, size);
    }

    public Text append(char character){
        ensureSize(size + 1);
        arr[size++] = character;
        return this;
    }

    public Text append(float number, int decimals){
        if (number < 0) {
            append('-');
            number = -number;
        }

        append((int) number);
        append('.');
        number = number % 1f;
        for (int i = 0; i < decimals; i++) {
            number *= 10;
        }
        return append((int) number);
    }

    public Text append(int number){
        return setNumber(number, size);
    }

    public Text setNumber(int number, int at){
        if(number == 0){
            append('0');
            return this;
        }
        if(number < 0){
            append('-');
            number = -number;
            at += 1;
        }
        int places = 0;
        {//Count the number of places
            int num = number;
            do{
                places++;
                num = num / 10;
            }while(num != 0);
        }
        final int minSize = at + places;
        {//Make sure there is space for it
            if (minSize > this.size) {
                ensureSize(minSize);
                this.size = minSize;
            }
        }
        {//Write it
            int num = number;
            for (int i = minSize-1; i >= at; i--) {
                arr[i] = (char)('0' + (num % 10));
                num = num / 10;
            }
        }
        //Done
        return this;
    }

    private void ensureSize(int atLeast){
        if(arr.length < atLeast){
            char[] newArr = new char[MathUtils.nextPowerOfTwo(atLeast)];
            System.arraycopy(arr, 0, newArr, 0, size);
            arr = newArr;
        }
    }

    public Text set(CharSequence text){
        ensureSize(text.length());
        for (int i = 0, s = size = text.length(); i < s; i++) {
            arr[i] = text.charAt(i);
        }
        return this;
    }

    public Text set(CharSequence text, int at){
        if (text == null) {
            text = "null";
        }

        final int size = at+text.length();
        //Will this make it bigger?
        if(size > this.size){
            ensureSize(size);
            this.size = size;
        }
        for (int i = 0, s = text.length(); i < s; i++) {
            arr[at + i] = text.charAt(i);
        }
        return this;
    }

    public Text set(char text, int at){
        if(at < 0)at = 0;
        final int size = at+1;
        //Will this make it bigger?
        if(size > this.size){
            ensureSize(size);
            this.size = size;
        }
        arr[at] = text;
        return this;
    }

    public void clear(){
        size = 0;
    }

    @Override
    public int length() {
        return size;
    }

    /**
     * Efficient way to drop some characters from the end of the text.
     */
    public void trimLengthTo(int newLength){
        if(newLength < size){
            size = newLength;
            if(size < 0)size = 0;
        }
    }

    @Override
    public char charAt(int index) {
        if(index >= size)throw new IndexOutOfBoundsException("index out of bounds: "+index+" (length = "+size+")");
        return arr[index];
    }

    @Override
    public String subSequence(int start, int end) {
        if(end >= size)throw new IndexOutOfBoundsException("end out of bounds: "+end+" (length = "+size+")");
        return new String(arr, start, end - start);
    }

    /**
     * Check if the content of this CharSequence exactly equals the content of cs.
     * @return true if the length and content of both CharSequences is equal.
     */
    public boolean contentEquals(CharSequence cs) {
        if(cs != null){
            if(cs.length() != size)return false;
            for (int i = 0, s = size; i < s; i++) {
                if(arr[i] != cs.charAt(i))return false;
            }
            return true;
        }else return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CharSequence && contentEquals((CharSequence) obj);
    }

    @Override
    public int hashCode() {
        final int size = this.size;
        final char[] arr = this.arr;

        int result = size;
        for (int i = 0; i < size; i++) {
            result = 31 * result + arr[i];
        }
        return result;
    }

    @Override
    public String toString() {
        return new String(arr, 0, size);
    }
}
