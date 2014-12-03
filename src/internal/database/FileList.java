package internal.database;

/*******************************************************************************
 * @file  FileList.java
 *
 * @author   John Miller
 */

import java.io.*;

import static java.lang.System.out;

import java.util.*;

/*******************************************************************************
 * This class allows data tuples/tuples (e.g., those making up a relational
 * table) to be stored in a random access file. This implementation requires
 * that each tuple be packed into a fixed length byte array.
 */
@SuppressWarnings("all")
public class FileList extends AbstractList<Comparable[]> implements
		List<Comparable[]>, RandomAccess {
	/**
	 * File extension for data files.
	 */
	private static final String EXT = ".dat";

	/**
	 * The random access file that holds the tuples.
	 */
	private RandomAccessFile file;

	/**
	 * The table it is used to store.
	 */
	private final Table table;

	/**
	 * The number bytes required to store a "packed tuple"/record.
	 */
	private final int recordSize;

	/**
	 * Counter for the number of tuples in this list.
	 */
	private int nRecords = 0;

	/***************************************************************************
	 * Construct a FileList.
	 * 
	 * @param _table
	 *            the name of list
	 * @param _recordSize
	 *            the size of tuple in bytes.
	 */
	public FileList(Table _table, int _recordSize) {
		table = _table;
		recordSize = _recordSize;

		try {
			File temp = new File(table.getName() + EXT);
			if (temp.exists() && !temp.delete()){
				System.err.println("Please remove this file and try to run the project again. File --> " + temp.getAbsolutePath());
				System.exit(-1);
			}
			
			file = new RandomAccessFile(table.getName() + EXT, "rw");
			
		} catch (FileNotFoundException ex) {
			file = null;
			out.println("FileList.constructor: unable to open - " + ex);
		} // try
	} // constructor

	/***************************************************************************
	 * Add a new tuple into the file list by packing it into a record and
	 * writing this record to the random access file. Write the record either at
	 * the end-of-file or into an empty slot.
	 * 
	 * @param tuple
	 *            the tuple to add
	 * @return whether the addition succeeded
	 */
	public boolean add(Comparable[] tuple) {
		byte[] record = table.pack(tuple);

		if (record.length != recordSize) {
			out.println("FileList.add: wrong record size " + record.length);
			return false;
		} // if

		try {
			this.file.seek(this.file.length());
			this.file.write(record);
			
			nRecords++;
		} catch (IOException e) {
			System.err.println("There was an error while writing to file");
			e.printStackTrace();
		}

		return true;
	} // add

	/***************************************************************************
	 * Get the ith tuple by seeking to the correct file position and reading the
	 * record.
	 * 
	 * @param i
	 *            the index of the tuple to get
	 * @return the ith tuple
	 */
	public Comparable[] get(int i) {
		byte[] record = new byte[recordSize];

		try {
			file.seek(i * recordSize);
			file.read(record, 0, recordSize);
		} catch (IOException e) {
			System.err
					.println("There was an error while reading from the file.");
			e.printStackTrace();
		}

		return table.unpack(record);
	} // get

	/***************************************************************************
	 * Return the size of the file list in terms of the number of
	 * tuples/records.
	 * 
	 * @return the number of tuples
	 */
	public int size() {
		return nRecords;
	} // size

	/***************************************************************************
	 * Close the file.
	 */
	public void close() {
		try {
			file.close();
		} catch (IOException ex) {
			out.println("FileList.close: unable to close - " + ex);
		} // try
	} // close

} // FileList class
