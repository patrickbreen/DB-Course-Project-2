package internal.database;

/*******************************************************************************
 * @file  Table.java
 *
 * @author   John Miller
 */

import static java.lang.System.out;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

/*******************************************************************************
 * This class implements relational database tables (including attribute names,
 * domains and a list of tuples. Five basic relational algebra operators are
 * provided: project, select, union, minus and join. The insert data
 * manipulation operator is also provided. Missing are update and delete data
 * manipulation operators.
 * This project has been implemented using a pre-implemented project by Sina.
 */
@SuppressWarnings({ "rawtypes", "serial", "unchecked" })
public class Table implements Serializable, Cloneable {
	/**
	 * Debug flag, turn off once implemented
	 */
	private static final boolean DEBUG = true;

	/**
	 * Counter for naming temporary tables.
	 */
	private static int count = 0;

	/**
	 * Table name.
	 */
	private final String name;

	/**
	 * Array of attribute names.
	 */
	private final String[] attribute;

	/**
	 * Array of attribute domains: a domain may be integer types: Long, Integer,
	 * Short, Byte real types: Double, Float string types: Character, String
	 */
	private final Class[] domain;

	/**
	 * Collection of tuples (data storage).
	 */
	private final List<Comparable[]> tuples;

	/**
	 * Primary key.
	 */
	private final String[] key;

	/**
	 * Index into tuples (maps key to tuple).
	 */
	private final Map<KeyType, Comparable[]> index;

	/***************************************************************************
	 * Construct an empty table from the meta-data specifications.
	 * 
	 * @param _name
	 *            the name of the relation
	 * @param _attribute
	 *            the string containing attributes names
	 * @param _domain
	 *            the string containing attribute domains (data types)
	 * @param _key
	 *            the primary key
	 */
	public Table(String _name, String[] _attribute, Class[] _domain,
			String[] _key) {
		name = _name;
		attribute = _attribute;
		domain = _domain;
		key = _key;
		// tuples = new ArrayList<>(); // also try FileList, see below
		tuples = new FileList(this, tupleSize());
		index = new TreeMap<>(); // also try BPTreeMap, LinHash or ExtHash
	} // Table

	/***************************************************************************
	 * Construct an empty table from the raw string specifications.
	 * 
	 * @param name
	 *            the name of the relation
	 * @param attributes
	 *            the string containing attributes names
	 * @param domains
	 *            the string containing attribute domains (data types)
	 */
	public Table(String name, String attributes, String domains, String _key) {
		this(name, attributes.split(" "), findClass(domains.split(" ")), _key
				.split(" "));

		out.println("DDL> create table " + name + " (" + attributes + ")");
	} // Table

	/***************************************************************************
	 * Construct an empty table using the meta-data of an existing table.
	 * 
	 * @param tab
	 *            the table supplying the meta-data
	 * @param suffix
	 *            the suffix appended to create new table name
	 */
	public Table(Table tab, String suffix) {
		this(tab.name + suffix, tab.attribute, tab.domain, tab.key);
	} // Table

	/***************************************************************************
	 * Project the tuples onto a lower dimension by keeping only the given
	 * attributes. Check whether the original key is included in the projection.
	 * #usage movie.project ("title year studioNo")
	 * 
	 * @param attributeList
	 *            the attributes to project onto
	 * @return the table consisting of projected tuples
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public Table project(String attributeList) {
		out.println("RA> " + name + ".project (" + attributeList + ")");

		String[] pAttribute = attributeList.split(" ");
		int[] colPos = match(pAttribute);
		Class[] colDomain = extractDom(domain, colPos);
		String[] newKey = null;

		for (String s : this.key) {
			if (!Arrays.asList(pAttribute).contains(s)) {
				newKey = new String[1];
				break;
			}
		}

		if (newKey == null) {
			newKey = Arrays.copyOf(this.key, this.key.length);
		} else {
			newKey = Arrays.copyOf(pAttribute, pAttribute.length);
		}

		Table result = new Table(name + count++, pAttribute, colDomain, newKey);

		for (Comparable[] tup : tuples) {
			Comparable[] current = tup;
			Comparable[] keyVal = new Comparable[result.key.length];
			int[] cols = match(result.key);

			for (int j = 0; j < keyVal.length; j++) {
				keyVal[j] = current[cols[j]];
			}

			// Insert only those keys which are in table2 but not in table
			// one
			if (!(result.index.containsKey(new KeyType(keyVal)))) {
				result.insert(extractTup(tup, colPos));
			}
		} // for

		return result;
	} // project

/***************************************************************************
	 * Select the tuples satisfying the given condition. A condition is written
	 * as infix expression consists of 6 comparison operators: "==", "!=", "<",
	 * "<=", ">", ">=" 2 Boolean operators: "&", "|" (from high to low
	 * precedence) #usage movie.select ("1979 < year & year < 1990")
	 * 
	 * @param condition
	 *            the check condition for tuples
	 * @return the table consisting of tuples satisfying the condition
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public Table select(String condition) {
		out.println("RA> " + name + ".select (" + condition + ")");

		String[] postfix = infix2postfix(condition);
		Table result = new Table(name + count++, attribute, domain, key);

		for (Comparable[] tup : tuples) {
			if (evalTup(postfix, tup))
				result.insert(tup);
		} // for

		return result;
	} // select

	/***************************************************************************
	 * Union this table and table2. Check that the two tables are compatible.
	 * #usage movie.union (show)
	 * 
	 * @param table2
	 *            the rhs table in the union operation
	 * 
	 * @return the table representing the union (this U table2)
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public Table union(Table table2) {
		out.println("RA> " + name + ".union (" + table2.name + ")");

		Table result = new Table(name + count++, attribute, domain, key);

		// Compatibility check
		if (!this.compatible(table2)) {
			out.println("Incompatible Tables");
			return this;
		}

		else {
			// Adds first table as it is to the result
			int length1 = this.tuples.size();
			for (int i = 0; i < length1; i++) {
				result.insert(this.tuples.get(i));
			}

			for (int i = 0; i < table2.tuples.size(); i++) {
				Comparable[] current = (Comparable[]) table2.tuples.get(i);
				Comparable[] keyVal = new Comparable[table2.key.length];
				int[] cols = match(result.key);

				for (int j = 0; j < keyVal.length; j++) {
					keyVal[j] = current[cols[j]];
				}

				// Insert only those keys which are in table2 but not in table
				// one
				if (!(result.index.containsKey(new KeyType(keyVal)))) {
					result.insert(current);
				}
			}
		}

		return result;
	} // union

	/***************************************************************************
	 * Take the difference of this table and table2. Check that the two tables
	 * are compatible. #usage movie.minus (show)
	 * 
	 * @param table2
	 *            the rhs table in the minus operation
	 * 
	 * @return the table representing the difference (this - table2)
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public Table minus(Table table2) {
		out.println("RA> " + name + ".minus (" + table2.name + ")");

		Table result = new Table(name + count++, attribute, domain, key);

		// Compatibility check
		if (!this.compatible(table2)) {
			out.println("Incompatible Tables");
			return this;
		} else {
			// Check whether tuples in Table1 are Equal to tuples in Table2 or
			// not
			for (Comparable[] tup1 : this.tuples) {
				Comparable[] keyVal = new Comparable[table2.key.length];
				int[] cols = match(result.key);

				for (int j = 0; j < keyVal.length; j++) {
					keyVal[j] = tup1[cols[j]];
				}

				if (!table2.index.containsKey(new KeyType(keyVal))) {
					result.insert(tup1);
				}
			}
		}

		return result;
	} // minus

	/***************************************************************************
	 * Compare two tuples deeply
	 * 
	 * @param tup1
	 *            the first tuple
	 * @param tup2
	 *            the second tuple
	 * @return true if two arrays are equal, false if those two differ
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	@SuppressWarnings("unused")
	private boolean compareTuples(Comparable[] tup1, Comparable[] tup2) {
		for (int i = 0; i < tup1.length; i++) {
			if (tup1[i].compareTo(tup2[i]) != 0) {
				return false;
			}
		}
		return true;
	}

	/***************************************************************************
	 * Join this table and table2. If an attribute name appears in both tables,
	 * assume it is from the first table unless it is qualified with the first
	 * letter of the second table's name (e.g., "s."). In the result,
	 * disambiguate the attribute names in a similar way (e.g., prefix the
	 * second occurrence with "s_"). Caveat: the key parameter assumes joining
	 * the table with the foreign key (this) to the table containing the primary
	 * key (table2). #usage movie.join ("studioNo == name", studio); #usage
	 * movieStar.join ("name == s.name", starsIn);
	 * 
	 * @param condition
	 *            the join condition for tuples
	 * @param table2
	 *            the rhs table in the join operation
	 * @return the table representing the join (this |><| table2)
	 * @author Brandon, Milad, Patrick, Will
	 */
	public Table join(String condition, Table table2) {
		/*
		 * Step one - Check for valid input it should be of type
		 * attributeonename == attributetwoname", both attributes should be
		 * available in respective tables
		 * 
		 * Step two - Create appropriate resulting table. In case of duplicate
		 * coloumn names add a "s_" prefix
		 */

		Table emptyTable = new Table(name + count++, new String[0],
				new Class[0], key);
		// first check the condition input to make sure it is valid
		String[] splitCondition = condition.split(" ");
		if (splitCondition.length != 3) {
			out.println("Invalid join : format must be \"attribute1name == attribute2Name\"");
			return (emptyTable);
		}
		if (!(splitCondition[1].equalsIgnoreCase("=="))) {
			out.println("Invalid join : comparator must be \"==\"");
			return (emptyTable);
		}
		// make sure the first attribute in the condition exists in the first
		// table
		int firstValuePos = this.columnPos(splitCondition[0]);
		if (firstValuePos == -1) {
			out.println("Invalid join : first attribute does not exist in calling table");
			return (emptyTable);
		}
		// make sure the second attribute in the condition exists in the second
		// table
		int secondValuePos = table2.columnPos(splitCondition[2]);
		if (secondValuePos == -1) {
			// The second attribute might be name s.attributename, so check for
			// it too
			if (splitCondition[2].startsWith("s.")) {
				splitCondition[2] = splitCondition[2].substring(2);
				secondValuePos = table2.columnPos(splitCondition[2]);
			}
			// If still not found,then invalid join
			if (secondValuePos == -1) {
				out.println("Invalid join : second attribute does not exist in parameter table");
				return (emptyTable);
			}
		}
		// Validity check successful

		boolean isNull = false;
		// First figure out how big the table will be (which should = table1 +
		// table2)
		int firstTable = this.attribute.length;
		int secondTableSize = table2.attribute.length;
		int resultTableSize = firstTable + secondTableSize;
		// create appropriate variables to hold attributes and domains for the
		// new table
		String[] resultAttributes = new String[resultTableSize];
		Class[] resultDomains = new Class[resultTableSize];
		// initialize these arrays by adding every attribute of table1
		// and every attribute of table2 EXCEPT for the one named in the
		// condition
		int colCounter = 0;
		// handle the first table
		while (colCounter < firstTable) {
			resultAttributes[colCounter] = this.attribute[colCounter];
			resultDomains[colCounter] = this.domain[colCounter];
			colCounter++;
		}
		// handle the second table
		int table2Counter = (colCounter - firstTable);
		while (colCounter < resultTableSize) {
				// check against the first table's attributes to look for
				// prefixing requirements
				String s_ = "s_";
				String curAttr = table2.attribute[table2Counter];
				for (int i = 0; i < firstTable; i++) {
					String current1Attr = this.attribute[i];
					// if the attribute name already exists in table 1, add a
					// prefix to the table 2 attribute name
					if (current1Attr.equalsIgnoreCase(curAttr)) {
						curAttr = s_ + curAttr;
						break;
					}
				}
				// carry on
				resultAttributes[colCounter] = curAttr;
				resultDomains[colCounter] = table2.domain[table2Counter];
				// if it is the exception, leave the table2 counter, but back up
				// on the colCounter, then carry on without adding anything
			colCounter++;
			table2Counter++;
		}

		// create the new table
		Table result = new Table(name + count++, resultAttributes,
				resultDomains, key);

		// now we can insert the tuples into the table
		// go through every tuple of the first table
		int tupCounter = 0;
		if (this.tuples.size() == 0) {
			out.println("There are no tuples in the first table, therefore join results in empty table");
		}
		while (tupCounter < this.tuples.size()) {
			// make a new tuple
			Comparable[] newTup = new Comparable[resultTableSize];
			// go through the first table's attributes and assign as usual
			colCounter = 0;
			while (colCounter < firstTable) {
				int[] pos = new int[1];
				pos[0] = colCounter;
				Comparable[] thisTupVal = extractTup(
						this.tuples.get(tupCounter), pos);
				newTup[colCounter] = thisTupVal[0];
				colCounter++;
			}

			List<Comparable[]> reference = new ArrayList<>();
			for (int i = 0; i < table2.tuples.size(); i++){
				Comparable[] temp = table2.tuples.get(i);
				if (temp[secondValuePos].equals(newTup[firstValuePos])){
					reference.add(temp);
				}
			}
			
			for (Comparable[] tup : reference){
				int i = 0;
				while (colCounter + i < resultTableSize) {
					newTup[i + colCounter] = tup[i];
					i++;
				}
				result.insert(newTup);
			}
			
			tupCounter++;
		}

		// all done
		return result;
	} // join

	/***************************************************************************
	 * Insert a tuple to the table. #usage movie.insert ("'Star_Wars'", 1977,
	 * 124, "T", "Fox", 12345)
	 * 
	 * @param tup
	 *            the array of attribute values forming the tuple
	 * @return whether insertion was successful
	 */
	public boolean insert(Comparable[] tup) {
		out.println("DML> insert into " + name + " values ( "
				+ Arrays.toString(tup) + " )");

		if (typeCheck(tup, domain)) {
			tuples.add(tup);
			Comparable[] keyVal = new Comparable[key.length];
			int[] cols = match(key);
			for (int j = 0; j < keyVal.length; j++)
				keyVal[j] = tup[cols[j]];
			index.put(new KeyType(keyVal), tup);
			return true;
		} else {
			return false;
		} // if
	} // insert

	/***************************************************************************
	 * Get the name of the table.
	 * 
	 * @return the table's name
	 */
	public String getName() {
		return name;
	} // getName

	/***************************************************************************
	 * Print the table.
	 */
	public void print() {
		out.println("\n Table " + name);

		out.print("|-");
		for (int i = 0; i < attribute.length; i++)
			out.print("---------------");
		out.println("-|");
		out.print("| ");
		for (String a : attribute)
			out.printf("%15s", a);
		out.println(" |");

		if (DEBUG) {
			out.print("|-");
			for (int i = 0; i < domain.length; i++)
				out.print("---------------");
			out.println("-|");
			out.print("| ");
			for (Class d : domain)
				out.printf("%15s", d.getSimpleName());
			out.println(" |");
		} // if

		out.print("|-");
		for (int i = 0; i < attribute.length; i++)
			out.print("---------------");
		out.println("-|");
		for (Comparable[] tup : tuples) {
			out.print("| ");
			for (Comparable attr : tup)
				out.printf("%15s", attr);
			out.println(" |");
		} // for
		out.print("|-");
		for (int i = 0; i < attribute.length; i++)
			out.print("---------------");
		out.println("-|");
	} // print

	/***************************************************************************
	 * Determine whether the two tables (this and table2) are compatible, i.e.,
	 * have the same number of attributes each with the same corresponding
	 * domain.
	 * 
	 * @param table2
	 *            the rhs table
	 * 
	 * @return whether the two tables are compatible
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private boolean compatible(Table table2) {
		// Two tables are union compatible if
		// 1) They have same number of columns
		// 2) Same domain type for each relative domain

		// Checking for case 1
		if (this.domain.length != table2.domain.length) {
			return false;
		}
		for (int i = 0; i < this.domain.length; i++) {
			int[] pos = new int[1];
			pos[0] = i;
			Class[] tableone = Table.extractDom(this.domain, pos);
			Class[] tabletwo = Table.extractDom(table2.domain, pos);
			// Checking for case 2
			if (tableone[0] != tabletwo[0]) {
				return (false);
			}
		}

		return true;
	} // compatible

	/***************************************************************************
	 * Return the column position for the given column/attribute name.
	 * 
	 * @param column
	 *            the given column/attribute name
	 * @return the column index position
	 */
	private int columnPos(String column) {
		for (int j = 0; j < attribute.length; j++) {
			if (column.equals(attribute[j]))
				return j;
		} // for

		out.println("columnPos: error - " + column + " not found");
		return -1; // column name not found in this table
	} // columnPos

	/***************************************************************************
	 * Return all the column positions for the given column/attribute names.
	 * 
	 * @param columns
	 *            the array of column/attribute names
	 * @return the array of column index positions
	 */
	private int[] match(String[] columns) {
		int[] colPos = new int[columns.length];

		for (int i = 0; i < columns.length; i++) {
			colPos[i] = columnPos(columns[i]);
		} // for

		return colPos;
	} // match

	/**************************************************************************
	 * Parse the operand into a Comparable object
	 * 
	 * @param inputStr
	 *            The input string
	 * 
	 * @param inputType
	 *            Class type of the operand
	 * 
	 * @return the Comparable object
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private static Comparable parseOperand(String inputStr, Class inputType) {

		if (inputType == String.class) {
			inputStr = inputStr.replaceAll("'", "");
			return inputStr;
		}

		if (inputType == Character.class)
			return inputStr.charAt(0);

		if (inputType == Byte.class)
			return Byte.parseByte(inputStr);

		if (inputType == Short.class)
			return Short.parseShort(inputStr);

		if (inputType == Integer.class)
			return Integer.parseInt(inputStr);

		if (inputType == Long.class)
			return Long.parseLong(inputStr);

		if (inputType == Float.class)
			return Float.parseFloat(inputStr);

		if (inputType == Double.class)
			return Double.parseDouble(inputStr);

		return null;
	}

	/***************************************************************************
	 * Check whether the tuple satisfies the condition. Use a stack-based
	 * postfix expression evaluation algorithm.
	 * 
	 * @param postfix
	 *            the postfix expression for the condition
	 * 
	 * @param tup
	 *            the tuple to check
	 * 
	 * @return whether to keep the tuple
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private boolean evalTup(String[] postfix, Comparable[] tup) {
		if (postfix == null)
			return true;
		Stack<Comparable<?>> s = new Stack<>();

		Class typeOfOperand = String.class;
		for (String token : postfix) {
			if (operator2priority(token) != 0) {
				Comparable operand1 = s.pop();
				Comparable operand2 = s.pop();
				s.push(evaluate(operand1, operand2, token));
				continue;
			}
			if (Arrays.asList(this.attribute).contains(token)) {
				s.push(tup[Arrays.asList(this.attribute).indexOf(token)]);
				typeOfOperand = this.domain[Arrays.asList(this.attribute)
						.indexOf(token)];
				continue;
			} else {
				s.push(parseOperand(token, typeOfOperand));
				typeOfOperand = String.class;
				continue;
			}
		}

		return (Boolean) s.pop();
	} // evalTup

	/***************************************************************************
	 * Pack tuple tup into a record/byte-buffer (array of bytes).
	 * 
	 * @param tup
	 *            the array of attribute values forming the tuple
	 * @return a tuple packed into a record/byte-buffer
	 */
	public byte[] pack(Comparable[] tup) {
		byte[] record = new byte[tupleSize()];
		byte[] b = null;
		int s = 0;
		int i = 0;
		for (int j = 0; j < this.domain.length; j++) {
			switch (this.domain[j].getName()) {
			case "java.lang.Byte":
				b = new byte[1];
				b[0] = (Byte) tup[j];
				s = 1;
				break;
			case "java.lang.Short":
				b = Conversions.short2ByteArray((Short) tup[j]);
				s = 2;
				break;
			case "java.lang.Integer":
				b = Conversions.int2ByteArray((Integer) tup[j]);
				s = 4;
				break;
			case "java.lang.Long":
				b = Conversions.long2ByteArray((Long) tup[j]);
				s = 8;
				break;
			case "java.lang.Float":
				b = Conversions.float2ByteArray((Float) tup[j]);
				s = 4;
				break;
			case "java.lang.Double":
				b = Conversions.double2ByteArray((Double) tup[j]);
				s = 8;
				break;
			case "java.lang.Character":
				b = ((Character) tup[j]).toString().getBytes();
				s = 1;
				break;
			case "java.lang.String":
				String len = String.format("%02d", ((String) tup[j]).length());
				byte[] temp = (len + (String) tup[j]).getBytes();
				s = 66;
				b = new byte[s];
				System.arraycopy(temp, 0, b, 0, temp.length);
				break;
			}
			if (b == null) {
				out.println("Table.pack: byte array b is null");
				return null;
			}
			for (int k = 0; k < s; k++) {
				record[i++] = b[k];
			}
		}
		return record;
	}

	/***************************************************************************
	 * Unpack the record/byte-buffer (array of bytes) to reconstruct a tuple.
	 * 
	 * @param record
	 *            the byte-buffer in which the tuple is packed
	 * @return an unpacked tuple
	 */
	public Comparable[] unpack(byte[] record) {
		Comparable[] tuple = new Comparable[this.domain.length];

		byte[] b = null;
		int i = 0;
		int s = 0;
		for (int j = 0; j < this.domain.length; j++) {
			switch (this.domain[j].getName()) {
			case "java.lang.Byte":
				tuple[j] = (byte) record[i];
				i++;
				break;
			case "java.lang.Short":
				s = 2;
				b = new byte[s];
				for (int k = 0; k < s; k++) {
					b[k] = record[i + k];
				}
				i += s;
				tuple[j] = Conversions.byteArray2Short(b);
				break;
			case "java.lang.Integer":
				s = 4;
				b = new byte[s];
				for (int k = 0; k < s; k++) {
					b[k] = record[i + k];
				}
				i += s;
				tuple[j] = Conversions.byteArray2Int(b);
				break;
			case "java.lang.Long":
				s = 8;
				b = new byte[s];
				for (int k = 0; k < s; k++) {
					b[k] = record[i + k];
				}
				i += s;
				tuple[j] = Conversions.byteArray2Long(b);
				break;
			case "java.lang.Float":
				s = 4;
				b = new byte[s];
				for (int k = 0; k < s; k++) {
					b[k] = record[i + k];
				}
				i += s;
				tuple[j] = Conversions.byteArray2Float(b);
				break;
			case "java.lang.Double":
				s = 8;
				b = new byte[s];
				for (int k = 0; k < s; k++) {
					b[k] = record[i + k];
				}
				i += s;
				tuple[j] = Conversions.byteArray2Double(b);
				break;
			case "java.lang.Character":
				tuple[j] = (char) record[i];
				i++;
				break;
			case "java.lang.String":
				s = 66;
				b = new byte[s];
				for (int k = 0; k < s; k++) {
					b[k] = record[i + k];
				}
				i += s;
				tuple[j] = new String(b);
				int len = Integer.parseInt(((String) tuple[j]).substring(0, 2));
				tuple[j] = ((String) tuple[j]).substring(2, len + 2);
				break;
			}
			if (tuple[j] == null) {
				out.println("Table.pack: There was an error here!");
				return null;
			}
		}

		return tuple;
	} // unpack

	/***************************************************************************
	 * Determine the size of tuples in this table in terms of the number of
	 * bytes required to store it in a record/byte-buffer.
	 * 
	 * @return the size of packed-tuples in bytes
	 */
	private int tupleSize() {
		int s = 0;

		for (int j = 0; j < domain.length; j++) {
			switch (domain[j].getName()) {
			case "java.lang.Byte":
				s += 1;
				break;
			case "java.lang.Short":
				s += 2;
				break;
			case "java.lang.Integer":
				s += 4;
				break;
			case "java.lang.Long":
				s += 8;
				break;
			case "java.lang.Float":
				s += 4;
				break;
			case "java.lang.Double":
				s += 8;
				break;
			case "java.lang.Character":
				s += 1;
				break;
			case "java.lang.String":
				s += 66;
				break;
			} // if
		} // for

		return s;
	} // tupleSize

	// ------------------------ Static Utility Methods
	// --------------------------

	/***************************************************************************
	 * Check the size of the tuple (number of elements in list) as well as the
	 * type of each value to ensure it is from the right domain.
	 * 
	 * @param tup
	 *            the tuple as a list of attribute values
	 * @param dom
	 *            the domains (attribute types)
	 * @return whether the tuple has the right size and values that comply with
	 *         the given domains
	 */
	private static boolean typeCheck(Comparable[] tup, Class[] dom) {
		if (tup.length != dom.length)
			return false;
		for (int i = 0; i < tup.length; i++) {
			if (!tup[i].getClass().equals(dom[i]))
				return false;
		}
		return true;
	} // typeCheck

	/***************************************************************************
	 * This method will take two values and apply the operator between them and
	 * return the result.
	 * 
	 * @param value1
	 *            The first value
	 * @param value2
	 *            the second value
	 * @param operator
	 *            the operator which is applied
	 * @return the result of value1 (operator) value2
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private static Comparable evaluate(Comparable value1, Comparable value2,
			String operator) {

		switch (operator) {
		case "==":
			return (value1.compareTo(value2) == 0);
		case "!=":
			return (value1.compareTo(value2) != 0);
		case "<":
			return (value1.compareTo(value2) > 0);
		case "<=":
			return (value1.compareTo(value2) >= 0);
		case ">":
			return (value1.compareTo(value2) < 0);
		case ">=":
			return (value1.compareTo(value2) <= 0);
		case "&":
			return ((Boolean) value1 && (Boolean) value2);
		case "|":
			return ((Boolean) value1 || (Boolean) value2);
		default:
			return null;
		}

	}

	/***************************************************************************
	 * This method gets an operator string and return the priority for that
	 * operator
	 * 
	 * @param inputStr
	 *            The input operator
	 * 
	 * @return Priority for that operator
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private static Integer operator2priority(String inputStr) {
		HashMap<String, Integer> operators = new HashMap<>();
		operators.put("==", 8);
		operators.put("!=", 7);
		operators.put("<", 6);
		operators.put("<=", 5);
		operators.put(">", 4);
		operators.put(">=", 3);
		operators.put("&", 2);
		operators.put("|", 1);

		if (operators.containsKey(inputStr)) {
			return operators.get(inputStr);
		} else {
			return 0;
		}
	}

	/***************************************************************************
	 * This method is used to convert a priority value to the corresponding
	 * operator string
	 * 
	 * @param inputInt
	 *            The priority
	 * 
	 * @return The operator string
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private static String priority2operator(Integer inputInt) {
		HashMap<Integer, String> operators = new HashMap<>();
		operators.put(8, "==");
		operators.put(7, "!=");
		operators.put(6, "<");
		operators.put(5, "<=");
		operators.put(4, ">");
		operators.put(3, ">=");
		operators.put(2, "&");
		operators.put(1, "|");

		if (operators.containsKey(inputInt)) {
			return operators.get(inputInt);
		} else {
			return null;
		}
	}

/***************************************************************************
	 * Convert an untokenized infix expression to a tokenized postfix
	 * expression. This implementation does not handle parentheses ( ). Ex:
	 * "1979 < year & year < 1990" --> { "1979", "year", "<", "year", "1990",
	 * "<", "&" }
	 * 
	 * "==", "!=", "<",
	 * "<=", ">", ">=" 2 Boolean operators: "&", "|" (from high to low
	 * precedence
	 * 
	 * @param condition
	 *            the untokenized infix condition
	 *            
	 * @return resultant tokenized postfix expression
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	private static String[] infix2postfix(String condition) {
		if (condition == null || condition.trim() == "")
			return null;
		String[] infix = condition.split(" ");
		String[] postfix = new String[infix.length];

		Stack<Integer> operatorStack = new Stack<Integer>();
		int count = 0;
		for (String str : infix) {
			if (operator2priority(str) == 0) {
				postfix[count] = str;
				count++;
				continue;
			} else {
				if (operatorStack.isEmpty()) {
					operatorStack.push(operator2priority(str));
					continue;
				} else {
					if (operatorStack.lastElement() <= operator2priority(str)) {
						operatorStack.push(operator2priority(str));
						continue;
					} else {
						while (operatorStack.lastElement() > operator2priority(str)) {
							postfix[count] = priority2operator(operatorStack
									.pop());
							count++;
							if (operatorStack.isEmpty())
								break;
						}
						operatorStack.push(operator2priority(str));
						continue;
					}
				}
			}
		}

		while (!operatorStack.isEmpty()) {
			postfix[count] = priority2operator(operatorStack.pop());
			count++;
		}

		return postfix;
	} // infix2postfix

	/***************************************************************************
	 * Find the classes in the "java.lang" package with given names.
	 * 
	 * @param className
	 *            the array of class name (e.g., {"Integer", "String"})
	 * @return the array of Java classes for the corresponding names
	 */
	private static Class[] findClass(String[] className) {
		Class[] classArray = new Class[className.length];

		for (int i = 0; i < className.length; i++) {
			try {
				classArray[i] = Class.forName("java.lang." + className[i]);
			} catch (ClassNotFoundException ex) {
				out.println("findClass: " + ex);
			} // try
		} // for

		return classArray;
	} // findClass

	/***************************************************************************
	 * Extract the corresponding domains from the group.
	 * 
	 * @param group
	 *            where to extract from
	 * @param colPos
	 *            the column positions to extract
	 * @return the extracted domains
	 */
	private static Class[] extractDom(Class[] group, int[] colPos) {
		Class[] dom = new Class[colPos.length];

		for (int j = 0; j < colPos.length; j++) {
			dom[j] = group[colPos[j]];
		} // for

		return dom;
	} // extractDom

	/***************************************************************************
	 * Extract the corresponding attribute values from the group.
	 * 
	 * @param group
	 *            where to extract from
	 * @param colPos
	 *            the column positions to extract
	 * @return the extracted attribute values
	 */
	private static Comparable[] extractTup(Comparable[] group, int[] colPos) {
		Comparable[] tup = new Comparable[colPos.length];

		int tupIndex = 0;
		for (Integer i : colPos) {
			tup[tupIndex] = group[i];
			tupIndex++;
		}

		return tup;
	} // extractTup

	/**
	 * @return the attribute
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public String[] getAttribute() {
		return attribute;
	}

	/**
	 * @return the domain
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public Class[] getDomain() {
		return domain;
	}

	/****************
	 * @return Number of tuples for a Table
	 * 
	 * @author Brandon, Milad, Patrick, Will
	 */
	public int getTupleCount() {
		return tuples.size();
	}

} // Table class
