package internal.database;

/*******************************************************************************
 * @file ExtHash.java
 *
 * @author  John Miller
 */

import java.io.*;
import java.lang.reflect.Array;

import static java.lang.System.out;

import java.util.*;
import java.util.Map.Entry;

import sun.util.logging.resources.logging;

import com.sun.corba.se.impl.interceptors.SlotTableStack;

/*******************************************************************************
 * This class provides hash maps that use the Extendable Hashing algorithm.
 * Buckets are allocated and stored in a hash table and are referenced using
 * directory dir.
 */
@SuppressWarnings("all")
public class ExtHash<K, V> extends AbstractMap<K, V> implements Serializable,
		Cloneable, Map<K, V> {
	/**
	 * The number of slots (for key-value pairs) per bucket.
	 */
	private static final int SLOTS = 4;

	/**
	 * The class for type K.
	 */
	private final Class<K> classK;

	/**
	 * The class for type V.
	 */
	private final Class<V> classV;

	/***************************************************************************
	 * This inner class defines buckets that are stored in the hash table.
	 */
	private class Bucket {
		int localMod;
		int nKeys;
		K[] key;
		V[] value;

		@SuppressWarnings("unchecked")
		Bucket(int localMod) {
			nKeys = 0;
			key = (K[]) Array.newInstance(classK, SLOTS);
			value = (V[]) Array.newInstance(classV, SLOTS);
			this.localMod = localMod;
		} // constructor
	} // Bucket inner class

	/**
	 * The hash table storing the buckets (buckets in physical order)
	 */
	private final List<Bucket> hTable;

	/**
	 * The directory providing access paths to the buckets (buckets in logical
	 * oder)
	 */
	private final List<Bucket> dir;

	/**
	 * The modulus for hashing (= 2^D) where D is the global depth
	 */
	private int mod;

	/**
	 * The number of buckets
	 */
	private int nBuckets;

	/**
	 * Counter for the number buckets accessed (for performance testing).
	 */
	private int count = 0;

	/***************************************************************************
	 * Construct a hash table that uses Extendible Hashing.
	 * 
	 * @param classK
	 *            the class for keys (K)
	 * @param classV
	 *            the class for values (V)
	 * @param initSize
	 *            the initial number of buckets (a power of 2, e.g., 4)
	 * @Author: Brandon, Milad, Patrick, Will           
	 */
	public ExtHash(Class<K> _classK, Class<V> _classV, int initSize) {
		
		if ((initSize & (initSize - 1)) != 0.0){
			System.err.println("As mentioned in the comments, the init size should be a power of two.");
			System.exit(-1);
		}
		
		classK = _classK;
		classV = _classV;
		hTable = new ArrayList<>(); // for bucket storage
		dir = new ArrayList<>(); // for bucket access
		mod = nBuckets = initSize;
		
		for (int i = 0; i < initSize; i++){
			Bucket temp = new Bucket(initSize);
			hTable.add(temp);
			dir.add(temp);
		}
		
	} // ExtHash

	/***************************************************************************
	 * Return a set containing all the entries as pairs of keys and values.
	 * 
	 * @return the set view of the map
	 * 
	 * @Author: Brandon, Milad, Patrick, Will
	 */
	public Set<Map.Entry<K, V>> entrySet() {
		Set<Map.Entry<K, V>> enSet = new HashSet<>();

		for (Bucket b : hTable){
			for (int i = 0; i < b.nKeys; i++){
				enSet.add(new SimpleEntry(b.key[i], b.value[i]));
			}
		}
		
		return enSet;
	} // entrySet

	/***************************************************************************
	 * Given the key, look up the value in the hash table.
	 * 
	 * @param key
	 *            the key used for look up
	 * @return the value associated with the key
	 * 
	 * @Author: Brandon, Milad, Patrick, Will
	 */
	public V get(Object key) {
		int i = h(key);
		Bucket b = dir.get(i);
		count++;

		for (int j = 0; j < b.nKeys; j++){
			if (((K)key).equals(b.key[j])){
				return b.value[j];
			}
		}

		return null;
	} // get

	public boolean containsKey(Object key){
		if (get(key) == null){
			return false;
		}else{
			return true;
		}
	}
	
	/***************************************************************************
	 * Put the key-value pair in the hash table.
	 * 
	 * @param key
	 *            the key to insert
	 * @param value
	 *            the value to insert
	 * @return null (not the previous value)
	 * 
	 * @Author: Brandon, Milad, Patrick, Will
	 */
	public V put(K key, V value) {
		int i = h(key);
		Bucket b = dir.get(i);
		//out.println("i : " + i + " " + key);
		count++;
		//there is space, do simple insert
		if (b.nKeys < SLOTS){
			b.key[b.nKeys] = key;
			b.value[b.nKeys] = value;
			b.nKeys++;
		}else{
			if (b.localMod == mod){
				//bucket has equal local mod to the table, must double and rehash entire table
				mod *= 2;
				nBuckets++;
				
				b.localMod = mod;
				Bucket newBucket = new Bucket(mod);
				hTable.add(newBucket);
				//out.println("mod : " + mod);

				for (int j = 0; j < mod / 2; j++){
					if (j != i && j < hTable.size()){
						dir.add(hTable.get(j));
					}else{
						dir.add(new Bucket(mod));
					}
				}
				redistribute(b, key, value);
				
			}else if (b.localMod < mod){
				//just need to double and rehash specific bucket
				b.localMod *= 2;
				Bucket newBucket = new Bucket(b.localMod);
				
				hTable.add(newBucket);
				
				dir.set(i, newBucket);
				
				redistribute(b, key, value);
				//out.println("hTable size : " + hTable.size());
			}else{
				//should never happen
				System.err.println("There was an error detected in the indexing system :D");
				System.exit(-1);
			}
		}

		return null;
	} // put

	
	/***************************************************************************
	 * This method is used to redistribute the items in the overflowed bucket and the new values which needs to be added to the table.
	 * @param b the overflowed bucket
	 * @param key the new key 
	 * @param value the new value
	 * @Author: Brandon, Milad, Patrick, Will
	 */
	private void redistribute(Bucket b, K key, V value){
		List<K> keys = new ArrayList<>();
		List<V> values = new ArrayList<>();
		
		keys.addAll(Arrays.asList(b.key));
		values.addAll(Arrays.asList(b.value));
		
		keys.add(key);
		values.add(value);
		
		b.nKeys = 0;
		
		for (int i = 0; i < keys.size(); i++){
			put(keys.get(i), values.get(i));
		}
	}
	
	/***************************************************************************
	 * Return the size (SLOTS * number of buckets) of the hash table.
	 * 
	 * @return the size of the hash table
	 */
	public int size() {
		return SLOTS * nBuckets;
	} // size

	/***************************************************************************
	 * Print the hash table.
	 * 
	 * @Author: Brandon, Milad, Patrick, Will
	 */
	private void print() {
		out.println("Hash Table (Extendable Hashing)");
		out.println("-------------------------------------------");

		for (int i = 0; i < dir.size(); i++){
			Bucket b = dir.get(i);
			System.out.print(i + ":\t");
			
			for (int j = 0; j < b.nKeys; j++){
				System.out.print("[ " + b.key[j] + " ] ");	
			}
			System.out.println();
		}

		out.println("-------------------------------------------");
		
		int N = 0;
		for (int i = 0; i < dir.size(); i++){
			Bucket b = dir.get(i);
			
			for (int j = 0; j < b.nKeys; j++){
				System.out.println("Key = " + b.key[j] + " value = " + b.value[j]);
				N++;
			}
		}
		
		out.println("-------------------------------------------");
		
		System.out.println("Average number of buckets access --> " + ((double) count / (double) N));
	} // print

	/***************************************************************************
	 * Hash the key using the hash function.
	 * 
	 * @param key
	 *            the key to hash
	 * @return the location of the directory entry referencing the bucket
	 */
	private int h(Object key) {
		int temp = ((K)key).hashCode() % mod;
		if (temp < 0){
			temp += mod;
		}
		return temp;
	} // h

	/***************************************************************************
	 * The main method used for testing.
	 * 
	 * @param the
	 *            command-line arguments (args [0] gives number of keys to
	 *            insert)
	 *            
	 *  @Author: Brandon, Milad, Patrick, Will
	 */
	public static void main(String[] args) {
		ExtHash<Integer, Integer> ht = new ExtHash<>(Integer.class, Integer.class, 2);
		
		for (int i = 700; i >= 600; i--){
			//if (i == 14 || i == 8 || i == 13 || i == 10){
			//	continue;
			//}
			ht.put(new Integer(i), new Integer(i * 100));
		}
		
		ht.print();
		
		int i = 0;
		for (Map.Entry<?, ?> e : ht.entrySet()){
			System.out.println(i + ":\t" + "Key = " + e.getKey() + "  Value = " + e.getValue());
			i++;
		}
		
	} // main

} // ExtHash class
